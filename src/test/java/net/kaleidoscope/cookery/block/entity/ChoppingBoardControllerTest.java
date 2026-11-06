package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.ChoppingBoardBehavior;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;

import static net.kaleidoscope.cookery.block.entity.ChoppingBoardController.CutResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChoppingBoardControllerTest {
    private static final String SAVE = "kaleidoscopecookery:chopping_board";
    private static final Key FOOD = Key.of("test:slices");
    private MockedStatic<Item> items;
    private MockedStatic<BlockEntityNbt> nbt;
    private MockedStatic<InventoryUtils> inventory;
    private MockedStatic<DropUtils> drops;
    private MockedConstruction<ChoppingBoardElement> display;
    private final Item raw = mock(Item.class);
    private final Item food = mock(Item.class);
    private final Item empty = mock(Item.class);
    private BlockEntity entity;
    private ChoppingBoardBehavior behavior;

    @BeforeEach void openBoard() {
        when(empty.isEmpty()).thenReturn(true);
        when(raw.count()).thenReturn(1);
        when(food.count()).thenReturn(4);
        when(food.copyWithCount(4)).thenReturn(food);
        items = mockStatic(Item.class);
        items.when(Item::empty).thenReturn(empty);
        nbt = mockStatic(BlockEntityNbt.class);
        nbt.when(() -> BlockEntityNbt.getItem(any(), eq("item"), anyInt())).thenReturn(raw);
        inventory = mockStatic(InventoryUtils.class);
        inventory.when(() -> InventoryUtils.createOrEmpty(FOOD)).thenReturn(food);
        drops = mockStatic(DropUtils.class);
        display = mockConstruction(ChoppingBoardElement.class);
        behavior = mock(ChoppingBoardBehavior.class);
        when(behavior.createBlockEntityController(any())).thenReturn(mock(BlockEntityController.class));
        ImmutableBlockState state = mock(ImmutableBlockState.class);
        when(state.behavior()).thenReturn(behavior);
        entity = new BlockEntity(new BlockPos(8, 64, 8), state);
        entity.world = mock(CEWorld.class);
    }

    @AfterEach void closeBoard() {
        if (display != null) display.close();
        if (drops != null) drops.close();
        if (inventory != null) inventory.close();
        if (nbt != null) nbt.close();
        if (items != null) items.close();
    }

    @ParameterizedTest
    @CsvSource({"4,0,4", "4,1,2", "4,2,1", "4,3,1", "5,1,3", "5,2,2", "4,-1,4", "4,255,1"})
    void finishesOnTheLastRequiredClickAndProducesOnlyOnce(int required, int enchantment, int clicks) {
        var board = savedBoard(1, required);
        for (int click = 1; click < clicks; click++) assertEquals(ADVANCED, board.cut(enchantment));
        assertEquals(FINISHED, board.cut(enchantment));
        assertTrue(board.isEmpty());
        assertEquals(0, board.currentStage());
        assertEquals(NOTHING, board.cut(enchantment));
        drops.verify(() -> DropUtils.dropAtHeight(eq(entity), eq(food), anyDouble()), times(1));
        drops.verifyNoMoreInteractions();
    }

    @Test
    void existingCutApiKeepsItsOneStepAndStoredStage() {
        var board = savedBoard(2, 4);
        assertEquals(ADVANCED, board.cut());
        assertEquals(3, board.currentStage());
        CompoundTag saved = new CompoundTag();
        board.saveCustomData(saved);
        var loaded = new ChoppingBoardController(entity, behavior);
        loaded.loadCustomData(saved);
        assertEquals(ADVANCED, loaded.cut());
        assertEquals(FINISHED, loaded.cut());
        drops.verify(() -> DropUtils.dropAtHeight(eq(entity), eq(food), anyDouble()));
    }

    @Test
    void changedKnifeContinuesTheSavedBatchWithoutAnExtraPickupClick() {
        var board = savedBoard(1, 5);
        assertEquals(ADVANCED, board.cut(1));
        assertEquals(3, board.currentStage());
        CompoundTag saved = new CompoundTag();
        board.saveCustomData(saved);
        var loaded = new ChoppingBoardController(entity, behavior);
        loaded.loadCustomData(saved);
        assertEquals(FINISHED, loaded.cut(2));
        assertSame(empty, loaded.takeBack());
    }

    @Test
    void missingOutputPreservesRawFoodAndBlocksFurtherProcessing() {
        var board = savedBoard(1, 4);
        inventory.when(() -> InventoryUtils.createOrEmpty(FOOD)).thenReturn(empty);
        assertEquals(NOTHING, board.cut(2));
        assertEquals(1, board.currentStage());
        assertSame(raw, board.placedItem());
        inventory.when(() -> InventoryUtils.createOrEmpty(FOOD)).thenReturn(food);
        assertEquals(NOTHING, board.cut(2));
        assertSame(raw, board.takeBack());
        assertTrue(board.isEmpty());
        drops.verifyNoInteractions();
    }

    @Test
    void nearMaximumSavedStageCannotOverflowIntoAnotherBatch() {
        var board = savedBoard(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        assertEquals(FINISHED, board.cut(2));
        assertEquals(NOTHING, board.cut(2));
        drops.verify(() -> DropUtils.dropAtHeight(eq(entity), eq(food), anyDouble()));
    }

    private ChoppingBoardController savedBoard(int stage, int work) {
        CookingPlan plan = new CookingPlan(true, true, Key.of("test:cutting"), work, 1, null,
                List.of(new CookingPlan.Output(FOOD, 4, List.of(), null)), List.of());
        CompoundTag state = new CompoundTag();
        state.putInt("stage", stage);
        state.put("processing_plan", plan.save());
        CompoundTag saved = new CompoundTag();
        saved.put(SAVE, state);
        var board = new ChoppingBoardController(entity, behavior);
        board.loadCustomData(saved);
        return board;
    }
}
