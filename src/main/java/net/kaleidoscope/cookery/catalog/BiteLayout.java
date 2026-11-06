package net.kaleidoscope.cookery.catalog;

import java.util.List;

/** Stable display slots for placed food bites. Coordinates are block-local centers. */
public record BiteLayout(String id, List<Slot> slots) {
    public BiteLayout {
        if (!java.util.Set.of("1x1", "1x2", "3x3").contains(id) || slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("Unknown bite layout: " + id);
        }
        slots = List.copyOf(slots);
    }

    public int maxVisibleBites(int maxBites) {
        if (maxBites < 1) throw new IllegalArgumentException("maxBites must be positive");
        return Math.min(maxBites, slots.size());
    }

    public static BiteLayout of(String id) {
        return switch (id) {
            case "1x1" -> new BiteLayout(id, List.of(new Slot(.5f, .5f)));
            case "1x2" -> new BiteLayout(id, List.of(new Slot(.25f, .5f), new Slot(.75f, .5f)));
            case "3x3" -> new BiteLayout(id, List.of(
                    new Slot(.25f, .25f), new Slot(.5f, .25f), new Slot(.75f, .25f),
                    new Slot(.25f, .5f), new Slot(.5f, .5f), new Slot(.75f, .5f),
                    new Slot(.25f, .75f), new Slot(.5f, .75f), new Slot(.75f, .75f)));
            default -> throw new IllegalArgumentException("Unknown bite layout: " + id);
        };
    }

    public record Slot(float x, float z) {
        public Slot {
            if (!Float.isFinite(x) || !Float.isFinite(z) || x <= 0 || x >= 1 || z <= 0 || z >= 1) {
                throw new IllegalArgumentException("Bite slot must be inside block bounds");
            }
        }
    }
}
