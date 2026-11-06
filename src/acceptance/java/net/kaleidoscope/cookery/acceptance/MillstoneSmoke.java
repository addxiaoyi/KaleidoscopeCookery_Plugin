package net.kaleidoscope.cookery.acceptance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kaleidoscope.cookery.api.MillstoneAnimals;
import net.kaleidoscope.cookery.api.event.MillstoneGrindCompleteEvent;
import net.kaleidoscope.cookery.block.entity.MillstoneController;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.kaleidoscope.cookery.recipe.CookingPlan;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Uses CE furniture ticks and real animals; never advances the controller through reflection. */
final class MillstoneSmoke implements Listener {
    private static final Key ID = Key.of("kaleidoscopecookery:millstone");
    private static final int CHUNK_X = 2;
    private static final int CHUNK_Z = 0;
    private static final int RAW_COUNT = 2;
    private static final int EXPECTED_OUTPUT = 10;
    private final JavaPlugin plugin;
    private final World world;
    private final Path checkpoint;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<Map<String, Object>> driveSamples = new ArrayList<>();
    private Furniture furniture;
    private MillstoneController mill;
    private UUID animalId;
    private int completionEvents;
    private int spawnedOutput;
    private boolean finished;

    private MillstoneSmoke(JavaPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        checkpoint = plugin.getDataFolder().toPath().resolve("millstone-checkpoint.json");
    }

    static void start(JavaPlugin plugin) {
        World world = Bukkit.getWorld("acceptance-world");
        if (world == null) throw new IllegalStateException("Millstone probe requires acceptance-world");
        var probe = new MillstoneSmoke(plugin, world);
        Bukkit.getPluginManager().registerEvents(probe, plugin);
        world.getChunkAtAsync(CHUNK_X, CHUNK_Z).whenComplete((chunk, failure) -> {
            if (failure != null) {
                probe.failures.add("Chunk load failed: " + failure);
                probe.finish();
            } else Bukkit.getRegionScheduler().execute(plugin, world, CHUNK_X, CHUNK_Z, () -> probe.guard(probe::prepare));
        });
    }

    private Location center() { return new Location(world, 40, 128, 8); }

