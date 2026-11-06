package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Official multisets and distinct-item recipes, kept separate from legacy cosine recipes. */
public record StockpotRecipe(Key id, Mode mode, List<Set<Key>> ingredients, Key soupBase,
                             Key result, int resultCount, int time, Key carrier, StockpotVisuals visuals) {
    public enum Mode { EXACT, DISTINCT }

    public StockpotRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(soupBase, "soupBase");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(visuals, "visuals");
        ingredients = RecipeInputs.copy(ingredients);
        if (resultCount < 1 || resultCount > 9 || time < 1) {
            throw new IllegalArgumentException(id + ": invalid stockpot inputs, servings or duration");
        }
    }

    public boolean matches(List<Key> inputs, Key liquid) {
        return soupBase.equals(liquid) && RecipeInputs.matches(ingredients, inputs, mode == Mode.DISTINCT);
    }

    public CookingPlan plan(List<Key> inputs) {
        DishQuality quality = RecipeInputs.quality(inputs, mode == Mode.DISTINCT);
        return new CookingPlan(true, true, id, time, inputs.size(), carrier,
                List.of(new CookingPlan.Output(result, resultCount, List.of(), quality)), List.of(), visuals);
    }

    public static StockpotRecipe fromConfig(Key id, ConfigSection section) {
        Set<String> fields = Set.of("match", "ingredients", "soup_base", "result", "result_count", "time", "carrier", "visuals");
        if (!fields.equals(section.keySet())) throw new IllegalArgumentException(section.path() + ": unknown or missing stockpot field");
        Object raw = section.get("ingredients");
        if (!(raw instanceof List<?> slots)) throw new IllegalArgumentException(section.path() + ".ingredients must be a list");
        List<Set<Key>> ingredients = new ArrayList<>();
        for (Object slot : slots) {
            if (!(slot instanceof List<?> choices)) throw new IllegalArgumentException(section.path() + ": each ingredient requires alternatives");
            ingredients.add(choices.stream().map(StockpotRecipe::itemKey).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
        ConfigSection visual = section.getSection("visuals");
        if (visual == null || !visual.keySet().equals(Set.of("cooking_model", "finished_model", "cooking_color", "finished_color"))) {
            throw new IllegalArgumentException(section.path() + ": invalid stockpot visuals");
        }
        Key carrier = itemKey(section.get("carrier"));
        return new StockpotRecipe(id, Mode.valueOf(section.getNonNullString("match").toUpperCase(Locale.ROOT)), ingredients,
                itemKey(section.get("soup_base")), itemKey(section.get("result")), integer(section, "result_count"), integer(section, "time"),
                carrier.asString().equals("minecraft:air") ? null : carrier,
                new StockpotVisuals(itemKey(visual.get("cooking_model")), itemKey(visual.get("finished_model")),
                        integer(visual, "cooking_color"), integer(visual, "finished_color")));
    }

    static Key itemKey(Object value) {
        if (!(value instanceof String text) || !text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || text.contains("..")) {
            throw new IllegalArgumentException("Invalid stockpot item id: " + value);
        }
        return Key.of(text);
    }

    private static int integer(ConfigSection section, String field) {
        Object value = section.get(field);
        if (!(value instanceof Integer number)) throw new IllegalArgumentException(section.path() + "." + field + " must be an integer");
        return number;
    }
}
