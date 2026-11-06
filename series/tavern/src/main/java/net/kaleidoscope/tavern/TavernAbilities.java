package net.kaleidoscope.tavern;

import net.kaleidoscope.series.Nearby;
import net.kyori.adventure.text.Component;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

final class TavernAbilities implements Listener {
    private final TavernPlugin plugin;
    private final TavernEffects effects;
    private final NamespacedKey collisions;

    TavernAbilities(TavernPlugin plugin, TavernEffects effects) {
        this.plugin = plugin;
        this.effects = effects;
        collisions = new NamespacedKey(plugin, "ardent_heat_collisions");
    }

    void pulse(Player player, String id, int remaining, int amplifier) {
        switch (id) {
            case "grass_stealth" -> { if (remaining % 10 == 0) hideInGrass(player); }
            case "vision" -> { if (remaining % 50 == 0) reveal(player, amplifier); }
            case "xp_drain" -> {
                player.setExpCooldown(0);
                if (player.getTicksLived() % 5 == 0) attractXp(player);
            }
            case "ardent_heat" -> { if (player.isSprinting()) breakFront(player); }
            default -> { /* Other timed effects act through attributes and combat events. */ }
        }
    }

    void instant(Player player, String id) {
        switch (id) {
            case "zenith" -> surface(player);
            case "upside_down" -> {
                for (var entity : Nearby.owned(player.getLocation(), 16)) {
                    if (entity instanceof Mob mob && !mob.isDead() && plugin.allows(player, Flag.INTERACT_ENTITY, mob)) {
                        mob.customName(Component.text("Grumm"));
                        mob.setCustomNameVisible(false);
                    }
                }
            }
            case "shriek_attack" -> shriek(player);
            default -> throw new IllegalArgumentException("Unknown instant drink effect: " + id);
        }
    }

    private boolean inGrass(Player player) {
        if (!player.isSneaking()) return false;
        Block feet = player.getLocation().getBlock();
        return grass(feet) || grass(feet.getRelative(BlockFace.UP));
    }

    private boolean grass(Block block) {
        if (block.getBlockData() instanceof Ageable age && Tag.CROPS.isTagged(block.getType())) return age.getAge() == age.getMaximumAge();
        return matchesBlock("grass_stealth_plants", block.getType());
    }

    private void hideInGrass(Player player) {
        if (!inGrass(player)) return;
        player.setExhaustion(player.getExhaustion() + 0.1f);
        for (var entity : Nearby.owned(player.getLocation(), 32)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && effects.active(player, "grass_stealth") && inGrass(player)) event.setCancelled(true);
    }

