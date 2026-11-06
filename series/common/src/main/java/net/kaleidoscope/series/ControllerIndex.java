package net.kaleidoscope.series;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Loaded controllers only; a block lookup never asks the server to load a chunk. */
public final class ControllerIndex<T extends InteractiveFurniture> {
    private final Map<Cell, Map<UUID, T>> cells = new ConcurrentHashMap<>();

    public void add(T controller) {
        cells.computeIfAbsent(Cell.at(controller.location()), key -> new ConcurrentHashMap<>())
                .put(controller.furniture().uuid(), controller);
    }

    public void remove(T controller) {
        cells.computeIfPresent(Cell.at(controller.location()), (cell, entries) -> {
            entries.remove(controller.furniture().uuid(), controller);
            return entries.isEmpty() ? null : entries;
        });
    }

    public T at(Location location) {
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || !Bukkit.isOwnedByCurrentRegion(location)) return null;
        var entries = cells.get(Cell.at(location));
        if (entries == null || entries.size() != 1) return null;
        T controller = entries.values().iterator().next();
        return controller.furniture().isValid() && !controller.blocked() ? controller : null;
    }

    public void clear() { cells.clear(); }

    private record Cell(UUID world, int x, int y, int z) {
        static Cell at(Location at) { return new Cell(at.getWorld().getUID(), at.getBlockX(), at.getBlockY(), at.getBlockZ()); }
    }
}
