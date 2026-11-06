package net.kaleidoscope.cookery.catalog;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookeryCatalogTest {
    @Test
    void importsPinnedSnapshotWithoutMergingWholeAndBiteFood() {
        var snapshot = CookeryCatalog.bundled().snapshot();
        assertEquals(CookeryCatalog.SOURCE_COMMIT, snapshot.sourceCommit());
        assertEquals(12, snapshot.customEffects().size());
        assertEquals(8, snapshot.items().values().stream().filter(entry -> entry.tea() != null).count());
        var meal = snapshot.items().get("kaleidoscopecookery:slime_ball_meal");
        assertEquals(9, meal.food().nutrition());
        assertEquals(3, meal.bites().food().nutrition());
        assertEquals(3, meal.bites().maxBites());
        var ham = snapshot.items().get("kaleidoscopecookery:cold_cut_ham_slices");
        assertNull(ham.food());
        assertEquals("3x3", ham.bites().layout());
        assertEquals(8, ham.bites().maxBites());
    }

    @Test
    void qualityPreservesZeroAndProbabilityAndUsesAbsoluteSaturation() {
        var food = new FoodDefinition(0, 0, true, false, 32,
                List.of(new ConsumeEffectDefinition("minecraft:poison", 100, 2, .25)));
        var scaled = food.scaled(new QualityProfile("POOR", .3));
        assertEquals(0, scaled.nutrition());
        assertEquals(30, scaled.effects().getFirst().duration());
        assertEquals(2, scaled.effects().getFirst().amplifier());
        assertEquals(.25, scaled.effects().getFirst().probability());
        var ordinary = new FoodDefinition(3, 3, true, false, 32, List.of()).scaled(new QualityProfile("POOR", .3));
        assertEquals(1, ordinary.nutrition());
        assertEquals(.9f, ordinary.saturation(), .0001f);
    }

    @Test
    void snapshotAndNestedCollectionsAreImmutable() {
        var snapshot = CookeryCatalog.bundled().snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.items().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.customEffects().clear());
        var meal = snapshot.items().get("kaleidoscopecookery:dongpo_pork");
        assertEquals(List.of("minecraft:bowl", "minecraft:bamboo"), meal.bites().drops());
        assertEquals("minecraft:bowl", meal.container());
        assertThrows(UnsupportedOperationException.class, () -> meal.bites().drops().clear());
    }

    @Test
    void rejectsDuplicateKeysAndRetainsLastGoodSnapshot() {
        var catalog = CookeryCatalog.bundled();
        var previous = catalog.snapshot();
        assertThrows(IllegalArgumentException.class, () -> catalog.reload(new StringReader("{\"schemaVersion\":1,\"schemaVersion\":2}")));
        assertSame(previous, catalog.snapshot());
        assertThrows(IllegalArgumentException.class, () -> catalog.reload(new StringReader("{\"schemaVersion\":2,\"sourceCommit\":\"wrong\"}")));
        assertSame(previous, catalog.snapshot());
    }

    @Test
    void rejectsFractionalIntegerAndInvalidEffectProperties() {
        assertThrows(IllegalArgumentException.class, () -> CatalogJson.integer(CatalogJson.read(new StringReader("{\"count\":1.5}")), "count"));
        assertThrows(IllegalArgumentException.class, () -> new ConsumeEffectDefinition("minecraft:poison", 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConsumeEffectDefinition("minecraft:poison", 1, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new QualityProfile("BASE", Double.POSITIVE_INFINITY));
    }
}
