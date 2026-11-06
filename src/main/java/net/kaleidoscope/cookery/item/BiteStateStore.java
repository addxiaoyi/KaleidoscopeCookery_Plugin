package net.kaleidoscope.cookery.item;

import net.kaleidoscope.cookery.catalog.BiteState;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** PDC bridge for returning a partially eaten plated dish to an item stack. */
public final class BiteStateStore {
    private static final NamespacedKey KEY = new NamespacedKey("kaleidoscopecookery", "bite_state_v1");

    private BiteStateStore() {}

    public static void write(ItemStack stack, BiteState state) {
        if (stack == null || state == null) throw new IllegalArgumentException("Bite state target is missing");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Bite state target has no item metadata");
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, state.encode());
        stack.setItemMeta(meta);
    }

    public static BiteState read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String encoded = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return encoded == null ? null : BiteState.decode(encoded);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY);
        stack.setItemMeta(meta);
    }
}
