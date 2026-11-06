package net.kaleidoscope.tavern;

import net.kaleidoscope.series.ItemIds;
import java.util.LinkedHashMap;
import java.util.List;

public record DrinkEffect(String effect, int duration, int amplifier, double probability) {
    public DrinkEffect {
        ItemIds.require(effect);
        if (duration < 0 || duration > Integer.MAX_VALUE / 20 || amplifier < 0 || amplifier > 255
                || !Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid drink effect: " + effect);
        }
    }

    public static List<DrinkEffect> mix(List<DrinkEffect> supplied) {
        var grouped = new LinkedHashMap<String, DrinkEffect>();
        for (DrinkEffect entry : supplied) {
            grouped.merge(entry.effect, entry, (a, b) -> new DrinkEffect(a.effect,
                    Math.addExact(a.duration, b.duration), Math.max(a.amplifier, b.amplifier),
                    Math.max(a.probability, b.probability)));
        }
        return grouped.values().stream().map(e -> new DrinkEffect(e.effect,
                Math.toIntExact((long) e.duration * 6 / 5), e.amplifier, e.probability)).toList();
    }
}
