package net.kaleidoscope.tavern;

import net.kaleidoscope.series.ItemIds;

import java.util.List;

public record BrewRecipe(String id, String fluid, List<List<String>> ingredients,
                         List<String> carriers, String output, int unitTime) {
    public BrewRecipe {
        ItemIds.require(id);
        ItemIds.require(fluid);
        ItemIds.require(output);
        ingredients = ingredients.stream().map(List::copyOf).toList();
        carriers = List.copyOf(carriers);
        if (ingredients.size() > 4 || ingredients.stream().anyMatch(List::isEmpty)
                || carriers.isEmpty() || unitTime <= 0) throw new IllegalArgumentException("Invalid brew recipe: " + id);
        ingredients.forEach(group -> group.forEach(ItemIds::require));
        carriers.forEach(ItemIds::require);
    }

    public boolean matches(String suppliedFluid, List<String> slots) {
        return fluid.equals(suppliedFluid) && net.kaleidoscope.series.Ingredients.match(ingredients, slots);
    }
}
