package net.kaleidoscope.tavern;

import net.kaleidoscope.series.InteractiveFurniture;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

final class TapController extends InteractiveFurniture {
    private final TavernPlugin tavern;
    private static final String OPERATOR_KEY = "kaleidoscope_tavern:tap_operator";
    private int remaining;
    private UUID operator;


    TapController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }

    @Override public void onLoad() { tavern.taps.add(this); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) {
        if (player != null) operator = ((Player) player.platformPlayer()).getUniqueId();
        tavern.taps.add(this); changed();
    }
    @Override public void onUnload() { remaining = 0; tavern.taps.remove(this); }
    @Override public void postRemove(net.momirealms.craftengine.core.entity.player.Player player) { tavern.taps.remove(this); }
    void redstone() {
        if (operational() && remaining == 0) begin(operator == null ? null : Bukkit.getPlayer(operator));
    }

    private Location source() {
        double angle = Math.toRadians(Math.round(furniture().position().yRot / 90.0) * 90.0);
        return location().add(Math.round(Math.sin(angle)), 0, -Math.round(Math.cos(angle)));
    }

    @Override protected void interact(Player player) {
        operator = player.getUniqueId();
        changed();
        if (remaining > 0) { remaining = 0; sound(false); return; }
        begin(player);
    }

    private boolean owned(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4) && Bukkit.isOwnedByCurrentRegion(at);
    }

    private boolean permitted(Player player) {
        Location source = source(), below = location().subtract(0, 1, 0);
        return owned(source) && owned(below) && player != null && Bukkit.isOwnedByCurrentRegion(player)
                && plugin.isEnabled() && player.getWorld().equals(location().getWorld())
                && plugin.allows(player, Flag.INTERACT, location())
                && plugin.allows(player, Flag.INTERACT, source) && plugin.allows(player, Flag.INTERACT, below);
    }

    private void begin(Player player) {
        if (!permitted(player)) return;
        BarrelController barrel = tavern.barrels.at(source());
        BottleController bottle = tavern.bottles.at(location().subtract(0, 1, 0));
        if (!canExtract(barrel, bottle)) {
            location().getWorld().spawnParticle(Particle.CLOUD, location().add(0, 0.3, 0), 3, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        remaining = 30;
        sound(true);
    }

    private boolean canExtract(BarrelController barrel, BottleController bottle) {
        if (bottle != null && bottle.empty()) {
            if (barrel != null) return barrel.canBottle(bottle.carrier());
            return bottle.carrier().equals("kaleidoscope_tavern:empty_bottle") && TapSources.output(source().getBlock()) != null;
        }
        return bottle == null && TapSources.canFillCauldron(source().getBlock(), location().subtract(0, 1, 0).getBlock(),
                plugin.getConfig().getBoolean("tap.infinite-lava", false));
    }

    private void sound(boolean open) {
        location().getWorld().playSound(location(), open ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1, 0.8f);
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> tick());
    }

    private void tick() {
        if (!operational()) return;
        if (remaining == 0) return;
        Player player = operator == null ? null : Bukkit.getPlayer(operator);
        if (--remaining > 0) {
            if (remaining >= 25) location().getWorld().spawnParticle(Particle.DRIPPING_WATER, location().add(0, 0.25, 0), 1);
            return;
        }
        sound(false);
        if (!permitted(player)) return;
        BarrelController barrel = tavern.barrels.at(source());
        BottleController bottle = tavern.bottles.at(location().subtract(0, 1, 0));
        if (!canExtract(barrel, bottle)) return;
        try {
            if (barrel != null && bottle != null) {
                barrel.fill(bottle);
                bottle.refreshBottle();
            } else if (bottle != null) TapSources.fillBottle(source().getBlock(), bottle);
            else {
                org.bukkit.block.Block destination = location().subtract(0, 1, 0).getBlock();
                var state = destination.getState();
                if (TapSources.water(source().getBlock())) {
                    state.setType(org.bukkit.Material.WATER_CAULDRON);
                    var level = (org.bukkit.block.data.Levelled) state.getBlockData();
                    level.setLevel(level.getMaximumLevel()); state.setBlockData(level);
                } else state.setType(org.bukkit.Material.LAVA_CAULDRON);
                var event = new org.bukkit.event.block.CauldronLevelChangeEvent(destination, player,
                        org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.UNKNOWN, state);
                if (event.callEvent()) state.update(true, true);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "龙头装瓶失败：" + location(), exception);
        }
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        if (operator != null) {
            CompoundTag payload = new CompoundTag();
            payload.putString("operator", operator.toString());
            root.put(OPERATOR_KEY, net.kaleidoscope.series.SavedState.envelope(
                    net.kaleidoscope.series.SavedState.namespace(OPERATOR_KEY), 1,
                    net.kaleidoscope.series.SavedState.SERIES_SOURCE, payload));
        }
    }
    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, OPERATOR_KEY, () -> {
            if (!root.containsKey(OPERATOR_KEY)) return;
            if (root.get(OPERATOR_KEY) instanceof CompoundTag envelope) {
                CompoundTag payload = net.kaleidoscope.series.SavedState.payload(envelope,
                        net.kaleidoscope.series.SavedState.namespace(OPERATOR_KEY),
                        net.kaleidoscope.series.SavedState.SERIES_SOURCE, 1, null);
                operator = UUID.fromString(net.kaleidoscope.series.SavedState.text(payload, "operator"));
            } else {
                operator = UUID.fromString(net.kaleidoscope.series.SavedState.text(root, OPERATOR_KEY));
            }
        });
    }
}
