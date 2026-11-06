package net.kaleidoscope.cookery.recipe;

import java.util.Locale;

public enum BambooTrayMode {
    DRYING,
    WETTING;

    public static BambooTrayMode parse(String value) {
        if (value == null) throw new IllegalArgumentException("Bamboo tray mode is required");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "drying" -> DRYING;
            case "wetting" -> WETTING;
            default -> throw new IllegalArgumentException("Bamboo tray mode must be drying or wetting: " + value);
        };
    }
}
