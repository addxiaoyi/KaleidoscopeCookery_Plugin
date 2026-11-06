package net.kaleidoscope.series;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class Menu implements InventoryHolder {
    private final UUID owner;
    private final Object scope;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public Menu(Player owner, Object scope, String title, int rows) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Menu rows: " + rows);
        this.owner = owner.getUniqueId();
        this.scope = scope;
        inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
    }

    public void button(int slot, ItemStack icon, Consumer<Player> action) {
        inventory.setItem(slot, icon);
        if (action != null) actions.put(slot, action);
    }

    public void click(Player player, int slot) {
        if (!owner.equals(player.getUniqueId())) return;
        Consumer<Player> action = actions.get(slot);
        if (action != null) action.accept(player);
    }

    public Object scope() { return scope; }
    @Override public Inventory getInventory() { return inventory; }
    public void open(Player player) { player.openInventory(inventory); }
}
