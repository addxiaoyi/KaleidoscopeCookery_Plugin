package net.kaleidoscope.doll;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.entity.furniture.tick.FurnitureTicker;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LotteryController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_doll:lottery_v1";
    private static final int DRAW_TICKS = 60;
    private final DollPlugin dolls;
    private final List<DrawBag> bags;
    private String pending;
    private int tier;
    private int ticks;
    private boolean outputFailed;

    public LotteryController(Furniture furniture, DollPlugin plugin) {
        super(furniture, plugin);
        dolls = plugin;
        bags = plugin.catalog().tiers().stream().map(DrawBag::new).toList();
    }

    @Override protected void interact(Player player) {
        if (pending != null) {
            outputFailed = false;
            player.sendMessage("抽奖正在进行，奖品会从机器掉出。");
            return;
        }
        int selected = dolls.catalog().tokens().indexOf(Items.id(player.getInventory().getItemInMainHand()));
        if (selected < 0) { player.sendMessage("投入铁锭、金锭或钻石，抽取对应档位的玩偶。"); return; }
        DrawBag bag = bags.get(selected);
        String candidate = bag.peek(ThreadLocalRandom.current());
        dolls.gifts().create(selected, candidate);
        String token = dolls.catalog().tokens().get(selected);
        if (!InventoryTrade.exchange(player.getInventory(), s -> Items.id(s).equals(token), 1, List.of())) return;
        bag.take(candidate);
        pending = candidate;
        tier = selected;
        ticks = DRAW_TICKS;
        outputFailed = false;
        changed();
        location().getWorld().playSound(location(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 2);
    }

    @Override public <T extends FurnitureController> FurnitureTicker<T> createFurnitureTicker() {
        return createTickerHelper((f, controller) -> tick());
    }

    private void tick() {
        if (!operational() || pending == null || outputFailed) return;
        if (ticks > 0) { ticks--; changed(); return; }
        try {
            var gift = dolls.gifts().create(tier, pending);
            var drop = location().getWorld().dropItemNaturally(location().add(0, 1.8, 0), gift);
            // EntitySpawnEvent cancellation must not consume an already paid prize.
            if (!drop.isValid()) { ticks = 20; return; }
            pending = null;
            changed();
            location().getWorld().playSound(location(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        } catch (RuntimeException exception) {
            outputFailed = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "抽奖机产出失败，奖品已保留：" + location(), exception);
        }
    }

    @Override public boolean hasContents() { return pending != null; }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        for (int i = 0; i < bags.size(); i++) saved.putString("tier_" + i, String.join("\n", bags.get(i).snapshot()));
        if (pending != null) {
            saved.putString("pending", pending);
            saved.putInt("tier", tier);
            saved.putInt("ticks", ticks);
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
            if (root.containsKey(SAVE_KEY)) throw new IllegalArgumentException("Invalid lottery save tag");
            return;
        }
        if (SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Unsupported lottery save version");
        for (int i = 0; i < bags.size(); i++) {
            String queue = SavedState.text(saved, "tier_" + i);
            bags.get(i).restore(queue == null || queue.isEmpty() ? List.of() : Arrays.asList(queue.split("\n")));
        }
        pending = SavedState.optionalText(saved, "pending");
        if (pending == null) return;
        if (pending.isEmpty()) throw new IllegalArgumentException("Empty pending lottery prize");
        tier = SavedState.integer(saved, "tier");
        ticks = SavedState.integer(saved, "ticks");
        if (tier < 0 || tier >= bags.size() || ticks < 0 || ticks > DRAW_TICKS
                || !dolls.catalog().tiers().get(tier).contains(pending)) throw new IllegalArgumentException("Invalid pending lottery prize");
    }
}
