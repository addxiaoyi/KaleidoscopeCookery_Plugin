package net.kaleidoscope.cookery.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/** Immutable plated-dish state shared by furniture interactions and item recovery. */
public record PlateServing(BiteState bites, List<String> drops) {
    private static final Gson JSON = new Gson();
    public PlateServing {
        if (bites == null || drops == null) throw new IllegalArgumentException("Plate state is incomplete");
        drops = List.copyOf(drops);
        drops.forEach(CatalogJson::requireId);
    }

    public static PlateServing of(CookeryCatalog.Entry entry, QualityProfile quality) {
        if (entry == null || entry.bites() == null) return null;
        return new PlateServing(BiteState.of(entry, quality), entry.bites().drops());
    }

    public static PlateServing of(String itemId, int maxBites, QualityProfile quality, List<String> drops) {
        return new PlateServing(BiteState.of(itemId, maxBites, quality), drops);
    }

    public boolean empty() { return bites.isEmpty(); }

    public boolean full() { return bites.isFull(); }

    public PlateServing takeOne() { return new PlateServing(bites.consumeOne(), drops); }

    public PlateServing refillOne() { return new PlateServing(bites.refillOne(), drops); }

    public String encode() { return JSON.toJson(this); }

    public static PlateServing decode(String encoded) {
        if (encoded == null || encoded.length() > 8192) throw new IllegalArgumentException("Invalid plate serving size");
        JsonObject root = JsonParser.parseString(encoded).getAsJsonObject();
        BiteState state = BiteState.decode(root.getAsJsonObject("bites").toString());
        List<String> restored = root.getAsJsonArray("drops").asList().stream().map(element -> element.getAsString()).toList();
        return new PlateServing(state, restored);
    }
}
