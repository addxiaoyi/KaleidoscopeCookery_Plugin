package net.kaleidoscope.tavern;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedItems;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ShakerController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_tavern:shaker_v1";
    private final TavernPlugin tavern;
    private final ItemStack[] slots = new ItemStack[3];
    private ItemStack cocktail;
    private UUID bartender;
    private int ticks;
    private boolean mixing;

    public ShakerController(Furniture furniture, TavernPlugin plugin) { super(furniture, plugin); tavern = plugin; }

    @Override public boolean hasContents() { return cocktail != null || Arrays.stream(slots).anyMatch(Objects::nonNull); }

    @Override protected void interact(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!mixing && player.isSneaking() && Items.id(held).equals("kaleidoscope_tavern:shaker")) {
            transfer(player, held);
            return;
        }
        if (cocktail != null) {
            if (!Items.id(held).equals("kaleidoscope_tavern:empty_glassware")) {
                player.sendMessage("调酒完成，手持空鸡尾酒杯取酒。"); return;
            }
            Runnable empty = prepareContents(new ShakerContents(new ItemStack[3], null));
            if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(cocktail))) {
                player.sendMessage("背包空间不足，鸡尾酒已保留。"); return;
            }
            empty.run();
            return;
        }
        if (mixing) {
            if (!player.getUniqueId().equals(bartender)) { player.sendMessage("另一位玩家正在调酒。"); return; }
            finish();
            player.sendMessage(cocktail == null ? "摇动不足 19 tick，原料仍在杯中。" : "调酒完成，请用空鸡尾酒杯取酒。");
            return;
        }
        if (held.isEmpty()) {
            if (Arrays.stream(slots).anyMatch(Objects::isNull)) { player.sendMessage("先放入三份原料；酒类需要熟成 4 级以上。"); return; }
            bartender = player.getUniqueId();
            mixing = true;
            ticks = 0;
            player.sendMessage("开始摇动：再次右键停止。69–88 tick 为特调，89–98 tick 尝试配方，超时会失败。");
            return;
        }
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == null) { index = i; break; }
        if (index == -1) { player.sendMessage("原料已满，空手右键开始调酒。"); return; }
        if (tavern.drinks().isBottled(Items.id(held)) && tavern.brewLevel(held) < 4) {
            player.sendMessage("酿造酒需要熟成 4 级以上才能调酒。"); return;
        }
        String carrier = tavern.drinks().container(held);
        List<ItemStack> returned = carrier == null ? List.of() : List.of(Items.create(carrier));
        ItemStack ingredient = held.asQuantity(1);
        ItemStack[] nextSlots = slots.clone();
        nextSlots[index] = ingredient;
        Runnable insert = prepareContents(new ShakerContents(nextSlots, null));
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, returned)) {
            player.sendMessage("背包没有空位返还容器。"); return;
        }
        insert.run();
        location().getWorld().playSound(location(), Sound.ITEM_BOTTLE_EMPTY, 0.75f, 1);
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> tick());
    }

    private void tick() {
        if (!operational() || !mixing) return;
        ticks++;
        if (ticks % 10 == 0) location().getWorld().playSound(location(), Sound.ITEM_BOTTLE_FILL, 0.5f, 1.2f);
        if (ticks >= ShakerTiming.TIMEOUT) {
            try { finish(); }
            catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "调酒产出失败，原料已保留：" + location(), exception);
            }
        }
    }

    private void finish() {
        mixing = false;
        bartender = null;
        prepareContents(new ShakerContents(slots, cocktail).mix(tavern, ticks)).run();
    }

    private void transfer(Player player, ItemStack held) {
        if (held.getAmount() != 1) { player.sendMessage("请只拿一个雪克杯进行转装。"); return; }
        ShakerContents carried = tavern.shakers().contents(held);
        ShakerContents placed = new ShakerContents(slots, cocktail);
        if (!carried.empty() && !placed.empty()) { player.sendMessage("两个雪克杯都有内容，请先腾空一个。"); return; }
        Runnable replace = prepareContents(carried);
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, 1, List.of(tavern.shakers().pack(held, placed)))) return;
        replace.run();
        player.sendMessage("雪克杯内容已转装；手持右键摇动，松开停止，潜行右键方块放置。");
    }

    private void syncSource() {
        if (blocked()) return;
        prepareContents(new ShakerContents(slots, cocktail)).run();
    }

    private Runnable prepareContents(ShakerContents next) {
        ItemStack source = furniture().sourceItem() == null ? Items.create("kaleidoscope_tavern:shaker")
                : net.momirealms.craftengine.bukkit.util.ItemStackUtils.getBukkitStack(furniture().sourceItem());
        // Serialize before payment so an oversized or invalid item cannot leave a stale furniture drop.
        var packed = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(tavern.shakers().pack(source, next));
        return () -> {
            System.arraycopy(next.slots, 0, slots, 0, slots.length);
            cocktail = next.cocktail;
            furniture().setSourceItem(packed);
            changed();
        };
    }
    @Override public void onLoad() { syncSource(); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) { syncSource(); }

    @Override public void loadCustomDataFromItem(net.momirealms.craftengine.core.item.Item item) {
        ItemStack original = net.momirealms.craftengine.bukkit.util.ItemStackUtils.getBukkitStack(item);
        CompoundTag root = new CompoundTag(), rejected = new CompoundTag();
        SavedItems.put(rejected, "source_item", original);
        root.put(SAVE_KEY, rejected);
        restoreSafely(root, SAVE_KEY, () -> {
            ShakerContents carried = tavern.shakers().contents(original);
            System.arraycopy(carried.slots, 0, slots, 0, slots.length);
            cocktail = carried.cocktail;
        });
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        for (int i = 0; i < slots.length; i++) SavedItems.put(saved, "slot_" + i, slots[i]);
        SavedItems.put(saved, "cocktail", cocktail);
        root.put(SAVE_KEY, saved);
    }

    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> {
            if (!root.containsKey(SAVE_KEY)) return;
            CompoundTag saved = root.getCompound(SAVE_KEY);
            if (saved == null || SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Invalid shaker save");
            for (int i = 0; i < slots.length; i++) slots[i] = SavedItems.get(saved, "slot_" + i, 1);
            cocktail = SavedItems.get(saved, "cocktail", 1);
            if (cocktail != null && Arrays.stream(slots).anyMatch(Objects::isNull)) throw new IllegalArgumentException("Incomplete shaker recipe state");
        });
    }
}
