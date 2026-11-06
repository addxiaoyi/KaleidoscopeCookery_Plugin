package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PotRecipeTest {
    private static final Key EGG = Key.of("minecraft:egg");
    private static final Key CARROT = Key.of("minecraft:carrot");
    private final FoodRecipeRegistry registry = FoodRecipeRegistry.instance();

    @AfterEach void clear() {
        if (registry.isConfigurationLoading()) registry.abortConfigurationLoad(registry.configurationLoadToken());
        registry.clearPot();
        registry.clearFlex(ApplianceType.POT);
        ApplianceFoodRegistry.instance().clear(ApplianceType.POT);
    }

    @Test void exactThenConcreteDistinctThenLegacyCosine() {
        registry.registerFlex(FlexFoodRecipe.of(Key.of("test:legacy"), Key.of("minecraft:bread"), ApplianceType.POT,
                Map.of(EGG, 1), List.of(), null, false, false));
        registry.registerPot(recipe("distinct", PotRecipe.Mode.DISTINCT, List.of(Set.of(EGG))));
        registry.registerPot(recipe("exact", PotRecipe.Mode.EXACT, List.of(Set.of(EGG), Set.of(EGG))));
        assertEquals(Key.of("test:exact"), registry.planPot(List.of(EGG, EGG), 6).recipeId());
        var distinct = registry.planPot(List.of(EGG, EGG, EGG), 6);
        assertEquals(Key.of("test:distinct"), distinct.recipeId());
        assertEquals(2, distinct.outputs().getFirst().count());
        assertEquals(3, distinct.workRequired());
        assertEquals(Key.of("test:legacy"), registry.planPot(List.of(EGG, CARROT), 6).recipeId());
        assertFalse(registry.planPot(List.of(CARROT), 6).matched());
    }

    @Test void overlappingTagsCannotConsumeTheSameInputTwice() {
        var recipe = recipe("overlap", PotRecipe.Mode.EXACT, List.of(Set.of(EGG, CARROT), Set.of(EGG)));
        assertTrue(recipe.matches(List.of(EGG, CARROT)));
        assertTrue(recipe.matches(List.of(CARROT, EGG)));
        assertFalse(recipe.matches(List.of(CARROT, CARROT)));
        assertFalse(recipe.matches(List.of(EGG)));
        assertThrows(IllegalArgumentException.class, () -> recipe.plan(List.of(CARROT)));
        var distinct = recipe("distinct", PotRecipe.Mode.DISTINCT, List.of(Set.of(EGG, CARROT)));
        assertTrue(distinct.matches(List.of(EGG, EGG)));
        assertFalse(distinct.matches(List.of(EGG, CARROT)));
    }

    @Test void qualityKeepsDeviationWhileOutputCountStaysFixed() {
        var recipe = recipe("quality", PotRecipe.Mode.DISTINCT, List.of(Set.of(EGG), Set.of(CARROT)));
        var plan = recipe.plan(List.of(EGG, EGG, EGG, CARROT));
        assertEquals(DishQuality.STANDARD, plan.outputs().getFirst().quality());
        assertEquals(2, plan.outputs().getFirst().count());
        assertEquals(4, plan.ingredientCount());
        assertEquals(3, plan.workRequired());
        assertEquals(200, recipe.sourceTime());
        assertEquals(plan, CookingPlan.load(plan.save()));
    }

    @Test void reloadPublishesRecipeWhitelistAndCarrierTogetherOrRetainsPrevious() {
        var before = recipe("before", PotRecipe.Mode.EXACT, List.of(Set.of(EGG)));
        registry.registerPot(before);
        registry.beginConfigurationLoad();
        registry.configurationUpdate(() -> registry.registerPot(recipe("after", PotRecipe.Mode.EXACT, List.of(Set.of(CARROT)))));
        assertEquals(before.id(), registry.planPot(List.of(EGG), 6).recipeId());
        registry.failConfigurationLoad();
        registry.finishConfigurationLoad();
        assertEquals(before.id(), registry.planPot(List.of(EGG), 6).recipeId());
        assertFalse(ApplianceFoodRegistry.instance().isAllowed(ApplianceType.POT, CARROT));
        assertEquals(1, registry.recipeCount(ApplianceType.POT));
        assertThrows(IllegalArgumentException.class, () -> registry.registerPot(before));
        var frozen = registry.planPot(List.of(EGG), 6);
        registry.clearPot();
        assertEquals(frozen, CookingPlan.load(frozen.save()));
    }

    @Test void ingredientsAndRuntimeListsAreImmutable() {
        var inputs = new ArrayList<>(List.of(Set.of(EGG)));
        var recipe = recipe("immutable", PotRecipe.Mode.EXACT, inputs);
        inputs.clear();
        assertTrue(recipe.matches(List.of(EGG)));
        registry.registerPot(recipe);
        assertThrows(UnsupportedOperationException.class, () -> registry.potRecipes().clear());
        assertThrows(UnsupportedOperationException.class, () -> recipe.ingredients().getFirst().clear());
    }

    @Test void parserWaitsForItemRegistrationAndRawWhitelistReset() {
        assertTrue(new PotRecipeParser().dependencies().contains(FoodRecipeManager.POT_FOOD_RAW));
    }

    @Test void strictConfigRejectsUnknownFieldsAndCoercedNumbers() {
        Map<String, Object> node = new HashMap<>(Map.of("match", "exact", "ingredients", List.of(List.of(EGG.asString())),
                "result", "minecraft:bread", "result_count", 2, "stir_fry_count", 3, "source_time", 200, "carrier", "minecraft:air"));
        assertNull(PotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("pot", node)).carrier());
        for (String field : List.of("stir_fry_count", "source_time", "result_count")) {
            Object original = node.get(field);
            for (Object bad : List.of(true, 0, -1, 2.5, 2147483648L, "3")) {
                node.put(field, bad);
                assertThrows(IllegalArgumentException.class, () -> PotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("pot", node)));
            }
            node.put(field, original);
        }
        node.put("typo", 1);
        assertThrows(IllegalArgumentException.class, () -> PotRecipe.fromConfig(Key.of("test:config"), ConfigSection.of("pot", node)));
    }

    private static PotRecipe recipe(String name, PotRecipe.Mode mode, List<Set<Key>> inputs) {
        return new PotRecipe(Key.of("test:" + name), mode, inputs, Key.of("minecraft:bread"), 2, 3, 200, Key.of("minecraft:bowl"));
    }
}
