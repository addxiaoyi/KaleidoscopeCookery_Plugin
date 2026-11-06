package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.nms.BambooTrayWeather;
import net.kaleidoscope.cookery.nms.NmsBridgeProvider;
import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Material;
import org.bukkit.World;

final class BambooTrayEnvironment {
    private BambooTrayEnvironment() {}

    static BambooTrayMode mode(CEWorld world, BlockPos pos, Key trayId) {
        BambooTrayWeather weather = NmsBridgeProvider.bridge().bambooTrayWeather(
                world.world().minecraftWorld(), pos.x(), pos.y(), pos.z());
        if (weather.wetting()) {
            return BambooTrayMode.WETTING;
        }
        if (weather.raining()) {
            return null;
        }
        return weather.canSeeSky() || hasDryingExposure(world, pos, trayId) ? BambooTrayMode.DRYING : null;
    }

    private static boolean hasDryingExposure(CEWorld world, BlockPos pos, Key trayId) {
        World bukkit = (World) world.world().platformWorld();
        for (int y = pos.y() + 1; y < bukkit.getMaxHeight(); y++) {
            ImmutableBlockState custom = world.getBlockStateAtIfLoaded(pos.x(), y, pos.z());
            if (custom != null && !custom.isEmpty()) {
                if (custom.owner().value().id().equals(trayId)) {
                    continue;
                }
                return false;
            }
            Material material = bukkit.getBlockAt(pos.x(), y, pos.z()).getType();
            if (!material.isAir() && material != Material.GLASS && material != Material.GLASS_PANE) {
                return false;
            }
        }
        return true;
    }
}
