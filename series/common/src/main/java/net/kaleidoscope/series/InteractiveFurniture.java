package net.kaleidoscope.series;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.hitbox.FurnitureHitBox;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.world.context.InteractEntityContext;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class InteractiveFurniture extends FurnitureController {
    protected final SeriesPlugin plugin;
    private net.momirealms.craftengine.libraries.nbt.Tag rejectedState;
    private String rejectedKey;

    protected InteractiveFurniture(Furniture furniture, SeriesPlugin plugin) {
        super(furniture);
        this.plugin = plugin;
    }

    public final Location location() {
        var position = furniture().position();
        return new Location((World) position.world().platformWorld(), position.x, position.y, position.z);
    }

    public final boolean accessible(Player player) {
        return furniture().isValid() && org.bukkit.Bukkit.isOwnedByCurrentRegion(location())
                && plugin.mayUse(player, location());
    }

    @Override public final InteractionResult useOnFurniture(FurnitureHitBox hitbox, InteractEntityContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND || context.getPlayer() == null) return InteractionResult.PASS;
        Player player = (Player) context.getPlayer().platformPlayer();
        if (!accessible(player)) return InteractionResult.FAIL;
        if (blocked()) {
            player.sendMessage("家具存档无法读取，已停止操作并保留原始记录，请联系管理员。");
            return InteractionResult.FAIL;
        }
        if (!consumesInteraction(player)) return InteractionResult.PASS;
        plugin.attempt(player, () -> interact(player));
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    protected abstract void interact(Player player);
    protected boolean consumesInteraction(Player player) { return true; }
    protected final void changed() { furniture().setUnsaved(); }

    public boolean hasContents() { return false; }

    @Override public final void preRemove(net.momirealms.craftengine.core.entity.player.Player player) {
        if (blocked() || hasContents()) plugin.recoverFurniture(this);
    }

    public final boolean blocked() { return rejectedKey != null; }
    protected final boolean operational() { return plugin.isEnabled() && !blocked(); }

    protected final void restoreSafely(net.momirealms.craftengine.libraries.nbt.CompoundTag root,
                                       String key, Runnable restore) {
        try {
            restore.run();
        } catch (RuntimeException exception) {
            rejectedKey = key;
            var original = root.get(key);
            rejectedState = original == null ? null : original.deepClone();
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "无法读取家具存档，原始记录已保留：" + key + " at " + location(), exception);
        }
    }

    protected final boolean preserveRejected(net.momirealms.craftengine.libraries.nbt.CompoundTag root) {
        if (!blocked()) return false;
        if (rejectedState != null) root.put(rejectedKey, rejectedState.deepClone());
        return true;
    }
}
