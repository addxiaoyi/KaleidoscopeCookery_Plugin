package net.kaleidoscope.cookery.recipe;

import net.kaleidoscope.cookery.api.ItemTags;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodRecipeRegistryBambooTrayTest {
    private static final Key TAG = Key.of("test:bamboo_tray_reload");
    private static final Key KELP = Key.of("minecraft:kelp");
    private static final Key GRASS = Key.of("minecraft:grass_block");
    private final FoodRecipeRegistry registry = FoodRecipeRegistry.instance();

    @AfterEach
    void clearRecipes() {
        if (registry.isConfigurationLoading()) registry.abortConfigurationLoad(registry.configurationLoadToken());
        registry.clearBambooTray();
        ItemTags.instance().remove(TAG);
    }

    @Test
    void publishesRecipesAndTagMembershipTogether() {
        ItemTags.instance().register(TAG, List.of(KELP.asString()));
        BambooTrayRecipe before = recipe("before");
        registry.registerBambooTray(before);

        registry.beginConfigurationLoad();
        ItemTags.instance().register(TAG, List.of(GRASS.asString()));
        BambooTrayRecipe after = recipe("after");
        registry.configurationUpdate(() -> registry.registerBambooTray(after));

        assertSame(before, registry.findBambooTray(KELP, BambooTrayMode.DRYING).orElseThrow());
        assertTrue(registry.findBambooTray(GRASS, BambooTrayMode.DRYING).isEmpty());
        registry.finishConfigurationLoad();

        assertTrue(registry.findBambooTray(KELP, BambooTrayMode.DRYING).isEmpty());
        assertSame(after, registry.findBambooTray(GRASS, BambooTrayMode.DRYING).orElseThrow());
    }

    @Test
    void failedReloadKeepsThePreviousRecipesAndTags() {
        ItemTags.instance().register(TAG, List.of(KELP.asString()));
        BambooTrayRecipe before = recipe("before");
        registry.registerBambooTray(before);

        registry.beginConfigurationLoad();
        ItemTags.instance().register(TAG, List.of(GRASS.asString()));
        registry.configurationUpdate(() -> registry.registerBambooTray(recipe("after")));
        registry.failConfigurationLoad();
        registry.finishConfigurationLoad();

        assertSame(before, registry.findBambooTray(KELP, BambooTrayMode.DRYING).orElseThrow());
        assertFalse(registry.findBambooTray(GRASS, BambooTrayMode.DRYING).isPresent());
        assertTrue(ItemTags.instance().matchesId(TAG, KELP));
    }

    @Test
    void duplicateApiRegistrationCannotReplaceThePublishedSnapshot() {
        ItemTags.instance().register(TAG, List.of(KELP.asString()));
        BambooTrayRecipe existing = recipe("same");
        registry.registerBambooTray(existing);
        assertThrows(IllegalArgumentException.class, () -> registry.registerBambooTray(recipe("same")));
        assertSame(existing, registry.findBambooTray(KELP, BambooTrayMode.DRYING).orElseThrow());
    }

    @Test
    void pinnedReadDoesNotMixBeforeAndAfterAnEdit() {
        ItemTags.instance().register(TAG, List.of(KELP.asString()));
        BambooTrayRecipe before = recipe("before");
        BambooTrayRecipe after = recipe("after");
        registry.registerBambooTray(before);

        registry.readSnapshot(() -> {
            registry.atomicUpdate(() -> {
                registry.clearBambooTray();
                registry.registerBambooTray(after);
            });
            assertSame(before, registry.findBambooTray(KELP, BambooTrayMode.DRYING).orElseThrow());
            return null;
        });

        assertSame(after, registry.findBambooTray(KELP, BambooTrayMode.DRYING).orElseThrow());
    }

    private static BambooTrayRecipe recipe(String id) {
        return new BambooTrayRecipe(Key.of("test:" + id), "#" + TAG.asString(),
                Key.of("minecraft:dried_kelp"), 1, BambooTrayMode.DRYING, 100);
    }
}
