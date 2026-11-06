package net.kaleidoscope.tavern;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

final class TapRedstone implements Listener {
    private static final int[][] NEIGHBORS = {{0, 0, 0}, {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private final TavernPlugin tavern;
    TapRedstone(TavernPlugin tavern) { this.tavern = tavern; }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void signal(BlockRedstoneEvent event) {
        if (event.getOldCurrent() != 0 || event.getNewCurrent() <= 0) return;
        for (int[] offset : NEIGHBORS) {
            TapController tap = tavern.taps.at(event.getBlock().getLocation().add(offset[0], offset[1], offset[2]));
            if (tap != null) tap.redstone();
        }
    }
}
