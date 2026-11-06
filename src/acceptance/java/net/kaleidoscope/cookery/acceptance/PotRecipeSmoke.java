package net.kaleidoscope.cookery.acceptance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.kaleidoscope.cookery.block.entity.PotController;
import net.kaleidoscope.cookery.block.entity.PotStage;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.PotRecipe;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.RecipeUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Isolated region-owned wok fixtures; does not pretend to exercise a connected player. */
final class PotRecipeSmoke {
    private static final String SAVE = "kaleidoscopecookery:cooking_pot";
    private static final List<String> IDS = List.of("kaleidoscopecookery:pot/potato_to_baked_potato_3",
            "kaleidoscopecookery:flex_pot/candied_potato");
    private final JavaPlugin plugin;
    private final World world;
    private final Path checkpoint;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    private PotRecipeSmoke(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        checkpoint = plugin.getDataFolder().toPath().resolve("pot-checkpoint.json");
    }

    static void start(JavaPlugin plugin) {
        World world = Bukkit.getWorld("acceptance-world");
        if (world == null) throw new IllegalStateException("Pot probe requires acceptance-world");
        var probe = new PotRecipeSmoke(plugin, world);
        world.getChunkAtAsync(0, 0).whenComplete((chunk, failure) -> {
            if (failure != null) {
                probe.failures.add("Chunk load failed: " + failure);
                probe.finish();
            } else Bukkit.getRegionScheduler().execute(plugin, world, 0, 0, () -> probe.guard(probe::prepare));
        });
    }

    private void prepare() throws Exception {
        world.addPluginChunkTicket(0, 0, plugin);
        verifyDefinitions();
        if (Files.isRegularFile(checkpoint)) {
            later(20, this::resume);
            return;
        }
        for (int index = 0; index < 2; index++) {
            location(index).clone().subtract(0, 1, 0).getBlock().setType(Material.MAGMA_BLOCK);
            var pot = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:pot"));
            check(CraftEngineBlocks.place(location(index), pot.defaultState(), UpdateFlags.UPDATE_ALL, false), "Cannot place wok " + index);
        }
        later(20, this::seed);
    }

