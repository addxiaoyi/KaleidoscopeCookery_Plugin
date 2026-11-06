package net.kaleidoscope.cookery.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kaleidoscope.cookery.catalog.CatalogJson;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

public final class EffectCodec {
    private EffectCodec() {}

    public static String encode(Map<String, EffectState> states) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray effects = new JsonArray();
        new TreeMap<>(states).values().forEach(state -> effects.add(encodeState(state)));
        root.add("effects", effects);
        return root.toString();
    }

    private static JsonObject encodeState(EffectState state) {
        JsonObject json = new JsonObject();
        json.addProperty("id", state.id());
        json.addProperty("level", state.level());
        json.addProperty("remaining", state.remaining());
        json.addProperty("source", state.source());
        if (state.hidden() != null) json.add("hidden", encodeState(state.hidden()));
        return json;
    }

    public static Map<String, EffectState> decode(String encoded) {
        if (encoded == null || encoded.length() > 32_768) throw new IllegalArgumentException("Kitchen effects exceed save limit");
        try {
            JsonObject root = CatalogJson.read(new StringReader(encoded));
            if (CatalogJson.integer(root, "version") != 1 || !(root.get("effects") instanceof JsonArray array) || array.size() > 12) {
                throw new IllegalArgumentException("Unknown kitchen effect save format");
            }
            Map<String, EffectState> states = new TreeMap<>();
            for (JsonElement raw : array) {
                EffectState state = decodeState(raw.getAsJsonObject(), 0);
                if (states.putIfAbsent(state.id(), state) != null) throw new IllegalArgumentException("Duplicate saved effect: " + state.id());
            }
            return Map.copyOf(states);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid kitchen effect save", exception);
        }
    }

    private static EffectState decodeState(JsonObject json, int depth) {
        if (depth >= 8) throw new IllegalArgumentException("Hidden effect chain exceeds save limit");
        return new EffectState(CatalogJson.string(json, "id"), CatalogJson.integer(json, "level"),
                CatalogJson.integer(json, "remaining"), CatalogJson.string(json, "source"),
                json.has("hidden") ? decodeState(json.getAsJsonObject("hidden"), depth + 1) : null);
    }
}
