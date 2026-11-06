package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import net.kaleidoscope.cookery.recipe.BambooTrayRecipe;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.world.WorldlyContainerHolder;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.inventory.CraftInventoryProxy;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class BambooTrayController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:bamboo_tray";
    private static final int SCHEMA_VERSION = 1;
    private static final int PROCESS_INTERVAL = 19;

    private final Key trayId;
    private final BambooTrayProgress[] progress = new BambooTrayProgress[BambooTrayContainer.SLOTS];
    private final Item[] processingItems = new Item[BambooTrayContainer.SLOTS];
    private final Set<Key> invalidOutputs = new HashSet<>();
    private final BambooTrayContainer container;
    private final Object minecraftContainer;
    private final BambooTrayElement element;
    private boolean internalMutation;
    private boolean displayDirty = true;
    private boolean removed;

    public BambooTrayController(BlockEntity blockEntity, Key trayId) {
        super(blockEntity);
        this.trayId = Objects.requireNonNull(trayId, "trayId");
        Arrays.setAll(progress, ignored -> new BambooTrayProgress());
        Arrays.fill(processingItems, Item.empty());
        WorldlyContainerHolder holder = new WorldlyContainerHolder(ignored -> {}, this::position);
        container = new BambooTrayContainer(holder, this);
        minecraftContainer = CraftEngine.instance().platform().createContainer(container);
        Inventory inventory = CraftInventoryProxy.INSTANCE.newInstance(minecraftContainer);
        holder.setInventory(inventory);
        element = new BambooTrayElement(this);
    }

    private WorldPosition position() {
        return new WorldPosition(blockEntity.world == null ? null : blockEntity.world.world(),
                blockEntity.pos.x() + 0.5f, blockEntity.pos.y() + 0.5f, blockEntity.pos.z() + 0.5f);
    }

    public Object minecraftContainer() {
        return removed || !blockEntity.isValid() ? null : minecraftContainer;
    }

    boolean isTrayItem(Item item) {
        return item.id().equals(trayId);
    }

    BambooTrayProgress progress(int slot) {
        return progress[Objects.checkIndex(slot, progress.length)];
    }

    Item item(int slot) {
        return container.getItem(slot);
    }

    public int insert(int slot, Item held) {
        if (removed || !container.canPlaceItem(slot, held)) {
            return 0;
        }
        Item stored = container.getItem(slot);
        int room = container.getMaxStackSize(held) - (stored.isEmpty() ? 0 : stored.count());
        int moved = Math.min(room, held.count());
        if (moved <= 0) {
            return 0;
        }
        container.setItem(slot, held.copyWithCount(moved + (stored.isEmpty() ? 0 : stored.count())));
        refreshDisplay();
        return moved;
    }

    public Item take(int slot, boolean all) {
        Objects.checkIndex(slot, progress.length);
        if (removed) {
            return Item.empty();
        }
        Item stored = container.getItem(slot);
        if (stored.isEmpty()) {
            return Item.empty();
        }
        Item extracted = container.removeItem(slot, all ? stored.count() : 1);
        if (container.getItem(slot).isEmpty()) {
            prepareEmptySlot(slot);
        }
        refreshDisplay();
        return extracted;
    }

    void prepareEmptySlot(int slot) {
        if (!container.getItem(slot).isEmpty()) {
            return;
        }
        progress[slot].reset();
        processingItems[slot] = Item.empty();
    }

    void inventoryChanged() {
        if (internalMutation || removed) {
            return;
        }
        for (int slot = 0; slot < progress.length; slot++) {
            reconcileSlot(slot, false);
        }
        displayDirty = true;
        markChanged();
    }

    private void reconcileSlot(int slot, boolean settleEmpty) {
        Item stored = container.getItem(slot);
        if (stored.isEmpty()) {
            // Hoppers temporarily empty a slot before InventoryMoveItemEvent can cancel.
            if (settleEmpty) {
                prepareEmptySlot(slot);
            }
            return;
        }
        Item previous = processingItems[slot];
        if (previous.isEmpty() || !previous.isSimilar(stored)) {
            if (!previous.isEmpty()) {
                progress[slot].reset();
            }
            processingItems[slot] = stored.copy();
        }
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(
            CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper((w, pos, state, controller) -> tick());
    }

    private void tick() {
        if (removed || blockEntity.world == null) {
            return;
        }
        for (int slot = 0; slot < progress.length; slot++) {
            reconcileSlot(slot, true);
        }
        org.bukkit.World world = (org.bukkit.World) blockEntity.world.world().platformWorld();
        long offset = (long) blockEntity.pos.x() + blockEntity.pos.y() + blockEntity.pos.z();
        if (!container.isEmpty() && Math.floorMod(world.getGameTime() + offset, PROCESS_INTERVAL) == 0) {
            BambooTrayMode mode = BambooTrayEnvironment.mode(blockEntity.world, blockEntity.pos, trayId);
            if (mode != null) {
                process(mode);
            }
        }
        refreshDisplay();
    }

    private void process(BambooTrayMode mode) {
        boolean changed = false;
        for (int slot = 0; slot < progress.length; slot++) {
            Item input = container.getItem(slot);
            BambooTrayProgress state = progress[slot];
            if (input.isEmpty() || state.isCompleted(mode)) {
                continue;
            }
            BambooTrayRecipe recipe = FoodRecipeRegistry.instance().findBambooTray(input.id(), mode).orElse(null);
            if (recipe == null) {
                changed |= state.ticks() != 0;
                state.resetTicks();
                continue;
            }
            long work = state.isCompleted() ? PROCESS_INTERVAL : (long) state.ticks() + PROCESS_INTERVAL;
            Item output = Item.empty();
            if (work >= recipe.durationTicks()) {
                output = InventoryUtils.createOrEmpty(recipe.result());
                if (output.isEmpty()) {
                    if (invalidOutputs.add(recipe.id())) {
                        KaleidoscopeCookeryPlugin.instance().getLogger().warning(
                                "Bamboo tray recipe " + recipe.id() + " has no buildable result " + recipe.result()
                                        + "; kept the input at " + blockEntity.pos);
                    }
                    continue;
                }
                invalidOutputs.remove(recipe.id());
            }
            changed = true;
            if (!state.advance(mode, recipe.durationTicks(), PROCESS_INTERVAL)) {
                continue;
            }
            int amount = (int) Math.min(container.getMaxStackSize(output),
                    (long) recipe.resultCount() * input.count());
            internalMutation = true;
            try {
                container.setItem(slot, output.copyWithCount(amount));
                processingItems[slot] = container.getItem(slot).copy();
            } finally {
                internalMutation = false;
            }
            displayDirty = true;
        }
        if (changed) {
            markChanged();
        }
    }

    private void markChanged() {
        if (blockEntity.world != null) {
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
    }

    private void refreshDisplay() {
        if (!displayDirty || blockEntity.world == null) {
            return;
        }
        element.refresh();
        TrackedPlayers.forEach(blockEntity, element::update);
        displayDirty = false;
    }

    @Override
    public boolean hasElement() {
        return true;
    }

    @Override
    public void gatherElements(Consumer<BlockEntityElement> consumer) {
        consumer.accept(element);
    }

    @Override
    public void onRemove() {
        if (removed) {
            return;
        }
        removed = true;
        element.deactivate();
        for (int slot = 0; slot < progress.length; slot++) {
            Item dropped = container.removeItemNoUpdate(slot);
            DropUtils.dropOnRemove(blockEntity, dropped);
            progress[slot].reset();
        }
        super.onRemove();
    }

    @Override
    public void onUnload() {
        element.deactivate();
        super.onUnload();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        CompoundTag saved = BlockEntityNbt.newData();
        saved.putInt("schema", SCHEMA_VERSION);
        ListTag items = new ListTag();
        int[] ticks = new int[progress.length];
        byte[] completion = new byte[progress.length];
        for (int slot = 0; slot < progress.length; slot++) {
            Item item = container.getItem(slot);
            if (!item.isEmpty()) {
                Tag itemTag = BlockEntityNbt.itemTag(item);
                if (itemTag == null) {
                    throw new IllegalStateException("Cannot save bamboo tray item in slot " + slot + " at " + blockEntity.pos);
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.put("item", itemTag);
                items.add(entry);
                ticks[slot] = progress[slot].ticks();
                completion[slot] = progress[slot].completionCode();
            }
        }
        saved.put("items", items);
        saved.putIntArray("progress", ticks);
        saved.putByteArray("completion", completion);
        tag.put(DATA_KEY, saved);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag saved = tag.getCompound(DATA_KEY);
        if (saved == null) {
            return;
        }
        int schema = saved.getInt("schema", SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported bamboo tray schema " + schema + " at " + blockEntity.pos);
        }
        Item[] items = readItems(saved);
        int[] ticks = saved.getIntArray("progress", new int[progress.length]);
        byte[] completion = saved.getByteArray("completion", new byte[progress.length]);
        BambooTrayProgress[] loaded = new BambooTrayProgress[progress.length];
        for (int slot = 0; slot < progress.length; slot++) {
            loaded[slot] = new BambooTrayProgress();
            if (!items[slot].isEmpty()) {
                loaded[slot].restore(slot < ticks.length ? ticks[slot] : 0,
                        slot < completion.length ? completion[slot] : 0);
            }
        }
        internalMutation = true;
        try {
            for (int slot = 0; slot < progress.length; slot++) {
                container.setItem(slot, items[slot]);
                processingItems[slot] = items[slot].copy();
                progress[slot].restore(loaded[slot].ticks(), loaded[slot].completionCode());
            }
        } finally {
            internalMutation = false;
        }
        displayDirty = true;
        element.refresh();
    }

    private Item[] readItems(CompoundTag saved) {
        Item[] items = new Item[progress.length];
        Arrays.fill(items, Item.empty());
        ListTag slots = saved.getList("items");
        if (slots == null) {
            return items;
        }
        Set<Integer> occupied = new HashSet<>();
        int dataVersion = BlockEntityNbt.dataVersion(saved);
        for (Tag slotTag : slots) {
            if (!(slotTag instanceof CompoundTag entry)) {
                throw new IllegalArgumentException("Invalid bamboo tray inventory entry at " + blockEntity.pos);
            }
            int slot = entry.getInt("slot", -1);
            if (slot < 0 || slot >= items.length || !occupied.add(slot)) {
                throw new IllegalArgumentException("Invalid or duplicate bamboo tray slot " + slot + " at " + blockEntity.pos);
            }
            Tag itemTag = entry.get("item");
            Object parsed = itemTag == null ? null : ItemStackUtils.parseMinecraftItem(itemTag, dataVersion);
            if (parsed == null) {
                throw new IllegalArgumentException("Cannot load bamboo tray item in slot " + slot + " at " + blockEntity.pos);
            }
            items[slot] = ItemStackUtils.wrap(parsed);
        }
        return items;
    }
}