    private void verifyDefinitions() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(plugin.getDataFolder().toPath().getParent().resolve("CraftEngine/resources/cookery_kitchen/configuration/kitchen.yml").toFile());
        var expected = config.getConfigurationSection("pot_recipes");
        check(expected != null, "Missing pot recipe section");
        var registry = FoodRecipeRegistry.instance();
        check(registry.potRecipes().size() == expected.getKeys(false).size(), "Pot registry count differs");
        int exact = 0;
        int distinct = 0;
        for (PotRecipe recipe : registry.potRecipes()) {
            List<Key> inputs = recipe.ingredients().stream().map(choices ->
                    choices.stream().min(Comparator.comparing(Key::asString)).orElseThrow()).toList();
            check(recipe.matches(inputs), recipe.id() + ": witness does not match");
            var selected = registry.planPot(inputs, 6);
            check(selected.valid() && selected.matched(), recipe.id() + ": runtime selection failed");
            if (recipe.mode() == PotRecipe.Mode.EXACT) {
                check(selected.recipeId().equals(recipe.id()), recipe.id() + ": exact recipe was shadowed");
                exact++;
            } else distinct++;
            CookingPlan plan = recipe.plan(inputs);
            var row = expected.getConfigurationSection(recipe.id().asString());
            check(plan.workRequired() == row.getInt("stir_fry_count") && recipe.sourceTime() == row.getInt("source_time"),
                    recipe.id() + ": stir count and source time differ");
            check(plan.outputs().getFirst().count() == row.getInt("result_count"), recipe.id() + ": servings differ");
            check(plan.equals(CookingPlan.load(plan.save())), recipe.id() + ": frozen plan lost on save");
            Item dish = plan.buildResult().orElseThrow().item();
            check(dish.id().equals(recipe.result()) && dish.count() == recipe.resultCount(), recipe.id() + ": output construction failed");
        }
        report.put("exactRecipes", exact);
        report.put("distinctRecipes", distinct);
        report.put("checkedPlans", exact + distinct);
        for (int index = 0; index < IDS.size(); index++) {
            ItemStack note = new ItemStack(Material.PAPER);
            RecipeUtils.setRecipeItem(note, Key.of(IDS.get(index)), "pot", inputs(index), null);
            ItemStack restored = ItemStack.deserializeBytes(note.serializeAsBytes());
            check(RecipeUtils.hasRecipe(restored) && "pot".equals(restored.getItemMeta().getPersistentDataContainer()
                    .get(RecipeUtils.RECIPE_TYPE_KEY, PersistentDataType.STRING)), "Pot note did not survive serialization");
            check(IDS.get(index).equals(restored.getItemMeta().getPersistentDataContainer()
                    .get(RecipeUtils.RECIPE_ID_KEY, PersistentDataType.STRING)), "Pot note recipe ID changed");
        }
        report.put("recipeNotes", "SERIALIZATION_PASS; player auto-fill and furniture rendering NOT_TESTED");
    }

    private void seed() {
        for (int index = 0; index < 2; index++) {
            var pot = controller(index);
            pot.setHasOil(true);
            for (Key input : inputs(index)) check(pot.addIngredient(item(input), true, null), "Wok rejected ingredient " + input);
            check(pot.stirFry(true, null) == PotController.StirResult.OK, "Initial stir rejected");
        }
        later(26, () -> {
            List<String> plans = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                CompoundTag state = state(index);
                check(controller(index).stage() == PotStage.COOKING && controller(index).resultCount() == 0, "Wok completed before third stir");
                check(state.getInt("stir_fry_count") == 1 && state.getInt("stir_animation_ticks") == 0, "Initial stir did not finish");
                var plan = CookingPlan.load(state.getCompound("processing_plan"));
                check(plan.valid() && plan.recipeId().asString().equals(IDS.get(index)) && plan.workRequired() == 3, "Wrong frozen plan");
                plans.add(plan.recipeId().asString());
            }
            write(checkpoint, Map.of("phase", "IN_PROGRESS_SAVED", "plans", plans));
            report.put("phase", "IN_PROGRESS_SAVED");
            report.put("stirs", List.of(1, 1));
            finish();
        });
    }

    private void resume() throws Exception {
        var saved = JsonParser.parseString(Files.readString(checkpoint, StandardCharsets.UTF_8)).getAsJsonObject();
        if (saved.get("phase").getAsString().equals("COMPLETED_RELOADED")) {
            verifyServings(false);
            finish();
            return;
        }
        check(saved.get("phase").getAsString().equals("IN_PROGRESS_SAVED"), "Unknown pot checkpoint");
        for (int index = 0; index < 2; index++) {
            var pot = controller(index);
            var state = state(index);
            var plan = CookingPlan.load(state.getCompound("processing_plan"));
            check(pot.stage() == PotStage.COOKING && pot.hasOil() && pot.ingredients().size() == 3, "Wok lost batch state");
            check(state.getInt("stir_fry_count") == 1 && plan.workRequired() == 3
                    && plan.recipeId().asString().equals(saved.getAsJsonArray("plans").get(index).getAsString()), "Wok lost frozen stir work");
            check(pot.stirFry(true, null) == PotController.StirResult.OK, "Second stir rejected");
        }
        report.put("reloadedPots", 2);
        later(26, () -> {
            for (int index = 0; index < 2; index++) {
                check(controller(index).stage() == PotStage.COOKING, "Wok completed after only two stirs");
                check(controller(index).stirFry(true, null) == PotController.StirResult.OK, "Third stir rejected");
            }
            later(26, () -> {
                verifyServings(true);
                write(checkpoint, Map.of("phase", "COMPLETED_RELOADED"));
                finish();
            });
        });
    }

    private void verifyServings(boolean firstCompletion) {
        var exact = controller(0);
        var distinct = controller(1);
        for (int index = 0; index < 2; index++) location(index).clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);
        if (firstCompletion) {
            check(exact.stage() == PotStage.DONE && exact.resultCount() == 3 && exact.peekResult().id().asString().equals("minecraft:baked_potato"),
                    "Exact recipe did not produce three potatoes");
            check(distinct.stage() == PotStage.DONE && distinct.resultCount() == 1
                    && distinct.peekResult().id().asString().equals("kaleidoscopecookery:candied_potato"), "Distinct batch output changed");
            check(exact.resultCarrier() == null && distinct.resultCarrier().asString().equals("minecraft:bowl"), "Wok carrier differs");
            check(!exact.hasOil() && !distinct.hasOil(), "Wok retained consumed oil");
            exact.consumeResult(1);
            distinct.consumeResult(1);
        }
        check(exact.stage() == PotStage.DONE && exact.resultCount() == 2 && exact.ingredients().size() == 2, "Partial servings changed on reload");
        check(distinct.stage() == PotStage.IDLE && distinct.peekResult().isEmpty() && distinct.ingredients().isEmpty(), "Empty wok retained output or raw items");
        distinct.consumeResult(1);
        check(distinct.peekResult().isEmpty() && distinct.resultCarrier() == null, "Empty wok produced twice");
        report.put("phase", "COMPLETED_RELOADED");
        report.put("servings", List.of(exact.resultCount(), distinct.resultCount()));
        report.put("emptyNoDuplicate", true);
    }

    private static List<Key> inputs(int index) {
        return (index == 0 ? List.of("minecraft:potato", "minecraft:potato", "minecraft:potato")
                : List.of("minecraft:sugar", "minecraft:potato", "minecraft:potato")).stream().map(Key::of).toList();
    }
    private Item item(Key id) {
        Item item = InventoryUtils.createOrEmpty(id);
        check(!item.isEmpty(), "Cannot construct " + id);
        return item;
    }
    private Location location(int index) { return new Location(world, 2 + index * 3, 128, 11); }
    private PotController controller(int index) {
        Location at = location(index);
        var entity = BukkitAdaptor.adapt(world).storageWorld().getBlockEntityAtIfLoaded(new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        if (entity == null) throw new IllegalStateException("Wok fixture missing " + index);
        return entity.controller.get(PotController.class, 0);
    }
    private CompoundTag state(int index) {
        CompoundTag saved = new CompoundTag();
        controller(index).saveCustomData(saved);
        return saved.getCompound(SAVE);
    }
    private void later(long ticks, CheckedAction action) {
        Bukkit.getRegionScheduler().runDelayed(plugin, world, 0, 0, task -> guard(action), ticks);
    }
    private void guard(CheckedAction action) {
        try { action.run(); }
        catch (Exception | LinkageError failure) {
            failures.add(failure.toString());
            plugin.getLogger().log(Level.SEVERE, "Pot recipe fixture failed", failure);
            finish();
        }
    }
    private void finish() {
        report.put("sourceCommit", CookeryCatalog.SOURCE_COMMIT);
        report.put("craftEngine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
        report.put("server", Bukkit.getVersion());
        report.put("failures", failures);
        report.put("method", "SERVER_API_AND_SAVED_STATE; real controller ticks and disk reload");
        report.put("playerFlow", "NOT_TESTED");
        report.put("clientResourcePack", "NOT_TESTED");
        try { write(plugin.getDataFolder().toPath().resolve("pot-smoke.json"), report); }
        catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "Cannot write pot evidence", failure); }
        plugin.getLogger().info("COOKERY_POT_SMOKE phase=" + report.get("phase") + " failures=" + failures.size());
    }
    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value), StandardCharsets.UTF_8);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
