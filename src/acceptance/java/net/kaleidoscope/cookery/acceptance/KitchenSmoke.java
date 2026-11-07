package net.kaleidoscope.cookery.acceptance;

import com.google.gson.GsonBuilder;
import net.kaleidoscope.cookery.block.entity.*;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Opt-in fixture that runs on the owning region and never uses a real player. */
final class KitchenSmoke {
    private record Appliance(String name, Class<?> controller, int states) {}
    private static final List<Appliance> APPLIANCES = List.of(
            new Appliance("chopping_board", ChoppingBoardController.class, 4),
            new Appliance("enamel_basin", EnamelBasinController.class, 20),
            new Appliance("kitchenware_racks", KitchenwareRacksController.class, 4),
            new Appliance("pot", PotController.class, 16),
            new Appliance("shawarma_spit", ShawarmaSpitController.class, 16),
            new Appliance("steamer", SteamerController.class, 48),
            new Appliance("stockpot", StockpotController.class, 32),
            new Appliance("stove", StoveController.class, 8),
            new Appliance("fruit_basket", FruitBasketController.class, 4),
            new Appliance("teapot", TeapotController.class, 16),
            new Appliance("teacup_coaster", TeacupCoasterController.class, 4),
            new Appliance("oil_pot", OilPotController.class, 8),
            new Appliance("bamboo_tray", BambooTrayController.class, 2),
            new Appliance("apple_platter", PlateController.class, 20),
            new Appliance("baozi_plate", PlateController.class, 24),
            new Appliance("berry_platter", PlateController.class, 20),
            new Appliance("chorus_fruit_platter", PlateController.class, 24),
            new Appliance("qingtuan_plate", PlateController.class, 20),
            new Appliance("shengjian_mantou_plate", PlateController.class, 24),
            new Appliance("sticky_candy_plate", PlateController.class, 20),
            new Appliance("sticky_rice_cake_plate", PlateController.class, 24),
            new Appliance("tomato_platter", PlateController.class, 24),
            new Appliance("watermelon_platter", PlateController.class, 16),
            new Appliance("zongzi_plate", PlateController.class, 20));
    private final JavaPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private int checkedStates;
    private int checkedBlocks;
    private int placedBlocks;
    private int reloadedBlocks;
    private int nonemptyContainers;
    private int checkedItems;

