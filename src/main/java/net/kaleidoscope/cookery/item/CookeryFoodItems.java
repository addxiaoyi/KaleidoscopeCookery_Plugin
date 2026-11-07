package net.kaleidoscope.cookery.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kaleidoscope.cookery.catalog.BakedFood;
import net.kaleidoscope.cookery.catalog.BiteState;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.kaleidoscope.cookery.catalog.QualityProfile;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public final class CookeryFoodItems {
    private static final NamespacedKey FOOD_KEY = new NamespacedKey("kaleidoscopecookery", "food_v1");
    private static volatile CookeryCatalog catalog;

    private CookeryFoodItems() {}

    public static void initialize(CookeryCatalog definitions) { catalog = java.util.Objects.requireNonNull(definitions); }

    /** Resolve a finished item to its immutable placed-food state using base quality. */
    public static BiteState biteState(Item item) {
        return biteState(item, QualityProfile.BASE);
    }

    public static BiteState biteState(Item item, QualityProfile quality) {
        CookeryCatalog current = catalog;
        if (current == null || item == null || item.isEmpty() || quality == null) return null;
        CookeryCatalog.Entry entry = current.snapshot().items().get(item.id().toString());
        return entry == null ? null : BiteState.of(entry, quality);
    }

    public static Item bake(Item item, QualityProfile quality) {
        return bake(item, quality, false);
    }

    /** Builds one bite item using the immutable bite food definition. */
    public static Item bakeBite(Item item, QualityProfile quality) {
        return bake(item, quality, true);
    }

    private static Item bake(Item item, QualityProfile quality, boolean bite) {
        CookeryCatalog current = catalog;
        if (current == null || item == null || item.isEmpty()) return item;
        CookeryCatalog.Entry entry = current.snapshot().items().get(item.id().toString());
        if (entry == null) return item;
        BakedFood baked = bite ? BakedFood.ofBite(entry, quality) : BakedFood.of(entry, quality);
        if (baked == null) return item;
        ItemStack stack = ItemStackUtils.getBukkitStack(item.minecraftItem()).clone();
        var meta = stack.getItemMeta();
        if (meta == null) throw new IllegalStateException("Food item has no metadata: " + item.id());
        var food = meta.getFood();
        food.setNutrition(baked.food().nutrition());
        food.setSaturation(baked.food().saturation());
        food.setCanAlwaysEat(baked.food().alwaysEat());
        meta.setFood(food);
        meta.getPersistentDataContainer().set(FOOD_KEY, PersistentDataType.STRING, baked.encode());
        stack.setItemMeta(meta);
        Consumable existing = stack.getData(DataComponentTypes.CONSUMABLE);
        Consumable.Builder consumable = existing == null ? Consumable.consumable() : existing.toBuilder();
        List<ConsumeEffect> effects = new ArrayList<>();
        if (existing != null) existing.consumeEffects().stream()
                .filter(effect -> !(effect instanceof ConsumeEffect.ApplyStatusEffects)).forEach(effects::add);
        for (var effect : baked.food().effects()) {
            if (!effect.id().startsWith("minecraft:")) continue;
            var type = Registry.EFFECT.get(NamespacedKey.fromString(effect.id()));
            if (type == null) throw new IllegalStateException("Unsupported vanilla food effect " + effect.id());
            effects.add(ConsumeEffect.applyStatusEffects(List.of(new PotionEffect(type, effect.duration(), effect.amplifier())), (float) effect.probability()));
        }
        consumable.consumeSeconds(baked.food().consumeTicks() / 20f).effects(effects)
                .animation(baked.drink() ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT);
        stack.setData(DataComponentTypes.CONSUMABLE, consumable.build());
        return BukkitItemManager.instance().wrap(stack);
    }

    public static BakedFood read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(FOOD_KEY)) return null;
        return BakedFood.decode(pdc.get(FOOD_KEY, PersistentDataType.STRING));
    }
}
