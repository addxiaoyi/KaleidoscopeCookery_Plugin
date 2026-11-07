package net.kaleidoscope.series;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Items {
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final Map<String, ItemStack> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private Items() {}

    public static String id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        Key custom = CraftEngineItems.getCustomItemId(stack);
        return custom == null ? stack.getType().getKey().toString() : custom.asString();
    }

    public static ItemStack create(String id) {
        ItemIds.require(id);
        if (id.startsWith("minecraft:")) {
            Material material = Material.matchMaterial(id);
            if (material == null || material.isAir() || !material.isItem()) {
                throw new IllegalArgumentException("Unknown vanilla item: " + id);
            }
            return new ItemStack(material);
        }
        synchronized (CACHE) {
            ItemStack cached = CACHE.get(id);
            if (cached != null) return cached.clone();
        }
        var item = BukkitItemManager.instance().getBuildableItem(Key.of(id))
                .orElseThrow(() -> new IllegalArgumentException("CraftEngine 未加载物品：" + id))
                .buildItem((net.momirealms.craftengine.core.entity.player.Player) null);
        if (item.isEmpty()) throw new IllegalStateException("CraftEngine 创建了空物品：" + id);
        ItemStack built = ItemStackUtils.getBukkitStack(item).clone();
        synchronized (CACHE) {
            CACHE.put(id, built.clone());
            while (CACHE.size() > MAX_CACHE_ENTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return built;
    }

    public static void clearCache() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    public static ItemStack icon(Material material, String label) {
        ItemStack icon = new ItemStack(material);
        var meta = icon.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(label));
        icon.setItemMeta(meta);
        return icon;
    }
}
