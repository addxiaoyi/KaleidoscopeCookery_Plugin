package net.kaleidoscope.tavern;

import java.util.List;
import java.util.Map;

public record TavernCatalog(List<BrewRecipe> barrel, List<PressingRecipe> pressing,
                            List<ShakerRecipe> shaker, Map<String, List<List<DrinkEffect>>> effects,
                            Map<String, List<String>> effectTags,
                            Map<String, String> buckets, Map<String, String> names) {
    public TavernCatalog {
        barrel = List.copyOf(barrel);
        pressing = List.copyOf(pressing);
        shaker = List.copyOf(shaker);
        effects = effects.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().stream().map(List::copyOf).toList()));
        effectTags = effectTags.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        buckets = Map.copyOf(buckets);
        names = Map.copyOf(names);
        if (barrel.isEmpty() || pressing.isEmpty()) throw new IllegalArgumentException("Empty Tavern recipe catalog");
        if (barrel.stream().map(BrewRecipe::id).distinct().count() != barrel.size()
                || pressing.stream().map(PressingRecipe::id).distinct().count() != pressing.size()
                || buckets.values().stream().distinct().count() != buckets.size()) {
            throw new IllegalArgumentException("Duplicate Tavern identifiers");
        }
    }

    public String fluidForBucket(String item) {
        return buckets.entrySet().stream().filter(entry -> entry.getValue().equals(item)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public BrewRecipe brew(String fluid, List<String> slots) {
        return barrel.stream().filter(recipe -> recipe.matches(fluid, slots)).findFirst().orElseGet(() ->
                new BrewRecipe("kaleidoscope_tavern:empty", fluid, List.of(),
                        List.of("kaleidoscope_tavern:empty_bottle"), "kaleidoscope_tavern:vinegar", 2400));
    }
}
