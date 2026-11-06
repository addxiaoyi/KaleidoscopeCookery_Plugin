package net.kaleidoscope.cookery.util;

import net.kaleidoscope.cookery.catalog.BiteState;
import net.kaleidoscope.cookery.catalog.QualityProfile;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/** CraftEngine block-entity representation of a plated food state. */
public final class BiteStateNbt {
    private static final String VERSION = "version";
    private static final String SOURCE = "source_commit";
    private static final String ITEM = "item_id";
    private static final String QUALITY = "quality";
    private static final String RATIO = "ratio";
    private static final String MAX = "max_bites";
    private static final String CONSUMED = "consumed";

    private BiteStateNbt() {}

    public static CompoundTag save(BiteState state) {
        if (state == null) throw new IllegalArgumentException("Bite state is missing");
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, state.version());
        tag.putString(SOURCE, state.sourceCommit());
        tag.putString(ITEM, state.itemId());
        tag.putString(QUALITY, state.quality().name());
        tag.putDouble(RATIO, state.quality().ratio());
        tag.putInt(MAX, state.maxBites());
        tag.putInt(CONSUMED, state.consumed());
        return tag;
    }

    public static BiteState load(CompoundTag tag) {
        if (tag == null) throw new IllegalArgumentException("Bite state tag is missing");
        return new BiteState(tag.getInt(VERSION, -1), tag.getString(SOURCE), tag.getString(ITEM),
                new QualityProfile(tag.getString(QUALITY), tag.getDouble(RATIO, Double.NaN)),
                tag.getInt(MAX, -1), tag.getInt(CONSUMED, -1));
    }
}
