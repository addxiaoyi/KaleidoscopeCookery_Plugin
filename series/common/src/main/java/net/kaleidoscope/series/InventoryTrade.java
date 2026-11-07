package net.kaleidoscope.series;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Plans the entire exchange before changing a live inventory. */
public final class InventoryTrade {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private InventoryTrade() {}

    public static boolean exchange(PlayerInventory inventory, Predicate<ItemStack> payment,
                                   int cost, List<ItemStack> rewards) {
        if (cost < 0) throw new IllegalArgumentException("Negative cost");
        Player player = inventory.getHolder() instanceof Player p ? p : null;
        UUID playerId = player == null ? null : player.getUniqueId();
        if (playerId != null && !ACTIVE.add(playerId)) return false;
        try {
            return exchangeOnce(inventory, player, payment, cost, rewards);
        } finally {
            if (playerId != null) ACTIVE.remove(playerId);
        }
    }

    private static boolean exchangeOnce(PlayerInventory inventory, Player player,
                                        Predicate<ItemStack> payment, int cost, List<ItemStack> rewards) {
        ItemStack[] original = inventory.getStorageContents().clone();
        ItemStack[] slots = original.clone();
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) original[i] = original[i].clone();
        }
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
        if (player != null) {
            InventoryTradeEvent event = new InventoryTradeEvent(player, cost, rewards);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            if (!sameInventory(original, inventory.getStorageContents())) return false;
        }
        inventory.setStorageContents(slots);
        return true;
    }

    private static boolean sameInventory(ItemStack[] expected, ItemStack[] actual) {
        if (expected.length != actual.length) return false;
        for (int i = 0; i < expected.length; i++) {
            ItemStack left = expected[i];
            ItemStack right = actual[i];
            if (left == null || left.isEmpty()) {
                if (right != null && !right.isEmpty()) return false;
            } else if (right == null || !left.isSimilar(right) || left.getAmount() != right.getAmount()) {
                return false;
            }
        }
        return true;
    }

    public static boolean give(PlayerInventory inventory, ItemStack item) {
        return exchange(inventory, stack -> false, 0, List.of(item));
    }
}
