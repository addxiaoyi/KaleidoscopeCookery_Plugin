package net.kaleidoscope.series;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Single Folia-aware boundary for player-owned work used by every series module. */
public final class SeriesScheduler {
    private final JavaPlugin owner;

    public SeriesScheduler(JavaPlugin owner) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
    }

    public void player(Player player, Runnable action) {
        // This path is also used during onDisable to close module-owned menus.
        if (player == null || action == null) return;
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
            return;
        }
        BukkitCraftEngine engine = BukkitCraftEngine.instance();
        if (engine != null) {
            engine.scheduler().platform().run(action, () -> {}, player);
        }
    }

    public void later(Player player, Runnable action) {
        if (player == null || action == null || !owner.isEnabled()) return;
        BukkitCraftEngine engine = BukkitCraftEngine.instance();
        if (engine == null) return;
        engine.scheduler().platform().runLater(() -> {
            if (owner.isEnabled() && player.isOnline()) action.run();
        }, () -> {}, 1, player);
    }
}
