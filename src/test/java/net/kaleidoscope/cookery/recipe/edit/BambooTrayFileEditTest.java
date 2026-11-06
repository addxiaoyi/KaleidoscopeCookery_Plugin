package net.kaleidoscope.cookery.recipe.edit;

import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BambooTrayFileEditTest {
    private static final Key ID = Key.of("test:kelp");
    private static final String SECTION = "bamboo-tray-recipes#local";
    private static final String NODE = SECTION + ".test:kelp";
    private static final String FACTORY = "factories#trays";
    private static final String FACTORY_NODE = "bamboo_tray_recipes.${id}";

    @TempDir Path directory;

    @Test
    void locatesNamedAliasAndDeletesOnlyThatRecipeAcrossReload() throws Exception {
        Path file = write("""
                bamboo-tray-recipes#local:
                  test:kelp:
                    require: minecraft:kelp
                    result: minecraft:dried_kelp
                    mode: drying
                    time: 100
                  test:other:
                    require: minecraft:stone
                    result: minecraft:cobblestone
                    mode: wetting
                    time: 200
                """);
        var targets = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.BAMBOO_TRAY, ID, file, NODE);
        assertEquals(1, targets.size());
        assertEquals(NODE, targets.getFirst().generatedNode());
        assertEquals(100, targets.getFirst().originalSource().get("time"));

        assertTrue(RecipeFileStore.deleteTarget(file, targets.getFirst()));
        assertTrue(RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.BAMBOO_TRAY, ID, file, NODE).isEmpty());
        var remaining = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.BAMBOO_TRAY,
                Key.of("test:other"), file, SECTION + ".test:other");
        assertEquals(1, remaining.size());
        assertEquals("minecraft:cobblestone", remaining.getFirst().originalSource().get("result"));
    }

    @Test
    void refusesDeletionAfterTheFileWasChangedOutsideTheMenu() throws Exception {
        Path file = write(directYaml(100));
        var source = RecipeFileStore.resolveTargets(RecipeSourceIndex.Kind.BAMBOO_TRAY, ID, file, NODE).getFirst();
        Files.writeString(file, directYaml(200));
        byte[] changed = Files.readAllBytes(file);

        IOException failure = assertThrows(IOException.class, () -> RecipeFileStore.deleteTarget(file, source));
        assertTrue(failure.getMessage().contains("已变化"));
        assertArrayEquals(changed, Files.readAllBytes(file));
    }

    @Test
    void deletesSingleOutputFactoryInstanceWithoutRemovingOtherInstances() throws Exception {
        Path file = write(factoryYaml(false, false));
        assertTrue(RecipeFileStore.deleteTarget(file, factoryTarget(false)));

        String saved = Files.readString(file);
        assertTrue(saved.contains("id: other"));
        assertFalse(saved.contains("id: chosen"));
        assertTrue(saved.contains("bamboo_tray_recipes:"));
        assertTrue(saved.contains("minecraft:dried_kelp"));
    }

    @Test
    void refusesFactoryDeletionWhenTheInstanceAlsoGeneratesAnItem() throws Exception {
        Path file = write(factoryYaml(true, false));
        byte[] original = Files.readAllBytes(file);

        IOException failure = assertThrows(IOException.class,
                () -> RecipeFileStore.deleteTarget(file, factoryTarget(true)));
        assertTrue(failure.getMessage().contains("其他内容"));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void refusesAmbiguousFactoryInstancesWithoutWriting() throws Exception {
        Path file = write(factoryYaml(false, true));
        byte[] original = Files.readAllBytes(file);

        IOException failure = assertThrows(IOException.class,
                () -> RecipeFileStore.deleteTarget(file, factoryTarget(false)));
        assertTrue(failure.getMessage().contains("重复来源"));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void keepsTemplateAndSynchronizesAllTimeAliasesWhenPatching() throws Exception {
        Map<String, Object> raw = Map.of("template", "test:drying", "arguments", Map.of("item", "kelp"),
                "cooking-time", 100, "overrides", Map.of("time", 100, "result_count", 2),
                "merges", Map.of("cooking_time", 100));
        Map<String, Object> expanded = Map.of("time", 100, "cooking-time", 100, "cooking_time", 100);
        Method patch = RecipePatchWriter.class.getDeclaredMethod("patchNode", Map.class, Map.class, Map.class, boolean.class);
        patch.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = (Map<String, Object>) patch.invoke(null, raw, Map.of("time", 319), expanded, false);

        assertEquals("test:drying", saved.get("template"));
        assertEquals(Map.of("item", "kelp"), saved.get("arguments"));
        assertFalse(saved.containsKey("cooking-time"));
        assertFalse(saved.containsKey("merges"));
        assertEquals(Map.of("time", 319, "cooking-time", 319, "cooking_time", 319, "result_count", 2), saved.get("overrides"));
        assertEquals(100, raw.get("cooking-time"));
    }

    private Path write(String yaml) throws IOException {
        Path file = directory.resolve("trays.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private static String directYaml(int time) {
        return SECTION + ":\n  test:kelp:\n    require: minecraft:kelp\n    result: minecraft:dried_kelp\n"
                + "    mode: drying\n    time: " + time + "\n";
    }

    private static String factoryYaml(boolean extraOutput, boolean duplicate) {
        return """
                factories#trays:
                  instances:
                    - id: chosen
                    - id: %s
                  blueprint:
                    bamboo_tray_recipes:
                      '${id}':
                        require: minecraft:kelp
                        result: minecraft:dried_kelp
                        mode: drying
                        time: 100
                """.formatted(duplicate ? "chosen" : "other") + (extraOutput ? """
                    items:
                      '${id}':
                        material: kelp
                """ : "");
    }

    private static RecipeFileStore.SourceTarget factoryTarget(boolean extraOutput) {
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("bamboo_tray_recipes", Map.of("${id}", Map.of("require", "minecraft:kelp",
                "result", "minecraft:dried_kelp", "mode", "drying", "time", 100)));
        if (extraOutput) blueprint.put("items", Map.of("${id}", Map.of("material", "kelp")));
        return new RecipeFileStore.SourceTarget("bamboo_tray_recipes.chosen", FACTORY, "instances", 0,
                Map.of("id", "chosen"), "blueprint", FACTORY_NODE, Map.of("blueprint", blueprint));
    }
}
