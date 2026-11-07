package net.kaleidoscope.series;

import net.momirealms.craftengine.libraries.nbt.ByteTag;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.IntTag;
import net.momirealms.craftengine.libraries.nbt.LongTag;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData;

import java.util.function.IntFunction;

/** Missing required fields indicate damage, not a new empty inventory. */
public final class SavedState {
    public static final int MAX_BYTES = 262144;
    public static final String ENVELOPE_NAMESPACE = "namespace";
    public static final String ENVELOPE_SCHEMA = "schema_version";
    public static final String ENVELOPE_SOURCE = "source_commit";
    public static final String ENVELOPE_PAYLOAD = "payload";
    public static final String SERIES_SOURCE = "series-0.1.0-dev";

    private SavedState() {}

    public static int integer(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntTag)) throw invalid(key);
        return tag.getInt(key);
    }

    public static long longValue(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof LongTag)) throw invalid(key);
        return tag.getLong(key);
    }

    public static boolean flag(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ByteTag) || (tag.getByte(key) != 0 && tag.getByte(key) != 1)) throw invalid(key);
        return tag.getBoolean(key);
    }

    public static String text(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof StringTag)) throw invalid(key);
        return tag.getString(key);
    }

    public static String optionalText(CompoundTag tag, String key) {
        return tag.containsKey(key) ? text(tag, key) : null;
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Missing or invalid furniture save field: " + key);
    }

    public static String namespace(String saveKey) {
        if (saveKey == null || saveKey.isBlank()) throw new IllegalArgumentException("Missing save key");
        int version = saveKey.lastIndexOf("_v");
        return version > 0 ? saveKey.substring(0, version) : saveKey;
    }

    /** Wraps a payload while leaving all existing fields inside the payload untouched. */
    public static CompoundTag envelope(String namespace, int schemaVersion, String sourceCommit,
                                       CompoundTag payload) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid state namespace: " + namespace);
        }
        if (schemaVersion < 1 || sourceCommit == null || sourceCommit.isBlank() || payload == null) {
            throw new IllegalArgumentException("Incomplete state envelope");
        }
        CompoundTag envelope = new CompoundTag();
        envelope.putString(ENVELOPE_NAMESPACE, namespace);
        envelope.putInt(ENVELOPE_SCHEMA, schemaVersion);
        envelope.putString(ENVELOPE_SOURCE, sourceCommit);
        envelope.put(ENVELOPE_PAYLOAD, payload.deepClone());
        assertSize(envelope);
        return envelope;
    }

    /**
     * Reads both the new envelope and legacy raw payloads. Legacy saves are
     * intentionally accepted so upgrading never turns an old inventory into
     * an empty one. Future/foreign envelopes are rejected and remain opaque.
     */
    public static CompoundTag payload(CompoundTag saved, String namespace, String sourceCommit,
                                      int currentSchema, IntFunction<CompoundTag> migrate) {
        if (saved == null) throw new IllegalArgumentException("Missing saved state");
        assertSize(saved);
        boolean envelope = saved.containsKey(ENVELOPE_NAMESPACE) || saved.containsKey(ENVELOPE_SCHEMA)
                || saved.containsKey(ENVELOPE_SOURCE) || saved.containsKey(ENVELOPE_PAYLOAD);
        if (!envelope) return saved;
        String actualNamespace = text(saved, ENVELOPE_NAMESPACE);
        int version = integer(saved, ENVELOPE_SCHEMA);
        String actualSource = text(saved, ENVELOPE_SOURCE);
        if (!namespace.equals(actualNamespace) || sourceCommit == null || !sourceCommit.equals(actualSource)) {
            throw new IllegalArgumentException("Foreign saved state envelope: " + actualNamespace);
        }
        if (!(saved.get(ENVELOPE_PAYLOAD) instanceof CompoundTag rawPayload)) {
            throw invalid(ENVELOPE_PAYLOAD);
        }
        CompoundTag payload = rawPayload;
        if (version > currentSchema || version < 1) {
            throw new IllegalArgumentException("Unsupported saved state schema: " + version);
        }
        if (version < currentSchema) {
            if (migrate == null) throw new IllegalArgumentException("No migration for saved state schema: " + version);
            payload = migrate.apply(version);
            if (payload == null) throw new IllegalArgumentException("Incomplete saved state migration");
        }
        return payload;
    }

    private static void assertSize(CompoundTag tag) {
        try {
            if (FurniturePersistentData.of(tag).toBytes().length > MAX_BYTES) {
                throw new IllegalArgumentException("Saved state too large");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Cannot encode saved state", exception);
        }
    }
}
