package net.kaleidoscope.tavern;

import com.google.gson.Gson;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TavernEffects implements Listener {
    private static final Set<String> TIMED = Set.of("slightly_tipsy", "high_heels", "grass_stealth", "vision",
            "bloody_mary", "ardent_heat", "long_reach", "tomb_raider", "xp_drain");
    private static final Set<String> INSTANT = Set.of("upside_down", "zenith", "shriek_attack");
    private record Timer(String id, int remaining, int amplifier) {
        Timer {
            if (!TIMED.contains(id) || remaining <= 0 || amplifier < 0 || amplifier > 255) throw new IllegalArgumentException("Invalid saved tavern effect");
        }
    }
    private static final class Session {
        final Map<String, Timer> timers = new HashMap<>();
        ScheduledTask task;
        int ticks;
    }

    private final TavernPlugin plugin;
    private final NamespacedKey savedKey;
    private final NamespacedKey heelsKey;
    private final NamespacedKey reachKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Gson json = new Gson();
    private final TavernAbilities abilities;

    public TavernEffects(TavernPlugin plugin) {
        this.plugin = plugin;
        savedKey = new NamespacedKey(plugin, "active_effects_v1");
        heelsKey = new NamespacedKey(plugin, "high_heels");
        reachKey = new NamespacedKey(plugin, "long_reach");
        abilities = new TavernAbilities(plugin, this);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(abilities, plugin);
        plugin.getServer().getOnlinePlayers().forEach(player -> plugin.later(player, () -> attach(player)));
    }

    public boolean active(Player player, String id) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) return false;
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.timers.containsKey(id);
    }

    public void apply(Player player, DrinkEffect effect) {
        String id = effect.effect().replace("kaleidoscope_tavern:", "");
        if (INSTANT.contains(id)) { abilities.instant(player, id); return; }
        if (!TIMED.contains(id)) throw new IllegalArgumentException("Unknown tavern effect: " + effect.effect());
        Session session = sessions.get(player.getUniqueId());
        if (session == null) { attach(player); session = sessions.get(player.getUniqueId()); }
        if (session == null) throw new IllegalStateException("Player effect save is invalid");
        Timer incoming = new Timer(id, Math.max(1, effect.duration() * 20), effect.amplifier());
        session.timers.merge(id, incoming, (old, fresh) -> fresh.amplifier >= old.amplifier ? fresh
                : new Timer(id, Math.max(old.remaining, fresh.remaining), old.amplifier));
        syncAttributes(player, session);
        save(player, session);
        player.sendActionBar(Component.text("饮品效果：" + id));
    }

    private void attach(Player player) {
        if (sessions.containsKey(player.getUniqueId())) return;
        Session session = new Session();
        try {
            String stored = player.getPersistentDataContainer().get(savedKey, PersistentDataType.STRING);
            if (stored != null) {
                if (stored.length() > 65536) throw new IllegalArgumentException("Tavern effect save exceeds limit");
                Timer[] timers = json.fromJson(stored, Timer[].class);
                if (timers == null || timers.length > TIMED.size()) throw new IllegalArgumentException("Invalid tavern effect count");
                for (Timer timer : timers) if (session.timers.put(timer.id, timer) != null) throw new IllegalArgumentException("Duplicate tavern effect");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot restore tavern effects for " + player.getUniqueId(), exception);
            player.sendMessage("饮品效果存档异常，原始记录已保留，请联系管理员。");
            return;
        }
        sessions.put(player.getUniqueId(), session);
        syncAttributes(player, session);
        session.task = player.getScheduler().runAtFixedRate(plugin, task -> pulse(player, session),
                () -> sessions.remove(player.getUniqueId(), session), 1, 1);
    }

    private void pulse(Player player, Session session) {
        for (Timer timer : session.timers.values().toArray(Timer[]::new)) {
            abilities.pulse(player, timer.id, timer.remaining, timer.amplifier);
            boolean exhausted = timer.id.equals("ardent_heat") && player.getFoodLevel() == 0 && player.getSaturation() <= 0.01f;
            if (timer.remaining == 1 || exhausted) {
                session.timers.remove(timer.id);
                if (timer.id.equals("ardent_heat")) player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 0));
                syncAttributes(player, session);
            } else session.timers.put(timer.id, new Timer(timer.id, timer.remaining - 1, timer.amplifier));
        }
        if (++session.ticks % 20 == 0) save(player, session);
    }

    private void syncAttributes(Player player, Session session) {
        attribute(player, Attribute.STEP_HEIGHT, heelsKey, session.timers.get("high_heels"), 0.5);
        attribute(player, Attribute.BLOCK_INTERACTION_RANGE, reachKey, session.timers.get("long_reach"), 3);
        attribute(player, Attribute.ENTITY_INTERACTION_RANGE, reachKey, session.timers.get("long_reach"), 3);
    }

    private void attribute(Player player, Attribute attribute, NamespacedKey key, Timer timer, double base) {
        var instance = player.getAttribute(attribute);
        if (instance == null) throw new IllegalStateException("Missing player attribute: " + attribute);
        instance.removeModifier(key);
        if (timer != null) instance.addTransientModifier(new AttributeModifier(key, base * (timer.amplifier + 1), AttributeModifier.Operation.ADD_NUMBER));
    }

    private void save(Player player, Session session) {
        player.getPersistentDataContainer().set(savedKey, PersistentDataType.STRING, json.toJson(session.timers.values()));
    }

    private void clear(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.timers.clear();
        syncAttributes(player, session);
        save(player, session);
    }

    @EventHandler public void joined(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler public void died(PlayerDeathEvent event) { clear(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drankMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) plugin.later(event.getPlayer(), () -> clear(event.getPlayer()));
    }
    @EventHandler public void left(PlayerQuitEvent event) { detach(event.getPlayer()); }

    private void detach(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        save(player, session);
        syncAttributes(player, new Session());
    }

    public void stop() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.task != null) session.task.cancel();
            if (Bukkit.isOwnedByCurrentRegion(player)) detach(player);
            else BukkitCraftEngine.instance().scheduler().platform().run(() -> detach(player), () -> {}, player);
        }
    }
}
