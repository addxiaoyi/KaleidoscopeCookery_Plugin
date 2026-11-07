package net.kaleidoscope.doll.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Stable cross-plugin notification; listeners do not need Doll internals. */
public final class DollCollectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String dollId;
    private final boolean firstCollection;

    public DollCollectedEvent(Player player, String dollId, boolean firstCollection) {
        this.player = player;
        this.dollId = dollId;
        this.firstCollection = firstCollection;
    }

    public Player player() { return player; }
    public String dollId() { return dollId; }
    public boolean firstCollection() { return firstCollection; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
