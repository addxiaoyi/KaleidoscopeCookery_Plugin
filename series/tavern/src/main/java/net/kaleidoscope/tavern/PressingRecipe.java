package net.kaleidoscope.tavern;

import net.kaleidoscope.series.ItemIds;

import java.util.List;

public record PressingRecipe(String id, List<String> ingredients, String fluid, int amount, String bucket) {
    public PressingRecipe {
        ItemIds.require(id);
        ItemIds.require(fluid);
        ItemIds.require(bucket);
        ingredients = List.copyOf(ingredients);
        ingredients.forEach(ItemIds::require);
        if (ingredients.isEmpty() || amount < 1 || amount > 1000) throw new IllegalArgumentException("Invalid pressing recipe: " + id);
    }
}
