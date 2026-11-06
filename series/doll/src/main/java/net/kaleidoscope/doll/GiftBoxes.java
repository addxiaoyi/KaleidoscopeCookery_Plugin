package net.kaleidoscope.doll;

import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.InventoryTrade;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class GiftBoxes implements Listener {
    private static final List<String> BOXES = List.of("kaleidoscope_doll:green_doll_gift_box",
            "kaleidoscope_doll:yellow_doll_gift_box", "kaleidoscope_doll:purple_doll_gift_box");
    private final DollPlugin plugin;
    private final NamespacedKey dollKey;

    public GiftBoxes(DollPlugin plugin) { this.plugin = plugin; dollKey = new NamespacedKey(plugin, "gift_doll_v1"); }

    public ItemStack create(int tier, String doll) {
        if (!plugin.catalog().tiers().get(tier).contains(doll)) throw new IllegalArgumentException("Doll outside gift tier");
        // Validate the prize before a token can be consumed.
        Items.create(doll);
        ItemStack gift = Items.create(BOXES.get(tier));
        var meta = gift.getItemMeta();
        meta.getPersistentDataContainer().set(dollKey, PersistentDataType.STRING, doll);
        gift.setItemMeta(meta);
        return gift;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack held = event.getItem();
        int tier = BOXES.indexOf(Items.id(held));
        if (tier < 0) return;
        event.setCancelled(true);
        String doll = held.getItemMeta().getPersistentDataContainer().get(dollKey, PersistentDataType.STRING);
        if (doll == null || !plugin.catalog().tiers().get(tier).contains(doll)) {
            event.getPlayer().sendMessage("礼盒没有有效的奖品记录，请从抽奖机获取。");
            return;
        }
        ItemStack payment = held.asOne();
        plugin.attempt(event.getPlayer(), () -> {
            if (InventoryTrade.exchange(event.getPlayer().getInventory(), payment::isSimilar, 1, List.of(Items.create(doll)))) {
                plugin.collected(event.getPlayer(), doll);
            } else event.getPlayer().sendMessage("背包空间不足，礼盒已保留。");
        });
    }
}
