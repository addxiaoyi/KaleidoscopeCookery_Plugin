package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BambooTrayProgressTest {
    @Test
    void processingFinishesOnTheFirstNineteenTickStepPastTheDuration() {
        BambooTrayProgress progress = new BambooTrayProgress();
        for (int step = 0; step < 94; step++) {
            assertFalse(progress.advance(BambooTrayMode.DRYING, 1800, 19));
        }
        assertEquals(1786, progress.ticks());
        assertTrue(progress.advance(BambooTrayMode.DRYING, 1800, 19));
        assertEquals(1800, progress.ticks());
        assertTrue(progress.isCompleted(BambooTrayMode.DRYING));
    }

    @Test
    void finishedDirectionCannotProcessAgainUntilReversed() {
        BambooTrayProgress progress = new BambooTrayProgress();
        assertTrue(progress.advance(BambooTrayMode.DRYING, 19, 19));
        assertFalse(progress.advance(BambooTrayMode.DRYING, 19, 19));
        assertFalse(progress.advance(BambooTrayMode.WETTING, 900, 19));
        assertEquals(19, progress.ticks());
        assertFalse(progress.isCompleted());
        for (int step = 0; step < 46; step++) {
            assertFalse(progress.advance(BambooTrayMode.WETTING, 900, 19));
        }
        assertTrue(progress.advance(BambooTrayMode.WETTING, 900, 19));
        assertEquals(900, progress.ticks());
        assertTrue(progress.isCompleted(BambooTrayMode.WETTING));
    }

    @Test
    void missingRecipeResetsTimeWithoutMakingFinishedItemsInsertable() {
        BambooTrayProgress progress = new BambooTrayProgress();
        progress.advance(BambooTrayMode.DRYING, 19, 19);
        progress.resetTicks();
        assertEquals(0, progress.ticks());
        assertTrue(progress.isCompleted(BambooTrayMode.DRYING));
        assertFalse(progress.advance(BambooTrayMode.DRYING, 19, 19));
    }

    @Test
    void unfinishedProgressSurvivesWeatherChangeAndRecipeReload() {
        BambooTrayProgress progress = new BambooTrayProgress();
        assertFalse(progress.advance(BambooTrayMode.DRYING, 1800, 600));
        // Upstream keeps one progress counter; only a completed reversal resets it.
        assertFalse(progress.advance(BambooTrayMode.WETTING, 900, 19));
        assertEquals(619, progress.ticks());
        assertTrue(progress.advance(BambooTrayMode.WETTING, 600, 19));
        assertEquals(600, progress.ticks());
    }

    @Test
    void savedCompletionUsesStableCodesAndEmptyingClearsIt() {
        BambooTrayProgress progress = new BambooTrayProgress();
        progress.restore(1800, (byte) 1);
        assertTrue(progress.isCompleted(BambooTrayMode.DRYING));
        assertEquals(1, progress.completionCode());
        progress.restore(900, (byte) 2);
        assertTrue(progress.isCompleted(BambooTrayMode.WETTING));
        progress.reset();
        assertEquals(0, progress.ticks());
        assertEquals(0, progress.completionCode());
        assertFalse(progress.isCompleted());
    }

    @Test
    void invalidPersistedStateIsRejected() {
        BambooTrayProgress progress = new BambooTrayProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.restore(-1, (byte) 0));
        assertThrows(IllegalArgumentException.class, () -> progress.restore(0, (byte) 3));
    }

    @Test
    void largeValidDurationsCannotOverflowProgress() {
        BambooTrayProgress progress = new BambooTrayProgress();
        progress.restore(Integer.MAX_VALUE - 1, (byte) 0);
        assertTrue(progress.advance(BambooTrayMode.DRYING, Integer.MAX_VALUE, 19));
        assertEquals(Integer.MAX_VALUE, progress.ticks());
    }

    @Test
    void invalidTickArgumentsFailBeforeChangingState() {
        BambooTrayProgress progress = new BambooTrayProgress();
        assertThrows(IllegalArgumentException.class,
                () -> progress.advance(BambooTrayMode.DRYING, 0, 19));
        assertThrows(IllegalArgumentException.class,
                () -> progress.advance(BambooTrayMode.DRYING, 100, 0));
        assertEquals(0, progress.ticks());
        assertFalse(progress.isCompleted());
    }
}
