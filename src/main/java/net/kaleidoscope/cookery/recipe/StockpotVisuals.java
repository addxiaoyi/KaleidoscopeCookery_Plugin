package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

import java.util.Objects;

/** Frozen with the batch so resource reloads cannot change a pot halfway through cooking. */
public record StockpotVisuals(Key cookingModel, Key finishedModel, int cookingColor, int finishedColor) {
    public StockpotVisuals {
        Objects.requireNonNull(cookingModel, "cookingModel");
        Objects.requireNonNull(finishedModel, "finishedModel");
        if (cookingColor < 0 || cookingColor > 0xFFFFFF || finishedColor < 0 || finishedColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Stockpot bubble colors must be RGB integers");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("cooking_model", cookingModel.asString());
        tag.putString("finished_model", finishedModel.asString());
        tag.putInt("cooking_color", cookingColor);
        tag.putInt("finished_color", finishedColor);
        return tag;
    }

    public static StockpotVisuals load(CompoundTag tag) {
        if (tag == null) throw new IllegalArgumentException("Missing stockpot visuals");
        return new StockpotVisuals(StockpotRecipe.itemKey(tag.getString("cooking_model", "")),
                StockpotRecipe.itemKey(tag.getString("finished_model", "")),
                tag.getInt("cooking_color", -1), tag.getInt("finished_color", -1));
    }

    public static float liquidOffset(int remaining, int maximum) {
        if (maximum < 1 || maximum > 9 || remaining < 0 || remaining > maximum) {
            throw new IllegalArgumentException("Invalid stockpot serving counts");
        }
        return -0.315f * (1f - (float) remaining / maximum);
    }
}
