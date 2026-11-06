package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.util.Key;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves plugin item_tags once per published generation, with no base-material fallback. */
final class BambooTrayIndex {
    private static final int MAX_TAG_DEPTH = 8;

    record Input(Key item, BambooTrayMode mode) {}

    private BambooTrayIndex() {}

    static Map<Key, Set<String>> captureTags() {
        ItemTags registry = ItemTags.instance();
        Map<Key, Set<String>> tags = new LinkedHashMap<>();
        for (Key tag : registry.keys()) tags.put(tag, Set.copyOf(registry.members(tag)));
        return Map.copyOf(tags);
    }

    static Map<Input, BambooTrayRecipe> compile(List<BambooTrayRecipe> recipes, Map<Key, Set<String>> tags) {
        Map<Input, BambooTrayRecipe> index = new LinkedHashMap<>();
        Map<Key, Set<Key>> expanded = new HashMap<>();
        Set<Key> recipeIds = new HashSet<>();
        for (BambooTrayRecipe recipe : recipes) {
            if (!recipeIds.add(recipe.id())) {
                throw new IllegalArgumentException("Duplicate bamboo tray recipe id: " + recipe.id());
            }
            Set<Key> inputs = recipe.tagInput()
                    ? expanded.computeIfAbsent(recipe.inputKey(), tag -> members(tags, tag, new HashSet<>(), 0))
                    : Set.of(recipe.inputKey());
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("Bamboo tray recipe " + recipe.id() + " has an empty input tag " + recipe.input());
            }
            for (Key input : inputs) index.putIfAbsent(new Input(input, recipe.mode()), recipe);
        }
        return Map.copyOf(index);
    }

    private static Set<Key> members(Map<Key, Set<String>> tags, Key tag, Set<Key> path, int depth) {
        if (depth > MAX_TAG_DEPTH || !path.add(tag)) {
            throw new IllegalArgumentException("Bamboo tray item tag cycle or excessive nesting: " + tag);
        }
        Set<String> entries = tags.get(tag);
        if (entries == null) throw new IllegalArgumentException("Bamboo tray input tag is not declared in item_tags: " + tag);
        Set<Key> members = new LinkedHashSet<>();
        for (String entry : entries) {
            String member = entry.trim();
            if (member.startsWith("#")) {
                members.addAll(members(tags, Key.of(member.substring(1).trim()), path, depth + 1));
            } else {
                if (member.startsWith("craftengine:")) member = member.substring("craftengine:".length());
                members.add(Key.of(member));
            }
        }
        path.remove(tag);
        return Set.copyOf(members);
    }
}
