package net.kaleidoscope.cookery.acceptance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kaleidoscope.cookery.block.entity.ChoppingBoardController;
import net.kaleidoscope.cookery.block.entity.SteamerController;
import net.kaleidoscope.cookery.block.entity.TeapotController;
import net.kaleidoscope.cookery.block.entity.StockpotController;
import net.kaleidoscope.cookery.block.entity.StockpotStage;
import net.kaleidoscope.cookery.block.behavior.StockpotBehavior;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.recipe.FoodRecipeRegistry;
import net.kaleidoscope.cookery.recipe.StockpotRecipe;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.kaleidoscope.cookery.util.RecipeUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Real controller ticks and disk reloads; fixture inputs are seeded without a player. */
final class KitchenRecipeSmoke {
    private static final String TEAPOT = "kaleidoscopecookery:teapot";
    private static final String[] BLOCKS = {"teapot", "steamer", "chopping_board", "stockpot", "stockpot"};
    private final JavaPlugin plugin;
    private final World world;
    private final Path checkpoint;
    private final YamlConfiguration config = new YamlConfiguration();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private int checkedPlans;
    private int checkedCrafting;

    private KitchenRecipeSmoke(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        checkpoint = plugin.getDataFolder().toPath().resolve("recipe-checkpoint.json");
    }

    static void start(JavaPlugin plugin) {
        World world = Bukkit.getWorld("acceptance-world");
        if (world == null) throw new IllegalStateException("Recipe probe requires acceptance-world");
        var probe = new KitchenRecipeSmoke(plugin, world);
        world.getChunkAtAsync(0, 0).whenComplete((chunk, failure) -> {
            if (failure != null) {
                probe.failures.add("Chunk load failed: " + failure);
                probe.finish();
            } else Bukkit.getRegionScheduler().execute(plugin, world, 0, 0, () -> probe.guard(probe::prepare));
        });
    }

    private void prepare() throws Exception {
        world.addPluginChunkTicket(0, 0, plugin);
        config.load(plugin.getDataFolder().toPath().getParent()
                .resolve("CraftEngine/resources/cookery_kitchen/configuration/kitchen.yml").toFile());
        verifyDefinitions();
        boolean reload = Files.isRegularFile(checkpoint);
        for (int i = 0; i < BLOCKS.length; i++) {
            if (reload) continue;
            location(i).clone().subtract(0, 1, 0).getBlock().setType(Material.MAGMA_BLOCK);
            var definition = CraftEngineBlocks.byId(Key.of("kaleidoscopecookery:" + BLOCKS[i]));
            check(CraftEngineBlocks.place(location(i), definition.defaultState(), UpdateFlags.UPDATE_ALL, false),
                    "Cannot place recipe fixture " + BLOCKS[i]);
        }
        later(20, () -> {
            if (reload) resume();
            else seed();
        });
    }

