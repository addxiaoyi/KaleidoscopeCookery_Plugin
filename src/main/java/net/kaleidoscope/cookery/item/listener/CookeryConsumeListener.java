package net.kaleidoscope.cookery.item.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kaleidoscope.cookery.catalog.BakedFood;
import net.kaleidoscope.cookery.effect.CookeryEffects;
import net.kaleidoscope.cookery.item.CookeryFoodItems;
import net.kaleidoscope.cookery.recipe.DishCarriers;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CookeryConsumeListener implements Listener {
    private final JavaPlugin plugin;
    private final CookeryEffects effects;

    public CookeryConsumeListener(JavaPlugin plugin, CookeryEffects effects) { this.plugin = plugin; this.effects = effects; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void validate(PlayerItemConsumeEvent event) {
        try { CookeryFoodItems.read(event.getItem()); }
        catch (IllegalArgumentException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("这份食物的保存数据有误，已保留物品，请联系管理员。");
            plugin.getLogger().log(Level.WARNING, "Invalid food snapshot for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consumed(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem().clone();
        BakedFood food = CookeryFoodItems.read(stack);
        boolean milk = stack.getType() == Material.MILK_BUCKET;
        if (food == null && !milk) return;
        event.getPlayer().getScheduler().run(plugin, task -> {
            Player player = event.getPlayer();
            if (event.isCancelled() || player.isDead()) return;
            if (milk) effects.clear(player, "milk");
            if (food != null) {
                apply(player, food);
                // Existing configured carriers keep their established handling.
                if (food.container() != null && DishCarriers.of(Key.of(food.itemId())) == null && !stack.hasData(DataComponentTypes.USE_REMAINDER)
                        && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    InventoryUtils.give(BukkitAdaptor.adapt(player), InventoryUtils.createOrEmpty(Key.of(food.container())));
                }
            }
        }, null);
    }

    public void apply(Player player, BakedFood food) {
        for (var effect : food.food().effects()) {
            if (effect.id().startsWith("minecraft:") || ThreadLocalRandom.current().nextDouble() >= effect.probability()) continue;
            effects.apply(player, effect, food.itemId());
        }
        if (food.removeHarmful()) {
            for (var effect : player.getActivePotionEffects()) {
                if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) player.removePotionEffect(effect.getType());
            }
        }
        if (food.damage() > 0) player.damage(food.damage());
    }

    public boolean blocksHarmfulFood(Player player, org.bukkit.potion.PotionEffectType type) {
        return type.getCategory() == PotionEffectTypeCategory.HARMFUL
                && effects.has(player.getUniqueId(), net.kaleidoscope.cookery.effect.EffectKind.PRESERVATION);
    }
}
