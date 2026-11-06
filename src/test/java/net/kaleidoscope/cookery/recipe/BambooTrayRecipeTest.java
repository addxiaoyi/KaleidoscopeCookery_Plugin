package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BambooTrayRecipeTest {
    private static final Key RECIPE_ID = Key.of("test:dry_kelp");

    @Test
    void readsWeatherAndWorkWithoutAccurateRecipeFields() {
        Map<String, Object> node = validNode();
        node.put("mode", "wetting");
        node.put("time", 19);
        node.put("result-count", 2);
        node.put("require", "craftengine:test:fresh_tea");

        BambooTrayRecipe recipe = BambooTrayRecipe.fromConfig(RECIPE_ID, ConfigSection.of("test.recipe", node));

        assertEquals(BambooTrayMode.WETTING, recipe.mode());
        assertEquals("test:fresh_tea", recipe.input());
        assertEquals(19, recipe.durationTicks());
        assertEquals(2, recipe.resultCount());
    }

    @Test
    void permitsEqualAliasesButRejectsConflictingValues() {
        Map<String, Object> node = validNode();
        node.put("cooking_time", 100);
        assertEquals(100, BambooTrayRecipe.fromConfig(RECIPE_ID, ConfigSection.of("test.recipe", node)).durationTicks());

        node.put("cooking-time", 101);
        assertThrows(IllegalArgumentException.class,
                () -> BambooTrayRecipe.fromConfig(RECIPE_ID, ConfigSection.of("test.recipe", node)));
    }

    @Test
    void rejectsMissingZeroFractionalAndOverflowingWork() {
        Map<String, Object> node = validNode();
        node.remove("time");
        assertThrows(IllegalArgumentException.class,
                () -> BambooTrayRecipe.fromConfig(RECIPE_ID, ConfigSection.of("test.recipe", node)));
        for (Object time : new Object[]{0, -1, 1.5, 2_147_483_648L}) {
            node.put("time", time);
            assertThrows(IllegalArgumentException.class,
                    () -> BambooTrayRecipe.fromConfig(RECIPE_ID, ConfigSection.of("test.recipe", node)));
        }
    }

    @Test
    void rejectsEmptyAirAndInvalidModeAtTheBoundary() {
        for (String input : new String[]{"", "#", "minecraft:air", "minecraft:kelp 2"}) {
            assertThrows(IllegalArgumentException.class, () -> new BambooTrayRecipe(RECIPE_ID, input,
                    Key.of("minecraft:dried_kelp"), 1, BambooTrayMode.DRYING, 100));
        }
        assertThrows(IllegalArgumentException.class, () -> BambooTrayMode.parse("frying"));
        assertThrows(IllegalArgumentException.class, () -> new BambooTrayRecipe(RECIPE_ID, "minecraft:kelp",
                Key.of("minecraft:air"), 1, BambooTrayMode.DRYING, 100));
    }

    private static Map<String, Object> validNode() {
        return new HashMap<>(Map.of("require", "minecraft:kelp", "result", "minecraft:dried_kelp",
                "mode", "drying", "time", 100));
    }
}
