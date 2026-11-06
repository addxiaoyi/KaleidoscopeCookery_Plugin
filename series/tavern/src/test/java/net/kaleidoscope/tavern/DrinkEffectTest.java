package net.kaleidoscope.tavern;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DrinkEffectTest {
    @Test void mergesDurationAndKeepsTheStrongestAmplifierAndProbability() {
        var merged = DrinkEffect.mix(List.of(new DrinkEffect("minecraft:speed", 10, 0, 0.5),
                new DrinkEffect("minecraft:speed", 20, 2, 0.8), new DrinkEffect("minecraft:haste", 7, 0, 1)));
        assertEquals(List.of(new DrinkEffect("minecraft:speed", 36, 2, 0.8),
                new DrinkEffect("minecraft:haste", 8, 0, 1)), merged);
    }

    @Test void rejectsImpossibleEffectData() {
        assertThrows(IllegalArgumentException.class, () -> new DrinkEffect("minecraft:speed", -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DrinkEffect("minecraft:speed", 1, 0, Double.NaN));
    }
}
