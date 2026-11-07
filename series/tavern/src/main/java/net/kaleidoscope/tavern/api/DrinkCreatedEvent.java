package net.kaleidoscope.tavern.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Stable cross-plugin notification emitted after a cocktail has been built. */
public final class DrinkCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String drinkId;
    private final ItemStack result;
    private final List<ItemStack> ingredients;

    public DrinkCreatedEvent(String drinkId, ItemStack result, List<ItemStack> ingredients) {
        this.drinkId = drinkId;
        this.result = result.clone();
        this.ingredients = ingredients.stream().map(ItemStack::clone).toList();
    }

    public String drinkId() { return drinkId; }
    public ItemStack result() { return result.clone(); }
    public List<ItemStack> ingredients() { return ingredients.stream().map(ItemStack::clone).toList(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
