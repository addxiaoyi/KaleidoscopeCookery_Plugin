package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.PotBehavior;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.*;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.HeatSourceUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PotControllerTest {
    private static final String SAVE = "kaleidoscopecookery:cooking_pot";
    private static final Key EGG = Key.of("minecraft:egg");
    private static final Key BREAD = Key.of("minecraft:bread");
    private final FoodRecipeRegistry recipes = FoodRecipeRegistry.instance();
    private final List<AutoCloseable> mocks = new ArrayList<>();
    private BlockEntity entity;
    private PotBehavior behavior;

    @BeforeEach void prepare() {
        MockedStatic<Item> items = mockStatic(Item.class);
        mocks.add(items);
        Item empty = item(Key.of("minecraft:air"), 0);
        items.when(Item::empty).thenReturn(empty);
        MockedStatic<FlexMatcher> dishes = mockStatic(FlexMatcher.class, call ->
                call.getMethod().getName().equals("buildDish") ? item(call.getArgument(0), 1) : call.callRealMethod());
        mocks.add(dishes);
        MockedStatic<BlockEntityNbt> nbt = mockStatic(BlockEntityNbt.class);
        mocks.add(nbt);
        nbt.when(() -> BlockEntityNbt.saveItems(anyList())).thenAnswer(call -> {
            ListTag saved = new ListTag();
            for (Item stack : call.<List<Item>>getArgument(0)) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", stack.id().asString());
                entry.putInt("count", stack.count());
                saved.add(entry);
            }
            return saved;
        });
        nbt.when(() -> BlockEntityNbt.loadItems(any(CompoundTag.class), anyString(), anyInt(), anyList())).thenAnswer(call -> {
            List<Item> target = call.getArgument(3);
            target.clear();
            ListTag saved = call.<CompoundTag>getArgument(0).getList(call.getArgument(1));
            if (saved != null) for (var value : saved) {
                CompoundTag entry = (CompoundTag) value;
                target.add(item(Key.of(entry.getString("id")), entry.getInt("count")));
            }
            return null;
        });
        mocks.add(mockStatic(Config.class));
        mocks.add(mockStatic(TrackedPlayers.class));
        mocks.add(mockStatic(BlockStates.class));
        mocks.add(mockStatic(LocationUtils.class));
        mocks.add(mockStatic(HeatSourceUtils.class));
        MockedConstruction<PotElement> display = mockConstruction(PotElement.class);
        mocks.add(display);
        behavior = mock(PotBehavior.class);
        behavior.stirFryCount = 6;
        behavior.cookDoneTime = 200;
        when(behavior.createBlockEntityController(any())).thenReturn(mock(BlockEntityController.class));
        ImmutableBlockState state = mock(ImmutableBlockState.class);
        when(state.behavior()).thenReturn(behavior);
        entity = new BlockEntity(new BlockPos(8, 64, 8), state);
        entity.world = mock(CEWorld.class, RETURNS_DEEP_STUBS);
        recipes.registerPot(new PotRecipe(Key.of("test:eggs"), PotRecipe.Mode.EXACT,
                List.of(Set.of(EGG)), BREAD, 3, 3, 200, Key.of("minecraft:bowl")));
    }

    @AfterEach void close() throws Exception {
        recipes.clearPot();
        recipes.clearFlex(ApplianceType.POT);
        ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
        for (AutoCloseable scoped : mocks.reversed()) scoped.close();
    }

    @Test void interruptedAnimationResumesFrozenBatchAfterRegistryChanges() {
        var pot = fresh();
        assertEquals(PotController.StirResult.OK, pot.stirFry(true, null));
        for (int tick = 0; tick < 8; tick++) pot.tick();
        var saved = save(pot);
        assertEquals(16, saved.getCompound(SAVE).getInt("stir_animation_ticks"));
        recipes.clearPot();
        var loaded = new PotController(entity, behavior);
        loaded.loadCustomData(saved);
        assertEquals(PotController.StirResult.IDLE, loaded.stirFry(true, null));
        for (int tick = 0; tick < 16; tick++) loaded.tick();
        assertEquals(PotStage.COOKING, loaded.stage());
        stirAndWait(loaded);
        assertEquals(PotStage.COOKING, loaded.stage());
        assertEquals(PotController.StirResult.OK, loaded.stirFry(true, null));
        for (int tick = 0; tick < 23; tick++) loaded.tick();
        assertEquals(PotStage.COOKING, loaded.stage());
        loaded.tick();
        assertEquals(PotStage.DONE, loaded.stage());
        assertEquals(3, loaded.resultCount());
        assertEquals(BREAD, loaded.peekResult().id());
        assertFalse(loaded.hasOil());
        loaded.consumeResult(1);
        var served = new PotController(entity, behavior);
        served.loadCustomData(save(loaded));
        assertEquals(2, served.resultCount());
        served.consumeResult(2);
        assertEquals(PotStage.IDLE, served.stage());
        assertTrue(served.peekResult().isEmpty());
        served.consumeResult(1);
        assertEquals(0, served.resultCount());
    }

    @Test void legacyStartedBatchKeepsCosineOutputAndDefaultStirCount() {
        recipes.registerFlex(FlexFoodRecipe.of(Key.of("test:legacy"), Key.of("minecraft:carrot"), ApplianceType.POT,
                Map.of(EGG, 1), List.of(), null, false, false));
        var pot = fresh();
        CompoundTag saved = save(pot);
        saved.getCompound(SAVE).putInt("stir_fry_count", 1);
        saved.getCompound(SAVE).putInt("cooking_status", PotStage.COOKING.ordinal());
        var loaded = new PotController(entity, behavior);
        loaded.loadCustomData(saved);
        loaded.tick();
        var plan = CookingPlan.load(save(loaded).getCompound(SAVE).getCompound("processing_plan"));
        assertEquals(Key.of("test:legacy"), plan.recipeId());
        assertEquals(6, plan.workRequired());
        for (int stir = 0; stir < 5; stir++) stirAndWait(loaded);
        assertEquals(PotStage.DONE, loaded.stage());
        assertEquals(Key.of("minecraft:carrot"), loaded.peekResult().id());
    }

    @Test void corruptSavedPlanPreservesIngredientsAndBlocksOutput() {
        var pot = fresh();
        stirAndWait(pot);
        CompoundTag saved = save(pot);
        saved.getCompound(SAVE).getCompound("processing_plan").putInt("version", 999);
        var loaded = new PotController(entity, behavior);
        loaded.loadCustomData(saved);
        loaded.tick();
        assertEquals(PotController.StirResult.DENIED, loaded.stirFry(true, null));
        assertEquals(1, loaded.ingredients().size());
        assertTrue(loaded.peekResult().isEmpty());
    }

    @Test void servingGuardBlocksNestedClaimAndReleasesAfterReset() {
        var pot = fresh();
        assertFalse(pot.beginServing());
        for (int stir = 0; stir < 3; stir++) stirAndWait(pot);
        assertTrue(pot.beginServing());
        assertFalse(pot.beginServing());
        pot.consumeResult(1);
        assertFalse(pot.beginServing());
        pot.endServing();
        assertTrue(pot.beginServing());
        pot.consumeResult(2);
        pot.endServing();
        assertFalse(pot.beginServing());
    }

    private PotController fresh() {
        var pot = new PotController(entity, behavior);
        pot.setHasOil(true);
        assertTrue(pot.addIngredient(item(EGG, 1), true, null));
        return pot;
    }

    private static void stirAndWait(PotController pot) {
        assertEquals(PotController.StirResult.OK, pot.stirFry(true, null));
        for (int tick = 0; tick < 24; tick++) pot.tick();
    }

    private static CompoundTag save(PotController pot) {
        CompoundTag tag = new CompoundTag();
        pot.saveCustomData(tag);
        return tag;
    }

    private static Item item(Key id, int amount) {
        Item stack = mock(Item.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(stack.id()).thenReturn(id);
        when(stack.count()).thenAnswer(call -> count.get());
        when(stack.isEmpty()).thenAnswer(call -> count.get() <= 0);
        when(stack.count(anyInt())).thenAnswer(call -> { count.set(call.getArgument(0)); return stack; });
        when(stack.copyWithCount(anyInt())).thenAnswer(call -> item(id, call.getArgument(0)));
        doAnswer(call -> { count.addAndGet(-call.<Integer>getArgument(0)); return null; }).when(stack).shrink(anyInt());
        return stack;
    }
}
