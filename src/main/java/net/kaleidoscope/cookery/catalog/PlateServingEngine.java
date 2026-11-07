package net.kaleidoscope.cookery.catalog;

/** Pure interaction transitions for a plated dish. Furniture code supplies inventory and display effects. */
public final class PlateServingEngine {
    private PlateServingEngine() {}

    public static PlateServing take(PlateServing serving) {
        if (serving == null) throw new IllegalArgumentException("Plate serving is missing");
        return serving.empty() ? serving : serving.takeOne();
    }

    public static PlateServing refill(PlateServing serving) {
        if (serving == null) throw new IllegalArgumentException("Plate serving is missing");
        return serving.full() ? serving : serving.refillOne();
    }

    public static boolean shouldBreak(PlateServing serving) {
        return serving != null && serving.empty();
    }
}
