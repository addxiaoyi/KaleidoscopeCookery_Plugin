package net.kaleidoscope.series;

import net.momirealms.craftengine.libraries.nbt.ByteTag;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.IntTag;
import net.momirealms.craftengine.libraries.nbt.LongTag;
import net.momirealms.craftengine.libraries.nbt.StringTag;

/** Missing required fields indicate damage, not a new empty inventory. */
public final class SavedState {
    private SavedState() {}

    public static int integer(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntTag)) throw invalid(key);
        return tag.getInt(key);
    }

    public static long longValue(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof LongTag)) throw invalid(key);
        return tag.getLong(key);
    }

    public static boolean flag(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ByteTag) || (tag.getByte(key) != 0 && tag.getByte(key) != 1)) throw invalid(key);
        return tag.getBoolean(key);
    }

    public static String text(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof StringTag)) throw invalid(key);
        return tag.getString(key);
    }

    public static String optionalText(CompoundTag tag, String key) {
        return tag.containsKey(key) ? text(tag, key) : null;
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Missing or invalid furniture save field: " + key);
    }
}
