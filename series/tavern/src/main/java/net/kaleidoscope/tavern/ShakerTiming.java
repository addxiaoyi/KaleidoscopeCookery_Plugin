package net.kaleidoscope.tavern;

public final class ShakerTiming {
    public static final int TIMEOUT = 111;
    public enum Outcome { UNFINISHED, MYSTERY, SIGNATURE, RECIPE }
    private ShakerTiming() {}

    public static Outcome outcome(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative shake time");
        if (ticks < 19) return Outcome.UNFINISHED;
        if (ticks < 69) return Outcome.MYSTERY;
        if (ticks < 89) return Outcome.SIGNATURE;
        if (ticks < 99) return Outcome.RECIPE;
        return Outcome.MYSTERY;
    }
}
