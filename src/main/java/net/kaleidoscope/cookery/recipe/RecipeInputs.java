package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared matching for the official multiset and concrete-item-set recipes. */
final class RecipeInputs {
    static final int MAX_SLOTS = 9;
    static final int MAX_ALTERNATIVES = 128;

    private RecipeInputs() {}

    static List<Set<Key>> copy(List<Set<Key>> ingredients) {
        List<Set<Key>> copy = ingredients.stream().map(Set::copyOf).toList();
        if (copy.isEmpty() || copy.size() > MAX_SLOTS
                || copy.stream().anyMatch(slot -> slot.isEmpty() || slot.size() > MAX_ALTERNATIVES)) {
            throw new IllegalArgumentException("Recipe requires 1..9 slots and 1..128 alternatives per slot");
        }
        return copy;
    }

    static boolean matches(List<Set<Key>> ingredients, List<Key> inputs, boolean distinct) {
        if (inputs.isEmpty() || inputs.size() > MAX_SLOTS) return false;
        List<Key> slots = distinct ? inputs.stream().distinct().toList() : inputs;
        if (slots.size() != ingredients.size()) return false;
        int[] owners = new int[slots.size()];
        Arrays.fill(owners, -1);
        for (int input = 0; input < slots.size(); input++) {
            if (!assign(ingredients, input, slots, owners, new boolean[owners.length])) return false;
        }
        return true;
    }

    // Augmenting paths avoid consuming the only choice of a narrower overlapping tag.
    private static boolean assign(List<Set<Key>> ingredients, int input, List<Key> slots, int[] owners, boolean[] visited) {
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (visited[ingredient] || !ingredients.get(ingredient).contains(slots.get(input))) continue;
            visited[ingredient] = true;
            if (owners[ingredient] < 0 || assign(ingredients, owners[ingredient], slots, owners, visited)) {
                owners[ingredient] = input;
                return true;
            }
        }
        return false;
    }

    static DishQuality quality(List<Key> inputs, boolean distinct) {
        if (!distinct) return DishQuality.SUPERB;
        Map<Key, Integer> counts = new HashMap<>();
        for (Key input : inputs) counts.merge(input, 1, Integer::sum);
        int portions = counts.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int deviation = counts.values().stream().mapToInt(count -> count - portions).sum();
        return DishQuality.fromDeviation(deviation);
    }
}
