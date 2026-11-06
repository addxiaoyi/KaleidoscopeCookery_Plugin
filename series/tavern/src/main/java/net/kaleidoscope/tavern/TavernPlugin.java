package net.kaleidoscope.tavern;

import net.kaleidoscope.series.FurnitureTypes;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.Menu;
import net.kaleidoscope.series.Resources;
import net.kaleidoscope.series.SeriesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class TavernPlugin extends SeriesPlugin {
    private TavernCatalog catalog;
    private NamespacedKey brewLevel;
    private Drinks drinks;
    private TavernEffects effects;
    private HandheldShaker shakers;
    final net.kaleidoscope.series.ControllerIndex<TapController> taps = new net.kaleidoscope.series.ControllerIndex<>();
    final net.kaleidoscope.series.ControllerIndex<BarrelController> barrels = new net.kaleidoscope.series.ControllerIndex<>();
    final net.kaleidoscope.series.ControllerIndex<BottleController> bottles = new net.kaleidoscope.series.ControllerIndex<>();
    final net.kaleidoscope.series.ControllerIndex<GrapeController> grapes = new net.kaleidoscope.series.ControllerIndex<>();

    @Override protected void start() {
        saveDefaultConfig();
        catalog = Resources.read(this, "catalog.json", TavernCatalog.class);
        brewLevel = new NamespacedKey(this, "brew_level_v1");
        effects = new TavernEffects(this);
        drinks = new Drinks(this, effects);
        getServer().getPluginManager().registerEvents(drinks, this);
        getServer().getPluginManager().registerEvents(effects, this);
        effects.start();
        shakers = new HandheldShaker(this);
        getServer().getPluginManager().registerEvents(shakers, this);
        getServer().getPluginManager().registerEvents(new TapRedstone(this), this);
        FurnitureTypes.register("kaleidoscope_tavern:barrel", f -> new BarrelController(f, this));
        FurnitureTypes.register("kaleidoscope_tavern:pressing_tub", f -> new PressingController(f, this));
        FurnitureTypes.register("kaleidoscope_tavern:shaker", f -> new ShakerController(f, this));
        FurnitureTypes.register("kaleidoscope_tavern:tap", f -> new TapController(f, this));
        FurnitureTypes.register("kaleidoscope_tavern:bottle", f -> new BottleController(f, this));
        FurnitureTypes.register("kaleidoscope_tavern:grape", f -> new GrapeController(f, this));
        command("ktavern", (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("请在游戏内使用。"); return true; }
            attempt(player, () -> {
                if (args.length == 1 && args[0].equalsIgnoreCase("kit") && player.hasPermission("kaleidoscopetavern.admin")) {
                    var kit = List.of(Items.create("kaleidoscope_tavern:barrel"), Items.create("kaleidoscope_tavern:pressing_tub"),
                            Items.create("kaleidoscope_tavern:empty_bottle").asQuantity(16),
                            Items.create("kaleidoscope_tavern:trellis"), Items.create("kaleidoscope_tavern:grapevine"),
                            Items.create("kaleidoscope_tavern:tap"), Items.create("kaleidoscope_tavern:shaker"), Items.create("kaleidoscope_tavern:empty_glassware").asQuantity(16));
                    if (!InventoryTrade.exchange(player.getInventory(), s -> false, 0, kit)) player.sendMessage("背包空间不足。");
                } else recipes(player);
            });
            return true;
        });
    }

    public TavernCatalog catalog() { return catalog; }
    HandheldShaker shakers() { return shakers; }
    public Drinks drinks() { return drinks; }
    public int brewLevel(ItemStack item) {
        return Math.clamp(item.getItemMeta().getPersistentDataContainer().getOrDefault(brewLevel, PersistentDataType.INTEGER, 0), 0, 6);
    }

    @Override protected void stop() { if (effects != null) effects.stop(); if (shakers != null) shakers.close(); barrels.clear(); bottles.clear(); grapes.clear(); taps.clear(); }

    public ItemStack bottle(BrewBatch batch) {
        ItemStack item = Items.create(batch.recipe().output());
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(brewLevel, PersistentDataType.INTEGER, batch.level());
        meta.lore(List.of(net.kyori.adventure.text.Component.text("熟成等级：" + batch.level() + " / 6")));
        item.setItemMeta(meta);
        return item;
    }

    private void recipes(Player player) {
        Menu menu = new Menu(player, this, "酒桶配方 · 4 桶液体 + 原料", 4);
        int slot = 0;
        for (BrewRecipe recipe : catalog.barrel()) {
            ItemStack icon = Items.create(recipe.output());
            var meta = icon.getItemMeta();
            var lore = new java.util.ArrayList<net.kyori.adventure.text.Component>();
            lore.add(net.kyori.adventure.text.Component.text("液体：" + recipe.fluid() + " × 4 桶"));
            for (List<String> group : recipe.ingredients()) lore.add(net.kyori.adventure.text.Component.text("原料：" + String.join(" / ", group)));
            lore.add(net.kyori.adventure.text.Component.text("每槽最多 16 个，产量取最少的一槽"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            menu.button(slot++, icon, null);
        }
        menu.open(player);
    }
}
