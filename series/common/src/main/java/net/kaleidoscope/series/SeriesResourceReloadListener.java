package net.kaleidoscope.series;

import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Invalidates derived CE item templates whenever CraftEngine reloads resources. */
final class SeriesResourceReloadListener implements Listener {
    @EventHandler
    public void reload(CraftEngineReloadEvent event) {
        Items.clearCache();
    }
}
