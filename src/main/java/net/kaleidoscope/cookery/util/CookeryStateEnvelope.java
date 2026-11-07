package net.kaleidoscope.cookery.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;

/** Versioned JSON envelope shared by food PDC bridges while accepting legacy payloads. */
public final class CookeryStateEnvelope {
    public static final int MAX_BYTES = 16_384;

    private CookeryStateEnvelope() {}

    public static String wrap(String namespace, int schemaVersion, String payload) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || schemaVersion < 1 || payload == null || payload.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid Cookery state envelope");
        }
        JsonObject root = new JsonObject();
        root.addProperty("namespace", namespace);
        root.addProperty("schema_version", schemaVersion);
        root.addProperty("source_commit", CookeryCatalog.SOURCE_COMMIT);
        root.add("payload", JsonParser.parseString(payload));
        String encoded = root.toString();
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Cookery state envelope too large");
        }
        return encoded;
    }

    public static String payload(String encoded, String namespace, int schemaVersion) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid Cookery state size");
        }
        JsonObject root = JsonParser.parseString(encoded).getAsJsonObject();
        if (!root.has("namespace") && !root.has("schema_version")
                && !root.has("source_commit") && !root.has("payload")) {
            return encoded; // pre-envelope save
        }
        if (!root.has("namespace") || !root.has("schema_version") || !root.has("source_commit")
                || !root.has("payload")) {
            throw new IllegalArgumentException("Incomplete Cookery state envelope");
        }
        if (!namespace.equals(root.get("namespace").getAsString())
                || schemaVersion != root.get("schema_version").getAsInt()
                || !CookeryCatalog.SOURCE_COMMIT.equals(root.get("source_commit").getAsString())
                ) {
            throw new IllegalArgumentException("Foreign or unsupported Cookery state envelope");
        }
        String payload = root.get("payload").toString();
        if (payload.length() > MAX_BYTES) throw new IllegalArgumentException("Cookery state payload too large");
        return payload;
    }
}
