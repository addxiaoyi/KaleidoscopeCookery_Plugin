package net.kaleidoscope.cookery.catalog;

public record ConsumeEffectDefinition(String id, int duration, int amplifier, double probability) {
    public ConsumeEffectDefinition {
        CatalogJson.requireId(id);
        if (duration < 1 || duration > 72_000_000 || amplifier < 0 || amplifier > 255
                || !Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid consume effect: " + id);
        }
    }

    public ConsumeEffectDefinition scaled(QualityProfile quality) {
        return new ConsumeEffectDefinition(id, Math.max(1, (int) Math.round(duration * quality.ratio())), amplifier, probability);
    }
}
