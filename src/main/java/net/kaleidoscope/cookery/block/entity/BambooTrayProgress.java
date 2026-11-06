package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.recipe.BambooTrayMode;

import java.util.Objects;

/** Processing state for one tray slot, independent of the server inventory. */
public final class BambooTrayProgress {
    private static final byte NONE = 0;
    private static final byte DRIED = 1;
    private static final byte WETTED = 2;

    private int ticks;
    private byte completionCode;

    public int ticks() {
        return ticks;
    }

    public byte completionCode() {
        return completionCode;
    }

    public void restore(int ticks, byte completionCode) {
        if (ticks < 0 || completionCode < NONE || completionCode > WETTED) {
            throw new IllegalArgumentException("Invalid bamboo tray progress: " + ticks + "/" + completionCode);
        }
        this.ticks = ticks;
        this.completionCode = completionCode;
    }

    public void reset() {
        ticks = 0;
        completionCode = NONE;
    }

    public void resetTicks() {
        ticks = 0;
    }

    public boolean isCompleted() {
        return completionCode != NONE;
    }

    public boolean isCompleted(BambooTrayMode mode) {
        return completionCode == code(mode);
    }

    public boolean advance(BambooTrayMode mode, int durationTicks, int elapsedTicks) {
        Objects.requireNonNull(mode, "mode");
        if (durationTicks <= 0 || elapsedTicks <= 0) {
            throw new IllegalArgumentException("Bamboo tray duration and elapsed ticks must be positive");
        }
        if (isCompleted(mode)) {
            return false;
        }
        if (isCompleted()) {
            reset();
        }
        ticks = (int) Math.min(durationTicks, (long) ticks + elapsedTicks);
        if (ticks < durationTicks) {
            return false;
        }
        completionCode = code(mode);
        return true;
    }

    private static byte code(BambooTrayMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case DRYING -> DRIED;
            case WETTING -> WETTED;
        };
    }
}
