package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.MillstoneBehavior;
import net.kaleidoscope.cookery.recipe.*;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MillstoneControllerTest {
    private static final String SAVE = "kaleidoscopecookery:millstone";
    private final FoodRecipeRegistry recipes = FoodRecipeRegistry.instance();
    private final Item empty = mock(Item.class);
    private final Item raw = mock(Item.class);
    private final Item output = mock(Item.class);
    private final ItemStack stack = mock(ItemStack.class);
    private final World world = mock(World.class);
    private MockedStatic<Item> items;
    private MockedStatic<BlockEntityNbt> nbt;
    private MockedStatic<Config> config;
    private MockedStatic<InventoryUtils> inventory;
    private MockedStatic<EventUtils> events;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<MillstoneElement> displays;
    private MillstoneController mill;
    private Furniture furniture;
    private MillstoneBehavior behavior;

    @BeforeEach void prepare() {
        when(empty.isEmpty()).thenReturn(true);
        when(raw.id()).thenReturn(Key.of("minecraft:bone"));
        when(raw.count()).thenReturn(1);
        when(raw.copy()).thenReturn(raw);
        when(raw.copyWithCount(1)).thenReturn(raw);
        when(output.count()).thenReturn(5);
        when(output.copyWithCount(5)).thenReturn(output);
        Object nms = new Object();
        when(output.minecraftItem()).thenReturn(nms);
        when(stack.clone()).thenReturn(stack);
        // Material.isAir consults a live Paper registry; the fixture only needs its answer.
        when(stack.getType()).thenReturn(mock(org.bukkit.Material.class));
        when(stack.getAmount()).thenReturn(5);
        items = mockStatic(Item.class);
        items.when(Item::empty).thenReturn(empty);
        nbt = mockStatic(BlockEntityNbt.class);
        nbt.when(() -> BlockEntityNbt.itemTag(raw)).thenAnswer(call -> new CompoundTag());
        nbt.when(() -> BlockEntityNbt.getItem(any(), eq("item"), anyInt())).thenReturn(raw);
        config = mockStatic(Config.class);
        inventory = mockStatic(InventoryUtils.class);
        inventory.when(() -> InventoryUtils.createOrEmpty(Key.of("minecraft:bone_meal"))).thenReturn(output);
        inventory.when(() -> InventoryUtils.bukkitCopy(output)).thenReturn(stack);
        events = mockStatic(EventUtils.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        displays = mockConstruction(MillstoneElement.class);
        var ceWorld = mock(net.momirealms.craftengine.core.world.World.class);
        when(ceWorld.platformWorld()).thenReturn(world);
        furniture = mock(Furniture.class);
        when(furniture.position()).thenReturn(new WorldPosition(ceWorld, 8, 64, 8));
        behavior = mock(MillstoneBehavior.class);
        behavior.grindRotations = 4;
        recipes.clearAccurate();
        recipe(1);
        mill = new MillstoneController(furniture, behavior);
    }

    @AfterEach void close() {
        for (AutoCloseable handle : new AutoCloseable[]{displays, bukkit, events, inventory, config, nbt, items}) {
            if (handle != null) {
                try { handle.close(); }
                catch (Exception failure) { throw new AssertionError("Cannot close test mock", failure); }
            }
        }
        recipes.clearAccurate();
        ApplianceFoodRegistry.instance().clear(ApplianceType.MILLSTONE);
    }

    @Test void takeCommitsBeforeInventoryCallback() {
        assertTrue(mill.tryAddGrind(raw));
        Player player = mock(Player.class);
        AtomicInteger delivered = new AtomicInteger();
        inventory.when(() -> InventoryUtils.giveOrHold(eq(player), eq(InteractionHand.MAIN_HAND), any())).thenAnswer(call -> {
            if (delivered.incrementAndGet() == 1) assertFalse(mill.takeGrind(player, InteractionHand.MAIN_HAND));
            return null;
        });
        assertTrue(mill.takeGrind(player, InteractionHand.MAIN_HAND));
        assertEquals(1, delivered.get());
        assertTrue(mill.grindIsEmpty());
    }

    @Test void completionCallbackCannotTakeTheRawAndTheOutput() throws Exception {
        assertTrue(mill.tryAddGrind(raw));
        events.when(() -> EventUtils.fireAndCheckCancel(any())).thenAnswer(call -> {
            assertFalse(mill.takeGrind(mock(Player.class), InteractionHand.MAIN_HAND));
            return false;
        });
        dropped(true);
        advance(360);
        assertTrue(mill.grindIsEmpty());
        verify(world).dropItem(any(Location.class), eq(stack));
    }

    @Test void canceledSpawnKeepsRawAndRollsBackEarlierDrops() throws Exception {
        assertTrue(mill.tryAddGrind(raw));
        assertTrue(mill.tryAddGrind(raw));
        var first = mock(org.bukkit.entity.Item.class);
        var canceled = mock(org.bukkit.entity.Item.class);
        when(first.isValid()).thenReturn(true);
        when(canceled.isValid()).thenReturn(false);
        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(first, canceled);
        advance(360);
        assertFalse(mill.grindIsEmpty());
        verify(first).remove();
        advance(360);
        verify(world, times(2)).dropItem(any(Location.class), any(ItemStack.class));
        assertTrue(state().getList("grind_items").size() == 2);
    }

    @Test void canceledCompletionIsSavedAndDoesNotRefire() throws Exception {
        mill.tryAddGrind(raw);
        events.when(() -> EventUtils.fireAndCheckCancel(any())).thenReturn(true);
        advance(360);
        CompoundTag saved = save();
        mill = new MillstoneController(furniture, behavior);
        mill.loadCustomData(saved);
        advance(360);
        assertFalse(mill.grindIsEmpty());
        events.verify(() -> EventUtils.fireAndCheckCancel(any()), times(1));
        verifyNoInteractions(world);
    }

    @Test void unfinishedPlanSurvivesRegistryChangesAndLegacyRotations() throws Exception {
        mill.tryAddGrind(raw);
        advance(90);
        CompoundTag saved = save();
        recipes.clearAccurate();
        recipe(8);
        mill = new MillstoneController(furniture, behavior);
        mill.loadCustomData(saved);
        dropped(true);
        advance(269);
        assertFalse(mill.grindIsEmpty());
        advance(1);
        assertTrue(mill.grindIsEmpty());
        var entry = (CompoundTag) saved.getCompound(SAVE).getList("grind_items").get(0);
        entry.remove("processing_plan");
        entry.putInt("rotations", 4);
        mill = new MillstoneController(furniture, behavior);
        mill.loadCustomData(saved);
        advance(350);
        assertFalse(mill.grindIsEmpty());
        advance(1000);
        assertTrue(mill.grindIsEmpty());
    }

    @Test void malformedPlanPreservesRetrievableInput() throws Exception {
        mill.tryAddGrind(raw);
        CompoundTag saved = save();
        var entry = (CompoundTag) saved.getCompound(SAVE).getList("grind_items").get(0);
        entry.getCompound("processing_plan").putInt("version", 999);
        entry.putFloat("progress_degrees", Float.NaN);
        mill = new MillstoneController(furniture, behavior);
        mill.loadCustomData(saved);
        advance(1000);
        assertFalse(mill.grindIsEmpty());
        assertTrue(mill.takeGrind(mock(Player.class), InteractionHand.MAIN_HAND));
        verifyNoInteractions(world);
    }

    @Test void partialRotationDoesNotScanTheAnimalChest() throws Exception {
        var chest = attachChest();
        assertTrue(mill.tryAddGrind(raw));
        advance(90);
        verifyNoInteractions(chest);
    }

    @Test void takingInputRefillsAfterReleasingTheTransferGuard() throws Exception {
        var chest = attachChest();
        assertTrue(mill.tryAddGrind(raw));
        when(chest.getSize()).thenAnswer(call -> {
            assertTrue(mill.tryAddGrind(raw), "Refill must run outside the take guard");
            return 0;
        });
        assertTrue(mill.takeGrind(mock(Player.class), InteractionHand.MAIN_HAND));
        verify(chest).getSize();
        assertFalse(mill.grindIsEmpty());
    }

    @Test void outputVelocityFailureRollsBackTheSpawnedEntity() throws Exception {
        mill.tryAddGrind(raw);
        var entity = mock(org.bukkit.entity.Item.class);
        when(entity.isValid()).thenReturn(true);
        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(entity);
        doThrow(new IllegalStateException("velocity rejected")).when(entity).setVelocity(any());
        advance(360);
        verify(entity).remove();
        assertBlocked();
        advance(360);
        verify(world).dropItem(any(Location.class), any(ItemStack.class));
    }

    @Test void completionExceptionBlocksAutomaticRetries() throws Exception {
        mill.tryAddGrind(raw);
        events.when(() -> EventUtils.fireAndCheckCancel(any())).thenThrow(new IllegalStateException("listener failed"));
        assertDoesNotThrow(() -> advance(360));
        assertBlocked();
        advance(360);
        events.verify(() -> EventUtils.fireAndCheckCancel(any()), times(1));
        verifyNoInteractions(world);
    }

    @Test void callbackRemovingFurnitureReturnsOnlyRawInput() throws Exception {
        mill.tryAddGrind(raw);
        inventory.when(() -> InventoryUtils.bukkitCopy(raw)).thenReturn(stack);
        events.when(() -> EventUtils.fireAndCheckCancel(any())).thenAnswer(call -> {
            mill.preRemove(null);
            return false;
        });
        advance(360);
        assertTrue(mill.grindIsEmpty());
        assertFalse(mill.tryAddGrind(raw));
        verify(world).dropItemNaturally(any(Location.class), eq(stack));
        verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
    }

    @Test void emptyAndInvalidEventOutputsKeepTheInput() throws Exception {
        for (String mutation : List.of("empty", "null", "air", "count")) {
            mill = new MillstoneController(furniture, behavior);
            mill.tryAddGrind(raw);
            events.when(() -> EventUtils.fireAndCheckCancel(any())).thenAnswer(call -> {
                var event = (net.kaleidoscope.cookery.api.event.MillstoneGrindCompleteEvent) call.getArgument(0);
                var outputs = event.products();
                outputs.clear();
                if (mutation.equals("null")) outputs.add(null);
                if (mutation.equals("air") || mutation.equals("count")) {
                    var invalid = mock(ItemStack.class);
                    var material = mock(org.bukkit.Material.class);
                    when(material.isAir()).thenReturn(mutation.equals("air"));
                    when(invalid.getType()).thenReturn(material);
                    when(invalid.getAmount()).thenReturn(mutation.equals("count") ? 0 : 1);
                    outputs.add(invalid);
                }
                return false;
            });
            advance(360);
            assertBlocked();
        }
        verifyNoInteractions(world);
    }

    @Test void invalidInputAndAnglesNeverCreateOrAdvanceABatch() throws Exception {
        Item invalid = mock(Item.class);
        when(invalid.id()).thenReturn(Key.of("minecraft:diamond"));
        assertFalse(mill.tryAddGrind(null));
        assertFalse(mill.tryAddGrind(empty));
        assertFalse(mill.tryAddGrind(invalid));
        mill.tryAddGrind(raw);
        advance(Float.NaN);
        advance(Float.POSITIVE_INFINITY);
        advance(-360);
        assertEquals(0, ((CompoundTag) state().getList("grind_items").get(0)).getFloat("progress_degrees"));
        events.verifyNoInteractions();
    }

    @Test void foliaTickerUsesTheReloadedControllerInsteadOfItsCapturedInstance() throws Exception {
        var meta = mock(net.momirealms.craftengine.core.entity.Entity.class);
        var entity = mock(org.bukkit.entity.Entity.class);
        when(meta.platformEntity()).thenReturn(entity);
        var metaField = Furniture.class.getDeclaredField("metaDataEntity");
        metaField.setAccessible(true);
        metaField.set(furniture, meta);
        var reloaded = mock(net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture.class);
        var current = mock(MillstoneController.class);
        when(reloaded.isValid()).thenReturn(true);
        var controllerField = Furniture.class.getDeclaredField("controller");
        controllerField.setAccessible(true);
        controllerField.set(reloaded, current);
        when(current.get(MillstoneController.class, 0)).thenReturn(current);
        try (var folia = mockStatic(net.kaleidoscope.cookery.util.FoliaUtil.class);
             var api = mockStatic(net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.class)) {
            folia.when(net.kaleidoscope.cookery.util.FoliaUtil::isFolia).thenReturn(true);
            api.when(() -> net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.getLoadedFurnitureByMetaEntity(entity)).thenReturn(reloaded);
            mill.onUnload();
            var ticker = mill.createFurnitureTicker();
            ticker.tick(furniture, mill);
            verify(current).tick();
            verify(current).get(MillstoneController.class, 0);
            api.when(() -> net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.getLoadedFurnitureByMetaEntity(entity)).thenReturn(null);
            ticker.tick(furniture, mill);
            verifyNoMoreInteractions(current);
        }
    }

    private org.bukkit.inventory.Inventory attachChest() throws Exception {
        var horse = mock(org.bukkit.entity.ChestedHorse.class);
        var chest = mock(org.bukkit.inventory.HorseInventory.class);
        when(horse.isCarryingChest()).thenReturn(true);
        when(horse.getInventory()).thenReturn(chest);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(horse)).thenReturn(true);
        var field = MillstoneController.class.getDeclaredField("pullingAnimal");
        field.setAccessible(true);
        field.set(mill, horse);
        return chest;
    }

    private void assertBlocked() {
        assertFalse(mill.grindIsEmpty());
        assertTrue(((CompoundTag) state().getList("grind_items").get(0)).getBoolean("processing_blocked"));
    }

    private void recipe(int rotations) {
        recipes.registerAccurate(new AccurateFoodRecipe(Key.of("test:bone"), raw.id(),
                List.of(new WeightedResult(Key.of("minecraft:bone_meal"), 100)), ApplianceType.MILLSTONE,
                rotations, 5, List.of()));
        ApplianceFoodRegistry.instance().register(ApplianceType.MILLSTONE, raw.id());
    }

    private void dropped(boolean valid) {
        var dropped = mock(org.bukkit.entity.Item.class);
        when(dropped.isValid()).thenReturn(valid);
        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(dropped);
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        mill.saveCustomData(tag);
        return tag;
    }

    private CompoundTag state() { return save().getCompound(SAVE); }

    private void advance(float degrees) throws Exception {
        var method = MillstoneController.class.getDeclaredMethod("advanceGrind", float.class);
        method.setAccessible(true);
        method.invoke(mill, degrees);
    }
}
