package net.kaleidoscope.cookery.util;

import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookeryBlockStateEnvelopeTest {
    private static final String KEY = "kaleidoscopecookery:plate";

    @Test
    void writesMetadataWhileKeepingTheLegacyPayloadProjection() {
        CompoundTag root = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        payload.putInt("schema", 1);
        CookeryBlockStateEnvelope.put(root, KEY, payload);

        assertEquals(1, root.getCompound(KEY).getInt("schema"));
        assertEquals(1, CookeryBlockStateEnvelope.payload(root, KEY).getInt("schema"));
        assertEquals(KEY, root.getCompound(CookeryBlockStateEnvelope.sidecar(KEY)).getString("namespace"));
    }

    @Test
    void readsLegacyPayloadWithoutInventingMetadata() {
        CompoundTag root = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        payload.putString("state", "served");
        root.put(KEY, payload);

        assertEquals("served", CookeryBlockStateEnvelope.payload(root, KEY).getString("state"));
        assertFalse(root.containsKey(CookeryBlockStateEnvelope.sidecar(KEY)));
    }

    @Test
    void rejectsForeignOrFutureEnvelope() {
        CompoundTag root = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        CookeryBlockStateEnvelope.put(root, KEY, payload);
        root.getCompound(CookeryBlockStateEnvelope.sidecar(KEY)).putInt("schema_version", 2);

        assertThrows(IllegalArgumentException.class, () -> CookeryBlockStateEnvelope.payload(root, KEY));
    }
}
