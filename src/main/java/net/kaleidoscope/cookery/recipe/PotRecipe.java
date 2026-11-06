package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record PotRecipe(Key id, Mode mode, List<Set<Key>> ingredients, Key result, int resultCount,
                        int stirFryCount, int sourceTime, Key carrier) {
    public enum Mode { EXACT, DISTINCT }

    public PotRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(result, "result");
        ingredients = RecipeInputs.copy(ingredients);
        if (resultCount < 1 || resultCount > RecipeInputs.MAX_SLOTS || stirFryCount < 1 || sourceTime < 1) {
            throw new IllegalArgumentException(id + ": invalid pot output count, stir count or source time");
        }
    }

    public boolean matches(List<Key> inputs) {
        return RecipeInputs.matches(ingredients, inputs, mode == Mode.DISTINCT);
    }

    public CookingPlan plan(List<Key> inputs) {
        if (!matches(inputs)) throw new IllegalArgumentException(id + ": inputs do not match pot recipe");
        DishQuality quality = RecipeInputs.quality(inputs, mode == Mode.DISTINCT);
        // Source time remains metadata: the existing kitchen finishes after the last stir animation.
        return new CookingPlan(true, true, id, stirFryCount, inputs.size(), carrier,
                List.of(new CookingPlan.Output(result, resultCount, List.of(), quality)), List.of());
    }

    public static PotRecipe fromConfig(Key id, ConfigSection section) {
        Set<String> fields = Set.of("match", "ingredients", "result", "result_count", "stir_fry_count", "source_time", "carrier");
        if (!fields.equals(section.keySet())) throw new IllegalArgumentException(section.path() + ": unknown or missing pot field");
        if (!(section.get("ingredients") instanceof List<?> slots)) {
            throw new IllegalArgumentException(section.path() + ".ingredients must be a list");
        }
        List<Set<Key>> ingredients = new ArrayList<>();
        for (Object slot : slots) {
            if (!(slot instanceof List<?> choices)) throw new IllegalArgumentException(section.path() + ": each ingredient requires alternatives");
            ingredients.add(choices.stream().map(PotRecipe::itemKey).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        Key carrier = itemKey(section.get("carrier"));
        return new PotRecipe(id, Mode.valueOf(section.getNonNullString("match").toUpperCase(Locale.ROOT)), ingredients,
                itemKey(section.get("result")), integer(section, "result_count"), integer(section, "stir_fry_count"),
                integer(section, "source_time"), carrier.asString().equals("minecraft:air") ? null : carrier);
    }

    private static Key itemKey(Object value) {
        if (!(value instanceof String text) || !text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || text.contains("..")) {
            throw new IllegalArgumentException("Invalid pot item id: " + value);
        }
        return Key.of(text);
    }

    private static int integer(ConfigSection section, String field) {
        if (!(section.get(field) instanceof Integer number)) {
            throw new IllegalArgumentException(section.path() + "." + field + " must be an integer");
        }
        return number;
    }
}
