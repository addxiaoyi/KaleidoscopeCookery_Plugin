package net.kaleidoscope.cookery.effect;

import org.bukkit.entity.Player;

public interface EffectHandler {
    void apply(Player player, EffectState state);
    void tick(Player player, EffectState state);
    void clear(Player player, String reason);
}
