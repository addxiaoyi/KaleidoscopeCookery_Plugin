package net.kaleidoscope.cookery.effect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class KitchenEffectEvents implements Listener {
    private final JavaPlugin plugin;
    private final CookeryEffects effects;

    public KitchenEffectEvents(JavaPlugin plugin, CookeryEffects effects) { this.plugin = plugin; this.effects = effects; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void flatulence(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !effects.has(player.getUniqueId(), EffectKind.FLATULENCE)) return;
        player.setVelocity(player.getVelocity().add(new Vector(0, .75, 0)));
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 8, .2, .1, .2, .02);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, .8f, .6f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preserveFood(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.FOOD || event.getNewEffect() == null) return;
        if (effects.has(event.getEntity().getUniqueId(), EffectKind.PRESERVATION)
                && event.getNewEffect().getType().getCategory() == PotionEffectTypeCategory.HARMFUL) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void repel(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() == null) return;
        var target = event.getTarget().getUniqueId();
        if (event.getEntity() instanceof Phantom && effects.has(target, EffectKind.SULFUR)
                || event.getEntity() instanceof Creeper && effects.has(target, EffectKind.MUSTARD)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void shield(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !effects.has(player.getUniqueId(), EffectKind.SATIATED_SHIELD)
                || player.getFoodLevel() < 4 || player.hasPotionEffect(PotionEffectType.HUNGER)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        double damage = event.getFinalDamage();
        double reduction = Math.min(64, Math.max(0, damage));
        if (reduction == 0) return;
        double scale = (damage - reduction) / damage;
        event.setDamage(event.getDamage() * scale);
        double weakness = switch (event.getDamageSource().getDamageType().getKey().getKey()) {
            case "wither_skull", "wither", "sonic_boom", "indirect_magic", "in_wall", "freeze", "explosion",
                    "player_explosion", "fireworks", "bad_respawn_point", "lightning_bolt" -> 2;
            default -> 1;
        };
        // Charge only after all damage handlers have had a chance to cancel the hit.
        player.getScheduler().run(plugin, task -> {
            if (!event.isCancelled() && !player.isDead()) player.setExhaustion(player.getExhaustion() + (float) (Math.round(reduction * 2) * weakness));
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hinder(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamageSource().getCausingEntity();
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0
                || !effects.has(attacker.getUniqueId(), EffectKind.HINDER)) return;
        target.getScheduler().run(plugin, task -> {
            if (!event.isCancelled() && !target.isDead()) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dodge(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player player) || !Bukkit.isOwnedByCurrentRegion(player)
                || !effects.has(player.getUniqueId(), EffectKind.PROJECTILE_DODGE)) return;
        event.setCancelled(true);
        effects.spend(player, EffectKind.PROJECTILE_DODGE, 200);
        Location origin = player.getLocation();
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            Location destination = origin.clone().add((random.nextDouble() - .5) * 3, (random.nextDouble() - .5) * 3, (random.nextDouble() - .5) * 3);
            if (!destination.isChunkLoaded() || !Bukkit.isOwnedByCurrentRegion(destination, 1)
                    || !destination.getWorld().getWorldBorder().isInside(destination)
                    || !destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()
                    || !destination.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            player.teleportAsync(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                if (success) player.getScheduler().run(plugin,
                        task -> player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1), null);
            });
            break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void vitality(EntityDeathEvent event) {
        Entity attacker = event.getDamageSource().getCausingEntity();
        if (attacker == null || !effects.has(attacker.getUniqueId(), EffectKind.VITALITY)) return;
        LivingEntity victim = event.getEntity();
        boolean adult = victim instanceof Ageable ageable && ageable.isAdult() || victim instanceof Zombie zombie && !zombie.isBaby();
        if (!adult) return;
        var type = victim instanceof Zombie && ThreadLocalRandom.current().nextInt(20) == 0 ? org.bukkit.entity.EntityType.VILLAGER : victim.getType();
        Location location = victim.getLocation().clone();
        Bukkit.getRegionScheduler().run(plugin, location, task -> {
            if (event.isCancelled() || !location.isChunkLoaded()) return;
            location.getWorld().spawnEntity(location, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
                if (entity instanceof Ageable ageable) ageable.setBaby();
                if (entity instanceof Zombie zombie) zombie.setBaby(true);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void smelt(BlockDropItemEvent event) {
        EffectState state = effects.state(event.getPlayer().getUniqueId(), EffectKind.INSTANT_SMELTING);
        Material block = event.getBlockState().getType();
        if (state == null || !(block.name().endsWith("_ORE") || block == Material.ANCIENT_DEBRIS)) return;
        var drops = List.copyOf(event.getItems());
        Location location = event.getBlockState().getLocation();
        int limit = state.level() + 1;
        Bukkit.getRegionScheduler().run(plugin, location, task -> {
            if (event.isCancelled()) return;
            int remaining = limit;
            for (var entity : drops) {
                if (remaining == 0 || !entity.isValid() || !Bukkit.isOwnedByCurrentRegion(entity)) continue;
                ItemStack input = entity.getItemStack();
                if (input.hasItemMeta()) continue;
                FurnaceRecipe recipe = furnaceRecipe(input);
                if (recipe == null) continue;
                int count = Math.min(remaining, input.getAmount());
                ItemStack output = recipe.getResult().clone();
                output.setAmount(Math.multiplyExact(output.getAmount(), count));
                if (count == input.getAmount() && output.getAmount() <= output.getMaxStackSize()) {
                    entity.setItemStack(output);
                } else {
                    List<ItemStack> products = split(output);
                    List<org.bukkit.entity.Item> spawned = new ArrayList<>();
                    for (ItemStack product : products) spawned.add(location.getWorld().dropItem(entity.getLocation(), product));
                    if (spawned.stream().anyMatch(drop -> !drop.isValid())) {
                        spawned.forEach(Entity::remove);
                        continue;
                    }
                    if (count == input.getAmount()) entity.remove();
                    else { input.setAmount(input.getAmount() - count); entity.setItemStack(input); }
                }
                remaining -= count;
            }
        });
    }

    private static FurnaceRecipe furnaceRecipe(ItemStack input) {
        var recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            var recipe = recipes.next();
            if (recipe instanceof FurnaceRecipe furnace && furnace.getInputChoice().test(input)) return furnace;
        }
        return null;
    }

    private static List<ItemStack> split(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int remaining = stack.getAmount(); remaining > 0; remaining -= stack.getMaxStackSize()) {
            ItemStack part = stack.clone();
            part.setAmount(Math.min(remaining, stack.getMaxStackSize()));
            stacks.add(part);
        }
        return stacks;
    }
}
