package net.kaleidoscope.doll;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.Nearby;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class MovingDolls implements Listener {
    private final DollPlugin plugin;
    private final NamespacedKey marker, author, phantomLife, phantomSeen;
    private final Set<String> dolls;
    private final Map<UUID, Charge> charges = new ConcurrentHashMap<>();
    private final Set<UUID> ticking = ConcurrentHashMap.newKeySet();
    private final int lifetime;
    private final double phantomChance;

    MovingDolls(DollPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "moving_doll_v1"); author = new NamespacedKey(plugin, "doll_owner");
        phantomLife = new NamespacedKey(plugin, "phantom_life"); phantomSeen = new NamespacedKey(plugin, "phantom_checked");
        dolls = Set.copyOf(plugin.catalog().dolls());
        lifetime = plugin.getConfig().getInt("phantom.lifetime-ticks", 3600);
        phantomChance = plugin.getConfig().getDouble("phantom.chance", 0.03);
        if (lifetime < -1 || !Double.isFinite(phantomChance) || phantomChance < 0 || phantomChance > 1) throw new IllegalArgumentException("Invalid phantom configuration");
    }

    private boolean doll(ItemStack item) { return item != null && dolls.contains(Items.id(item)); }
    private boolean marked(Entity entity) { return entity instanceof ArmorStand && entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void charge(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !doll(held) || !plugin.getConfig().getBoolean("throw.enabled", true)) return;
        event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY);
        if (!plugin.mayUse(player, player.getLocation()) || charges.containsKey(player.getUniqueId())) return;
        ItemStack original = held.clone();
        held = held.clone();
        held.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(3600)
                .animation(ItemUseAnimation.BOW).hasConsumeParticles(false).build());
        player.getInventory().setItemInMainHand(held);
        Charge charge = new Charge(player, player.getInventory().getHeldItemSlot(), original, held.clone());
        charges.put(player.getUniqueId(), charge);
        player.startUsingItem(EquipmentSlot.HAND);
        var scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (charges.get(player.getUniqueId()) != charge) { task.cancel(); return; }
            if (!player.hasActiveItem() || !sameHand(player, charge.slot, charge.item)) {
                cancel(player); task.cancel();
            }
        }, () -> charges.remove(player.getUniqueId(), charge), 1, 5);
        if (scheduled == null) cancel(player);
    }

    @EventHandler public void release(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        Charge charge = charges.remove(player.getUniqueId());
        double power = DollPhysics.throwPower(event.getTicksHeldFor());
        if (charge == null) return;
        boolean released = charge.item.isSimilar(event.getItem()) && sameHand(player, charge.slot, charge.item);
        restore(charge);
        if (!released || power == 0) return;
        Location at = player.getEyeLocation().subtract(0, 0.1, 0);
        if (!plugin.allows(player, Flag.PLACE, at) || !plugin.mayUse(player, at)) return;
        plugin.attempt(player, () -> {
            ArmorStand stand = spawn(at, charge.original.asQuantity(1), player.getUniqueId(), false);
            if (!stand.isValid() || !sameHand(player, charge.slot, charge.original)) { stand.remove(); return; }
            if (player.getGameMode() != GameMode.CREATIVE) player.getInventory().setItemInMainHand(charge.original.asQuantity(charge.original.getAmount() - 1));
            stand.setVelocity(at.getDirection().multiply(power));
            watch(stand);
            player.playSound(at, Sound.ENTITY_SNOWBALL_THROW, 0.5f, 0.5f);
        });
    }

    private boolean sameHand(Player player, int slot, ItemStack item) {
        return player.getInventory().getHeldItemSlot() == slot && item.equals(player.getInventory().getItemInMainHand());
    }

    private ArmorStand spawn(Location at, ItemStack item, UUID owner, boolean phantom) {
        return at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setVisible(false); stand.setSmall(true); stand.setBasePlate(false);
            stand.setPersistent(true); stand.setCanMove(true); stand.setCanTick(true);
            stand.setSilent(true); stand.setGravity(true);
            stand.setDisabledSlots(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
            stand.getEquipment().setHelmet(item);
            stand.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            if (owner != null) stand.getPersistentDataContainer().set(author, PersistentDataType.STRING, owner.toString());
            if (phantom) stand.getPersistentDataContainer().set(phantomLife, PersistentDataType.INTEGER, 0);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !plugin.getConfig().getBoolean("mount.enabled", true)) return;
        Player player = event.getPlayer(); Entity target = event.getRightClicked();
        if (charges.containsKey(player.getUniqueId())) { event.setCancelled(true); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!doll(held) || !Bukkit.isOwnedByCurrentRegion(target)) return;
        event.setCancelled(true);
        if (!plugin.mayUse(player, target.getLocation()) || !plugin.allows(player, Flag.INTERACT_ENTITY, target)
                || !plugin.allows(player, Flag.PLACE, target.getLocation())) return;
        int layers = 1;
        for (Entity vehicle = target.getVehicle(); vehicle != null && layers < 5; vehicle = vehicle.getVehicle()) layers++;
        if (layers >= 5) { player.sendMessage("玩偶最多叠放五层。"); return; }
        ItemStack before = held.clone();
        plugin.attempt(player, () -> {
            ArmorStand stand = spawn(target.getLocation(), before.asQuantity(1), player.getUniqueId(), false);
            if (!stand.isValid() || !target.addPassenger(stand) || !before.equals(player.getInventory().getItemInMainHand())) {
                stand.remove(); return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) player.getInventory().setItemInMainHand(before.asQuantity(before.getAmount() - 1));
            watch(stand);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void touch(PlayerInteractAtEntityEvent event) {
        if (!marked(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (doll(event.getPlayer().getInventory().getItemInMainHand())) { mount(event); return; }
        if (!plugin.mayUse(event.getPlayer(), event.getRightClicked().getLocation())
                || !plugin.allows(event.getPlayer(), Flag.INTERACT_ENTITY, event.getRightClicked())) return;
        noise((ArmorStand) event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (!marked(event.getEntity())) return;
        boolean denied = event.isCancelled();
        event.setCancelled(true);
        ArmorStand stand = (ArmorStand) event.getEntity();
        if (denied) return;
        if (event instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player player) {
            if (!plugin.mayUse(player, stand.getLocation()) || !plugin.allows(player, Flag.DAMAGE_ENTITY, stand)
                    || !plugin.allows(player, Flag.BREAK, stand.getLocation())) return;
            plugin.attempt(player, () -> {
                ItemStack item = stand.getEquipment().getHelmet();
                if (!doll(item)) throw new IllegalStateException("Moving doll lost its item: " + stand.getUniqueId());
                if (player.getGameMode() != GameMode.CREATIVE && !InventoryTrade.give(player.getInventory(), item)) {
                    player.sendMessage("背包空间不足，玩偶已保留。"); return;
                }
                plugin.collected(player, Items.id(item));
                stand.remove();
            });
        } else {
            noise(stand);
            if (stand.getVehicle() == null && stand.isOnGround()) stand.setVelocity(new Vector(0, 0.2, 0));
        }
    }

    private void noise(ArmorStand stand) {
        stand.getWorld().playSound(stand.getLocation(), "kaleidoscope_doll:duck_toy", SoundCategory.BLOCKS, 1, ThreadLocalRandom.current().nextFloat(0.75f, 1.25f));
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void equip(PlayerArmorStandManipulateEvent event) {
        if (marked(event.getRightClicked())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void drop(EntityDropItemEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler public void load(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (marked(entity)) watch((ArmorStand) entity);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void phantom(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom) || !plugin.getConfig().getBoolean("phantom.enabled", true)) return;
        if (phantom.getPersistentDataContainer().has(phantomSeen)) return;
        phantom.getPersistentDataContainer().set(phantomSeen, PersistentDataType.BYTE, (byte) 1);
        if (ThreadLocalRandom.current().nextDouble() >= phantomChance) return;
        phantom.getScheduler().runDelayed(plugin, task -> {
            if (!phantom.isValid() || phantom.isDead()) return;
            try {
                String id = plugin.catalog().dolls().get(ThreadLocalRandom.current().nextInt(plugin.catalog().dolls().size()));
                ArmorStand stand = spawn(phantom.getLocation(), Items.create(id), null, true);
                if (!stand.isValid() || !phantom.addPassenger(stand)) { stand.remove(); return; }
                watch(stand);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "幻翼玩偶生成失败：" + phantom.getUniqueId(), exception);
            }
        }, () -> {}, 5);
    }

    private void watch(ArmorStand stand) {
        if (!ticking.add(stand.getUniqueId())) return;
        Motion motion = new Motion();
        var scheduled = stand.getScheduler().runAtFixedRate(plugin, task -> {
            if (!stand.isValid()) { ticking.remove(stand.getUniqueId()); task.cancel(); return; }
            try { tick(stand, motion); }
            catch (RuntimeException exception) {
                ticking.remove(stand.getUniqueId()); task.cancel();
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "玩偶运动已停止，实体保留：" + stand.getUniqueId(), exception);
            }
        }, () -> ticking.remove(stand.getUniqueId()), 1, 1);
        if (scheduled == null) ticking.remove(stand.getUniqueId());
    }

    private void tick(ArmorStand stand, Motion motion) {
        motion.ticks++;
        if (stand.getVehicle() != null) return;
        if (motion.ticks % 20 == 0 && lifetime > 0 && stand.getPersistentDataContainer().has(phantomLife)) {
            int age = stand.getPersistentDataContainer().getOrDefault(phantomLife, PersistentDataType.INTEGER, 0) + 20;
            if (age >= lifetime) { stand.remove(); return; }
            stand.getPersistentDataContainer().set(phantomLife, PersistentDataType.INTEGER, age);
        }
        Vector velocity = stand.getVelocity();
        if (stand.isOnGround()) {
            double bounce = DollPhysics.bounce(motion.falling);
            motion.falling = 0;
            if (bounce > 0) stand.setVelocity(new Vector(velocity.getX() * 0.6, bounce, velocity.getZ() * 0.6));
            motion.hits = 0;
            return;
        }
        motion.falling = velocity.getY();
        if (velocity.lengthSquared() < 0.0625) return;
        if (motion.ticks % 4 == 0 && plugin.getConfig().getBoolean("throw.particles", true)) stand.getWorld().spawnParticle(Particle.GLOW,
                stand.getLocation().add(0, 0.4, 0), 2, 0.1, 0.1, 0.1, 0.02);
        if (motion.ticks <= 2 || motion.hits >= 5 || motion.ticks % 2 != 0 || !plugin.getConfig().getBoolean("throw.knockback", true)) return;
        String owner = stand.getPersistentDataContainer().get(author, PersistentDataType.STRING);
        Player player = owner == null ? null : Bukkit.getPlayer(UUID.fromString(owner));
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) return;
        for (Entity target : Nearby.owned(stand.getLocation(), 0.8)) {
            if (!(target instanceof LivingEntity living) || target == stand || target == player || marked(target)
                    || !plugin.allows(player, Flag.DAMAGE_ENTITY, target) || !plugin.allows(player, Flag.INTERACT, target.getLocation())) continue;
            double force = DollPhysics.knockback(velocity.length(), Math.max(stand.getWidth(), stand.getHeight()), 1);
            Vector push = velocity.clone().normalize().multiply(force); push.setY(Math.max(0.2, push.getY() * 0.5));
            // Paper emits its knockback event, allowing combat plugins to veto the push.
            living.knockback(force, -push.getX(), -push.getZ());
            if (++motion.hits == 5) break;
        }
    }

    void start() {
        for (World world : Bukkit.getWorlds()) for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            Bukkit.getRegionScheduler().execute(plugin, world, chunk.getX(), chunk.getZ(), () -> {
                if (!chunk.isLoaded()) return;
                for (Entity entity : chunk.getEntities()) if (marked(entity)) watch((ArmorStand) entity);
            });
        }
    }
    private void restore(Charge charge) {
        Player player = charge.player;
        if (charge.item.equals(player.getInventory().getItem(charge.slot))) player.getInventory().setItem(charge.slot, charge.original);
    }

    private void clear(Charge charge) {
        boolean active = charge.player.hasActiveItem() && charge.item.isSimilar(charge.player.getActiveItem());
        restore(charge);
        if (active) charge.player.clearActiveItem();
    }

    private void cancel(Player player) {
        Charge charge = charges.remove(player.getUniqueId());
        if (charge != null) clear(charge);
    }

    void close() {
        var pending = List.copyOf(charges.values());
        charges.clear(); ticking.clear();
        for (Charge charge : pending) plugin.onPlayer(charge.player, () -> clear(charge));
    }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {
        Charge charge = charges.remove(event.getEntity().getUniqueId());
        if (charge == null) return;
        event.getDrops().replaceAll(item -> charge.item.isSimilar(item) ? charge.original.asQuantity(item.getAmount()) : item);
        clear(charge);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) { if (charges.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) { if (charges.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dropped(PlayerDropItemEvent event) { if (charges.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) { if (charges.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) { if (charges.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void consume(PlayerItemConsumeEvent event) {
        if (doll(event.getItem())) event.setCancelled(true);
    }
    private record Charge(Player player, int slot, ItemStack original, ItemStack item) {}
    private static final class Motion { int ticks; int hits; double falling; }
}
