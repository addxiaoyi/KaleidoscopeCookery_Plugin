package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;

import java.util.Objects;

/** One input item changes under dry or wet weather; time is measured in server ticks. */
public record BambooTrayRecipe(Key id, String input, Key result, int resultCount,
                               BambooTrayMode mode, int durationTicks) {
    public BambooTrayRecipe {
        Objects.requireNonNull(id, "Bamboo tray recipe id");
        Objects.requireNonNull(result, "Bamboo tray result");
        Objects.requireNonNull(mode, "Bamboo tray mode");
        input = normalizeInput(input);
        if (resultCount < 1) throw new IllegalArgumentException("Bamboo tray result_count must be positive");
        if (durationTicks < 1) throw new IllegalArgumentException("Bamboo tray time must be positive");
        if (result.equals(Key.of("minecraft:air"))) throw new IllegalArgumentException("Bamboo tray result cannot be air");
    }

    public boolean tagInput() {
        return input.startsWith("#");
    }

    public Key inputKey() {
        return Key.of(tagInput() ? input.substring(1) : input);
    }

    public static BambooTrayRecipe fromConfig(Key id, ConfigSection section) {
        int duration = RecipeProcessingFields.optionalPositive(section, "time", "cooking_time", "cooking-time");
        if (duration == 0) throw new IllegalArgumentException(section.path() + ".time is required");
        int count = RecipeProcessingFields.optionalPositive(section, "result_count", "result-count");
        return new BambooTrayRecipe(id, section.getNonNullString("require"),
                section.getNonNullIdentifier("result"), count == 0 ? 1 : count,
                BambooTrayMode.parse(section.getNonNullString("mode")), duration);
    }

    private static String normalizeInput(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Bamboo tray require is missing");
        String input = value.trim();
        boolean tag = input.startsWith("#");
        if (tag) input = input.substring(1).trim();
        else if (input.startsWith("craftengine:")) input = input.substring("craftengine:".length());
        if (input.isBlank() || input.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Bamboo tray require must contain one item id or #tag");
        }
        Key key = Key.of(input);
        if (!tag && key.equals(Key.of("minecraft:air"))) throw new IllegalArgumentException("Bamboo tray input cannot be air");
        return (tag ? "#" : "") + key.asString();
    }
}
