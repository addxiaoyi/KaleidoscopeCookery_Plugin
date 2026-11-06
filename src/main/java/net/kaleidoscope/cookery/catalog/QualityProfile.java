package net.kaleidoscope.cookery.catalog;

import net.kaleidoscope.cookery.recipe.DishQuality;

public record QualityProfile(String name, double ratio) {
    public static final QualityProfile BASE = new QualityProfile("BASE", 1);

    public QualityProfile {
        if (name == null || !name.matches("[A-Z_]{1,32}") || !Double.isFinite(ratio) || ratio <= 0 || ratio > 2) {
            throw new IllegalArgumentException("Invalid food quality: " + name);
        }
    }

    public static QualityProfile of(DishQuality quality) {
        return new QualityProfile(quality.name(), quality.ratio());
    }
}
