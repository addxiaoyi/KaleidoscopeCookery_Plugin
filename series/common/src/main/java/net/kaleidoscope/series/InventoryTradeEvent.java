package net.kaleidoscope.series;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Cancellable boundary fired after an exchange plan is valid and before inventory commit. */
public final class InventoryTradeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int cost;
    private final List<ItemStack> rewards;
    private boolean cancelled;

    public InventoryTradeEvent(Player player, int cost, List<ItemStack> rewards) {
        this.player = player;
        this.cost = cost;
        this.rewards = rewards.stream().map(ItemStack::clone).toList();
    }

    public Player player() { return player; }
    public int cost() { return cost; }
    public List<ItemStack> rewards() { return rewards; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