    private KitchenSmoke(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static void start(JavaPlugin plugin) {
        KitchenSmoke probe = new KitchenSmoke(plugin);
        World world = Bukkit.getWorld("acceptance-world");
        if (world == null) {
            probe.failures.add("Kitchen fixture requires the isolated acceptance-world");
            probe.finish();
            return;
        }
        world.getChunkAtAsync(0, 0).whenComplete((chunk, exception) -> {
            if (exception != null) {
                probe.failures.add("Cannot load fixture chunk: " + exception);
                probe.finish();
                return;
            }
            Bukkit.getRegionScheduler().execute(plugin, world, 0, 0, () -> probe.place(world));
        });
    }

    private Location position(World world, int index) {
        return new Location(world, 2 + index % 4 * 3, 120, 2 + index / 4 * 4);
    }

    private void place(World world) {
        verifyItems();
        boolean reload = Files.isRegularFile(plugin.getDataFolder().toPath().resolve("kitchen-placed.json"));
        world.addPluginChunkTicket(0, 0, plugin);
        for (int index = 0; index < APPLIANCES.size(); index++) {
            Appliance appliance = APPLIANCES.get(index);
            try {
                Key id = Key.of("kaleidoscopecookery:" + appliance.name());
                var definition = CraftEngineBlocks.byId(id);
                check(definition != null, id + ": missing block definition");
                check(definition.variantProvider().states().size() == appliance.states(), id + ": missing states");
                for (ImmutableBlockState state : definition.variantProvider().states()) {
                    check(state.visualBlockState() != null, state + ": missing visual state");
                    check(definition.getBlockState(state.propertiesNbt()) == state, state + ": state serialization differs");
                    BlockEntity detached = new BlockEntity(new BlockPos(0, 120, 0), state);
                    check(detached.controller.get(appliance.controller(), 0) != null, state + ": missing controller");
                    detached.saveAsTag();
                    checkedStates++;
                }
                Location location = position(world, index);
                if (!reload) {
                    location.clone().subtract(0, 1, 0).getBlock().setType(Material.STONE);
                    check(CraftEngineBlocks.place(location, definition.defaultState(), UpdateFlags.UPDATE_ALL, false),
                            id + ": API placement failed");
                    if (appliance.name().equals("shawarma_spit")) {
                        CompoundTag upper = new CompoundTag();
                        upper.putString("half", "upper");
                        check(CraftEngineBlocks.place(location.clone().add(0, 1, 0), definition.getBlockState(upper),
                                UpdateFlags.UPDATE_ALL, false), id + ": upper API placement failed");
                    }
                    placedBlocks++;
                }
                checkedBlocks++;
            } catch (Exception | LinkageError exception) {
                failures.add(appliance.name() + ": " + exception);
            }
        }
        Bukkit.getRegionScheduler().runDelayed(plugin, world, 0, 0, task -> inspect(world, reload), 20);
    }

    private void verifyItems() {
        try {
            var config = new YamlConfiguration();
            config.load(plugin.getDataFolder().toPath().getParent()
                    .resolve("CraftEngine/resources/cookery_kitchen/configuration/kitchen.yml").toFile());
            var items = config.getConfigurationSection("items");
            check(items != null, "Kitchen fixture has no items section");
            for (String id : items.getKeys(false)) {
                var buildable = BukkitItemManager.instance().getBuildableItem(Key.of(id)).orElseThrow();
                var built = buildable.buildItem((net.momirealms.craftengine.core.entity.player.Player) null);
                ItemStack stack = ItemStackUtils.getBukkitStack(built.minecraftItem());
                check(!stack.isEmpty(), id + ": empty constructed item");
                check(stack.equals(ItemStack.deserializeBytes(stack.serializeAsBytes())), id + ": item serialization differs");
                checkedItems++;
            }
        } catch (Exception | LinkageError exception) {
            failures.add("Kitchen item construction: " + exception);
        }
    }

    private void inspect(World world, boolean reload) {
        var storage = BukkitAdaptor.adapt(world).storageWorld();
        for (int index = 0; index < APPLIANCES.size(); index++) {
            Appliance appliance = APPLIANCES.get(index);
            Location location = position(world, index);
            try {
                var state = CraftEngineBlocks.getCustomBlockState(location.getBlock());
                check(state != null && state.owner().value().id().value().equals(appliance.name()),
                        appliance.name() + ": placed block missing/changed");
                var pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                BlockEntity entity = storage.getBlockEntityAtIfLoaded(pos);
                check(entity != null && entity.controller.get(appliance.controller(), 0) != null,
                        appliance.name() + ": live controller missing");
                if (reload) reloadedBlocks++;
                if (appliance.name().equals("enamel_basin")) {
                    var basin = entity.controller.get(EnamelBasinController.class, 0);
                    if (!reload) {
                        basin.addOil(7);
                        basin.setClosed(false);
                    }
                    check(basin.getOilCount() == 7 && !basin.isClosed(), "basin oil or lid lost");
                    nonemptyContainers++;
                }
                if (appliance.name().equals("oil_pot")) {
                    var pot = entity.controller.get(OilPotController.class, 0);
                    if (!reload) pot.setOilCount(87);
                    check(pot.oilCount() == 87, "oil pot contents lost");
                    nonemptyContainers++;
                }
                if (appliance.name().equals("shawarma_spit")) {
                    var upper = CraftEngineBlocks.getCustomBlockState(location.clone().add(0, 1, 0).getBlock());
                    check(upper != null && "upper".equals(upper.propertiesNbt().getString("half")), "shawarma upper half lost");
                }
            } catch (Exception | LinkageError exception) {
                failures.add(appliance.name() + ": " + exception);
            }
        }
        if (!reload && failures.isEmpty()) {
            try {
                Files.writeString(plugin.getDataFolder().toPath().resolve("kitchen-placed.json"),
                        "{\"sourceCommit\":\"" + CookeryCatalog.SOURCE_COMMIT + "\",\"blocks\":24}\n", StandardCharsets.UTF_8);
            } catch (IOException exception) {
                failures.add("Cannot save fixture checkpoint: " + exception);
            }
        }
        finish();
    }

    private void finish() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sourceCommit", CookeryCatalog.SOURCE_COMMIT);
        report.put("server", Bukkit.getVersion());
        report.put("craftEngine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
        report.put("checkedBlocks", checkedBlocks);
        report.put("checkedStates", checkedStates);
        report.put("checkedItems", checkedItems);
        report.put("placedBlocks", placedBlocks);
        report.put("reloadedBlocks", reloadedBlocks);
        report.put("nonemptyContainers", nonemptyContainers);
        report.put("failures", failures);
        report.put("placementMethod", "SERVER_API; player placement and multi-block item behavior NOT_TESTED");
        report.put("playerFlow", "NOT_TESTED");
        report.put("clientResourcePack", "NOT_TESTED");
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(plugin.getDataFolder().toPath().resolve("kitchen-smoke.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot write kitchen smoke evidence", exception);
        }
        plugin.getLogger().log(failures.isEmpty() ? Level.INFO : Level.SEVERE,
                "COOKERY_KITCHEN_SMOKE blocks=" + checkedBlocks + " states=" + checkedStates + " failures=" + failures.size());
        failures.forEach(plugin.getLogger()::severe);
    }

    private static void check(boolean condition, String failure) {
        if (!condition) throw new IllegalStateException(failure);
    }
}
