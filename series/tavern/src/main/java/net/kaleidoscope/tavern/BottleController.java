package net.kaleidoscope.tavern;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedItems;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class BottleController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_tavern:bottle_v1";
    private final TavernPlugin tavern;
    private ItemStack filled;

    BottleController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }
    String carrier() { return furniture().id().asString(); }
    boolean empty() { return filled == null && !blocked(); }
    @Override public boolean hasContents() { return filled != null; }
    @Override public void onLoad() { tavern.bottles.add(this); if (!blocked()) refreshBottle(); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) { tavern.bottles.add(this); if (!blocked()) refreshBottle(); }
    @Override public void onUnload() { tavern.bottles.remove(this); }
    @Override public void postRemove(net.momirealms.craftengine.core.entity.player.Player player) { tavern.bottles.remove(this); }

    void fill(ItemStack drink) {
        if (!empty() || drink == null || drink.getAmount() != 1) throw new IllegalStateException("Bottle is not empty");
        filled = drink.clone();
        changed();
    }

    void refreshBottle() {
        String variant = filled == null ? "ground" : Items.id(filled).replace(':', '/');
        if (!furniture().setVariant(variant)) plugin.getLogger().warning("Missing bottle display variant: " + variant);
    }

    @Override protected void interact(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (filled != null) {
            if (!Items.id(held).equals(carrier())) { player.sendMessage("手持同类空瓶或空杯换取成品；满瓶已保存。"); return; }
            if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(filled))) {
                player.sendMessage("背包空间不足。"); return;
            }
            filled = null;
            changed();
            refreshBottle();
            return;
        }
        if (!carrier().equals("kaleidoscope_tavern:empty_glassware") || !Items.id(held).equals("kaleidoscope_tavern:shaker")) return;
        ShakerContents contents = tavern.shakers().contents(held);
        if (contents.cocktail == null) return;
        ItemStack empty = tavern.shakers().pack(held, new ShakerContents(new ItemStack[3], null));
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(empty))) return;
        fill(contents.cocktail);
        refreshBottle();
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        SavedItems.put(saved, "filled", filled);
        root.put(SAVE_KEY, SavedState.envelope(SavedState.namespace(SAVE_KEY), 1, SavedState.SERIES_SOURCE, saved));
    }
    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> {
            if (!root.containsKey(SAVE_KEY)) return;
            CompoundTag saved = SavedState.payload(root.getCompound(SAVE_KEY), SavedState.namespace(SAVE_KEY),
                    SavedState.SERIES_SOURCE, 1, null);
            if (saved == null) throw new IllegalArgumentException("Invalid bottle save");
            filled = SavedItems.get(saved, "filled", 1);
            if (filled != null && !tavern.catalog().effects().containsKey(Items.id(filled))
                    && !Items.id(filled).equals("kaleidoscope_tavern:signature_cocktail")
                    && !TapSources.accepts(Items.id(filled), filled)) throw new IllegalArgumentException("Unknown bottled drink");
        });
    }
}
