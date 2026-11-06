package net.kaleidoscope.tavern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrewRecipeTest {
    @Test void overlappingIngredientChoicesUseDistinctSlots() {
        BrewRecipe recipe = recipe(List.of(List.of("minecraft:apple", "minecraft:sugar"), List.of("minecraft:apple")));
        assertTrue(recipe.matches("minecraft:water", List.of("minecraft:apple", "minecraft:sugar")));
        assertTrue(recipe.matches("minecraft:water", List.of("minecraft:sugar", "minecraft:apple")));
        assertFalse(recipe.matches("minecraft:water", List.of("minecraft:sugar", "minecraft:sugar")));
    }

    @Test void extraMissingAndWrongFluidInputsDoNotMatch() {
        BrewRecipe recipe = recipe(List.of(List.of("minecraft:apple")));
        assertFalse(recipe.matches("minecraft:water", List.of()));
        assertFalse(recipe.matches("minecraft:lava", List.of("minecraft:apple")));
        assertFalse(recipe.matches("minecraft:water", List.of("minecraft:apple", "minecraft:sugar")));
    }

    @Test void pureJuiceOnlyMatchesAnEmptyIngredientInventory() {
        BrewRecipe recipe = recipe(List.of());
        assertTrue(recipe.matches("minecraft:water", List.of()));
        assertFalse(recipe.matches("minecraft:water", List.of("minecraft:apple")));
    }

    static BrewRecipe recipe(List<List<String>> inputs) {
        return new BrewRecipe("tavern:test", "minecraft:water", inputs, List.of("minecraft:glass_bottle"), "tavern:wine", 100);
    }
}
