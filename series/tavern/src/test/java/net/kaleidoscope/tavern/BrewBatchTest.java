package net.kaleidoscope.tavern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrewBatchTest {
    @Test void upstreamWaitsForTheFollowingCheckAfterCrossingZero() {
        BrewBatch batch = new BrewBatch(BrewRecipeTest.recipe(List.of()), 16);
        batch.pulse();
        assertEquals(3, batch.remaining());
        batch.pulse();
        assertEquals(-94, batch.remaining());
        assertEquals(1, batch.level());
        batch.pulse();
        assertEquals(2, batch.level());
        assertEquals(200, batch.remaining());
    }

    @Test void stopsAtSixAndDoesNotCreateMoreServings() {
        BrewBatch batch = new BrewBatch(BrewRecipeTest.recipe(List.of()), 3);
        for (int i = 0; i < 100; i++) batch.pulse();
        assertEquals(6, batch.level());
        assertEquals(-1, batch.remaining());
        assertEquals(3, batch.servings());
        for (int i = 0; i < 3; i++) batch.bottle();
        assertThrows(IllegalStateException.class, batch::bottle);
    }

    @Test void reloadedBatchContinuesFromTheSameStage() {
        BrewBatch original = new BrewBatch(BrewRecipeTest.recipe(List.of()), 12);
        for (int i = 0; i < 9; i++) original.pulse();
        original.bottle();
        BrewBatch loaded = new BrewBatch(original.recipe(), original.servings(), original.level(), original.remaining());
        for (int i = 0; i < 100; i++) {
            original.pulse(); loaded.pulse();
            assertEquals(original.level(), loaded.level());
            assertEquals(original.remaining(), loaded.remaining());
        }
        assertEquals(11, loaded.servings());
    }

    @Test void longStageDurationsDoNotOverflow() {
        BrewRecipe recipe = new BrewRecipe("tavern:test", "minecraft:water", List.of(), List.of("minecraft:glass_bottle"), "tavern:wine", Integer.MAX_VALUE);
        BrewBatch batch = new BrewBatch(recipe, 1, 4, 0);
        batch.pulse();
        assertEquals(5L * Integer.MAX_VALUE, batch.remaining());
    }

    @Test void rejectsInvalidSavedLevelsCountsAndTimes() {
        BrewRecipe recipe = BrewRecipeTest.recipe(List.of());
        assertThrows(IllegalArgumentException.class, () -> new BrewBatch(recipe, 0));
        assertThrows(IllegalArgumentException.class, () -> new BrewBatch(recipe, 17));
        assertThrows(IllegalArgumentException.class, () -> new BrewBatch(recipe, 1, 7, 0));
        assertThrows(IllegalArgumentException.class, () -> new BrewBatch(recipe, 1, 1, -98));
    }
}
