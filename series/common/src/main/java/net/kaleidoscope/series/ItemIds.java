package net.kaleidoscope.series;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ItemIds {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private ItemIds() {}

    public static String require(String id) {
        if (!ID.matcher(Objects.requireNonNull(id, "item id")).matches()) {
            throw new IllegalArgumentException("Invalid item id: " + id);
        }
        return id;
    }
}
