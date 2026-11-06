package net.kaleidoscope.cookery.catalog;

import java.util.List;

public record FoodDefinition(int nutrition, float saturation, boolean alwaysEat, boolean meat,
                             int consumeTicks, List<ConsumeEffectDefinition> effects) {
    public FoodDefinition {
        if (nutrition < 0 || nutrition > 2000 || !Float.isFinite(saturation) || saturation < 0 || saturation > 400_000
                || consumeTicks < 1 || consumeTicks > 1200 || effects == null || effects.size() > 32) {
            throw new IllegalArgumentException("Invalid food properties");
        }
        effects = List.copyOf(effects);
    }

    public FoodDefinition scaled(QualityProfile quality) {
        int scaledNutrition = nutrition == 0 ? 0 : Math.max(1, (int) Math.round(nutrition * quality.ratio()));
        return new FoodDefinition(scaledNutrition, (float) (saturation * quality.ratio()), alwaysEat, meat,
                consumeTicks, effects.stream().map(effect -> effect.scaled(quality)).toList());
    }
}
