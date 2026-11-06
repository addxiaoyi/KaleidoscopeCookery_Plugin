package net.kaleidoscope.cookery.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.StringReader;

/** Item-local consumption data: eating never recalculates a recipe or quality. */
public record BakedFood(int version, String sourceCommit, String itemId, QualityProfile quality,
                        FoodDefinition food, boolean drink, String container, int damage, boolean removeHarmful) {
    private static final Gson JSON = new Gson();

    public BakedFood {
        CatalogJson.requireId(itemId);
        if (version != 1 || !CookeryCatalog.SOURCE_COMMIT.equals(sourceCommit) || quality == null || food == null
                || damage < 0 || damage > 20) throw new IllegalArgumentException("Invalid baked food snapshot");
        if (container != null) CatalogJson.requireId(container);
        for (var effect : food.effects()) {
            if (!effect.id().startsWith("minecraft:") && !net.kaleidoscope.cookery.effect.EffectKind.IDS.contains(effect.id())) {
                throw new IllegalArgumentException("Unknown baked food effect: " + effect.id());
            }
        }
    }

    public static BakedFood of(CookeryCatalog.Entry entry, QualityProfile quality) {
        return of(entry, quality, false);
    }

    /** Creates an immutable snapshot for the placed-food bite definition. */
    public static BakedFood ofBite(CookeryCatalog.Entry entry, QualityProfile quality) {
        return of(entry, quality, true);
    }

    private static BakedFood of(CookeryCatalog.Entry entry, QualityProfile quality, boolean bite) {
        boolean drink = entry.tea() != null || entry.consumeAction().equals("remove_harmful_effects");
        FoodDefinition base = bite ? entry.bites() == null ? null : entry.bites().food() : entry.food();
        if (bite && base == null) return null;
        if (base == null && drink) {
            base = new FoodDefinition(0, 0, true, false, 32, entry.tea() == null ? java.util.List.of() : entry.tea().effects());
        }
        if (base == null) return null;
        return new BakedFood(1, CookeryCatalog.SOURCE_COMMIT, entry.id(), quality, base.scaled(quality), bite ? false : drink,
                bite ? null : entry.container(), bite ? 0 : entry.consumeDamage(), bite ? false : entry.consumeAction().equals("remove_harmful_effects"));
    }

    public String encode() { return JSON.toJson(this); }

    public static BakedFood decode(String encoded) {
        if (encoded == null || encoded.length() > 16_384) throw new IllegalArgumentException("Invalid baked food save size");
        try {
            JsonObject json = CatalogJson.read(new StringReader(encoded));
            JsonObject quality = json.getAsJsonObject("quality");
            return new BakedFood(CatalogJson.integer(json, "version"), CatalogJson.string(json, "sourceCommit"),
                    CatalogJson.string(json, "itemId"), new QualityProfile(CatalogJson.string(quality, "name"), CatalogJson.number(quality, "ratio")),
                    CatalogJson.food(json.getAsJsonObject("food")), CatalogJson.bool(json, "drink"),
                    json.has("container") ? CatalogJson.string(json, "container") : null,
                    CatalogJson.integer(json, "damage"), CatalogJson.bool(json, "removeHarmful"));
        } catch (IOException | IllegalStateException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid baked food save", exception);
        }
    }
}
