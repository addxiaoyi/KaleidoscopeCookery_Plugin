package net.kaleidoscope.cookery.block.entity;

import net.momirealms.craftengine.bukkit.world.inventory.BukkitWorldlyStorageContainer;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Direction;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;

/** The same inventory backs player interaction, persistence and vanilla hoppers. */
public final class BambooTrayContainer extends BukkitWorldlyStorageContainer {
    public static final int SLOTS = 4;
    private final BambooTrayController controller;

    BambooTrayContainer(InventoryHolder holder, BambooTrayController controller) {
        super(holder, SLOTS, true, true);
        this.controller = controller;
    }

    @Override
    public boolean canPlaceItem(int slot, Item item) {
        Objects.checkIndex(slot, SLOTS);
        if (item.isEmpty() || controller.isTrayItem(item)) {
            return false;
        }
        Item stored = getItem(slot);
        if (stored.isEmpty()) {
            // A cancelled extraction restores with setItem, without this insertion check.
            controller.prepareEmptySlot(slot);
            return true;
        }
        return !controller.progress(slot).isCompleted() && stored.isSimilar(item)
                && stored.count() < getMaxStackSize(stored);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, Item item, Direction face) {
        return face != Direction.DOWN && canPlaceItem(slot, item);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, Item item, Direction face) {
        Objects.checkIndex(slot, SLOTS);
        return face == Direction.DOWN && !getItem(slot).isEmpty() && controller.progress(slot).isCompleted();
    }

    @Override
    public void setItem(int slot, Item item) {
        Objects.checkIndex(slot, SLOTS);
        Objects.requireNonNull(item, "item");
        super.setItem(slot, item);
        setChanged();
    }

    @Override
    public Item removeItem(int slot, int amount) {
        Objects.checkIndex(slot, SLOTS);
        if (amount <= 0) {
            return Item.empty();
        }
        return super.removeItem(slot, amount);
    }

    @Override
    public Item removeItemNoUpdate(int slot) {
        Objects.checkIndex(slot, SLOTS);
        Item stored = getItem(slot);
        super.setItem(slot, Item.empty());
        setChanged();
        return stored;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        setChanged();
    }

    @Override
    public void setChanged() {
        controller.inventoryChanged();
    }
}
