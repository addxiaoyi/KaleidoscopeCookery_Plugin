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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class BarrelController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_tavern:barrel_v1";
    private final TavernPlugin tavern;
    private final ItemStack[] slots = new ItemStack[4];
    private boolean open = true;
    private String fluid;
    private int buckets;
    private int checkTicks;
    private BrewBatch batch;
    private boolean failed;

    public BarrelController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }

    @Override public void onLoad() { tavern.barrels.add(this); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) { tavern.barrels.add(this); }
    @Override public void onUnload() { tavern.barrels.remove(this); }
    @Override public void postRemove(net.momirealms.craftengine.core.entity.player.Player player) { tavern.barrels.remove(this); }

    boolean canBottle(String carrier) { return operational() && batch != null && batch.recipe().carriers().contains(carrier); }
    void fill(BottleController destination) {
        if (!canBottle(destination.carrier()) || !destination.empty()) return;
        ItemStack drink = tavern.bottle(batch);
        destination.fill(drink);
        batch.bottle();
        if (batch.servings() == 0) { batch = null; open = true; }
        changed();
    }

    @Override public boolean hasContents() {
        return batch != null || buckets > 0 || Arrays.stream(slots).anyMatch(Objects::nonNull);
    }

    @Override protected void interact(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = Items.id(held);
        if (batch != null) { extract(player, held, id); return; }
        if (held.isEmpty()) {
            if (player.isSneaking()) {
                open = !open;
                failed = false;
                changed();
                player.sendMessage(open ? "酒桶已开盖。" : "酒桶已封盖，装满 4 桶液体后开始发酵。");
            } else if (open) takeIngredient(player);
            else status(player);
            return;
        }
        if (!open) { player.sendMessage("先空手潜行右键开盖。"); return; }
        String incomingFluid = tavern.catalog().fluidForBucket(id);
        if (incomingFluid != null || id.equals("minecraft:bucket")) { transferFluid(player, held, incomingFluid); return; }
        if (buckets < 4) { player.sendMessage("先加入 4 桶同种液体，再放原料。"); return; }
        addIngredient(player, held);
    }

    private void transferFluid(Player player, ItemStack held, String incoming) {
        if (Arrays.stream(slots).anyMatch(Objects::nonNull)) { player.sendMessage("请先取出原料。"); return; }
        if (incoming == null) {
            if (buckets == 0) return;
            ItemStack filled = Items.create(tavern.catalog().buckets().get(fluid));
            if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(filled))) return;
            buckets--;
            if (buckets == 0) fluid = null;
        } else {
            if (buckets == 4 || (fluid != null && !fluid.equals(incoming))) { player.sendMessage("酒桶已满或液体不同。"); return; }
            if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(Items.create("minecraft:bucket")))) return;
            fluid = incoming;
            buckets++;
        }
        changed();
        status(player);
    }

    private void addIngredient(Player player, ItemStack held) {
        int slot = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i].isSimilar(held) && slots[i].getAmount() < Math.min(16, held.getMaxStackSize())) { slot = i; break; }
        }
        if (slot < 0) for (int i = 0; i < slots.length; i++) if (slots[i] == null) { slot = i; break; }
        if (slot < 0) { player.sendMessage("4 个原料槽已满。"); return; }
        int stored = slots[slot] == null ? 0 : slots[slot].getAmount();
        int accepted = Math.min(held.getAmount(), Math.min(16, held.getMaxStackSize()) - stored);
        ItemStack filled = held.asQuantity(stored + accepted);
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, accepted, List.of())) return;
        slots[slot] = filled;
        changed();
        player.sendMessage("已放入 " + accepted + " 个原料；空手潜行右键封盖。");
    }

    private void takeIngredient(Player player) {
        for (int i = slots.length - 1; i >= 0; i--) {
            if (slots[i] == null) continue;
            if (InventoryTrade.give(player.getInventory(), slots[i])) { slots[i] = null; changed(); }
            else player.sendMessage("背包空间不足。");
            return;
        }
        status(player);
    }

    private void extract(Player player, ItemStack held, String id) {
        if (!batch.recipe().carriers().contains(id)) { status(player); return; }
        ItemStack filled = tavern.bottle(batch);
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(filled))) {
            player.sendMessage("背包空间不足，酒液已保留。"); return;
        }
        batch.bottle();
        if (batch.servings() == 0) { batch = null; open = true; }
        changed();
    }

    private void status(Player player) {
        if (batch == null) player.sendMessage("酒桶：" + buckets + "/4 桶液体，" + (open ? "开盖" : "封盖"));
        else player.sendMessage("熟成 " + batch.level() + "/6 · 剩余 " + batch.servings() + " 瓶；手持空酒瓶右键取酒。");
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> tick());
    }

    private void tick() {
        if (!operational() || open || failed || (batch != null && batch.level() == BrewBatch.MAX_LEVEL)) return;
        if (++checkTicks < BrewBatch.CHECK_INTERVAL) return;
        checkTicks = 0;
        if (batch != null) { batch.pulse(); changed(); return; }
        if (buckets != 4) return;
        List<String> ids = Arrays.stream(slots).filter(Objects::nonNull).map(Items::id).toList();
        BrewRecipe recipe = tavern.catalog().brew(fluid, ids);
        int servings = recipe.id().equals("kaleidoscope_tavern:empty") ? 16
                : Arrays.stream(slots).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).min().orElse(16);
        try {
            Items.create(recipe.output());
            batch = new BrewBatch(recipe, servings);
            Arrays.fill(slots, null);
            buckets = 0;
            fluid = null;
            changed();
        } catch (RuntimeException exception) {
            failed = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "酒桶启动失败，原料已保留：" + location(), exception);
        }
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        saved.putBoolean("open", open);
        saved.putInt("buckets", buckets);
        saved.putInt("check_ticks", checkTicks);
        if (fluid != null) saved.putString("fluid", fluid);
        for (int i = 0; i < slots.length; i++) SavedItems.put(saved, "slot_" + i, slots[i]);
        if (batch != null) {
            saved.putString("recipe", new com.google.gson.Gson().toJson(batch.recipe()));
            saved.putInt("servings", batch.servings());
            saved.putInt("level", batch.level());
            saved.putLong("remaining", batch.remaining());
        }
        root.put(SAVE_KEY, SavedState.envelope(SavedState.namespace(SAVE_KEY), 1, SavedState.SERIES_SOURCE, saved));
    }

    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> restore(root));
    }

    private void restore(CompoundTag root) {
        CompoundTag saved = SavedState.payload(root.getCompound(SAVE_KEY), SavedState.namespace(SAVE_KEY),
                SavedState.SERIES_SOURCE, 1, null);
        if (saved == null) {
            if (root.containsKey(SAVE_KEY)) throw new IllegalArgumentException("Invalid barrel save tag");
            return;
        }
        if (SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Unsupported barrel save version");
        open = SavedState.flag(saved, "open");
        buckets = SavedState.integer(saved, "buckets");
        checkTicks = SavedState.integer(saved, "check_ticks");
        fluid = SavedState.optionalText(saved, "fluid");
        if (fluid != null && fluid.isEmpty()) throw new IllegalArgumentException("Empty saved barrel fluid");
        if (buckets < 0 || buckets > 4 || (buckets > 0) != (fluid != null)
                || (fluid != null && !tavern.catalog().buckets().containsKey(fluid))
                || checkTicks < 0 || checkTicks >= BrewBatch.CHECK_INTERVAL) throw new IllegalArgumentException("Invalid saved barrel fluid/timer");
        for (int i = 0; i < slots.length; i++) slots[i] = SavedItems.get(saved, "slot_" + i, 16);
        String recipe = SavedState.optionalText(saved, "recipe");
        if (recipe != null) {
            if (recipe.isEmpty()) throw new IllegalArgumentException("Empty saved barrel recipe");
            batch = new BrewBatch(new com.google.gson.Gson().fromJson(recipe, BrewRecipe.class),
                    SavedState.integer(saved, "servings"), SavedState.integer(saved, "level"), SavedState.longValue(saved, "remaining"));
            if (open || buckets != 0 || Arrays.stream(slots).anyMatch(Objects::nonNull)) throw new IllegalArgumentException("Conflicting saved barrel contents");
        }
    }
}
