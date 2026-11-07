package net.kaleidoscope.tavern;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedState;
import net.kaleidoscope.series.SavedItems;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PressingController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_tavern:pressing_v1";
    private final TavernPlugin tavern;
    private final Map<UUID, Float> falls = new HashMap<>();
    private ItemStack ingredients;
    private String fluid;
    private int amount;

    public PressingController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }

    @Override public boolean hasContents() { return ingredients != null || amount > 0; }

    @Override protected void interact(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = Items.id(held);
        if (held.isEmpty()) {
            if (player.isSneaking() && ingredients != null && InventoryTrade.give(player.getInventory(), ingredients)) {
                ingredients = null;
                changed();
            }
            player.sendMessage("果盆：" + amount + "/1000 mB；投料后站进果盆跳踩，装满后用桶取汁。");
            return;
        }
        if (id.equals("minecraft:bucket")) {
            if (amount < 1000) { player.sendMessage("果汁尚未装满一桶。"); return; }
            ItemStack bucket = Items.create(tavern.catalog().buckets().get(fluid));
            if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(bucket))) return;
            amount = 0;
            fluid = null;
            changed();
            return;
        }
        if (ingredients != null && !ingredients.isSimilar(held)) { player.sendMessage("请先空手潜行取出原料。"); return; }
        int stored = ingredients == null ? 0 : ingredients.getAmount();
        int accepted = Math.min(held.getAmount(), Math.min(64, held.getMaxStackSize()) - stored);
        if (accepted == 0) return;
        ItemStack filled = held.asQuantity(stored + accepted);
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, accepted, List.of())) return;
        ingredients = filled;
        changed();
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> tick());
    }

    private void tick() {
        if (!operational() || ingredients == null || amount >= 1000) { falls.clear(); return; }
        var center = location();
        Set<UUID> nearby = new HashSet<>();
        for (var viewer : furniture().trackedBy()) {
            Player player = (Player) viewer.platformPlayer();
            if (!Bukkit.isOwnedByCurrentRegion(player)) continue;
            var feet = player.getLocation();
            if (!feet.getWorld().equals(center.getWorld())) continue;
            if (Math.abs(feet.getX() - center.getX()) > 0.6 || Math.abs(feet.getZ() - center.getZ()) > 0.6) continue;
            UUID id = player.getUniqueId();
            nearby.add(id);
            float previous = falls.getOrDefault(id, 0f);
            if (player.isOnGround()) {
                falls.remove(id);
                if (previous >= 0.5f && Math.abs(feet.getY() - center.getY()) <= 0.6 && accessible(player)) press();
            } else falls.put(id, Math.max(previous, player.getFallDistance()));
        }
        falls.keySet().retainAll(nearby);
    }

    private void press() {
        if (ingredients == null || amount >= 1000) return;
        String id = Items.id(ingredients);
        PressingRecipe recipe = tavern.catalog().pressing().stream().filter(r -> r.ingredients().contains(id)).findFirst().orElse(null);
        if (recipe == null || (fluid != null && !fluid.equals(recipe.fluid()))) return;
        fluid = recipe.fluid();
        amount = Math.min(1000, amount + recipe.amount());
        ingredients.subtract();
        if (ingredients.isEmpty()) ingredients = null;
        changed();
        location().getWorld().playSound(location(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.6f, 1);
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        saved.putInt("amount", amount);
        if (fluid != null) saved.putString("fluid", fluid);
        SavedItems.put(saved, "ingredients", ingredients);
        root.put(SAVE_KEY, SavedState.envelope(SavedState.namespace(SAVE_KEY), 1, SavedState.SERIES_SOURCE, saved));
    }

    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> restore(root));
    }

    private void restore(CompoundTag root) {
        CompoundTag saved = SavedState.payload(root.getCompound(SAVE_KEY), SavedState.namespace(SAVE_KEY),
                SavedState.SERIES_SOURCE, 1, null);
        if (saved == null) {
            if (root.containsKey(SAVE_KEY)) throw new IllegalArgumentException("Invalid pressing tub save tag");
            return;
        }
        if (SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Unsupported pressing tub save version");
        amount = SavedState.integer(saved, "amount");
        fluid = SavedState.optionalText(saved, "fluid");
        if (fluid != null && fluid.isEmpty()) throw new IllegalArgumentException("Empty saved pressing fluid");
        if (amount < 0 || amount > 1000 || (amount > 0) != (fluid != null)
                || (fluid != null && !tavern.catalog().buckets().containsKey(fluid))) throw new IllegalArgumentException("Invalid saved pressing fluid");
        ingredients = SavedItems.get(saved, "ingredients", 64);
    }
}
