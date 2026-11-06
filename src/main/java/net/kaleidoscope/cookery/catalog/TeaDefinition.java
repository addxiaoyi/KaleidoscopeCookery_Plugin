package net.kaleidoscope.cookery.catalog;

import java.util.List;

public record TeaDefinition(int maxCups, int consumeTicks, String container, List<ConsumeEffectDefinition> effects) {
    public TeaDefinition {
        CatalogJson.requireId(container);
        if (maxCups < 1 || maxCups > 64 || consumeTicks < 1 || consumeTicks > 1200 || effects == null || effects.size() > 32) {
            throw new IllegalArgumentException("Invalid tea definition");
        }
        effects = List.copyOf(effects);
    }
}
