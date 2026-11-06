package net.kaleidoscope.series;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class Items {
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
        var item = BukkitItemManager.instance().getBuildableItem(Key.of(id))
                .orElseThrow(() -> new IllegalArgumentException("CraftEngine 未加载物品：" + id))
                .buildItem((net.momirealms.craftengine.core.entity.player.Player) null);
        if (item.isEmpty()) throw new IllegalStateException("CraftEngine 创建了空物品：" + id);
        return ItemStackUtils.getBukkitStack(item).clone();
    }

    public static ItemStack icon(Material material, String label) {
        ItemStack icon = new ItemStack(material);
        var meta = icon.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(label));
        icon.setItemMeta(meta);
        return icon;
    }
}