    private void prepare() throws Exception {
        world.addPluginChunkTicket(CHUNK_X, CHUNK_Z, plugin);
        check(CraftEngineFurniture.byId(ID) != null, "Millstone furniture was not registered");
        report.put("checkedFurniture", 1);
        report.put("animalProfile", Map.of("type", "COW", "secondsPerRevolution",
                MillstoneAnimals.instance().profileForType(EntityType.COW).secondsPerRevolution(), "override", false));
        if (Files.isRegularFile(checkpoint)) {
            later(5, this::resume);
            return;
        }
        for (int x = 36; x <= 44; x++) for (int z = 4; z <= 12; z++) {
            world.getBlockAt(x, 127, z).setType(Material.STONE);
            for (int y = 128; y <= 131; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
        }
        furniture = CraftEngineFurniture.place(center(), CraftEngineFurniture.byId(ID), "ground", false);
        mill = furniture.controller.get(MillstoneController.class, 0);
        check(mill != null, "Placed furniture has no millstone controller");
        later(5, this::seed);
    }

    private void seed() {
        for (int count = 0; count < RAW_COUNT; count++) {
            check(mill.tryAddGrind(InventoryUtils.createOrEmpty(Key.of("minecraft:bone"))), "Bone input rejected");
        }
        Cow cow = world.spawn(center().add(0, 0, 2.5), Cow.class, animal -> {
            animal.setAdult();
            animal.setPersistent(true);
            animal.setRemoveWhenFarAway(false);
        });
        animalId = cow.getUniqueId();
        check(mill.spinWithAnimal(cow, null, true), "Adult cow could not drive millstone");
        awaitPartial(0);
    }

    private void awaitPartial(int ticks) {
        later(10, () -> {
            List<Float> progress = progress();
            if (progress.stream().allMatch(value -> value > 0 && value < 360)) {
                check(completionEvents == 0 && spawnedOutput == 0, "Output appeared before a full rotation");
                check(state().getBoolean("animating"), "Animal stopped before checkpoint");
                furniture.setUnsaved();
                furniture.saveIfDirty();
                write(checkpoint, Map.of("phase", "IN_PROGRESS_SAVED", "furniture", furniture.uuid().toString(),
                        "animal", animalId.toString(), "progressDegrees", progress));
                report.put("phase", "IN_PROGRESS_SAVED");
                report.put("progressDegrees", progress);
                report.put("rawCount", RAW_COUNT);
                finish();
            } else {
                check(ticks < 200, "Animal did not advance the real furniture ticker");
                awaitPartial(ticks + 10);
            }
        });
    }

    private void resume() throws Exception {
        JsonObject saved = JsonParser.parseString(Files.readString(checkpoint, StandardCharsets.UTF_8)).getAsJsonObject();
        UUID furnitureId = UUID.fromString(saved.get("furniture").getAsString());
        Entity meta = localEntity(furnitureId);
        check(meta != null, "Furniture metadata entity did not survive restart");
        furniture = CraftEngineFurniture.getLoadedFurnitureByMetaEntity(meta);
        check(furniture != null && furniture.uuid().equals(furnitureId), "CE did not reload the furniture");
        mill = furniture.controller.get(MillstoneController.class, 0);
        check(mill != null, "Reloaded furniture lost its controller");
        animalId = UUID.fromString(saved.get("animal").getAsString());
        check(localEntity(animalId) instanceof Cow, "Pulling animal did not survive restart");
        report.put("reloadedFurniture", 1);
        if (saved.get("phase").getAsString().equals("COMPLETED_RELOADED")) {
            check(mill.grindIsEmpty(), "Empty millstone regained raw input after restart");
            later(80, () -> {
                check(mill.grindIsEmpty() && completionEvents == 0 && spawnedOutput == 0 && visibleOutput() == 0,
                        "Empty millstone produced again after restart");
                report.put("phase", "EMPTY_RELOADED");
                report.put("emptyNoDuplicate", true);
                finish();
            });
            return;
        }
        check(saved.get("phase").getAsString().equals("IN_PROGRESS_SAVED"), "Unknown millstone checkpoint phase");
        List<Float> restored = progress();
        check(restored.size() == RAW_COUNT, "Millstone lost raw slots on restart");
        for (int slot = 0; slot < RAW_COUNT; slot++) {
            check(restored.get(slot) >= saved.getAsJsonArray("progressDegrees").get(slot).getAsFloat()
                    && restored.get(slot) < 360, "Unfinished progress changed or completed before inspection");
        }
        check(state().getBoolean("animating"), "Saved animal driver was not restored");
        var cow = (Cow) localEntity(animalId);
        check(!cow.hasAI() && !cow.hasGravity(), "Animal drive state was not restored");
        report.put("animalRestored", true);
        report.put("reloadedProgressDegrees", restored);
        awaitCompletion(0);
    }

    private void awaitCompletion(int ticks) {
        later(20, () -> {
            if (!mill.grindIsEmpty()) {
                if (ticks % 200 == 0) sampleDrive(ticks);
                check(ticks < 2400 && mill.isAnimating(), "Animal-driven processing stopped or timed out");
                awaitCompletion(ticks + 20);
                return;
            }
            check(completionEvents == 1, "Expected one completion event for two equal batches");
            check(spawnedOutput == EXPECTED_OUTPUT && visibleOutput() == EXPECTED_OUTPUT,
                    "Two bones must produce exactly ten visible bone meal");
            for (Entity entity : world.getChunkAt(CHUNK_X, CHUNK_Z).getEntities()) {
                if (entity instanceof org.bukkit.entity.Item item && isFixture(item.getLocation())) item.remove();
            }
            furniture.setUnsaved();
            furniture.saveIfDirty();
            write(checkpoint, Map.of("phase", "COMPLETED_RELOADED", "furniture", furniture.uuid().toString(),
                    "animal", animalId.toString()));
            report.put("phase", "COMPLETED_RELOADED");
            report.put("outputCount", EXPECTED_OUTPUT);
            report.put("emptyNoDuplicate", true);
            finish();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void completed(MillstoneGrindCompleteEvent event) {
        if (!isFixture(event.location())) return;
        completionEvents++;
        if (event.player() != null) failures.add("Animal completion was attributed to a player");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawned(ItemSpawnEvent event) {
        if (!isFixture(event.getLocation())) return;
        if (event.getEntity().getItemStack().getType() == Material.BONE_MEAL) spawnedOutput += event.getEntity().getItemStack().getAmount();
        else failures.add("Unexpected fixture output: " + event.getEntity().getItemStack());
    }

    private boolean isFixture(Location at) {
        return at.getWorld() == world && Math.abs(at.getX() - 40) < 4
                && Math.abs(at.getY() - 128) < 4 && Math.abs(at.getZ() - 8) < 4;
    }

    private Entity localEntity(UUID id) {
        return Arrays.stream(world.getChunkAt(CHUNK_X, CHUNK_Z).getEntities())
                .filter(entity -> entity.getUniqueId().equals(id)).findFirst().orElse(null);
    }

    private void sampleDrive(int ticks) throws Exception {
        Map<String, Object> sample = new LinkedHashMap<>();
        var saved = state();
        sample.put("probeTicks", ticks);
        sample.put("rawTick", saved.getInt("raw_tick"));
        sample.put("animating", saved.getBoolean("animating"));
        sample.put("progress", progress());
        sample.put("furnitureValid", furniture.isValid());
        sample.put("controllerLoaded", diagnosticField(mill, "loaded"));
        Entity meta = localEntity(furniture.uuid());
        sample.put("sameLoadedFurniture", meta != null && CraftEngineFurniture.getLoadedFurnitureByMetaEntity(meta) == furniture);
        Object manager = net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager.instance();
        var tickers = (Map<?, ?>) diagnosticField(manager, "syncTickers");
        Object ticker = tickers.get(furniture.entityId());
        sample.put("registeredTicker", ticker != null);
        if (ticker != null) {
            Furniture ticking = (Furniture) diagnosticField(ticker, "furniture");
            sample.put("sameTickingFurniture", ticking == furniture);
            sample.put("tickingFurnitureValid", ticking.isValid());
        }
        Object movement = diagnosticField(mill, "animalMovement");
        sample.put("pendingMovement", movement != null);
        if (movement != null) {
            sample.put("movementComplete", diagnosticField(movement, "complete"));
            sample.put("movementSuccessful", diagnosticField(movement, "successful"));
        }
        Entity animal = localEntity(animalId);
        sample.put("animalPresent", animal != null);
        if (animal != null) {
            sample.put("animalOwned", Bukkit.isOwnedByCurrentRegion(animal));
            sample.put("animalValid", animal.isValid());
            sample.put("animalLocation", List.of(animal.getX(), animal.getY(), animal.getZ()));
            Object bound = diagnosticField(mill, "pullingAnimal");
            sample.put("sameBoundAnimal", bound == animal);
            sample.put("boundAnimalOwned", bound instanceof Entity entity && Bukkit.isOwnedByCurrentRegion(entity));
        }
        driveSamples.add(sample);
        write(plugin.getDataFolder().toPath().resolve("millstone-drive.json"), driveSamples);
        if (driveSamples.size() > 1) {
            var previous = driveSamples.get(driveSamples.size() - 2);
            check(!previous.get("rawTick").equals(sample.get("rawTick")), "Animal made no progress for 200 probe ticks: " + sample);
        }
    }

    // Read-only diagnostics; acceptance never modifies private state or invokes advanceGrind.
    private static Object diagnosticField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // CE keeps ticker maps on its abstract manager.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private int visibleOutput() {
        int count = 0;
        for (Entity entity : world.getChunkAt(CHUNK_X, CHUNK_Z).getEntities()) {
            if (entity instanceof org.bukkit.entity.Item item && isFixture(item.getLocation())) {
                check(item.getItemStack().getType() == Material.BONE_MEAL, "Unexpected visible output");
                count += item.getItemStack().getAmount();
            }
        }
        return count;
    }

    private CompoundTag state() {
        CompoundTag saved = new CompoundTag();
        mill.saveCustomData(saved);
        return saved.getCompound(ID.asString());
    }

    private List<Float> progress() {
        List<Float> progress = new ArrayList<>();
        for (var tag : state().getList("grind_items")) {
            var slot = (CompoundTag) tag;
            var plan = CookingPlan.load(slot.getCompound("processing_plan"));
            check(plan.valid() && plan.matched() && plan.workRequired() == 1, "Millstone lost the one-rotation recipe snapshot");
            check(!slot.getBoolean("processing_blocked"), "Millstone blocked the saved recipe");
            progress.add(slot.getFloat("progress_degrees"));
        }
        check(progress.size() == RAW_COUNT, "Expected exactly two raw slots");
        return progress;
    }

    private void later(long ticks, CheckedAction action) {
        Bukkit.getRegionScheduler().runDelayed(plugin, world, CHUNK_X, CHUNK_Z, task -> guard(action), ticks);
    }

    private void guard(CheckedAction action) {
        if (finished) return;
        try { action.run(); }
        catch (Exception | LinkageError failure) {
            failures.add(failure.toString());
            plugin.getLogger().log(Level.SEVERE, "Millstone fixture failed", failure);
            finish();
        }
    }

    private void finish() {
        if (finished) return;
        finished = true;
        HandlerList.unregisterAll(this);
        report.put("sourceCommit", CookeryCatalog.SOURCE_COMMIT);
        report.put("craftEngine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
        report.put("server", Bukkit.getVersion());
        report.put("completionEvents", completionEvents);
        report.put("spawnedOutput", spawnedOutput);
        report.put("driveSamples", driveSamples);
        report.put("failures", failures);
        report.put("method", "CE_FURNITURE_TICKS_AND_COW; real disk restart, no reflected progression");
        report.put("playerFlow", "NOT_TESTED");
        report.put("clientResourcePack", "NOT_TESTED");
        try { write(plugin.getDataFolder().toPath().resolve("millstone-smoke.json"), report); }
        catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "Cannot write millstone evidence", failure); }
        plugin.getLogger().info("COOKERY_MILLSTONE_SMOKE phase=" + report.get("phase") + " failures=" + failures.size());
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value), StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