    private void verifyDefinitions() {
        FoodRecipeRegistry registry = FoodRecipeRegistry.instance();
        check(registry.teapotRecipeCount() == section("teapot_result").getKeys(false).size(), "Tea recipe count differs");
        for (ApplianceType appliance : List.of(ApplianceType.STEAMER, ApplianceType.MILLSTONE))
            check(registry.accurateRecipeCount(appliance) == accurateIds(appliance).size(), appliance + " recipe count differs");
        check(registry.choppingRecipeCount() == section("chopping_board_raws").getKeys(false).size(), "Cutting recipe count differs");
        check(registry.teaCupCount() == section("tea_cup").getKeys(false).size(), "Cup count differs");
        check(registry.teapotLiquidCount() == section("teapot_liquid").getKeys(false).size(), "Tea liquid count differs");
        for (String sectionName : List.of("teapot_result", "accurate_foods", "chopping_board_raws")) {
            ConfigurationSection recipes = section(sectionName);
            for (String id : recipes.getKeys(false)) {
                ConfigurationSection row = recipes.getConfigurationSection(id);
                Key input = Key.of(row.getString("require").split(" ")[0]);
                CookingPlan plan = switch (sectionName) {
                    case "teapot_result" -> registry.planTeapot(Key.of(row.getString("fluid")), input);
                    case "accurate_foods" -> registry.planAccurate(ApplianceType.valueOf(row.getString("cook")), input, 4);
                    default -> registry.planChopping(input);
                };
                check(plan.valid() && plan.matched() && plan.recipeId().asString().equals(id), id + ": wrong selected plan");
                int work = row.getInt("rotations", row.getInt("time", row.getInt("cooking_time", row.getInt("stage"))));
                check(plan.workRequired() == work, id + ": incorrect work duration");
                check(plan.equals(CookingPlan.load(plan.save())), id + ": plan round-trip changed");
                Map<String, Integer> expected = new LinkedHashMap<>();
                String[] main = row.getString("result").split(" ");
                expected.put(main[0], main.length > 1 ? Integer.parseInt(main[1]) : row.getInt("result_count", 1));
                for (String extra : row.getStringList("extra")) {
                    String[] fields = extra.split(" ");
                    check(fields.length == 3 && fields[2].equals("100"), id + ": fixture only supports deterministic extras");
                    expected.merge(fields[0], Integer.parseInt(fields[1]), Integer::sum);
                }
                Map<String, Integer> actual = new LinkedHashMap<>();
                for (Item output : plan.buildOutputs().orElseThrow()) actual.merge(output.id().asString(), output.count(), Integer::sum);
                check(expected.equals(actual), id + ": constructed outputs differ " + actual);
                checkedPlans++;
            }
        }
        check(!registry.planTeapot(Key.of("minecraft:water"), Key.of("kaleidoscopecookery:butter_tea_bag")).matched(),
                "Butter tea incorrectly accepts water");
        for (String id : section("recipes").getKeys(false)) {
            var recipe = Bukkit.getRecipe(NamespacedKey.fromString(id));
            check(recipe != null, id + ": native recipe not registered");
            ConfigurationSection row = section("recipes").getConfigurationSection(id);
            Item output = ItemStackUtils.wrap(ItemStackUtils.unwrap(recipe.getResult()));
            check(output.id().asString().equals(row.getString("result.id"))
                    && output.count() == row.getInt("result.count"), id + ": native recipe output differs");
            checkedCrafting++;
        }
        report.put("teaCups", registry.teaCupCount());
        report.put("soupBases", net.kaleidoscope.cookery.recipe.SoupBaseRegistry.instance().keys().size());
        verifyStockpotDefinitions();
    }

