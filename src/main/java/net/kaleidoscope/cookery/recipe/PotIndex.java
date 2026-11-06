package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PotIndex {
    private record Slot(PotRecipe.Mode mode, int count) {}
    private final Map<Slot, List<PotRecipe>> recipes;

    PotIndex(List<PotRecipe> source) {
        Map<Slot, List<PotRecipe>> index = new HashMap<>();
        source.stream().sorted(Comparator.comparing(recipe -> recipe.id().asString())).forEach(recipe ->
                index.computeIfAbsent(new Slot(recipe.mode(), recipe.ingredients().size()), ignored -> new ArrayList<>()).add(recipe));
        index.replaceAll((slot, choices) -> List.copyOf(choices));
        recipes = Map.copyOf(index);
    }

    PotRecipe find(List<Key> inputs) {
        for (PotRecipe.Mode mode : PotRecipe.Mode.values()) {
            int count = mode == PotRecipe.Mode.EXACT ? inputs.size() : (int) inputs.stream().distinct().count();
            for (PotRecipe recipe : recipes.getOrDefault(new Slot(mode, count), List.of())) {
                if (recipe.matches(inputs)) return recipe;
            }
        }
        return null;
    }
}
