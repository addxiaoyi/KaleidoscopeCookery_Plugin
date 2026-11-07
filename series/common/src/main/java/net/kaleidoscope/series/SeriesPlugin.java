package net.kaleidoscope.series;

import net.momirealms.antigrieflib.AntiGriefLib;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public abstract class SeriesPlugin extends JavaPlugin {
    private AntiGriefLib protection;
    private SeriesScheduler scheduler;

    @Override public final void onEnable() {
        scheduler = new SeriesScheduler(this);
        protection = AntiGriefLib.builder(this).ignoreOP(true).build();
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new StoredFurnitureListener(), this);
        getServer().getPluginManager().registerEvents(new SeriesResourceReloadListener(), this);
        start();
    }

    protected abstract void start();

    protected void stop() {}

    @Override public final void onDisable() {
        stop();
        for (Player player : getServer().getOnlinePlayers()) {
            Runnable close = () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.scope() == this) {
                    player.closeInventory();
                }
            };
            onPlayer(player, close);
        }
    }

    public void onPlayer(Player player, Runnable action) {
        scheduler.player(player, action);
    }

    public boolean mayUse(Player player, Location location) {
        return isEnabled() && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= 64
                && protection.test(player, Flag.INTERACT, location);
    }

    public <T> boolean allows(Player player, Flag<T> flag, T target) {
        return isEnabled() && protection.test(player, flag, target);
    }

    public void recoverFurniture(InteractiveFurniture controller) {
        var saved = new net.momirealms.craftengine.libraries.nbt.CompoundTag();
        controller.saveCustomData(saved);
        var at = controller.location();
        saved.putString("recovery_world", at.getWorld().getUID().toString());
        saved.putString("recovery_furniture", controller.furniture().id().asString());
        saved.putString("recovery_position", at.getX() + "," + at.getY() + "," + at.getZ());
        var folder = getDataFolder().toPath().resolve("recovery");
        var file = folder.resolve(controller.furniture().uuid() + "-" + java.util.UUID.randomUUID() + ".nbt");
        try {
            java.nio.file.Files.createDirectories(folder);
            byte[] bytes = net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData.of(saved).toBytes();
            try (var channel = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot preserve removed furniture contents: " + file, exception);
        }
        getLogger().warning("家具被强制移除，内容快照已保存到 " + file + "；请核对移除原因后人工恢复，快照不会自动发放物品。");
    }

    public void later(Player player, Runnable action) {
        scheduler.later(player, action);
    }

    public void attempt(Player player, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            getLogger().log(Level.SEVERE, "交互失败，玩家=" + player.getUniqueId(), exception);
            player.sendMessage("操作未完成：请让管理员查看插件日志及 CraftEngine 资源配置。");
        }
    }

    protected void command(String name, org.bukkit.command.CommandExecutor executor) {
        Objects.requireNonNull(getCommand(name), "Missing command: " + name).setExecutor(executor);
    }
}
