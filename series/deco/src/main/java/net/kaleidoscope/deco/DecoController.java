package net.kaleidoscope.deco;

import net.kaleidoscope.series.InteractiveFurniture;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.ItemState;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

final class DecoController extends InteractiveFurniture {
    private static final String SAVE_KEY = "kaleidoscope_deco:state_v1";
    private static final List<String> MODES = List.of("single", "left", "right", "middle");
    private static final Set<String> COLORS = Set.of("none", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private final DecoPlugin deco;
    private String state;
    private String color = "none";
    private String mode = "single";

    DecoController(Furniture furniture, DecoPlugin plugin) {
        super(furniture, plugin); deco = plugin; state = furniture.id().asString();
    }
    private boolean sofa() { return state.equals("kaleidoscope_deco:kaleidoscope_bar/sofa"); }
    private String variant() { return state.replace(':', '/') + (sofa() ? "/style/" + color + "/" + mode : ""); }

    private DecoRule rule(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = Items.id(held);
        return deco.entry(state).rules().stream().filter(rule -> rule.matches(id, player.isSneaking(), name -> {
            if (!id.startsWith("minecraft:")) return false;
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(name), Material.class);
            return tag != null && tag.isTagged(held.getType());
        })).findFirst().orElse(null);
    }

    private boolean sofaAction(Player player) {
        if (!sofa()) return false;
        String id = Items.id(player.getInventory().getItemInMainHand());
        return id.equals("kaleidoscope_deco:state_tool") || id.equals("minecraft:water_bucket")
                || id.startsWith("minecraft:") && id.endsWith("_dye") && COLORS.contains(id.substring(10, id.length() - 4));
    }

    @Override protected boolean consumesInteraction(Player player) { return sofaAction(player) || rule(player) != null; }

    @Override protected void interact(Player player) {
        if (sofaAction(player)) { style(player); return; }
        DecoRule rule = rule(player);
        if (rule == null) return;
        if (rule.pending()) { player.sendMessage("这件家具的特殊事件尚未移植，当前状态已保留。"); return; }
        if (!rule.target().isEmpty()) {
            String previous = state;
            state = rule.target();
            if (!furniture().setVariant(variant())) { state = previous; throw new IllegalStateException("Missing Deco variant: " + rule.target()); }
            changed();
            saveSource();
        }
        for (String sound : rule.sounds()) location().getWorld().playSound(location(), sound.replace("kaleidoscope:", "kdeco_kaleidoscope:"), SoundCategory.BLOCKS, 1, 1);
    }

    private void style(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = Items.id(held), nextColor = color, nextMode = mode;
        int cost = 0;
        if (id.equals("kaleidoscope_deco:state_tool")) nextMode = MODES.get((MODES.indexOf(mode) + 1) % MODES.size());
        else if (id.equals("minecraft:water_bucket")) nextColor = "none";
        else { nextColor = id.substring(10, id.length() - 4); cost = player.getGameMode() == GameMode.CREATIVE ? 0 : 1; }
        if (color.equals(nextColor) && mode.equals(nextMode)) return;
        String nextVariant = state.replace(':', '/') + "/style/" + nextColor + "/" + nextMode;
        // Validate the generated visual before taking a dye.
        if (!deco.styles().contains(nextVariant)) throw new IllegalStateException("Missing sofa style: " + nextVariant);
        if (!InventoryTrade.exchange(player.getInventory(), held::isSimilar, cost, List.of())) return;
        color = nextColor; mode = nextMode;
        changed();
        saveSource();
        if (!furniture().setVariant(nextVariant)) throw new IllegalStateException("CraftEngine rejected sofa variant: " + nextVariant);
        location().getWorld().playSound(location(), org.bukkit.Sound.ENTITY_LLAMA_SWAG, 1, 1);
    }

    private void saveSource() {
        if (blocked()) return;
        CompoundTag saved = new CompoundTag(); saveCustomData(saved);
        ItemStack original = furniture().sourceItem() == null ? Items.create(furniture().id().asString())
                : ItemStackUtils.getBukkitStack(furniture().sourceItem()).asQuantity(1);
        ItemStack item = ItemState.write(original, deco.stateKey(), saved);
        furniture().setSourceItem(BukkitItemManager.instance().wrap(item));
    }
    @Override public void onLoad() { if (!blocked()) { furniture().setVariant(variant()); saveSource(); } }
    @Override public void onPlace(net.momirealms.craftengine.core.entity.player.Player player) { onLoad(); }
    @Override public void loadCustomDataFromItem(net.momirealms.craftengine.core.item.Item item) {
        ItemStack original = ItemStackUtils.getBukkitStack(item);
        CompoundTag root = new CompoundTag(), rejected = new CompoundTag();
        net.kaleidoscope.series.SavedItems.put(rejected, "source_item", original);
        root.put(SAVE_KEY, rejected);
        restoreSafely(root, SAVE_KEY, () -> {
            CompoundTag saved = ItemState.read(original, deco.stateKey());
            if (saved != null) loadCustomData(saved);
        });
    }
    @Override public void saveCustomData(CompoundTag root) {
        if (preserveRejected(root)) return;
        CompoundTag saved = new CompoundTag(); saved.putString("state", state);
        saved.putString("color", color); saved.putString("mode", mode);
        root.put(SAVE_KEY, saved);
    }
    @Override public void loadCustomData(CompoundTag root) {
        restoreSafely(root, SAVE_KEY, () -> {
            if (!root.containsKey(SAVE_KEY)) return;
            CompoundTag saved = root.getCompound(SAVE_KEY);
            if (saved == null) throw new IllegalArgumentException("Invalid Deco save");
            state = SavedState.text(saved, "state"); color = SavedState.text(saved, "color"); mode = SavedState.text(saved, "mode");
            if (!COLORS.contains(color) || !MODES.contains(mode) || !deco.reachable(furniture().id().asString(), state))
                throw new IllegalArgumentException("Unknown or unrelated Deco state");
        });
    }
}
