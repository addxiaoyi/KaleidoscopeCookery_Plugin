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

final class PotRecipeParser extends IdSectionConfigParser {
    static final LoadingStage STAGE = new LoadingStage("pot imported recipes");
    private int count;

    @Override public Key type() { return Key.of("kaleidoscopecookery:pot_recipes"); }
    @Override public String[] sectionId() { return new String[]{"pot_recipes"}; }
    @Override public LoadingStage loadingStage() { return STAGE; }
    @Override public List<LoadingStage> dependencies() {
        return List.of(FoodRecipeManager.RECIPE_LOAD_BEGIN, FoodRecipeManager.POT_FOOD_RAW);
    }
    @Override public int count() { return count; }
    @Override public void setErrorHandler(Consumer<ResourceException> handler) {
        super.setErrorHandler(FoodRecipeManager.trackRecipeLoadErrors(handler));
    }
    @Override public void preProcess() {
        count = 0;
        FoodRecipeRegistry.instance().configurationUpdate(() -> FoodRecipeRegistry.instance().clearPot());
    }
    @Override protected boolean isDuplicate(Key id, Path path, String node) { return false; }
    @Override protected void parseSection(Pack pack, Path path, Key id, ConfigSection section) {
        FoodRecipeRegistry.instance().configurationUpdate(() -> {
            PotRecipe recipe = PotRecipe.fromConfig(id, section);
            Set<Key> required = new HashSet<>(List.of(recipe.result()));
            recipe.ingredients().forEach(required::addAll);
            if (recipe.carrier() != null) required.add(recipe.carrier());
            for (Key item : required) {
                if (item.asString().equals("minecraft:air") || BukkitItemManager.instance().getBuildableItem(item).isEmpty()) {
                    throw new IllegalArgumentException(path + ": " + id + " references unavailable item " + item);
                }
            }
            FoodRecipeRegistry.instance().registerPot(recipe);
            count++;
        });
    }
}
