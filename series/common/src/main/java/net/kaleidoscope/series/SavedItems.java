package net.kaleidoscope.series;

import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

public final class SavedItems {
    private SavedItems() {}

    public static void put(CompoundTag tag, String key, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) tag.putByteArray(key, stack.serializeAsBytes());
    }

    public static ItemStack get(CompoundTag tag, String key, int limit) {
        if (!tag.containsKey(key)) return null;
        if (!(tag.get(key) instanceof net.momirealms.craftengine.libraries.nbt.ByteArrayTag)) {
            throw new IllegalArgumentException("Invalid saved item type: " + key);
        }
        byte[] saved = tag.getByteArray(key);
        if (saved == null || saved.length == 0) throw new IllegalArgumentException("Empty saved item: " + key);
        ItemStack stack = ItemStack.deserializeBytes(saved);
        if (stack.isEmpty() || stack.getAmount() < 1 || stack.getAmount() > Math.min(limit, stack.getMaxStackSize())) {
            throw new IllegalArgumentException("Invalid saved item slot: " + key);
        }
        return stack;
    }
}
