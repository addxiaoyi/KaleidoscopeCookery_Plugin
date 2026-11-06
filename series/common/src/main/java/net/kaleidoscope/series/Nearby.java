package net.kaleidoscope.series;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import java.util.ArrayList;
import java.util.List;

public final class Nearby {
    private Nearby() {}

    /** Do not load chunks or touch entities owned by another Folia region. */
    public static List<Entity> owned(Location center, double radius) {
        var world = center.getWorld();
        List<Entity> nearby = new ArrayList<>();
        int minX = (int) Math.floor(center.getX() - radius) >> 4;
        int maxX = (int) Math.floor(center.getX() + radius) >> 4;
        int minZ = (int) Math.floor(center.getZ() - radius) >> 4;
        int maxZ = (int) Math.floor(center.getZ() + radius) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!Bukkit.isOwnedByCurrentRegion(world, x, z) || !world.isChunkLoaded(x, z)) continue;
            for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                if (!Bukkit.isOwnedByCurrentRegion(entity)) continue;
                Location at = entity.getLocation();
                if (at.getWorld().equals(world) && Math.abs(at.getX() - center.getX()) <= radius
                        && Math.abs(at.getY() - center.getY()) <= radius && Math.abs(at.getZ() - center.getZ()) <= radius) nearby.add(entity);
            }
        }
        return nearby;
    }
}
