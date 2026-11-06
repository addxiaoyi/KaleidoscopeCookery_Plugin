package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;

// 按品质倍率缩放成品的食物属性 出锅时就写进 minecraft:food 组件
// 之后它就是一个普通食物 不需要在进食时再拦一道
public final class DishFoodScaler {
    private DishFoodScaler() {}

    public static Item scale(Item item, double ratio) {
        if (item == null || ratio == 1.0) {
            return item;
        }
        try {
            ItemStack stack = ItemStackUtils.getBukkitStack(item.minecraftItem());
            ItemMeta meta = stack.getItemMeta();
            if (meta == null || !meta.hasFood()) {
                return item;
            }
            FoodComponent food = meta.getFood();
            // Preserve intentionally zero-nutrition drinks; ordinary dishes retain the old minimum.
            food.setNutrition(food.getNutrition() == 0 ? 0 : Math.max(1, (int) Math.round(food.getNutrition() * ratio)));
            food.setSaturation((float) (food.getSaturation() * ratio));
            meta.setFood(food);
            stack.setItemMeta(meta);
            return BukkitItemManager.instance().wrap(stack);
        } catch (Exception e) {
            net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin.instance().getLogger().log(
                    java.util.logging.Level.WARNING, "Cannot scale dish food properties: " + item.id(), e);
            return item;
        }
    }
}