    private void reveal(Player player, int amplifier) {
        boolean fresh = false;
        for (var entity : Nearby.owned(player.getLocation(), Math.min(amplifier + 1, 3) * 6)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) continue;
            fresh |= !living.hasPotionEffect(PotionEffectType.GLOWING);
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
        }
        if (fresh) player.playSound(player.getLocation(), "kaleidoscope_tavern:effect.vision", 1, 1);
    }

    private void attractXp(Player player) {
        var destination = player.getLocation().add(0, 0.5, 0).toVector();
        for (var entity : Nearby.owned(player.getLocation(), 8)) {
            if (!(entity instanceof ExperienceOrb orb) || !orb.isValid()) continue;
            Vector direction = destination.clone().subtract(orb.getLocation().toVector());
            double distance = direction.length();
            if (distance > 0.01) orb.setVelocity(direction.multiply(Math.min(0.5 + 1 / distance, 1.5) / distance));
        }
    }

    private void breakFront(Player player) {
        BlockFace facing = player.getFacing();
        var center = player.getLocation().clone().add(facing.getModX(), 0, facing.getModZ());
        boolean broke = false;
        for (int y = 0; y <= 2; y++) for (int side = -1; side <= 1; side++) {
            var at = center.clone().add(facing.getModX() == 0 ? side : 0, y, facing.getModZ() == 0 ? side : 0);
            if (!Bukkit.isOwnedByCurrentRegion(at)) continue;
            Block block = at.getBlock();
            if (matchesBlock("ardent_heat_breakable", block.getType()) && plugin.allows(player, Flag.BREAK, at)) {
                broke |= player.breakBlock(block);
            }
        }
        if (!broke) return;
        player.setExhaustion(player.getExhaustion() + 1.2f);
        ItemStack[] armor = player.getInventory().getArmorContents();
        var equipped = new ArrayList<Integer>();
        for (int i = 0; i < armor.length; i++) if (armor[i] != null && !armor[i].isEmpty()) equipped.add(i);
        if (!equipped.isEmpty()) {
            int index = equipped.get(ThreadLocalRandom.current().nextInt(equipped.size()));
            armor[index] = armor[index].damage(1, player);
            player.getInventory().setArmorContents(armor);
            return;
        }
        int count = player.getPersistentDataContainer().getOrDefault(collisions, PersistentDataType.INTEGER, 0) + 1;
        if (count >= 5) { player.damage(1); count -= 5; }
        player.getPersistentDataContainer().set(collisions, PersistentDataType.INTEGER, count);
    }

    private boolean matchesBlock(String name, Material material) {
        for (String entry : plugin.catalog().effectTags().get(name)) {
            if (entry.equals("minecraft:grass")) entry = "minecraft:short_grass";
            if (entry.equals(material.getKey().toString())) return true;
            if (entry.startsWith("#")) {
                var tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.fromString(entry.substring(1)), Material.class);
                if (tag == null) throw new IllegalStateException("Missing block tag: " + entry);
                if (tag.isTagged(material)) return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void disarm(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !effects.active(player, "tomb_raider")
                || !(event.getEntity() instanceof Mob target) || event.getFinalDamage() <= 0
                || !Bukkit.isOwnedByCurrentRegion(target) || !disarmable(target.getType())
                || !plugin.allows(player, Flag.DAMAGE_ENTITY, target) || ThreadLocalRandom.current().nextDouble() >= 0.3) return;
        ItemStack item = target.getEquipment().getItemInMainHand().clone();
        if (item.isEmpty()) return;
        if (item.getItemMeta() instanceof Damageable meta) {
            int maxDamage = meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
            if (maxDamage > 0) { meta.setDamage(maxDamage - 1); item.setItemMeta(meta); }
        }
        var drop = target.getWorld().dropItem(target.getLocation(), item);
        if (!drop.isValid()) return;
        drop.setPickupDelay(40);
        target.getEquipment().setItemInMainHand(null);
    }

    private boolean disarmable(EntityType type) {
        for (String entry : plugin.catalog().effectTags().get("tomb_raider_disarmable")) {
            if (entry.equals(type.getKey().toString())) return true;
            if (entry.startsWith("#")) {
                var tag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, NamespacedKey.fromString(entry.substring(1)), EntityType.class);
                if (tag == null) throw new IllegalStateException("Missing entity tag: " + entry);
                if (tag.isTagged(type)) return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void healAfterKill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null || !effects.active(player, "bloody_mary") || player == event.getEntity() || player.isDead()) return;
        double healing = Math.floor(event.getEntity().getAttribute(Attribute.MAX_HEALTH).getValue() / 3);
        if (healing <= 0) return;
        var heal = new EntityRegainHealthEvent(player, healing, EntityRegainHealthEvent.RegainReason.MAGIC);
        if (heal.callEvent()) player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + heal.getAmount()));
    }

    private void surface(Player player) {
        var start = player.getLocation();
        int surface = start.getWorld().getHighestBlockYAt(start, HeightMap.MOTION_BLOCKING) + 1;
        if (start.getBlockY() >= surface) return;
        var target = start.clone();
        target.setX(start.getBlockX() + 0.5);
        target.setZ(start.getBlockZ() + 0.5);
        target.setY(surface);
        player.playSound(start, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1, 1);
        player.teleportAsync(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(moved -> {
            if (moved && plugin.isEnabled()) plugin.later(player, () -> {
                player.setFallDistance(0);
                player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1, 1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 0));
            });
        }).exceptionally(error -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Zenith teleport failed for " + player.getUniqueId(), error);
            return null;
        });
    }

    private void shriek(Player player) {
        var eye = player.getEyeLocation();
        var look = eye.getDirection();
        Vector horizontal = new Vector(look.getX(), 0, look.getZ());
        if (horizontal.lengthSquared() > 0.001) horizontal.normalize().multiply(0.63);
        else horizontal.zero();
        var source = DamageSource.builder(DamageType.SONIC_BOOM).withCausingEntity(player).withDirectEntity(player).build();
        for (var entity : Nearby.owned(player.getLocation(), 32)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) continue;
            Vector center = living.getBoundingBox().getCenter();
            double distance = center.clone().subtract(eye.toVector()).dot(look);
            if (distance < 0 || distance > 32) continue;
            double perpendicular = eye.toVector().add(look.clone().multiply(distance)).distance(center);
            if (perpendicular > 1 + living.getWidth() / 2) continue;
            if (!plugin.allows(player, Flag.DAMAGE_ENTITY, living)) continue;
            double health = living.getHealth() + living.getAbsorptionAmount();
            living.damage(player.getHealth() * 1.2, source);
            if (living.getHealth() + living.getAbsorptionAmount() < health) living.setVelocity(living.getVelocity().add(horizontal.clone().setY(0.28)));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        for (int i = 1; i <= 16; i++) player.getWorld().spawnParticle(Particle.SONIC_BOOM, eye.clone().add(look.clone().multiply(i * 2)), 1);
    }
}
