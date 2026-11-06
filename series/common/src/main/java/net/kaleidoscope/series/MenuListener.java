package net.kaleidoscope.series;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;

public final class MenuListener implements Listener {
    private final SeriesPlugin plugin;
    private final java.util.Set<java.util.UUID> queued = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MenuListener(SeriesPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)
                || menu.scope() != plugin) return;
        boolean blocked = event.isCancelled();
        event.setCancelled(true);
        if (blocked || event instanceof InventoryCreativeEvent || !event.isLeftClick()
                || event.isShiftClick() || event.getRawSlot() < 0
                || event.getRawSlot() >= menu.getInventory().getSize()) return;
        Player player = (Player) event.getWhoClicked();
        if (!queued.add(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        plugin.later(player, () -> {
            try {
                if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                plugin.attempt(player, () -> menu.click(player, slot));
            } finally {
                queued.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu && menu.scope() == plugin) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void left(org.bukkit.event.player.PlayerQuitEvent event) {
        queued.remove(event.getPlayer().getUniqueId());
    }
}
