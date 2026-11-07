package net.kaleidoscope.tavern;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class GrapeController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_tavern:grape_v1";
    private static final int[][] DIRECTIONS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private final TavernPlugin tavern;
    private GrapeGrowth growth = GrapeGrowth.empty();
    private UUID owner;
    private boolean failed;
    private int elapsed = ThreadLocalRandom.current().nextInt(GrapeGrowth.PULSE_TICKS);

    GrapeController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }
    @Override public boolean hasContents() { return growth.planted(); }
    @Override public void onLoad() { tavern.grapes.add(this); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) {
        if (player != null) owner = ((Player) player.platformPlayer()).getUniqueId();
        tavern.grapes.add(this);
        changed();
    }
    @Override public void onUnload() { tavern.grapes.remove(this); }
    @Override public void postRemove(net.momirealms.craftengine.core.entity.player.Player player) { tavern.grapes.remove(this); }

    @Override protected void interact(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = Items.id(held);
        if (!growth.planted()) {
            if (id.equals("minecraft:honeycomb") && !growth.waxed()) {
                if (!pay(player, held)) return;
                growth = new GrapeGrowth("none", 0, -1, true); changed(); return;
            }
            if (Tag.ITEMS_AXES.isTagged(held.getType()) && growth.waxed()) {
                growth = GrapeGrowth.empty(); changed(); held.damage(1, player); return;
            }
            if (!id.equals("kaleidoscope_tavern:grapevine") || growth.waxed()) return;
            Location below = location().subtract(0, 1, 0);
            if (!owned(below)) return;
            Material soil = below.getBlock().getType();
            String kind = Tag.DIRT.isTagged(soil) ? "grape" : Tag.ICE.isTagged(soil) || soil == Material.SNOW_BLOCK ? "ice_grape"
                    : soil == Material.NETHERRACK || soil == Material.MAGMA_BLOCK ? "gold_grape" : null;
            if (kind == null) { player.sendMessage("普通葡萄种在泥土上，冰葡萄种在冰雪上，金葡萄种在下界岩或岩浆块上。"); return; }
            if (!pay(player, held)) return;
            owner = player.getUniqueId();
            growth = new GrapeGrowth(kind, 0, -1, false);
            refresh();
            return;
        }
        if (held.getType() == Material.SHEARS && id.equals("minecraft:shears")) {
            if (growth.fruit() >= 0 && growth.fruit() < 5 && !player.isSneaking()) {
                player.sendMessage("葡萄尚未成熟；潜行使用剪刀可移除葡萄藤。"); return;
            }
            List<ItemStack> harvest = new ArrayList<>();
            boolean fruit = growth.fruit() == 5 && !player.isSneaking();
            if (fruit) {
                harvest.add(Items.create("kaleidoscope_tavern:" + growth.kind()).asQuantity(3));
                if (ThreadLocalRandom.current().nextDouble() < 0.3) harvest.add(Items.create("kaleidoscope_tavern:green_grape")
                        .asQuantity(ThreadLocalRandom.current().nextInt(1, 3)));
            } else harvest.add(Items.create("kaleidoscope_tavern:grapevine"));
            if (!InventoryTrade.exchange(player.getInventory(), s -> false, 0, harvest)) {
                player.sendMessage("背包空间不足，葡萄已保留。"); return;
            }
            growth = fruit ? growth.harvested() : GrapeGrowth.empty();
            ItemStack shears = player.getInventory().getItemInMainHand();
            shears.damage(1, player);
            player.getInventory().setItemInMainHand(shears);
            refresh();
            location().getWorld().playSound(location(), Sound.BLOCK_BEEHIVE_SHEAR, 1, 1);
            return;
        }
        if (id.equals("minecraft:bone_meal")) {
            Runnable grow = nextGrowth(player);
            if (grow != null && pay(player, held)) grow.run();
        }
    }

    private boolean pay(Player player, ItemStack held) {
        return player.getGameMode() == GameMode.CREATIVE || InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of());
    }

    private boolean owned(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4) && Bukkit.isOwnedByCurrentRegion(at);
    }

    private Runnable nextGrowth(Player actor) {
        if (growth.vine() < 3) return () -> { growth = growth.ageVine(); refresh(); };
        if (growth.fruit() >= 0) {
            if (growth.fruit() == 5) return null;
            return () -> { growth = growth.ageFruit(ThreadLocalRandom.current().nextInt(1, 3)); refresh(); };
        }
        for (int[] offset : DIRECTIONS) {
            GrapeController adjacent = tavern.grapes.at(location().add(offset[0], offset[1], offset[2]));
            // Automatic growth stays inside trellises placed by the same player.
            if (adjacent == null || owner == null || !owner.equals(adjacent.owner) || adjacent.growth.planted() || adjacent.growth.waxed()) continue;
            if (actor == null || !Bukkit.isOwnedByCurrentRegion(actor)
                    || !plugin.allows(actor, net.momirealms.antigrieflib.Flag.INTERACT, adjacent.location())) return null;
            return () -> {
                adjacent.growth = new GrapeGrowth(growth.kind(), offset[1] == 1 ? 0 : 3, -1, false);
                adjacent.refresh();
            };
        }
        Location below = location().subtract(0, 1, 0);
        if (!owned(below) || !below.getBlock().isEmpty() || tavern.grapes.at(below) != null) return null;
        return () -> { growth = growth.ageFruit(1); refresh(); };
    }

    private void refresh() {
        changed();
        if (!furniture().setVariant(growth.variant())) throw new IllegalStateException("Missing grape variant: " + growth.variant());
        failed = false;
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> {
            if (!operational() || failed || !growth.planted() || ++elapsed < GrapeGrowth.PULSE_TICKS) return;
            elapsed = 0;
            try { grow(); }
            catch (RuntimeException exception) {
                failed = true;
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "葡萄生长已停止，存档已保留：" + location(), exception);
            }
        });
    }

    private void grow() {
        Location below = location().subtract(0, 1, 0);
        if (growth.fruit() >= 0 && owned(below) && !below.getBlock().isEmpty()) {
            growth = growth.harvested(); refresh();
        }
        int speed = location().getWorld().getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
        double chance = growth.probability(location().getBlock().getTemperature(), speed);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        Runnable grow = nextGrowth(owner == null ? null : Bukkit.getPlayer(owner));
        if (grow != null) grow.run();
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putString("kind", growth.kind()); saved.putInt("vine", growth.vine());
        saved.putInt("fruit", growth.fruit()); saved.putBoolean("waxed", growth.waxed());
        if (owner != null) saved.putString("owner", owner.toString());
        root.put(SAVE_KEY, SavedState.envelope(SavedState.namespace(SAVE_KEY), 1, SavedState.SERIES_SOURCE, saved));
    }
    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> {
            if (!root.containsKey(SAVE_KEY)) return;
            CompoundTag saved = SavedState.payload(root.getCompound(SAVE_KEY), SavedState.namespace(SAVE_KEY),
                    SavedState.SERIES_SOURCE, 1, null);
            if (saved == null) throw new IllegalArgumentException("Invalid grape save");
            growth = new GrapeGrowth(SavedState.text(saved, "kind"), SavedState.integer(saved, "vine"),
                    SavedState.integer(saved, "fruit"), SavedState.flag(saved, "waxed"));
            String author = SavedState.optionalText(saved, "owner");
            owner = author == null ? null : UUID.fromString(author);
        });
    }
}
