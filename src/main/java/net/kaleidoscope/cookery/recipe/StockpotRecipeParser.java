package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Imported recipes are read-only in the menu and participate in the existing atomic reload. */
final class StockpotRecipeParser extends IdSectionConfigParser {
    static final LoadingStage STAGE = new LoadingStage("stockpot imported recipes");
    private int count;

    @Override public Key type() { return Key.of("kaleidoscopecookery:stockpot_recipes"); }
    @Override public String[] sectionId() { return new String[]{"stockpot_recipes"}; }
    @Override public LoadingStage loadingStage() { return STAGE; }
    @Override public List<LoadingStage> dependencies() { return List.of(FoodRecipeManager.RECIPE_LOAD_BEGIN, FoodRecipeManager.STOCK_FOOD_RAW); }
    @Override public int count() { return count; }
    @Override public void setErrorHandler(Consumer<ResourceException> handler) {
        super.setErrorHandler(FoodRecipeManager.trackRecipeLoadErrors(handler));
    }
    @Override public void preProcess() {
        count = 0;
        FoodRecipeRegistry.instance().configurationUpdate(() -> FoodRecipeRegistry.instance().clearStockpot());
    }
    @Override protected boolean isDuplicate(Key id, Path path, String node) { return false; }
    @Override protected void parseSection(Pack pack, Path path, Key id, ConfigSection section) {
        FoodRecipeRegistry.instance().configurationUpdate(() -> {
            StockpotRecipe recipe = StockpotRecipe.fromConfig(id, section);
            Set<Key> required = new HashSet<>(List.of(recipe.result(), recipe.soupBase(), recipe.visuals().cookingModel(), recipe.visuals().finishedModel()));
            recipe.ingredients().forEach(required::addAll);
            if (recipe.carrier() != null) required.add(recipe.carrier());
            for (Key item : required) {
                if (item.asString().equals("minecraft:air") || BukkitItemManager.instance().getBuildableItem(item).isEmpty()) {
                    throw new IllegalArgumentException(path + ": " + id + " references unavailable item " + item);
                }
            }
            if (!SoupBaseRegistry.instance().isSoupBase(recipe.soupBase())) {
                throw new IllegalArgumentException(path + ": " + id + " references an unregistered soup base");
            }
            FoodRecipeRegistry.instance().registerStockpot(recipe);
            count++;
        });
    }
}
