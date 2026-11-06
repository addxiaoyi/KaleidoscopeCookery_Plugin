package net.kaleidoscope.tavern;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kaleidoscope.series.ItemState;
import net.kaleidoscope.series.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HandheldShaker implements Listener {
    private final TavernPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Use> uses = new ConcurrentHashMap<>();

    HandheldShaker(TavernPlugin plugin) { this.plugin = plugin; key = new NamespacedKey(plugin, "shaker_contents_v1"); }

    ShakerContents contents(ItemStack item) {
        var saved = ItemState.read(item, key);
        return saved == null ? new ShakerContents(new ItemStack[3], null) : ShakerContents.read(saved);
    }

    ItemStack pack(ShakerContents contents) {
        return pack(Items.create("kaleidoscope_tavern:shaker"), contents);
    }

    ItemStack pack(ItemStack source, ShakerContents contents) {
        ItemStack packed = ItemState.write(source.asQuantity(1), key, contents.save());
        if (contents.full() && contents.cocktail == null) packed.setData(DataComponentTypes.CONSUMABLE,
                Consumable.consumable().consumeSeconds(3600).animation(ItemUseAnimation.DRINK).hasConsumeParticles(false).build());
        else packed.unsetData(DataComponentTypes.CONSUMABLE);
        return packed;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!Items.id(held).equals("kaleidoscope_tavern:shaker")) return;
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) return;
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!plugin.mayUse(player, player.getLocation())) return;
        plugin.attempt(player, () -> begin(player));
    }

    private void begin(Player player) {
        if (uses.containsKey(player.getUniqueId())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        ShakerContents contents = contents(held);
        if (contents.cocktail != null) { player.sendMessage("请对放置的空鸡尾酒杯右键倒酒，或潜行右键空雪克杯放回。"); return; }
        if (!contents.full()) { player.sendMessage("先在放置的雪克杯装入三份原料，再手持空雪克杯潜行右键转装。"); return; }
        Use use = new Use(player, player.getInventory().getHeldItemSlot(), held.clone());
        uses.put(player.getUniqueId(), use);
        player.startUsingItem(EquipmentSlot.HAND);
        var scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (uses.get(player.getUniqueId()) != use) { task.cancel(); return; }
            if (!use.item.equals(player.getInventory().getItem(use.slot)) || !player.hasActiveItem()) {
                uses.remove(player.getUniqueId(), use); task.cancel(); return;
            }
            use.ticks = player.getActiveItemUsedTime();
            if (use.ticks > 0 && use.ticks % 10 == 0) player.playSound(player.getLocation(), "kaleidoscope_tavern:shaker_shaking", 0.8f, 1);
            if (use.ticks >= ShakerTiming.TIMEOUT) {
                finish(player, use, use.ticks);
                player.clearActiveItem();
                task.cancel();
            }
        }, () -> uses.remove(player.getUniqueId(), use), 1, 1);
        if (scheduled == null) uses.remove(player.getUniqueId(), use);
    }

    private void finish(Player player, Use use, int ticks) {
        if (!uses.remove(player.getUniqueId(), use)) return;
        ItemStack held = player.getInventory().getItem(use.slot);
        if (held == null || !held.equals(use.item)) return;
        plugin.attempt(player, () -> {
            ShakerContents mixed = contents(held).mix(plugin, ticks);
            player.getInventory().setItem(use.slot, pack(held, mixed));
            player.sendMessage(mixed.cocktail == null ? "摇动不足 19 tick，原料已保留。" : "调酒完成，请倒入空鸡尾酒杯。");
        });
    }

    @EventHandler public void stop(PlayerStopUsingItemEvent event) {
        Use use = uses.get(event.getPlayer().getUniqueId());
        if (use != null && use.item.isSimilar(event.getItem())) finish(event.getPlayer(), use, Math.clamp(event.getTicksHeldFor(), 0, ShakerTiming.TIMEOUT));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void switchSlot(PlayerItemHeldEvent event) { if (uses.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) { if (uses.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { if (uses.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) { if (uses.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) { if (uses.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { cancel(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (Items.id(event.getItem()).equals("kaleidoscope_tavern:shaker")) event.setCancelled(true);
    }
    private void cancel(Player player) {
        Use use = uses.remove(player.getUniqueId());
        if (use != null) clear(use);
    }

    private void clear(Use use) {
        if (use.player.hasActiveItem() && use.item.isSimilar(use.player.getActiveItem())) use.player.clearActiveItem();
    }

    void close() {
        var pending = List.copyOf(uses.values());
        uses.clear();
        for (Use use : pending) plugin.onPlayer(use.player, () -> clear(use));
    }

    private static final class Use {
        final Player player;
        final int slot;
        final ItemStack item;
        int ticks;
        Use(Player player, int slot, ItemStack item) { this.player = player; this.slot = slot; this.item = item; }
    }
}
