package net.kaleidoscope.tavern;

import com.google.gson.Gson;
import net.kaleidoscope.series.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class Drinks implements Listener {
    private final TavernPlugin plugin;
    private final NamespacedKey signature;
    private final Set<String> bottled;
    private final TavernEffects custom;
    private final Gson json = new Gson();

    public Drinks(TavernPlugin plugin, TavernEffects custom) {
        this.plugin = plugin;
        this.custom = custom;
        signature = new NamespacedKey(plugin, "signature_effects_v1");
        bottled = plugin.catalog().barrel().stream().map(BrewRecipe::output).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBottled(String id) { return bottled.contains(id) || id.equals("kaleidoscope_tavern:vinegar"); }

    public String container(ItemStack item) {
        String id = Items.id(item);
        if (isBottled(id)) return "kaleidoscope_tavern:empty_bottle";
        if (plugin.catalog().effects().containsKey(id) || id.equals("kaleidoscope_tavern:signature_cocktail")) return "kaleidoscope_tavern:empty_glassware";
        if (item.getType() == Material.POTION) return "minecraft:glass_bottle";
        return null;
    }

    public ItemStack cocktail(String id, List<ItemStack> ingredients) {
        ItemStack drink = Items.create(id);
        if (id.equals("kaleidoscope_tavern:signature_cocktail")) {
            List<DrinkEffect> mixed = DrinkEffect.mix(ingredients.stream().flatMap(item -> effects(item).stream()).toList());
            var meta = drink.getItemMeta();
            meta.getPersistentDataContainer().set(signature, PersistentDataType.STRING, json.toJson(mixed));
            drink.setItemMeta(meta);
        }
        return drink;
    }

    public List<DrinkEffect> effects(ItemStack item) {
        String id = Items.id(item);
        if (id.equals("kaleidoscope_tavern:signature_cocktail")) {
            String saved = item.getItemMeta().getPersistentDataContainer().get(signature, PersistentDataType.STRING);
            if (saved == null) return List.of();
            if (saved.length() > 65536) throw new IllegalArgumentException("Signature cocktail effects exceed limit");
            DrinkEffect[] effects = json.fromJson(saved, DrinkEffect[].class);
            if (effects == null || effects.length > 64) throw new IllegalArgumentException("Invalid signature cocktail effects");
            return List.copyOf(Arrays.asList(effects));
        }
        if (item.getType() == Material.POTION && item.getItemMeta() instanceof PotionMeta meta) {
            var effects = new ArrayList<PotionEffect>();
            if (meta.getBasePotionType() != null) effects.addAll(meta.getBasePotionType().getPotionEffects());
            effects.addAll(meta.getCustomEffects());
            return effects.stream().map(effect -> new DrinkEffect(effect.getType().getKey().toString(),
                    Math.max(0, effect.getDuration() / 20), effect.getAmplifier(), 1)).toList();
        }
        var levels = plugin.catalog().effects().get(id);
        if (levels == null || levels.isEmpty()) return List.of();
        int level = isBottled(id) ? plugin.brewLevel(item) : 1;
        return level == 0 ? List.of() : levels.get(Math.min(level, levels.size()) - 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void validateDrink(PlayerItemConsumeEvent event) {
        String id = Items.id(event.getItem());
        if (!plugin.catalog().effects().containsKey(id) && !id.equals("kaleidoscope_tavern:signature_cocktail")) return;
        try { effects(event.getItem()); }
        catch (RuntimeException exception) {
            event.setCancelled(true);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot read drink effects: " + id, exception);
            event.getPlayer().sendMessage("饮品记录无法读取，物品已保留，请联系管理员。");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drink(PlayerItemConsumeEvent event) {
        String id = Items.id(event.getItem());
        if (!plugin.catalog().effects().containsKey(id) && !id.equals("kaleidoscope_tavern:signature_cocktail")) return;
        List<DrinkEffect> effects = effects(event.getItem());
        plugin.later(event.getPlayer(), () -> effects.forEach(effect -> apply(event.getPlayer(), effect)));
    }

    private void apply(Player player, DrinkEffect effect) {
        if (ThreadLocalRandom.current().nextDouble() >= effect.probability()) return;
        var type = Registry.MOB_EFFECT.get(NamespacedKey.fromString(effect.effect()));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, Math.max(1, effect.duration() * 20), effect.amplifier()));
        } else custom.apply(player, effect);
    }
}
