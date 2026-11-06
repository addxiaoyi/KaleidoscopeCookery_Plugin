package net.kaleidoscope.cookery.effect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class KitchenEffectHandlers {
    private static final NamespacedKey TUNDRA_SPEED = new NamespacedKey("kaleidoscopecookery", "tundra_speed");
    private static final Set<Material> SNOW = Set.of(Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW);
    private static final Set<Material> ICE = Set.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);
    private static final Set<Material> HEAT = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK);

    private KitchenEffectHandlers() {}

    static Map<String, EffectHandler> create() {
        var handlers = new java.util.HashMap<String, EffectHandler>();
        for (EffectKind kind : EffectKind.values()) handlers.put(kind.id(), new Handler((player, state) -> {}, player -> {}));
        handlers.put(EffectKind.VIGOR.id(), new Handler((player, state) -> {
            if (player.isSprinting()) player.setExhaustion(0);
        }, player -> {}));
        handlers.put(EffectKind.WARMTH.id(), new Handler(KitchenEffectHandlers::warmth, player -> {}));
        handlers.put(EffectKind.TUNDRA_STRIDER.id(), new Handler(KitchenEffectHandlers::tundra, KitchenEffectHandlers::clearTundra));
        handlers.put(EffectKind.SULFUR.id(), new Handler(KitchenEffectHandlers::sulfur, player -> {}));
        handlers.put(EffectKind.MUSTARD.id(), new Handler(KitchenEffectHandlers::mustard, player -> {}));
        return Map.copyOf(handlers);
    }

    private record Handler(BiConsumer<Player, EffectState> action, Consumer<Player> cleanup) implements EffectHandler {
        @Override public void apply(Player player, EffectState state) {}
        @Override public void tick(Player player, EffectState state) { action.accept(player, state); }
        @Override public void clear(Player player, String reason) { cleanup.accept(player); }
    }

    private static boolean owned(Location location, int radiusChunks) {
        return Bukkit.isOwnedByCurrentRegion(location, radiusChunks);
    }

    private static void warmth(Player player, EffectState state) {
        if (state.remaining() % 20 != 0 || player.getHealth() >= player.getAttribute(Attribute.MAX_HEALTH).getValue()) return;
        Location origin = player.getLocation();
        World world = origin.getWorld();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 1; y++) for (int z = -2; z <= 2; z++) {
            Location sample = new Location(world, origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
            if (!sample.isChunkLoaded() || !owned(sample, 0)) continue;
            var block = sample.getBlock();
            if (HEAT.contains(block.getType()) || block.getBlockData() instanceof Lightable light && light.isLit()) {
                heal(player, 1);
                return;
            }
        }
        if (world.getEnvironment() == World.Environment.NETHER && ThreadLocalRandom.current().nextInt(4) == 0) heal(player, .5);
    }

    private static void heal(Player player, double amount) {
        EntityRegainHealthEvent event = new EntityRegainHealthEvent(player, amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
        if (event.callEvent() && !player.isDead()) {
            player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + event.getAmount()));
        }
    }

    private static void tundra(Player player, EffectState state) {
        if (state.remaining() % 5 != 0) return;
        Location feet = player.getLocation();
        if (!feet.isChunkLoaded() || !owned(feet, 0)) { clearTundra(player); return; }
        Material ground = feet.clone().subtract(0, .2, 0).getBlock().getType();
        double boost = ICE.contains(ground) ? (ground == Material.BLUE_ICE ? .1055 : .11) : SNOW.contains(ground) ? .3 : 0;
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        var old = speed.getModifier(TUNDRA_SPEED);
        if (old != null && old.getAmount() == boost) return;
        speed.removeModifier(TUNDRA_SPEED);
        if (boost > 0) speed.addTransientModifier(new AttributeModifier(TUNDRA_SPEED, boost, AttributeModifier.Operation.ADD_SCALAR));
    }

    static void farmerOutfit(Player player) {
        if (!player.isInWater()) return;
        var armor = player.getInventory().getArmorContents();
        if (armor.length != 4) return;
        String[] slots = {"farmer_boots", "farmer_leggings", "farmer_chest_plate", "straw_hat"};
        for (int slot = 0; slot < armor.length; slot++) {
            if (armor[slot] == null || armor[slot].getType().isAir()) return;
            String id = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(armor[slot]).id().toString();
            if (!id.equals("kaleidoscopecookery:" + slots[slot])
                    && !(slot == 3 && id.equals("kaleidoscopecookery:straw_hat_flower"))) return;
        }
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, 25, 0, true, true, false));
    }

    private static void clearTundra(Player player) {
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(TUNDRA_SPEED);
    }

    private static void sulfur(Player player, EffectState state) {
        if (state.remaining() % 5 != 0 || !owned(player.getLocation(), 1)) return;
        for (var entity : player.getNearbyEntities(8, 16, 8)) {
            if (entity instanceof Phantom phantom && Bukkit.isOwnedByCurrentRegion(phantom) && player.equals(phantom.getTarget())) phantom.setTarget(null);
        }
    }

    private static void mustard(Player player, EffectState state) {
        if (state.remaining() % 10 != 0 || !owned(player.getLocation(), 1)) return;
        for (var entity : player.getNearbyEntities(6, 6, 6)) {
            if (!(entity instanceof Creeper creeper) || !Bukkit.isOwnedByCurrentRegion(creeper)) continue;
            var away = creeper.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < .01) away.setX(1);
            Location destination = creeper.getLocation().add(away.normalize().multiply(6));
            if (!owned(destination, 0) || !destination.isChunkLoaded()) continue;
            if (player.equals(creeper.getTarget())) creeper.setTarget(null);
            creeper.getPathfinder().moveTo(destination, 1.2);
        }
    }
}