    private void verifyStockpotDefinitions() {
        var registry = FoodRecipeRegistry.instance();
        var recipes = section("stockpot_recipes");
        check(registry.stockpotRecipes().size() == recipes.getKeys(false).size(), "Stockpot registry count differs");
        int exact = 0;
        int distinct = 0;
        for (StockpotRecipe recipe : registry.stockpotRecipes()) {
            List<Key> inputs = recipe.ingredients().stream()
                    .map(choices -> choices.stream().sorted(java.util.Comparator.comparing(Key::asString)).findFirst().orElseThrow()).toList();
            check(recipe.matches(inputs, recipe.soupBase()), recipe.id() + ": imported witness does not match");
            CookingPlan selected = registry.planStockpot(inputs, recipe.soupBase(), 1);
            check(selected.valid() && selected.matched(), recipe.id() + ": no runtime match");
            if (recipe.mode() == StockpotRecipe.Mode.EXACT) {
                check(selected.recipeId().equals(recipe.id()), recipe.id() + ": exact match was shadowed");
                exact++;
            } else distinct++;
            CookingPlan plan = recipe.plan(inputs);
            check(plan.equals(CookingPlan.load(plan.save())), recipe.id() + ": plan/visuals lost on save");
            var expected = recipes.getConfigurationSection(recipe.id().asString());
            check(plan.workRequired() == expected.getInt("time"), recipe.id() + ": wrong cooking duration");
            check(plan.outputs().getFirst().count() == expected.getInt("result_count"), recipe.id() + ": wrong servings");
            check(recipe.visuals().cookingModel().asString().equals(expected.getString("visuals.cooking_model"))
                    && recipe.visuals().finishedModel().asString().equals(expected.getString("visuals.finished_model"))
                    && recipe.visuals().cookingColor() == expected.getInt("visuals.cooking_color")
                    && recipe.visuals().finishedColor() == expected.getInt("visuals.finished_color"), recipe.id() + ": wrong visuals");
            Item output = plan.buildResult().orElseThrow().item();
            check(output.id().equals(recipe.result()) && output.count() == recipe.resultCount(), recipe.id() + ": result construction failed");
            item(recipe.visuals().cookingModel().asString());
            item(recipe.visuals().finishedModel().asString());
            checkedPlans++;
        }
        report.put("exactStockpotRecipes", exact);
        report.put("distinctStockpotRecipes", distinct);
        var note = new org.bukkit.inventory.ItemStack(Material.PAPER);
        Key riceId = Key.of("kaleidoscopecookery:stockpot/rice_3");
        RecipeUtils.setRecipeItem(note, riceId, "stockpot", java.util.Collections.nCopies(3, Key.of("kaleidoscopecookery:rice")), Key.of("minecraft:water_bucket"));
        var restored = org.bukkit.inventory.ItemStack.deserializeBytes(note.serializeAsBytes());
        check(RecipeUtils.hasRecipe(restored), "Stockpot note lost recipe marker");
        check("stockpot".equals(restored.getItemMeta().getPersistentDataContainer().get(RecipeUtils.RECIPE_TYPE_KEY,
                org.bukkit.persistence.PersistentDataType.STRING)), "Stockpot note lost recipe type");
        check(registry.findStockpotById(riceId).result().asString().equals("kaleidoscopecookery:cooked_rice"), "Stockpot furniture cannot resolve recorded recipe");
        report.put("stockpotRecipeNote", "SERIALIZATION_PASS; player auto-fill and furniture rendering NOT_TESTED");
    }

