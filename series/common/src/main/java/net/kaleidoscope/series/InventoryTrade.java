package net.kaleidoscope.series;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.function.Predicate;

/** Plans the entire exchange before changing a live inventory. */
public final class InventoryTrade {
    private InventoryTrade() {}

    public static boolean exchange(PlayerInventory inventory, Predicate<ItemStack> payment,
                                   int cost, List<ItemStack> rewards) {
        if (cost < 0) throw new IllegalArgumentException("Negative cost");
        ItemStack[] slots = inventory.getStorageContents().clone();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) slots[i] = slots[i].clone();
        }
        int unpaid = cost;
        for (int i = 0; i < slots.length && unpaid > 0; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty() || !payment.test(stack)) continue;
            int paid = Math.min(stack.getAmount(), unpaid);
            stack.subtract(paid);
            unpaid -= paid;
            if (stack.isEmpty()) slots[i] = null;
        }
        if (unpaid != 0) return false;
        for (ItemStack reward : rewards) {
            if (reward == null || reward.isEmpty()) throw new IllegalArgumentException("Empty reward");
            int remaining = reward.getAmount();
            int limit = Math.min(reward.getMaxStackSize(), inventory.getMaxStackSize());
            for (ItemStack slot : slots) {
                if (slot == null || !slot.isSimilar(reward)) continue;
                int added = Math.min(remaining, Math.max(0, limit - slot.getAmount()));
                slot.add(added);
                remaining -= added;
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] != null && !slots[i].isEmpty()) continue;
                slots[i] = reward.asQuantity(Math.min(remaining, limit));
                remaining -= slots[i].getAmount();
            }
            if (remaining > 0) return false;
        }
        inventory.setStorageContents(slots);
        return true;
    }

    public static boolean give(PlayerInventory inventory, ItemStack item) {
        return exchange(inventory, stack -> false, 0, List.of(item));
    }
}
