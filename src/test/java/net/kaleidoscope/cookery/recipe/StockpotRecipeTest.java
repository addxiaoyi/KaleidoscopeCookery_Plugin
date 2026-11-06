package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StockpotRecipeTest {
    private static final Key BONE = Key.of("minecraft:bone");
    private static final Key CARROT = Key.of("minecraft:carrot");
    private static final Key WATER = Key.of("minecraft:water_bucket");
    private static final StockpotVisuals VISUALS = new StockpotVisuals(Key.of("show:cooking"), Key.of("show:rice"), 0xFFECC3, 0xE9E3DB);
    private final FoodRecipeRegistry registry = FoodRecipeRegistry.instance();

    @AfterEach void clear() {
        if (registry.isConfigurationLoading()) registry.abortConfigurationLoad(registry.configurationLoadToken());
        registry.clearStockpot();
        registry.clearFlex(ApplianceType.STOCKPOT);
        ApplianceFoodRegistry.instance().clear(ApplianceType.STOCKPOT);
    }

    @Test void exactCountsBeatDistinctAndLegacyCosine() {
        registry.registerFlex(FlexFoodRecipe.of(Key.of("test:legacy"), Key.of("minecraft:bread"), ApplianceType.STOCKPOT,
                Map.of(BONE, 1), List.of(WATER), null, false, false));
        registry.registerStockpot(recipe("distinct", StockpotRecipe.Mode.DISTINCT, List.of(Set.of(BONE))));
        registry.registerStockpot(recipe("exact", StockpotRecipe.Mode.EXACT, List.of(Set.of(BONE), Set.of(BONE))));
        assertEquals(Key.of("test:exact"), registry.planStockpot(List.of(BONE, BONE), WATER, 100).recipeId());
        assertEquals(Key.of("test:distinct"), registry.planStockpot(List.of(BONE, BONE, BONE), WATER, 100).recipeId());
        assertEquals(Key.of("test:legacy"), registry.planStockpot(List.of(BONE, CARROT), WATER, 100).recipeId());
        assertFalse(registry.planStockpot(List.of(BONE), Key.of("minecraft:lava_bucket"), 100).matched());
    }

    @Test void overlappingAlternativesUseOneInputPerSlot() {
        var recipe = recipe("overlap", StockpotRecipe.Mode.EXACT, List.of(Set.of(BONE, CARROT), Set.of(BONE)));
        assertTrue(recipe.matches(List.of(BONE, CARROT), WATER));
        assertTrue(recipe.matches(List.of(CARROT, BONE), WATER));
        assertFalse(recipe.matches(List.of(CARROT, CARROT), WATER));
        assertFalse(recipe.matches(List.of(BONE), WATER));
        assertFalse(recipe.matches(List.of(BONE, CARROT, BONE), WATER));
    }

    @Test void distinctMeansConcreteItemsNotCollapsedTags() {
        var recipe = recipe("alternatives", StockpotRecipe.Mode.DISTINCT, List.of(Set.of(BONE, CARROT)));
        assertTrue(recipe.matches(List.of(BONE, BONE), WATER));
        assertFalse(recipe.matches(List.of(BONE, CARROT), WATER));
        var soup = recipe("quality", StockpotRecipe.Mode.DISTINCT, List.of(Set.of(BONE), Set.of(CARROT)));
        CookingPlan plan = soup.plan(List.of(BONE, BONE, BONE, CARROT));
        assertEquals(DishQuality.STANDARD, plan.outputs().getFirst().quality());
        assertEquals(2, plan.outputs().getFirst().count());
    }

    @Test void failedReloadKeepsThePreviousGenerationAndWhitelist() {
        var first = recipe("before", StockpotRecipe.Mode.EXACT, List.of(Set.of(BONE)));
        registry.registerStockpot(first);
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> registry.registerStockpot(recipe("after", StockpotRecipe.Mode.EXACT, List.of(Set.of(CARROT)))));
        assertEquals(first.id(), registry.planStockpot(List.of(BONE), WATER, 100).recipeId());
        registry.failConfigurationLoad();
        registry.finishConfigurationLoad();
        assertEquals(first.id(), registry.planStockpot(List.of(BONE), WATER, 100).recipeId());
        assertFalse(registry.planStockpot(List.of(CARROT), WATER, 100).matched());
        assertFalse(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.STOCKPOT, CARROT));
        assertThrows(IllegalArgumentException.class, () -> registry.registerStockpot(first));
    }

    @Test void frozenVisualsAndOutputSurviveReloadAndSave() {
        var recipe = recipe("saved", StockpotRecipe.Mode.EXACT, List.of(Set.of(BONE)));
        registry.registerStockpot(recipe);
        CookingPlan plan = registry.planStockpot(List.of(BONE), WATER, 1);
        registry.clearStockpot();
        assertEquals(plan, CookingPlan.load(plan.save()));
        assertEquals(VISUALS, CookingPlan.load(plan.withWorkRequired(123).save()).stockpotVisuals());
        CompoundTag invalid = plan.save();
        invalid.getCompound("stockpot_visuals").putInt("cooking_color", -1);
        assertFalse(CookingPlan.load(invalid).valid());
    }

    @Test void versionOnePlansKeepTheirOriginalRules() {
        CookingPlan plan = new CookingPlan(true, true, Key.of("test:old"), 123, 1, null,
                List.of(new CookingPlan.Output(BONE, 2, List.of(), DishQuality.POOR)), List.of());
        CompoundTag old = plan.save();
        old.putInt("version", 1);
        assertEquals(plan, CookingPlan.load(old));
        assertNull(CookingPlan.load(old).stockpotVisuals());
    }

    @Test void liquidHeightFollowsOfficialRemainingFraction() {
        assertEquals(0, StockpotVisuals.liquidOffset(9, 9), 0.000001);
        assertEquals(-0.28, StockpotVisuals.liquidOffset(1, 9), 0.000001);
        assertEquals(-0.1575, StockpotVisuals.liquidOffset(1, 2), 0.000001);
        assertThrows(IllegalArgumentException.class, () -> StockpotVisuals.liquidOffset(10, 9));
        assertThrows(IllegalArgumentException.class, () -> StockpotVisuals.liquidOffset(1, 0));
    }

    @Test void strictConfigRejectsUnknownFieldsAndCoercedNumbers() {
        Map<String, Object> node = new HashMap<>(Map.of("match", "exact", "ingredients", List.of(List.of(BONE.asString())),
                "soup_base", WATER.asString(), "result", "minecraft:bread", "result_count", 2, "time", 300,
                "carrier", "minecraft:air", "visuals", Map.of("cooking_model", "show:cooking", "finished_model", "show:rice",
                        "cooking_color", 0xFFECC3, "finished_color", 0xE9E3DB)));
        assertNull(StockpotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("stockpot", node)).carrier());
        for (Object time : List.of(true, 0, -1, 2.5, 2147483648L, "300")) {
            node.put("time", time);
            assertThrows(IllegalArgumentException.class, () -> StockpotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("stockpot", node)));
        }
        node.put("time", 300);
        node.put("typo", 1);
        assertThrows(IllegalArgumentException.class, () -> StockpotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("stockpot", node)));
    }

    private static StockpotRecipe recipe(String name, StockpotRecipe.Mode mode, List<Set<Key>> inputs) {
        return new StockpotRecipe(Key.of("test:" + name), mode, inputs, WATER, Key.of("minecraft:bread"), 2, 300, null, VISUALS);
    }
}
