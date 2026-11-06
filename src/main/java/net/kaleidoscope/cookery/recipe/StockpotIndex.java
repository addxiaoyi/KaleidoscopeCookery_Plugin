package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class StockpotIndex {
    private record Slot(StockpotRecipe.Mode mode, Key soup, int count) {}
    private final Map<Slot, List<StockpotRecipe>> recipes;

    StockpotIndex(List<StockpotRecipe> source) {
        Map<Slot, List<StockpotRecipe>> index = new HashMap<>();
        source.stream().sorted(Comparator.comparing(recipe -> recipe.id().asString())).forEach(recipe ->
                index.computeIfAbsent(new Slot(recipe.mode(), recipe.soupBase(), recipe.ingredients().size()),
                        ignored -> new ArrayList<>()).add(recipe));
        index.replaceAll((slot, choices) -> List.copyOf(choices));
        recipes = Map.copyOf(index);
    }

    StockpotRecipe find(List<Key> inputs, Key soup) {
        for (StockpotRecipe.Mode mode : StockpotRecipe.Mode.values()) {
            int count = mode == StockpotRecipe.Mode.EXACT ? inputs.size() : (int) inputs.stream().distinct().count();
            for (StockpotRecipe recipe : recipes.getOrDefault(new Slot(mode, soup, count), List.of())) {
                if (recipe.matches(inputs, soup)) return recipe;
            }
        }
        return null;
    }
}
