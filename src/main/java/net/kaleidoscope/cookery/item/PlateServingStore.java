package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.catalog.PlateServing;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** PDC bridge for a recovered plated dish, including its additional drops. */
public final class PlateServingStore {
    private static final NamespacedKey KEY = new NamespacedKey("kaleidoscopecookery", "plate_serving_v1");

    private PlateServingStore() {}

    public static void write(ItemStack stack, PlateServing serving) {
        if (stack == null || serving == null) throw new IllegalArgumentException("Plate serving target is missing");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Plate serving target has no item metadata");
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, serving.encode());
        stack.setItemMeta(meta);
    }

    public static PlateServing read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String encoded = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (encoded == null) return null;
        return PlateServing.decode(encoded);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY);
        stack.setItemMeta(meta);
    }
}
