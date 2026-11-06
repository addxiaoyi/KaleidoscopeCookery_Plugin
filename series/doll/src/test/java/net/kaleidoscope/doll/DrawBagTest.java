package net.kaleidoscope.doll;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DrawBagTest {
    private static final List<String> POOL = List.of("doll:a", "doll:b", "doll:c");

    @Test void exhaustsEachTierBeforeRepeating() {
        DrawBag bag = new DrawBag(POOL);
        Random random = new Random(7);
        for (int cycle = 0; cycle < 5; cycle++) {
            var drawn = new HashSet<String>();
            for (int i = 0; i < POOL.size(); i++) {
                String prize = bag.peek(random);
                assertTrue(drawn.add(prize));
                bag.take(prize);
            }
            assertEquals(new HashSet<>(POOL), drawn);
        }
    }

    @Test void failedPaymentDoesNotAdvanceTheQueue() {
        DrawBag bag = new DrawBag(POOL);
        String prize = bag.peek(new Random(3));
        assertEquals(prize, bag.peek(new Random(99)));
        assertEquals(3, bag.snapshot().size());
    }

    @Test void restartPreservesTheNextPrize() {
        DrawBag original = new DrawBag(POOL);
        original.take(original.peek(new Random(5)));
        DrawBag loaded = new DrawBag(POOL);
        loaded.restore(original.snapshot());
        assertEquals(original.peek(new Random()), loaded.peek(new Random()));
        assertEquals(2, loaded.snapshot().size());
    }

    @Test void rejectsInvalidStateWithoutErasingTheQueue() {
        DrawBag bag = new DrawBag(POOL);
        bag.peek(new Random(1));
        var before = bag.snapshot();
        assertThrows(IllegalArgumentException.class, () -> bag.restore(List.of("doll:unknown")));
        assertThrows(IllegalArgumentException.class, () -> bag.restore(List.of("doll:a", "doll:a")));
        assertThrows(IllegalStateException.class, () -> bag.take("doll:unknown"));
        assertEquals(before, bag.snapshot());
    }

    @Test void rejectsEmptyAndDuplicatePools() {
        assertThrows(IllegalArgumentException.class, () -> new DrawBag(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DrawBag(List.of("doll:a", "doll:a")));
    }
}
