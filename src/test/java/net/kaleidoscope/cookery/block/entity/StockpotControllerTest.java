package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.DishQuality;
import net.kaleidoscope.cookery.recipe.StockpotVisuals;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockpotControllerTest {
    private static final String SAVE = "kaleidoscopecookery:stockpot";
    private final Item empty = mock(Item.class);
    private final Item dish = mock(Item.class);
    private MockedStatic<Item> items;
    private MockedStatic<BlockEntityNbt> nbt;
    private MockedStatic<Config> config;
    private MockedStatic<InventoryUtils> inventory;
    private MockedStatic<TrackedPlayers> tracking;
    private MockedStatic<BlockStates> states;
    private MockedConstruction<StockpotElement> display;
    private BlockEntity entity;
    private StockpotBehavior behavior;

    @BeforeEach void prepare() {
        when(empty.isEmpty()).thenReturn(true);
        when(dish.copyWithCount(1)).thenReturn(dish);
        items = mockStatic(Item.class);
        items.when(Item::empty).thenReturn(empty);
        nbt = mockStatic(BlockEntityNbt.class);
        nbt.when(() -> BlockEntityNbt.getItem(any(), anyString(), anyInt())).thenReturn(empty);
        nbt.when(() -> BlockEntityNbt.getItem(any(), eq("result"), anyInt())).thenReturn(dish);
        inventory = mockStatic(InventoryUtils.class);
        inventory.when(() -> InventoryUtils.createOrEmpty(any(Key.class))).thenReturn(empty);
        tracking = mockStatic(TrackedPlayers.class);
        states = mockStatic(BlockStates.class);
        config = mockStatic(Config.class);
        display = mockConstruction(StockpotElement.class);
        behavior = mock(StockpotBehavior.class);
        when(behavior.createBlockEntityController(any())).thenReturn(mock(BlockEntityController.class));
        ImmutableBlockState state = mock(ImmutableBlockState.class);
        when(state.behavior()).thenReturn(behavior);
        entity = new BlockEntity(new BlockPos(8, 64, 8), state);
        entity.world = mock(CEWorld.class, RETURNS_DEEP_STUBS);
    }

    @AfterEach void close() {
        display.close(); config.close(); states.close(); tracking.close(); inventory.close(); nbt.close(); items.close();
    }

    @Test void partialServingSurvivesSaveAndCannotBeTakenTwice() {
        var pot = new StockpotController(entity, behavior);
        pot.loadCustomData(finished());
        assertSame(dish, pot.peekResult());
        assertEquals(3, pot.takeoutCount());
        assertSame(dish, pot.takeOutResult());
        assertEquals(2, pot.takeoutCount());
        assertEquals(-0.105, pot.liquidOffset(), 0.000001);
        CompoundTag saved = new CompoundTag();
        pot.saveCustomData(saved);
        var loaded = new StockpotController(entity, behavior);
        loaded.loadCustomData(saved);
        assertEquals(2, loaded.takeoutCount());
        assertEquals(pot.visuals(), loaded.visuals());
        assertSame(dish, loaded.takeOutResult());
        assertSame(dish, loaded.takeOutResult());
        assertSame(empty, loaded.takeOutResult());
        assertEquals(StockpotStage.PUT_SOUP_BASE, loaded.stage());
        assertNull(loaded.resultCarrier());
        assertEquals(0, loaded.finishedMax());
        verify(entity.world, atLeast(3)).blockEntityChanged(entity.pos);
    }

    @Test void unavailableReturnedBucketDoesNotEraseSoup() {
        var pot = new StockpotController(entity, behavior);
        assertTrue(pot.addSoupBase(Key.of("minecraft:milk_bucket"), false));
        assertNull(pot.extractSoupBase());
        assertEquals(StockpotStage.PUT_INGREDIENT, pot.stage());
        assertEquals(Key.of("minecraft:milk_bucket"), pot.soupBaseId());
    }

    @Test void invalidPlanAndInflatedServingsAreBlocked() {
        for (boolean corruptPlan : List.of(false, true)) {
            CompoundTag saved = finished();
            CompoundTag state = saved.getCompound(SAVE);
            if (corruptPlan) state.getCompound("cooking_plan").putInt("version", 999);
            else state.putInt("takeout_count", 100);
            var pot = new StockpotController(entity, behavior);
            pot.loadCustomData(saved);
            assertSame(empty, pot.peekResult());
            assertSame(empty, pot.takeOutResult());
            assertEquals(StockpotStage.FINISHED, pot.stage());
        }
    }

    private static CompoundTag finished() {
        var plan = new CookingPlan(true, true, Key.of("test:rice"), 300, 3, Key.of("minecraft:bowl"),
                List.of(new CookingPlan.Output(Key.of("minecraft:bread"), 3, List.of(), DishQuality.SUPERB)), List.of(),
                new StockpotVisuals(Key.of("show:cooking"), Key.of("show:rice"), 0xFFECC3, 0xE9E3DB));
        CompoundTag state = new CompoundTag();
        state.putInt("status", StockpotStage.FINISHED.ordinal());
        state.putInt("takeout_count", 3);
        state.putInt("finished_max", 3);
        state.put("cooking_plan", plan.save());
        state.putString("carrier", "minecraft:bowl");
        state.putString("soup_base_id", "minecraft:water_bucket");
        CompoundTag saved = new CompoundTag();
        saved.put(SAVE, state);
        return saved;
    }
}
