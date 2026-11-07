package net.kaleidoscope.cookery.catalog;

import net.kaleidoscope.cookery.recipe.DishQuality;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BakedFoodTest {
    private final CookeryCatalog.Snapshot catalog = CookeryCatalog.bundled().snapshot();

    @Test void itemSnapshotRetainsQualityNutritionAndEffectsAcrossRestart() {
        var entry = catalog.items().get("kaleidoscopecookery:donkey_burger");
        var food = BakedFood.of(entry, QualityProfile.of(DishQuality.STANDARD));
        assertEquals(7, food.food().nutrition());
        assertEquals(540, food.food().effects().getFirst().duration());
        assertEquals(11.52f, food.food().saturation(), .0001f);
        assertEquals(food, BakedFood.decode(food.encode()));
    }

    @Test void teaIsDrinkWithZeroNutritionAndCustomEffect() {
        var tea = BakedFood.of(catalog.items().get("kaleidoscopecookery:biluochun"), QualityProfile.BASE);
        assertTrue(tea.drink());
        assertEquals(0, tea.food().nutrition());
        assertEquals("kaleidoscopecookery:empty_cup", tea.container());
        assertEquals("kaleidoscope_cookery:projectile_dodge", tea.food().effects().getFirst().id());
        assertEquals(7200, tea.food().effects().getFirst().duration());
        assertEquals(tea, BakedFood.decode(tea.encode()));
    }

    @Test void wholeFoodAndPlacedFoodHaveDifferentContainers() {
        var soup = catalog.items().get("kaleidoscopecookery:brown_mushroom_pot_soup");
        assertEquals("minecraft:bowl", BakedFood.of(soup, QualityProfile.BASE).container());
        var bite = BakedFood.ofBite(soup, QualityProfile.BASE);
        assertEquals(6, bite.food().nutrition());
        assertNull(bite.container());
        assertFalse(bite.drink());
        assertEquals(java.util.List.of("minecraft:flower_pot"), soup.bites().drops());
    }

    @Test void clayTeaClearsHarmfulEffectsAndChiliHasExactDamage() {
        var tea = BakedFood.of(catalog.items().get("kaleidoscopecookery:clay_pot_milk_tea"), QualityProfile.BASE);
        assertTrue(tea.removeHarmful());
        assertEquals("minecraft:flower_pot", tea.container());
        assertEquals(2, BakedFood.of(catalog.items().get("kaleidoscopecookery:red_chili"), QualityProfile.BASE).damage());
        assertEquals(1, BakedFood.of(catalog.items().get("kaleidoscopecookery:green_chili"), QualityProfile.BASE).damage());
    }

    @Test void biteStatePersistsRemainingCountAndRejectsFutureVersions() {
        var entry = catalog.items().get("kaleidoscopecookery:cold_cut_ham_slices");
        var state = BiteState.of(entry, QualityProfile.BASE);
        assertEquals(8, state.remaining());
        assertTrue(state.isFull());
        var after = state.consumeOne().consumeOne();
        assertEquals(6, BiteState.decode(after.encode()).remaining());
        assertEquals(7, after.refillOne().remaining());
        assertEquals(after, after.refillOne().consumeOne());
        var empty = state;
        for (int i = 0; i < state.maxBites(); i++) empty = empty.consumeOne();
        assertTrue(empty.isEmpty());
        assertSame(empty, empty.consumeOne());
        assertEquals(1, empty.refillOne().remaining());
        assertThrows(IllegalArgumentException.class,
                () -> BiteState.decode(after.encode().replace("\"version\":1", "\"version\":2")));
    }

    @Test void plateServingKeepsBitesAndRecoveryDropsTogether() {
        var entry = catalog.items().get("kaleidoscopecookery:brown_mushroom_pot_soup");
        var plate = PlateServing.of(entry, QualityProfile.BASE);
        assertEquals(java.util.List.of("minecraft:flower_pot"), plate.drops());
        assertEquals(plate, PlateServing.decode(plate.encode()));
        assertFalse(plate.empty());
        assertEquals(1, plate.takeOne().bites().remaining());
        var empty = plate;
        for (int i = 0; i < entry.bites().maxBites(); i++) empty = empty.takeOne();
        assertTrue(empty.empty());
        assertEquals(1, empty.refillOne().bites().remaining());
        assertTrue(PlateServingEngine.shouldBreak(empty));
        assertSame(empty, PlateServingEngine.take(empty));
        assertEquals(1, PlateServingEngine.refill(empty).bites().remaining());
    }

    @Test void badOrFutureSaveIsRejectedWithoutReinterpretingIt() {
        String json = BakedFood.of(catalog.items().get("kaleidoscopecookery:baozi"), QualityProfile.BASE).encode();
        assertThrows(IllegalArgumentException.class, () -> BakedFood.decode(json.replace("\"version\":1", "\"version\":2")));
        assertThrows(IllegalArgumentException.class, () -> BakedFood.decode(json.replace("\"nutrition\":8", "\"nutrition\":-8")));
        assertThrows(IllegalArgumentException.class, () -> BakedFood.decode(json.replace("\"ratio\":1.0", "\"ratio\":\"1.0\"")));
        assertThrows(IllegalArgumentException.class, () -> BakedFood.decode("{}"));
    }
}
