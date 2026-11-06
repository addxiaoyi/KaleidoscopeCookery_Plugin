package net.kaleidoscope.cookery.effect;

import net.kaleidoscope.cookery.api.event.CookeryEffectChangeEvent;
import net.kaleidoscope.cookery.catalog.ConsumeEffectDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CookeryEffects implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final NamespacedKey saveKey = new NamespacedKey("kaleidoscopecookery", "effects_v1");
    private final Map<String, EffectHandler> handlers = KitchenEffectHandlers.create();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private static final class Session {
        volatile Map<String, EffectState> states = Map.of();
        ScheduledTask task;
        boolean invalidSave;
        int ticks;
        String saved;
    }

    public CookeryEffects(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean has(UUID player, EffectKind kind) { return state(player, kind) != null; }

    public EffectState state(UUID player, EffectKind kind) {
        Session session = sessions.get(player);
        return session == null ? null : session.states.get(kind.id());
    }

    public boolean apply(Player player, ConsumeEffectDefinition effect, String source) {
        requireOwner(player);
        Session session = session(player);
        if (session.invalidSave || closed || player.isDead()) return false;
        EffectState incoming = new EffectState(effect.id(), effect.amplifier(), effect.duration(), source, null);
        EffectState previous = session.states.get(effect.id());
        EffectState next;
        try {
            next = previous == null ? incoming : previous.merge(incoming);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Cannot merge kitchen effect for " + player.getUniqueId(), exception);
            return false;
        }
        if (previous == next) return true;
        if (!new CookeryEffectChangeEvent(player, previous, next, "consume").callEvent()) return false;
        // A listener may synchronously apply another state while the event is firing.
        // Commit only if the state we observed is still current, otherwise keep the listener's change.
        if (!replaceIfCurrent(session, next.id(), previous, next)) return true;
        handlers.get(next.id()).apply(player, next);
        save(player, session);
        return true;
    }

    public void clear(Player player, String reason) {
        requireOwner(player);
        Session session = session(player);
        if (session.invalidSave) return;
        for (EffectState state : List.copyOf(session.states.values())) {
            if (new CookeryEffectChangeEvent(player, state, null, reason).callEvent()) {
                if (removeIfCurrent(session, state.id(), state)) handlers.get(state.id()).clear(player, reason);
            }
        }
        save(player, session);
    }

    public void spend(Player player, EffectKind kind, int ticks) {
        requireOwner(player);
        Session session = session(player);
        EffectState old = session.states.get(kind.id());
        if (old == null) return;
        EffectState next;
        try {
            next = old.elapse(ticks);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Cannot spend kitchen effect for " + player.getUniqueId(), exception);
            return;
        }
        if (!new CookeryEffectChangeEvent(player, old, next, "spend").callEvent()) return;
        if (next == null) {
            if (removeIfCurrent(session, old.id(), old)) handlers.get(old.id()).clear(player, "spent");
        } else {
            replaceIfCurrent(session, old.id(), old, next);
        }
        save(player, session);
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> load(player));
    }

    private Session load(Player player) {
        Session session = new Session();
        try {
            var pdc = player.getPersistentDataContainer();
            if (pdc.has(saveKey)) {
                String encoded = pdc.get(saveKey, PersistentDataType.STRING);
                session.states = EffectCodec.decode(encoded);
                session.saved = encoded;
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            session.invalidSave = true;
            plugin.getLogger().log(Level.SEVERE, "Kitchen effect save preserved and paused for " + player.getUniqueId(), exception);
        }
        session.task = player.getScheduler().runAtFixedRate(plugin, task -> tick(player, session),
                () -> sessions.remove(player.getUniqueId(), session), 1, 1);
        return session;
    }

    private void tick(Player player, Session session) {
        if (closed || session.invalidSave || player.isDead()) return;
        try {
            if (++session.ticks % 20 == 0) KitchenEffectHandlers.farmerOutfit(player);
            if (session.states.isEmpty()) return;
            for (EffectState state : List.copyOf(session.states.values())) {
                EffectHandler handler = handlers.get(state.id());
                handler.tick(player, state);
                if (!Objects.equals(session.states.get(state.id()), state)) continue;
                EffectState next = state.elapse(1);
                if (next == null) {
                    if (removeIfCurrent(session, state.id(), state)) handler.clear(player, "expired");
                } else {
                    replaceIfCurrent(session, state.id(), state, next);
                }
            }
            Map<String, EffectState> states = session.states;
            if (states.isEmpty() || session.ticks % 20 == 0) {
                save(player, session);
                if (!states.isEmpty()) {
                    String status = states.values().stream().sorted(java.util.Comparator.comparing(EffectState::id))
                            .map(state -> EffectKind.fromId(state.id()).title() + " " + (state.remaining() + 19) / 20 + "s")
                            .collect(java.util.stream.Collectors.joining(" · "));
                    player.sendActionBar(Component.text(status));
                }
            }
        } catch (RuntimeException exception) {
            session.states.forEach((id, state) -> handlers.get(id).clear(player, "tick_failed"));
            session.invalidSave = true;
            plugin.getLogger().log(Level.SEVERE, "Kitchen effect tick paused for " + player.getUniqueId(), exception);
        }
    }

    private void save(Player player, Session session) {
        if (session.invalidSave) return;
        String encoded = EffectCodec.encode(session.states);
        if (!encoded.equals(session.saved)) {
            player.getPersistentDataContainer().set(saveKey, PersistentDataType.STRING, encoded);
            session.saved = encoded;
        }
    }

    private static boolean replaceIfCurrent(Session session, String id, EffectState expected, EffectState replacement) {
        Map<String, EffectState> current = session.states;
        if (!Objects.equals(current.get(id), expected)) return false;
        Map<String, EffectState> updated = new HashMap<>(current);
        updated.put(id, replacement);
        session.states = Map.copyOf(updated);
        return true;
    }

    private static boolean removeIfCurrent(Session session, String id, EffectState expected) {
        Map<String, EffectState> current = session.states;
        if (!Objects.equals(current.get(id), expected)) return false;
        Map<String, EffectState> updated = new HashMap<>(current);
        updated.remove(id);
        session.states = Map.copyOf(updated);
        return true;
    }

    private static void requireOwner(Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Kitchen effects require player region ownership");
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { session(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = session(event.getPlayer());
        session.states.forEach((id, state) -> handlers.get(id).clear(event.getPlayer(), "world_change"));
        save(event.getPlayer(), session);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) { clear(event.getPlayer(), "death"); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        save(player, session);
        session.states.forEach((id, state) -> handlers.get(id).clear(player, "logout"));
        if (session.task != null) session.task.cancel();
    }

    @Override public void close() {
        closed = true;
        for (var entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.task != null) session.task.cancel();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Runnable cleanup = () -> {
                save(player, session);
                session.states.forEach((id, state) -> handlers.get(id).clear(player, "disable"));
            };
            if (Bukkit.isOwnedByCurrentRegion(player)) cleanup.run();
            else net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine.instance().scheduler().platform().run(cleanup, null, player);
        }
        sessions.clear();
    }
}
