package net.kaleidoscope.tavern;

record GrapeGrowth(String kind, int vine, int fruit, boolean waxed) {
    static final int PULSE_TICKS = 68;
    GrapeGrowth {
        if (!java.util.Set.of("none", "grape", "ice_grape", "gold_grape").contains(kind)
                || vine < 0 || vine > 3 || fruit < -1 || fruit > 5
                || (kind.equals("none") && (vine != 0 || fruit != -1))
                || (fruit >= 0 && vine != 3) || (!kind.equals("none") && waxed)) {
            throw new IllegalArgumentException("Invalid grape growth state");
        }
    }
    static GrapeGrowth empty() { return new GrapeGrowth("none", 0, -1, false); }
    boolean planted() { return !kind.equals("none"); }
    GrapeGrowth ageVine() { return new GrapeGrowth(kind, Math.min(3, vine + 1), fruit, false); }
    GrapeGrowth ageFruit(int growth) {
        if (growth < 1 || growth > 2 || vine != 3) throw new IllegalArgumentException("Invalid fruit growth");
        return new GrapeGrowth(kind, vine, fruit < 0 ? 0 : Math.min(5, fruit + growth), false);
    }
    GrapeGrowth harvested() { return new GrapeGrowth(kind, vine, -1, false); }
    double probability(double temperature, int randomTickSpeed) {
        double chance = kind.equals("ice_grape") && temperature < 0.15 || kind.equals("gold_grape") && temperature > 1 ? 0.8 : 0.25;
        // Vanilla selects random block positions out of a 16^3 chunk section each tick.
        return 1 - Math.pow(1 - chance / 4096, (double) Math.max(0, randomTickSpeed) * PULSE_TICKS);
    }
    String variant() { return planted() ? kind + "_" + vine + "_" + (fruit + 1) : "ground"; }
}
