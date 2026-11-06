package net.kaleidoscope.doll;

import net.kaleidoscope.series.ItemIds;

import java.util.List;
import java.util.Map;

public record DollCatalog(List<String> dolls, List<String> players, List<List<String>> tiers,
                          List<String> tokens, Map<String, String> names) {
    public DollCatalog {
        dolls = List.copyOf(dolls);
        players = List.copyOf(players);
        tiers = tiers.stream().map(List::copyOf).toList();
        tokens = List.copyOf(tokens);
        names = Map.copyOf(names);
        dolls.forEach(ItemIds::require);
        tokens.forEach(ItemIds::require);
        if (dolls.size() != new java.util.HashSet<>(dolls).size() || tiers.size() != 3 || tokens.size() != 3
                || !dolls.containsAll(players)) {
            throw new IllegalArgumentException("Invalid doll catalog");
        }
        for (List<String> tier : tiers) {
            if (tier.isEmpty() || !dolls.containsAll(tier)) throw new IllegalArgumentException("Invalid doll tier");
        }
    }
}
