package net.kaleidoscope.cookery.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectHandlerCoverageTest {
    @Test
    void everyCatalogEffectHasAHandlerAndStableId() {
        var handlers = KitchenEffectHandlers.create();
        assertEquals(12, EffectKind.values().length);
        assertEquals(12, EffectKind.IDS.size());
        assertEquals(12, handlers.size());
        for (EffectKind kind : EffectKind.values()) {
            assertTrue(EffectKind.IDS.contains(kind.id()));
            assertNotNull(handlers.get(kind.id()), kind.id());
            assertSame(kind, EffectKind.fromId(kind.id()));
        }
    }
}
