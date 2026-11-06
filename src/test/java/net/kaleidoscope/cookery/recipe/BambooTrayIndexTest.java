package net.kaleidoscope.cookery.recipe;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BambooTrayIndexTest {
    @Test
    void separatesWeatherModesAndDoesNotSubstituteCustomItemMaterials() {
        BambooTrayRecipe dry = recipe("dry", "minecraft:kelp", BambooTrayMode.DRYING);
        BambooTrayRecipe wet = recipe("wet", "minecraft:kelp", BambooTrayMode.WETTING);
        var index = BambooTrayIndex.compile(List.of(dry, wet), Map.of());

        assertSame(dry, index.get(input("minecraft:kelp", BambooTrayMode.DRYING)));
        assertSame(wet, index.get(input("minecraft:kelp", BambooTrayMode.WETTING)));
        assertFalse(index.containsKey(input("test:kelp_based_tea", BambooTrayMode.DRYING)));
    }

    @Test
    void nestedTagsAreResolvedOnceAndPreserveConfiguredRecipeOrder() {
        BambooTrayRecipe tagged = recipe("tagged", "#test:plants", BambooTrayMode.DRYING);
        BambooTrayRecipe exact = recipe("exact", "minecraft:kelp", BambooTrayMode.DRYING);
        Map<Key, Set<String>> tags = new HashMap<>();
        tags.put(Key.of("test:plants"), Set.of("#test:seaweed", "craftengine:test:fresh_tea"));
        tags.put(Key.of("test:seaweed"), Set.of("minecraft:kelp"));
        var before = BambooTrayIndex.compile(List.of(tagged, exact), tags);
        tags.put(Key.of("test:seaweed"), Set.of("minecraft:grass_block"));
        var after = BambooTrayIndex.compile(List.of(tagged, exact), tags);

        assertSame(tagged, before.get(input("minecraft:kelp", BambooTrayMode.DRYING)));
        assertSame(tagged, before.get(input("test:fresh_tea", BambooTrayMode.DRYING)));
        assertSame(exact, after.get(input("minecraft:kelp", BambooTrayMode.DRYING)));
        assertSame(tagged, after.get(input("minecraft:grass_block", BambooTrayMode.DRYING)));
        assertFalse(before.containsKey(input("minecraft:grass_block", BambooTrayMode.DRYING)));
        assertThrows(UnsupportedOperationException.class, before::clear);
    }

    @Test
    void refusesMissingEmptyAndCyclicTagsInsteadOfPublishingPartialMatches() {
        BambooTrayRecipe tagged = recipe("tagged", "#test:plants", BambooTrayMode.DRYING);
        assertThrows(IllegalArgumentException.class, () -> BambooTrayIndex.compile(List.of(tagged), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> BambooTrayIndex.compile(List.of(tagged),
                Map.of(Key.of("test:plants"), Set.of())));
        assertThrows(IllegalArgumentException.class, () -> BambooTrayIndex.compile(List.of(tagged),
                Map.of(Key.of("test:plants"), Set.of("#test:nested"),
                        Key.of("test:nested"), Set.of("#test:plants", "minecraft:kelp"))));
    }

    @Test
    void refusesDuplicateIdsEvenWhenWeatherModesDiffer() {
        BambooTrayRecipe dry = recipe("same", "minecraft:kelp", BambooTrayMode.DRYING);
        BambooTrayRecipe wet = recipe("same", "minecraft:kelp", BambooTrayMode.WETTING);
        assertThrows(IllegalArgumentException.class, () -> BambooTrayIndex.compile(List.of(dry, wet), Map.of()));
    }

    @Test
    void noTrayRecipesDoNotInvalidateUnrelatedTags() {
        var index = BambooTrayIndex.compile(List.of(), Map.of(Key.of("test:cycle"), Set.of("#test:cycle")));
        assertEquals(Map.of(), index);
    }

    private static BambooTrayRecipe recipe(String id, String input, BambooTrayMode mode) {
        return new BambooTrayRecipe(Key.of("test:" + id), input, Key.of("minecraft:dried_kelp"), 1, mode, 100);
    }

    private static BambooTrayIndex.Input input(String item, BambooTrayMode mode) {
        return new BambooTrayIndex.Input(Key.of(item), mode);
    }
}
