package net.kaleidoscope.cookery.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CookeryCatalog {
    public static final String SOURCE_COMMIT = "2f4e386ce23f49a385ddf003c67fc6415c55417a";
    private volatile Snapshot snapshot;

    public CookeryCatalog(Snapshot initial) {
        snapshot = java.util.Objects.requireNonNull(initial);
    }

    public Snapshot snapshot() { return snapshot; }

    public void reload(Reader input) throws IOException {
        Snapshot next = parse(input);
        snapshot = next;
    }

    public static CookeryCatalog bundled() {
        InputStream resource = CookeryCatalog.class.getResourceAsStream("/cookery-parity.json");
        if (resource == null) throw new IllegalStateException("Missing bundled cookery-parity.json");
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return new CookeryCatalog(parse(reader));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load cookery-parity.json", exception);
        }
    }

    public static Snapshot parse(Reader input) throws IOException {
        JsonObject root = CatalogJson.read(input);
        if (CatalogJson.integer(root, "schemaVersion") != 1 || !SOURCE_COMMIT.equals(CatalogJson.string(root, "sourceCommit"))) {
            throw new IllegalArgumentException("Unsupported Cookery catalog schema/source commit");
        }
        if (!(root.get("entries") instanceof JsonArray entries) || entries.isEmpty()) {
            throw new IllegalArgumentException("Cookery catalog has no entries");
        }
        Map<String, Entry> items = new HashMap<>();
        Set<String> effects = new HashSet<>();
        Set<String> identities = new HashSet<>();
        for (JsonElement raw : entries) {
            JsonObject entry = raw.getAsJsonObject();
            String type = CatalogJson.string(entry, "type");
            if (!Set.of("food", "item", "tool", "tea", "effect", "enchantment", "profession", "structure").contains(type)) {
                throw new IllegalArgumentException("Unknown Cookery catalog entry type: " + type);
            }
            String id = CatalogJson.string(entry, "pluginId");
            CatalogJson.requireId(id);
            CatalogJson.requireId(CatalogJson.string(entry, "sourceId"));
            if (!identities.add(type + "/" + id) || !SOURCE_COMMIT.equals(CatalogJson.string(entry, "sourceCommit"))) {
                throw new IllegalArgumentException("Duplicate or mixed-source catalog entry: " + id);
            }
            if (type.equals("effect")) effects.add(CatalogJson.string(entry, "sourceId"));
            if (!Set.of("food", "item", "tool", "tea").contains(type)) continue;
            FoodDefinition food = object(entry, "food") == null ? null : CatalogJson.food(object(entry, "food"));
            JsonObject rawTea = object(entry, "tea");
            TeaDefinition tea = rawTea == null ? null : new TeaDefinition(CatalogJson.integer(rawTea, "maxCups"),
                    CatalogJson.integer(rawTea, "consumeTicks"), CatalogJson.string(rawTea, "container"), CatalogJson.effects(rawTea));
            JsonObject rawBites = object(entry, "bites");
            Bites bites = null;
            if (rawBites != null) {
                JsonElement rawDrops = rawBites.get("drops");
                JsonElement rawCollision = rawBites.get("collision");
                if (!(rawDrops instanceof JsonArray) || !(rawCollision instanceof JsonArray)
                        && !(rawCollision instanceof JsonPrimitive primitive && primitive.isString()
                        && primitive.getAsString().equals("FoodBiteThreeByThreeBlock.PART"))) {
                    throw new IllegalArgumentException("Invalid bite drops or collision data: " + id);
                }
                JsonArray dropArray = (JsonArray) rawDrops;
                List<String> drops = dropArray.asList().stream().map(element -> {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("Bite drop must be an item ID: " + id);
                    }
                    return element.getAsString();
                }).toList();
                bites = new Bites(CatalogJson.integer(rawBites, "maxBites"), CatalogJson.string(rawBites, "layout"),
                        CatalogJson.food(rawBites.getAsJsonObject("food")), drops, validateCollision(rawCollision, id),
                        rawBites.get("animation") == null || rawBites.get("animation").isJsonNull()
                                ? null : CatalogJson.string(rawBites, "animation"));
            }
            String container = entry.get("container").isJsonNull() ? null : CatalogJson.string(entry, "container");
            if (container != null) CatalogJson.requireId(container);
            int consumeDamage = entry.has("consumeDamage") ? CatalogJson.integer(entry, "consumeDamage") : 0;
            String consumeAction = entry.has("consumeAction") ? CatalogJson.string(entry, "consumeAction") : "none";
            if (consumeDamage < 0 || consumeDamage > 20 || !Set.of("none", "remove_harmful_effects").contains(consumeAction)) {
                throw new IllegalArgumentException("Unknown consumption action: " + id);
            }
            if (items.putIfAbsent(id, new Entry(id, food, tea, bites, container, consumeDamage, consumeAction)) != null) {
                throw new IllegalArgumentException("Duplicate item: " + id);
            }
        }
        if (!effects.equals(net.kaleidoscope.cookery.effect.EffectKind.IDS)) {
            throw new IllegalArgumentException("Cookery catalog must register all 12 supported effects");
        }
        for (Entry entry : items.values()) {
            if (entry.food != null) validateEffects(entry.food.effects(), effects);
            if (entry.tea != null) validateEffects(entry.tea.effects(), effects);
            if (entry.bites != null) validateEffects(entry.bites.food.effects(), effects);
        }
        return new Snapshot(SOURCE_COMMIT, items, effects);
    }

    private static void validateEffects(List<ConsumeEffectDefinition> definitions, Set<String> custom) {
        for (ConsumeEffectDefinition effect : definitions) {
            if (!effect.id().startsWith("minecraft:") && !custom.contains(effect.id())) {
                throw new IllegalArgumentException("Unregistered consume effect: " + effect.id());
            }
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null) throw new IllegalArgumentException("Missing catalog key: " + key);
        return value.isJsonNull() ? null : value.getAsJsonObject();
    }

    private static String validateCollision(JsonElement raw, String id) {
        if (raw instanceof JsonPrimitive primitive && primitive.isString()) return primitive.getAsString();
        JsonArray boxes = raw.getAsJsonArray();
        if (boxes.size() > 8) throw new IllegalArgumentException("Too many bite collision boxes: " + id);
        for (JsonElement box : boxes) {
            if (!(box instanceof JsonArray values) || values.size() != 6) {
                throw new IllegalArgumentException("Bite collision boxes require six coordinates: " + id);
            }
            for (JsonElement value : values) {
                if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                    throw new IllegalArgumentException("Bite collision coordinate is not numeric: " + id);
                }
                double coordinate = primitive.getAsDouble();
                if (!Double.isFinite(coordinate) || coordinate < 0 || coordinate > 16) {
                    throw new IllegalArgumentException("Bite collision coordinate is outside block bounds: " + id);
                }
            }
        }
        return boxes.toString();
    }

    public record Snapshot(String sourceCommit, Map<String, Entry> items, Set<String> customEffects) {
        public Snapshot {
            items = Map.copyOf(items);
            customEffects = Set.copyOf(customEffects);
        }
    }

    public record Entry(String id, FoodDefinition food, TeaDefinition tea, Bites bites, String container,
                        int consumeDamage, String consumeAction) {}

    public record Bites(int maxBites, String layout, FoodDefinition food, List<String> drops,
                        String collision, String animation) {
        public Bites {
            if (maxBites < 1 || maxBites > 64 || !Set.of("1x1", "1x2", "3x3").contains(layout)) {
                throw new IllegalArgumentException("Invalid bite layout/count");
            }
            if (collision == null || collision.isBlank() || animation != null && !animation.matches("[A-Z0-9_]{1,64}")) {
                throw new IllegalArgumentException("Invalid bite rendering data");
            }
            drops = List.copyOf(drops);
            drops.forEach(CatalogJson::requireId);
        }
    }
}
