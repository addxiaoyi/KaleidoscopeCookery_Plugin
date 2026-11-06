package net.kaleidoscope.cookery.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.StringReader;

/** Versioned persistence for a plated dish's remaining bites. */
public record BiteState(int version, String sourceCommit, String itemId, QualityProfile quality,
                        int maxBites, int consumed) {
    private static final Gson JSON = new Gson();

    public BiteState {
        CatalogJson.requireId(itemId);
        if (version != 1 || !CookeryCatalog.SOURCE_COMMIT.equals(sourceCommit) || quality == null
                || maxBites < 1 || maxBites > 64 || consumed < 0 || consumed > maxBites) {
            throw new IllegalArgumentException("Invalid bite state");
        }
    }

    public static BiteState of(CookeryCatalog.Entry entry, QualityProfile quality) {
        if (entry == null || entry.bites() == null) return null;
        return new BiteState(1, CookeryCatalog.SOURCE_COMMIT, entry.id(), quality,
                entry.bites().maxBites(), 0);
    }

    public int remaining() { return maxBites - consumed; }

    public boolean isEmpty() { return remaining() == 0; }

    public boolean isFull() { return consumed == 0; }

    public BiteState consumeOne() {
        if (remaining() == 0) return this;
        return new BiteState(version, sourceCommit, itemId, quality, maxBites, consumed + 1);
    }

    public BiteState refillOne() {
        if (consumed == 0) return this;
        return new BiteState(version, sourceCommit, itemId, quality, maxBites, consumed - 1);
    }

    public String encode() { return JSON.toJson(this); }

    public static BiteState decode(String encoded) {
        if (encoded == null || encoded.length() > 4096) throw new IllegalArgumentException("Invalid bite state size");
        try {
            JsonObject json = CatalogJson.read(new StringReader(encoded));
            JsonObject quality = json.getAsJsonObject("quality");
            return new BiteState(CatalogJson.integer(json, "version"), CatalogJson.string(json, "sourceCommit"),
                    CatalogJson.string(json, "itemId"),
                    new QualityProfile(CatalogJson.string(quality, "name"), CatalogJson.number(quality, "ratio")),
                    CatalogJson.integer(json, "maxBites"), CatalogJson.integer(json, "consumed"));
        } catch (IOException | IllegalStateException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid bite state", exception);
        }
    }
}