    private void seed() {
        CompoundTag tea = new CompoundTag();
        tea.putString("fluid", "minecraft:milk");
        tea.putInt("status", TeapotController.PUT_INGREDIENT);
        tea.putInt("current_tick", 200);
        BlockEntityNbt.putItem(tea, "input", item("kaleidoscopecookery:butter_tea_bag"));
        tea.put("cooking_plan", FoodRecipeRegistry.instance().planTeapot(Key.of("minecraft:milk"),
                Key.of("kaleidoscopecookery:butter_tea_bag")).save());
        CompoundTag tag = new CompoundTag();
        tag.put(TEAPOT, tea);
        controller(0, TeapotController.class).loadCustomData(tag);
        changed(0);
        var steamer = controller(1, SteamerController.class);
        for (String id : accurateIds(ApplianceType.STEAMER))
            check(steamer.tryAddOne(item(section("accurate_foods").getString(id + ".require"))), "Cannot fill steamer " + id);
        steamer.setHasLid(true);
        changed(1);
        var board = controller(2, ChoppingBoardController.class);
        check(board.place(item("minecraft:chicken")), "Cannot place chicken on cutting board");
        check(board.cut() == ChoppingBoardController.CutResult.ADVANCED, "First cut did not advance");
        seedStockpot(3, "kaleidoscopecookery:rice", 3);
        seedStockpot(4, "minecraft:bone", 2);
        later(100, () -> {
            controller(1, SteamerController.class).setHasLid(false);
            changed(1);
            location(0).clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);
            for (int index : List.of(3, 4)) location(index).clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);
            later(25, this::saveCheckpoint);
        });
    }

    private void saveCheckpoint() throws Exception {
        var steamer = controller(1, SteamerController.class);
        CompoundTag tea = teaState();
        int progress = steamer.getCookingProgress()[0];
        int teaRemaining = tea.getInt("current_tick");
        check(progress > 0 && progress < 1200, "Steamer did not advance before pause");
        check(teaRemaining > 0 && teaRemaining < 200, "Tea did not advance before heat removal");
        check(controller(2, ChoppingBoardController.class).currentStage() == 2, "Cutting stage changed unexpectedly");
        for (int value : steamer.getCookingProgress()) check(value == 0 || value == progress, "Steamer slots progressed differently");
        List<Integer> soupProgress = new ArrayList<>();
        List<String> soupModels = new ArrayList<>();
        for (int index : List.of(3, 4)) {
            var pot = controller(index, StockpotController.class);
            check(pot.stage() == StockpotStage.COOKING && pot.currentTick() > 0 && pot.currentTick() < 300,
                    "Stockpot failed to start/pause " + index);
            check(pot.visuals() != null, "Stockpot visuals not frozen");
            soupProgress.add(pot.currentTick());
            soupModels.add(pot.visuals().finishedModel().asString());
        }
        write(checkpoint, Map.of("phase", "IN_PROGRESS_SAVED", "steamProgress", progress, "teaRemaining", teaRemaining,
                "stockpotRemaining", soupProgress, "stockpotModels", soupModels));
        report.put("stockpotRemaining", soupProgress);
        report.put("stockpotModels", soupModels);
        report.put("phase", "IN_PROGRESS_SAVED");
        report.put("steamProgress", progress);
        report.put("teaRemaining", teaRemaining);
        finish();
    }

    private void resume() throws Exception {
        JsonObject saved = JsonParser.parseString(Files.readString(checkpoint, StandardCharsets.UTF_8)).getAsJsonObject();
        if (saved.get("phase").getAsString().equals("COMPLETED_RELOADED")) {
            verifyCompleted();
            verifyStockpotServings(false);
            finish();
            return;
        }
        check(saved.get("phase").getAsString().equals("IN_PROGRESS_SAVED"), "Unrecognized recipe checkpoint");
        var steamer = controller(1, SteamerController.class);
        check(steamer.getItemCount() == 4, "Reload lost steamer ingredients");
        for (int i = 0; i < 4; i++) check(steamer.getCookingProgress()[i] == saved.get("steamProgress").getAsInt(), "Reload lost steamer progress");
        check(teaState().getInt("current_tick") == saved.get("teaRemaining").getAsInt(), "Reload lost tea progress");
        check(controller(2, ChoppingBoardController.class).currentStage() == 2, "Reload lost cutting stage");
        report.put("reloadedBatches", 3);
        for (int index : List.of(3, 4)) {
            var pot = controller(index, StockpotController.class);
            check(pot.stage() == StockpotStage.COOKING, "Stockpot lost cooking state");
            check(pot.currentTick() == saved.getAsJsonArray("stockpotRemaining").get(index - 3).getAsInt(), "Stockpot progress changed while stopped");
            check(pot.visuals().finishedModel().asString().equals(saved.getAsJsonArray("stockpotModels").get(index - 3).getAsString()), "Stockpot model changed while stopped");
            check(pot.ingredients().size() == (index == 3 ? 3 : 2), "Stockpot raw ingredients lost");
            location(index).clone().subtract(0, 1, 0).getBlock().setType(Material.MAGMA_BLOCK);
        }
        report.put("reloadedStockpots", 2);
        steamer.setHasLid(true);
        changed(1);
        location(0).clone().subtract(0, 1, 0).getBlock().setType(Material.MAGMA_BLOCK);
        later(1220 - saved.get("steamProgress").getAsInt(), () -> {
            finishCutting();
            verifyCompleted();
            verifyStockpotServings(true);
            write(checkpoint, Map.of("phase", "COMPLETED_RELOADED"));
            finish();
        });
    }

    private void seedStockpot(int index, String ingredient, int count) {
        var pot = controller(index, StockpotController.class);
        check(pot.addSoupBase(Key.of("minecraft:water_bucket"), true), "Cannot add stockpot water");
        for (int slot = 0; slot < count; slot++) check(pot.addIngredient(item(ingredient)), "Cannot add stockpot ingredient");
        stockpotLid(index, true);
    }

    private void stockpotLid(int index, boolean closed) {
        BlockEntity entity = entity(index);
        var behavior = entity.blockState.behavior().getFirst(StockpotBehavior.class);
        BlockStates.sync(entity, BlockStates.with(entity.blockState, behavior.getHasLidProperty(), closed));
    }

    private void verifyStockpotServings(boolean firstCompletion) {
        var rice = controller(3, StockpotController.class);
        var bone = controller(4, StockpotController.class);
        stockpotLid(3, false);
        stockpotLid(4, false);
        if (firstCompletion) {
            check(rice.stage() == StockpotStage.FINISHED && rice.takeoutCount() == 3 && rice.finishedMax() == 3,
                    "Exact stockpot did not produce 3 servings");
            check(rice.ingredients().isEmpty() && Math.abs(rice.liquidOffset()) < 0.000001, "Stockpot inputs or initial liquid level differ");
            check(bone.stage() == StockpotStage.FINISHED && bone.takeoutCount() == 1,
                    "Distinct recipe incorrectly scaled output by duplicate bones");
            check(rice.takeOutResult().id().asString().equals("kaleidoscopecookery:cooked_rice"), "Wrong rice output");
            check(bone.takeOutResult().id().asString().equals("kaleidoscopecookery:pork_bone_soup"), "Wrong bone soup output");
        }
        check(rice.takeoutCount() == 2 && rice.finishedMax() == 3, "Partial servings did not survive reload");
        check(Math.abs(rice.liquidOffset() + 0.105f) < 0.000001, "Partial liquid height differs");
        check(rice.resultCarrier().asString().equals("minecraft:bowl"), "Rice carrier lost");
        check(rice.visuals().finishedModel().asString().endsWith("stockpot/rice_finished"), "Rice surface lost");
        check(bone.stage() == StockpotStage.PUT_SOUP_BASE && bone.takeOutResult().isEmpty(), "Empty stockpot produced twice");
        report.put("stockpotServings", List.of(rice.takeoutCount(), bone.takeoutCount()));
        report.put("stockpotLiquidOffset", rice.liquidOffset());
        report.put("stockpotEmptyNoDuplicate", true);
    }

    private void finishCutting() {
        Set<UUID> before = new HashSet<>();
        world.getNearbyEntities(location(2), 2, 2, 2).forEach(entity -> before.add(entity.getUniqueId()));
        var board = controller(2, ChoppingBoardController.class);
        check(board.cut() == ChoppingBoardController.CutResult.ADVANCED, "Second cut failed");
        check(board.cut() == ChoppingBoardController.CutResult.ADVANCED, "Third cut failed");
        check(board.cut() == ChoppingBoardController.CutResult.FINISHED, "Final cut failed");
        check(board.cut() == ChoppingBoardController.CutResult.NOTHING && board.isEmpty(), "Cutting produced twice");
        Map<String, Integer> drops = new LinkedHashMap<>();
        world.getNearbyEntities(location(2), 2, 2, 2).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Item dropped && !before.contains(entity.getUniqueId())) {
                Item stack = ItemStackUtils.wrap(ItemStackUtils.unwrap(dropped.getItemStack()));
                drops.merge(stack.id().asString(), stack.count(), Integer::sum);
                entity.remove();
            }
        });
        check(drops.equals(Map.of("kaleidoscopecookery:raw_cut_small_meats", 3, "minecraft:bone", 1)), "Cutting drops differ: " + drops);
        report.put("cuttingDrops", drops);
    }

    private void verifyCompleted() {
        var steamer = controller(1, SteamerController.class);
        check(steamer.getItemCount() == 4, "Steamer lost or duplicated outputs");
        int slot = 0;
        for (String id : accurateIds(ApplianceType.STEAMER)) {
            Item output = steamer.getItems()[slot];
            check(output.id().asString().equals(section("accurate_foods").getString(id + ".result"))
                    && output.count() == 1 && steamer.getCookingTime()[slot] == -1, "Steamer did not finish " + id);
            slot++;
        }
        CompoundTag tea = teaState();
        Item result = BlockEntityNbt.getItem(tea, "result", VersionHelper.WORLD_VERSION);
        check(tea.getInt("status") == TeapotController.FINISHED && tea.getInt("servings") == 8
                && result.id().asString().equals("kaleidoscopecookery:butter_tea"), "Butter tea did not finish with 8 plugin servings");
        check(BlockEntityNbt.getItem(tea, "input", VersionHelper.WORLD_VERSION).isEmpty(), "Finished tea retains its raw ingredient");
        check(controller(2, ChoppingBoardController.class).isEmpty(), "Finished cutting board is not empty");
        report.put("phase", "COMPLETED_RELOADED");
        report.put("steamedOutputs", 4);
        report.put("teaServings", tea.getInt("servings"));
    }

    private CompoundTag teaState() {
        CompoundTag tag = new CompoundTag();
        controller(0, TeapotController.class).saveCustomData(tag);
        return tag.getCompound(TEAPOT);
    }

    private List<String> accurateIds(ApplianceType cook) {
        return section("accurate_foods").getKeys(false).stream()
                .filter(id -> cook.name().equals(section("accurate_foods").getString(id + ".cook"))).toList();
    }

    private ConfigurationSection section(String name) {
        ConfigurationSection section = config.getConfigurationSection(name);
        if (section == null) throw new IllegalStateException("Missing fixture section " + name);
        return section;
    }

    private Item item(String id) {
        Item item = InventoryUtils.createOrEmpty(Key.of(id));
        check(!item.isEmpty(), "Cannot construct " + id);
        return item;
    }

    private Location location(int index) { return new Location(world, 2 + index * 3, 128, 2); }
    private BlockEntity entity(int index) {
        Location at = location(index);
        BlockEntity entity = BukkitAdaptor.adapt(world).storageWorld()
                .getBlockEntityAtIfLoaded(new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        if (entity == null) throw new IllegalStateException("Fixture controller missing: " + BLOCKS[index]);
        return entity;
    }
    private <T> T controller(int index, Class<T> type) { return entity(index).controller.get(type, 0); }
    private void changed(int index) {
        BlockEntity entity = entity(index);
        entity.world.blockEntityChanged(entity.pos);
    }
    private void later(long ticks, CheckedAction action) {
        Bukkit.getRegionScheduler().runDelayed(plugin, world, 0, 0, task -> guard(action), ticks);
    }
    private void guard(CheckedAction action) {
        try { action.run(); }
        catch (Exception | LinkageError failure) {
            failures.add(failure.toString());
            plugin.getLogger().log(Level.SEVERE, "Kitchen recipe fixture failed", failure);
            finish();
        }
    }
    private void finish() {
        report.put("sourceCommit", CookeryCatalog.SOURCE_COMMIT);
        report.put("craftEngine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
        report.put("server", Bukkit.getVersion());
        report.put("checkedPlans", checkedPlans);
        report.put("checkedCrafting", checkedCrafting);
        report.put("failures", failures);
        report.put("method", "SERVER_API_AND_SAVED_STATE; real controller ticks and disk reload");
        report.put("playerFlow", "NOT_TESTED");
        report.put("clientResourcePack", "NOT_TESTED");
        try { write(plugin.getDataFolder().toPath().resolve("recipe-smoke.json"), report); }
        catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "Cannot write recipe evidence", failure); }
        plugin.getLogger().info("COOKERY_RECIPE_SMOKE phase=" + report.get("phase") + " failures=" + failures.size());
    }
    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value), StandardCharsets.UTF_8);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
