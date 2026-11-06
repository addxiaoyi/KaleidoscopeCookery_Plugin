package net.kaleidoscope.tavern;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrapeGrowthTest {
    @Test void fruitStartsAtZeroAndStopsAtFive() {
        GrapeGrowth growth = new GrapeGrowth("grape", 3, -1, false).ageFruit(2);
        assertEquals(0, growth.fruit());
        for (int i = 0; i < 3; i++) growth = growth.ageFruit(2);
        assertEquals(5, growth.fruit());
        assertEquals(-1, growth.harvested().fruit());
        assertEquals(3, growth.harvested().vine());
    }
    @Test void coldAndWarmVarietiesHaveSourceProbabilities() {
        double fastPulse = 1 - Math.pow(1 - 0.8 / 4096, 3 * 68);
        double normalPulse = 1 - Math.pow(1 - 0.25 / 4096, 3 * 68);
        assertEquals(fastPulse, new GrapeGrowth("ice_grape", 0, -1, false).probability(0.1, 3), 0.00001);
        assertEquals(fastPulse, new GrapeGrowth("gold_grape", 0, -1, false).probability(1.1, 3), 0.00001);
        assertEquals(normalPulse, new GrapeGrowth("gold_grape", 0, -1, false).probability(1, 3), 0.00001);
        assertEquals(0, new GrapeGrowth("grape", 0, -1, false).probability(1, 0));
    }
    @Test void corruptOrImpossibleSavedStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GrapeGrowth("grape", 2, 4, false));
        assertThrows(IllegalArgumentException.class, () -> new GrapeGrowth("none", 1, -1, false));
        assertThrows(IllegalArgumentException.class, () -> new GrapeGrowth("ice_grape", 3, 6, false));
        assertThrows(IllegalArgumentException.class, () -> new GrapeGrowth("gold_grape", 0, -1, true));
    }
}
