package net.kaleidoscope.series;

import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class StoredFurnitureListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakFurniture(FurnitureBreakEvent event) {
        InteractiveFurniture contents = event.furniture().controller.get(InteractiveFurniture.class, 0);
        if (contents != null && (contents.blocked() || contents.hasContents())) {
            event.setCancelled(true);
            event.player().sendMessage("请先取出内部物品或等待加工完成，再拆除家具。");
        }
    }
}
