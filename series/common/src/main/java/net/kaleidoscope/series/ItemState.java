package net.kaleidoscope.series;

import net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ItemState {
    private static final int MAX_BYTES = 262144;
    private ItemState() {}

    public static CompoundTag read(ItemStack item, NamespacedKey key) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(key)) return null;
        byte[] bytes = pdc.get(key, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid item state: " + key);
        try {
            CompoundTag decoded = FurniturePersistentData.fromBytes(bytes).copyTag();
            return SavedState.payload(decoded, key.toString(), SavedState.SERIES_SOURCE, 1, null);
        }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Cannot read item state: " + key, exception); }
    }

    public static ItemStack write(ItemStack item, NamespacedKey key, CompoundTag state) {
        ItemStack copy = item.clone();
        byte[] bytes;
        try { bytes = FurniturePersistentData.of(
                SavedState.envelope(key.toString(), 1, SavedState.SERIES_SOURCE, state)).toBytes(); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Cannot save item state: " + key, exception); }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Item state too large: " + key);
        var meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, bytes);
        meta.setMaxStackSize(1);
        copy.setItemMeta(meta);
        return copy;
    }
}
