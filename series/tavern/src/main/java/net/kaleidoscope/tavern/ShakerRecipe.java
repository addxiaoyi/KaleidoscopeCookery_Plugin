package net.kaleidoscope.tavern;

import net.kaleidoscope.series.Ingredients;
import net.kaleidoscope.series.ItemIds;
import java.util.List;

public record ShakerRecipe(String id, List<List<String>> ingredients, String output) {
    public ShakerRecipe {
        ItemIds.require(id);
        ItemIds.require(output);
        ingredients = ingredients.stream().map(List::copyOf).toList();
        if (ingredients.size() != 3 || ingredients.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("Shaker recipes require three ingredients: " + id);
        }
        ingredients.forEach(group -> group.forEach(ItemIds::require));
    }

    public boolean matches(List<String> supplied) { return Ingredients.match(ingredients, supplied); }
}
