package net.kaleidoscope.cookery.api.event;

import net.kaleidoscope.cookery.effect.EffectState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Runs on the player's owning thread before an explicit apply or clear. */
public final class CookeryEffectChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final EffectState previous;
    private final EffectState next;
    private final String reason;
    private boolean cancelled;

    public CookeryEffectChangeEvent(Player player, @Nullable EffectState previous, @Nullable EffectState next, String reason) {
        super(player);
        this.previous = previous;
        this.next = next;
        this.reason = reason;
    }

    public @Nullable EffectState previous() { return previous; }
    public @Nullable EffectState next() { return next; }
    public String reason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
