package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.component.value.Enchantment;
import net.momirealms.craftengine.core.util.Key;

import java.util.List;

public final class CookeryEnchantments {
    private static final Key QUICK_KNIFE = Key.of("kaleidoscopecookery:quick_knife");
    private static final Key SWEEP = Key.of("kaleidoscopecookery:sweep");
    private static final int MAX_QUICK_KNIFE = 2;
    private static final int MAX_SWEEP = 3;

    private CookeryEnchantments() {}

    public static int quickKnifeLevel(Item knife) {
        return level(knife, QUICK_KNIFE, MAX_QUICK_KNIFE);
    }

    public static int quickKnifeWork(int level) {
        return 1 << clamp(level, MAX_QUICK_KNIFE);
    }

    public static int sweepLevel(Item sickle) {
        return level(sickle, SWEEP, MAX_SWEEP);
    }

    public static int sweepRadius(int baseRadius, int level) {
        if (baseRadius < 0) throw new IllegalArgumentException("Negative sickle radius");
        return Math.addExact(baseRadius, clamp(level, MAX_SWEEP));
    }

    public static int sweepDurability(int baseCost, int level) {
        if (baseCost < 0) throw new IllegalArgumentException("Negative sickle durability cost");
        return Math.multiplyExact(baseCost, 1 << clamp(level, MAX_SWEEP));
    }

    private static int level(Item item, Key id, int maximum) {
        if (item == null || item.isEmpty()) return 0;
        int level = 0;
        // Read active enchantments only; a stored book or matching lore is not an enchanted tool.
        for (Enchantment enchantment : item.enchantments().orElse(List.of())) {
            if (id.equals(enchantment.id())) level = Math.max(level, enchantment.level());
        }
        return clamp(level, maximum);
    }

    private static int clamp(int level, int maximum) {
        return Math.max(0, Math.min(maximum, level));
    }
}
