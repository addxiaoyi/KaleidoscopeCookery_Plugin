package net.kaleidoscope.cookery.effect;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EffectStateTest {
    private EffectState vigor(int level, int ticks) {
        return new EffectState(EffectKind.VIGOR.id(), level, ticks, "kaleidoscopecookery:baozi", null);
    }

    @Test void strongerShortEffectRestoresLongerPreviousEffect() {
        var state = vigor(0, 100).merge(vigor(1, 20));
        assertEquals(1, state.level());
        assertEquals(0, state.elapse(20).level());
        assertEquals(80, state.elapse(20).remaining());
    }

    @Test void weakLongEffectWaitsUntilStrongerExpires() {
        var state = vigor(2, 100).merge(vigor(0, 200));
        assertEquals(2, state.level());
        assertEquals(0, state.elapse(100).level());
        assertEquals(100, state.elapse(100).remaining());
        assertNull(state.elapse(200));
    }

    @Test void repeatDoesNotStackOrShortenDuration() {
        assertEquals(100, vigor(0, 100).merge(vigor(0, 20)).remaining());
        assertEquals(200, vigor(0, 100).merge(vigor(0, 200)).remaining());
    }

    @Test void saveRoundTripPreservesHiddenState() {
        var state = vigor(0, 100).merge(vigor(1, 20));
        var original = Map.of(state.id(), state);
        assertEquals(original, EffectCodec.decode(EffectCodec.encode(original)));
    }

    @Test void invalidSavesFailInsteadOfDroppingEffects() {
        for (String encoded : new String[]{"{}", "{\"version\":2,\"effects\":[]}", "{\"version\":1,\"version\":1,\"effects\":[]}"}) {
            assertThrows(IllegalArgumentException.class, () -> EffectCodec.decode(encoded));
        }
        String valid = EffectCodec.encode(Map.of(vigor(0, 20).id(), vigor(0, 20)));
        assertThrows(IllegalArgumentException.class, () -> EffectCodec.decode(valid.replace(":20", ":-1")));
        assertThrows(IllegalArgumentException.class, () -> EffectCodec.decode(valid.replace(":20", ":1.5")));
        assertThrows(IllegalArgumentException.class, () -> EffectCodec.decode(valid.replace("vigor", "unknown")));
    }
}
