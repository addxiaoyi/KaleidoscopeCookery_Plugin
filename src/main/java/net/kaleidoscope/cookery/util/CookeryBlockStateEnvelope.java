package net.kaleidoscope.cookery.util;

import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

/**
 * Versioned metadata for block entity payloads.
 *
 * The original payload remains under its historical key so old readers and
 * existing fixtures keep working. The sidecar is the canonical envelope for
 * new saves and lets a corrupt or future state be rejected without treating
 * it as an empty appliance.
 */
public final class CookeryBlockStateEnvelope {
    public static final int SCHEMA_VERSION = 1;
    private static final String NAMESPACE = "namespace";
    private static final String SCHEMA = "schema_version";
    private static final String SOURCE = "source_commit";
    private static final String PAYLOAD = "payload";
    private static final String SIDECAR_SUFFIX = "__state_envelope";

    private CookeryBlockStateEnvelope() {}

    public static void put(CompoundTag root, String dataKey, CompoundTag payload) {
        if (root == null || payload == null || !validKey(dataKey)) {
            throw new IllegalArgumentException("Invalid Cookery block state");
        }
        root.put(dataKey, payload);
        CompoundTag envelope = new CompoundTag();
        envelope.putString(NAMESPACE, dataKey);
        envelope.putInt(SCHEMA, SCHEMA_VERSION);
        envelope.putString(SOURCE, CookeryCatalog.SOURCE_COMMIT);
        envelope.put(PAYLOAD, payload);
        root.put(sidecar(dataKey), envelope);
    }

    /**
     * Reads an enveloped state and accepts a legacy raw payload when no
     * sidecar exists. A present but invalid sidecar is always an error.
     */
    public static CompoundTag payload(CompoundTag root, String dataKey) {
        if (root == null || !validKey(dataKey)) {
            throw new IllegalArgumentException("Invalid Cookery block state key");
        }
        String sidecarKey = sidecar(dataKey);
        if (!root.containsKey(sidecarKey)) {
            return root.getCompound(dataKey);
        }
        CompoundTag envelope = root.getCompound(sidecarKey);
        if (envelope == null || !dataKey.equals(envelope.getString(NAMESPACE, null))
                || envelope.getInt(SCHEMA, -1) != SCHEMA_VERSION
                || !CookeryCatalog.SOURCE_COMMIT.equals(envelope.getString(SOURCE, null))) {
            throw new IllegalArgumentException("Foreign or unsupported Cookery block state envelope");
        }
        Tag encoded = envelope.get(PAYLOAD);
        if (!(encoded instanceof CompoundTag state)) {
            throw new IllegalArgumentException("Cookery block state payload is not a compound");
        }
        return state;
    }

    public static String sidecar(String dataKey) {
        if (!validKey(dataKey)) throw new IllegalArgumentException("Invalid Cookery block state key");
        return dataKey + SIDECAR_SUFFIX;
    }

    private static boolean validKey(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}
