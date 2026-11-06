package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.TeapotBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.item.ItemKeys;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.Localization;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeapotControllerTest {
    private static final String SAVE = "kaleidoscopecookery:teapot";
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private final Item empty = mock(Item.class);
    private final Player player = mock(Player.class);
    private final Item bucket = mock(Item.class);
    private final Item filled = mock(Item.class);
    private MockedStatic<Item> items;
    private MockedStatic<BlockEntityNbt> nbt;
    private MockedStatic<InventoryUtils> inventory;
    private MockedStatic<Localization> messages;
    private MockedStatic<TeapotBar> bars;
    private MockedStatic<TrackedPlayers> tracking;
    private MockedStatic<Config> config;
    private MockedConstruction<TeapotElement> display;
    private BlockEntity entity;
    private TeapotBehavior behavior;

    @BeforeEach void openPot() {
        when(empty.isEmpty()).thenReturn(true);
        when(bucket.id()).thenReturn(ItemKeys.BUCKET);
        when(bucket.vanillaId()).thenReturn(ItemKeys.BUCKET);
        when(bucket.count()).thenReturn(1);
        when(filled.count()).thenReturn(1);
        items = mockStatic(Item.class);
        items.when(Item::empty).thenReturn(empty);
        nbt = mockStatic(BlockEntityNbt.class);
        nbt.when(() -> BlockEntityNbt.getItem(any(), anyString(), anyInt())).thenReturn(empty);
        inventory = mockStatic(InventoryUtils.class);
        inventory.when(() -> InventoryUtils.createOrEmpty(ItemKeys.BUCKET)).thenReturn(bucket);
        messages = mockStatic(Localization.class);
        messages.when(() -> Localization.component(anyString())).thenReturn(Component.empty());
        bars = mockStatic(TeapotBar.class);
        bars.when(() -> TeapotBar.build(any())).thenReturn("");
        tracking = mockStatic(TrackedPlayers.class);
        config = mockStatic(Config.class);
        display = mockConstruction(TeapotElement.class);
        behavior = mock(TeapotBehavior.class);
        when(behavior.createBlockEntityController(any())).thenReturn(mock(BlockEntityController.class));
        ImmutableBlockState state = mock(ImmutableBlockState.class);
        when(state.behavior()).thenReturn(behavior);
        entity = new BlockEntity(new BlockPos(8, 64, 8), state);
        entity.world = mock(CEWorld.class, RETURNS_DEEP_STUBS);
    }

    @AfterEach void closePot() {
        display.close();
        config.close();
        tracking.close();
        bars.close();
        messages.close();
        inventory.close();
        nbt.close();
        items.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"water", "lava", "milk"})
    void oneBucketSurvivesSaveAndCanOnlyBeTakenOnce(String name) {
        Key fluid = Key.of("minecraft:" + name);
        Key filledId = Key.of("minecraft:" + name + "_bucket");
        when(filled.id()).thenReturn(filledId);
        when(filled.vanillaId()).thenReturn(filledId);
        inventory.when(() -> InventoryUtils.createOrEmpty(filledId)).thenReturn(filled);
        var pot = new TeapotController(entity, behavior);
        assertTrue(pot.addFluidBucket(player, HAND, filled, fluid));
        assertFalse(pot.addFluidBucket(player, HAND, filled, fluid));
        CompoundTag saved = new CompoundTag();
        pot.saveCustomData(saved);
        assertEquals(fluid.asString(), saved.getCompound(SAVE).getString("fluid"));
        var loaded = new TeapotController(entity, behavior);
        loaded.loadCustomData(saved);
        assertTrue(loaded.drainToBucket(player, HAND, bucket));
        assertFalse(loaded.drainToBucket(player, HAND, bucket));
        inventory.verify(() -> InventoryUtils.shrinkHeld(player, filled, 1), times(1));
        inventory.verify(() -> InventoryUtils.giveOrHold(player, HAND, bucket), times(1));
        inventory.verify(() -> InventoryUtils.shrinkHeld(player, bucket, 1), times(1));
        inventory.verify(() -> InventoryUtils.giveOrHold(player, HAND, filled), times(1));
    }

    @Test void unsupportedFluidDoesNotConsumeTheBucket() {
        var pot = new TeapotController(entity, behavior);
        assertFalse(pot.addFluidBucket(player, HAND, filled, Key.of("test:unknown")));
        inventory.verifyNoInteractions();
    }

    @Test void missingReturnItemPreservesTheMilkAndHeldBucket() {
        when(filled.id()).thenReturn(Key.of("minecraft:milk_bucket"));
        when(filled.vanillaId()).thenReturn(Key.of("minecraft:milk_bucket"));
        var pot = new TeapotController(entity, behavior);
        assertTrue(pot.addFluidBucket(player, HAND, filled, Key.of("minecraft:milk")));
        inventory.clearInvocations();
        inventory.when(() -> InventoryUtils.createOrEmpty(Key.of("minecraft:milk_bucket"))).thenReturn(empty);
        assertFalse(pot.drainToBucket(player, HAND, bucket));
        CompoundTag saved = new CompoundTag();
        pot.saveCustomData(saved);
        assertEquals("minecraft:milk", saved.getCompound(SAVE).getString("fluid"));
        inventory.verify(() -> InventoryUtils.shrinkHeld(any(), any(), anyInt()), never());
        inventory.when(() -> InventoryUtils.createOrEmpty(Key.of("minecraft:milk_bucket"))).thenReturn(filled);
        assertTrue(pot.drainToBucket(player, HAND, bucket));
        inventory.verify(() -> InventoryUtils.shrinkHeld(player, bucket, 1), times(1));
    }

    @Test void creativeMilkRoundTripDoesNotCreateOrConsumeItems() {
        when(player.canInstabuild()).thenReturn(true);
        when(filled.id()).thenReturn(Key.of("minecraft:milk_bucket"));
        when(filled.vanillaId()).thenReturn(Key.of("minecraft:milk_bucket"));
        var pot = new TeapotController(entity, behavior);
        assertTrue(pot.addFluidBucket(player, HAND, filled, Key.of("minecraft:milk")));
        assertTrue(pot.drainToBucket(player, HAND, bucket));
        inventory.verifyNoInteractions();
    }

    @Test void wrongHeldBucketCannotCreateOrDrainMilk() {
        when(filled.id()).thenReturn(ItemKeys.WATER_BUCKET);
        var pot = new TeapotController(entity, behavior);
        assertFalse(pot.addFluidBucket(player, HAND, filled, Key.of("minecraft:milk")));
        inventory.verifyNoInteractions();
        when(filled.id()).thenReturn(Key.of("minecraft:milk_bucket"));
        assertTrue(pot.addFluidBucket(player, HAND, filled, Key.of("minecraft:milk")));
        inventory.clearInvocations();
        assertFalse(pot.drainToBucket(player, HAND, filled));
        inventory.verifyNoInteractions();
    }

    @Test void missingEmptyBucketDoesNotFillThePot() {
        when(filled.id()).thenReturn(Key.of("minecraft:milk_bucket"));
        inventory.when(() -> InventoryUtils.createOrEmpty(ItemKeys.BUCKET)).thenReturn(empty);
        var pot = new TeapotController(entity, behavior);
        assertFalse(pot.addFluidBucket(player, HAND, filled, Key.of("minecraft:milk")));
        CompoundTag saved = new CompoundTag();
        pot.saveCustomData(saved);
        assertFalse(saved.getCompound(SAVE).containsKey("fluid"));
        inventory.verify(() -> InventoryUtils.shrinkHeld(any(), any(), anyInt()), never());
    }

    @ParameterizedTest
    @ValueSource(ints = {TeapotController.PROCESSING, TeapotController.FINISHED})
    void brewingAndFinishedTeaCannotBeDrainedToABucket(int status) {
        CompoundTag tea = new CompoundTag();
        tea.putString("fluid", "minecraft:milk");
        tea.putInt("status", status);
        CompoundTag saved = new CompoundTag();
        saved.put(SAVE, tea);
        var pot = new TeapotController(entity, behavior);
        pot.loadCustomData(saved);
        assertFalse(pot.drainToBucket(player, HAND, bucket));
        inventory.verifyNoInteractions();
    }
}
