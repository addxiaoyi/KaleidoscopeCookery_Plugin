package net.kaleidoscope.cookery.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CatalogJson {
    private CatalogJson() {}

    public static JsonObject read(Reader input) throws IOException {
        JsonReader reader = new JsonReader(input);
        reader.setStrictness(Strictness.STRICT);
        JsonElement root = readValue(reader, 0);
        if (!root.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
            throw new IllegalArgumentException("Expected a single JSON object");
        }
        return root.getAsJsonObject();
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 24) throw new IllegalArgumentException("Catalog nesting exceeds 24 levels");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate JSON key: " + name);
                    object.add(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected JSON token at " + reader.getPath());
        };
    }

    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException("Expected string: " + key);
        }
        return primitive.getAsString();
    }

    public static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber() || !Double.isFinite(primitive.getAsDouble())) {
            throw new IllegalArgumentException("Expected finite number: " + key);
        }
        return primitive.getAsDouble();
    }

    public static int integer(JsonObject object, String key) {
        double value = number(object, key);
        if (value != (int) value) throw new IllegalArgumentException("Expected integer: " + key);
        return (int) value;
    }

    public static boolean bool(JsonObject object, String key) {
        if (!(object.get(key) instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: " + key);
        }
        return primitive.getAsBoolean();
    }

    public static List<ConsumeEffectDefinition> effects(JsonObject object) {
        if (!(object.get("effects") instanceof JsonArray array) || array.size() > 32) {
            throw new IllegalArgumentException("Expected at most 32 consume effects");
        }
        List<ConsumeEffectDefinition> effects = new ArrayList<>();
        for (JsonElement entry : array) {
            JsonObject effect = entry.getAsJsonObject();
            effects.add(new ConsumeEffectDefinition(string(effect, "id"), integer(effect, "duration"),
                    integer(effect, "amplifier"), number(effect, "probability")));
        }
        return List.copyOf(effects);
    }

    public static FoodDefinition food(JsonObject object) {
        return new FoodDefinition(integer(object, "nutrition"), (float) number(object, "saturation"),
                bool(object, "alwaysEat"), bool(object, "meat"), integer(object, "consumeTicks"), effects(object));
    }

    public static void requireId(String id) {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new IllegalArgumentException("Invalid catalog ID: " + id);
        }
    }
}
