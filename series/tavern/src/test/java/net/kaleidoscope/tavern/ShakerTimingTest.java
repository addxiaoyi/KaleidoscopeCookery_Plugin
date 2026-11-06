package net.kaleidoscope.tavern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ShakerTimingTest {
    @ParameterizedTest
    @CsvSource({"0,UNFINISHED", "18,UNFINISHED", "19,MYSTERY", "68,MYSTERY", "69,SIGNATURE",
            "88,SIGNATURE", "89,RECIPE", "98,RECIPE", "99,MYSTERY", "111,MYSTERY"})
    void usesUpstreamTimingBoundaries(int ticks, ShakerTiming.Outcome expected) {
        assertEquals(expected, ShakerTiming.outcome(ticks));
    }
}
