package net.kaleidoscope.doll;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.Menu;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class DollController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_doll:pose_v1";
    private static final String STATE_NAMESPACE = "kaleidoscope_doll:pose";
    private static final String[] AXIS_NAMES = {"宽度", "高度", "深度", "左右", "上下", "前后", "俯仰", "转向", "侧倾"};
    private DollPose pose = DollPose.origin();
    private final DollDisplay display = new DollDisplay(this);
    public DollController(Furniture furniture, DollPlugin plugin) { super(furniture, plugin); }

    DollPose pose() { return pose; }

    @Override public void gatherElements(java.util.function.Consumer<FurnitureElement> elements) { elements.accept(display); }
    @Override public void onLoad() { display.rebuild(); }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) { display.rebuild(); }

    @Override protected void interact(Player player) {
        if (Items.id(player.getInventory().getItemInMainHand()).equals("kaleidoscope_doll:tweaks_tool")) {
            editPose(player);
            return;
        }
        var random = ThreadLocalRandom.current();
        var position = location().add(random.nextDouble(-0.25, 0.25), 1 + random.nextDouble(0.2), random.nextDouble(-0.25, 0.25));
        position.getWorld().spawnParticle(Particle.NOTE, position, 0, random.nextInt(4) / 24.0, 0, 0, 1);
        position.getWorld().playSound(position, "kaleidoscope_doll:duck_toy", SoundCategory.BLOCKS, 1, random.nextFloat(0.75f, 1.25f));
    }

    private void editPose(Player player) {
        if (!accessible(player) || blocked()) return;
        Menu menu = new Menu(player, plugin, "玩偶微调 · 显示姿态", 4);
        for (int i = 0; i < DollPose.AXES; i++) {
            int axis = i;
            String name = AXIS_NAMES[i] + " " + String.format(java.util.Locale.ROOT, "%.2f", pose.value(i));
            menu.button(i, Items.icon(Material.PAPER, name), null);
            menu.button(i + 9, Items.icon(Material.RED_DYE, "减少" + AXIS_NAMES[i]), visitor -> adjust(visitor, axis, -1));
            menu.button(i + 18, Items.icon(Material.LIME_DYE, "增加" + AXIS_NAMES[i]), visitor -> adjust(visitor, axis, 1));
        }
        menu.button(31, Items.icon(Material.MILK_BUCKET, "重置姿态"), visitor -> {
            if (!canEdit(visitor)) return;
            pose = DollPose.origin();
            refreshPose(visitor);
        });
        menu.open(player);
    }

    private boolean canEdit(Player player) {
        return accessible(player) && !blocked() && Items.id(player.getInventory().getItemInMainHand()).equals("kaleidoscope_doll:tweaks_tool");
    }

    private void adjust(Player player, int axis, int direction) {
        if (!canEdit(player)) { player.closeInventory(); return; }
        pose = pose.adjust(axis, direction);
        refreshPose(player);
    }

    private void refreshPose(Player player) {
        changed();
        display.rebuild();
        editPose(player);
    }

    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        saved.putString("pose", pose.encode());
        root.put(SAVE_KEY, SavedState.envelope(STATE_NAMESPACE, 1, SavedState.SERIES_SOURCE, saved));
    }

    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> {
            if (!root.containsKey(SAVE_KEY)) return;
            CompoundTag saved = SavedState.payload(root.getCompound(SAVE_KEY), STATE_NAMESPACE,
                    SavedState.SERIES_SOURCE, 1, null);
            if (saved == null || SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Invalid doll pose save");
            pose = DollPose.decode(SavedState.text(saved, "pose"));
        });
    }
}
