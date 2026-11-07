package net.kaleidoscope.doll;

import net.kaleidoscope.series.FurnitureTypes;
import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.Menu;
import net.kaleidoscope.series.Resources;
import net.kaleidoscope.series.SeriesPlugin;
import net.kaleidoscope.doll.api.DollCollectedEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class DollPlugin extends SeriesPlugin implements Listener {
    private DollCatalog catalog;
    private NamespacedKey collectionKey;
    private GiftBoxes gifts;
    private MovingDolls moving;

    @Override protected void start() {
        catalog = Resources.read(this, "catalog.json", DollCatalog.class);
        saveDefaultConfig();
        moving = new MovingDolls(this);
        getServer().getPluginManager().registerEvents(moving, this);
        moving.start();
        collectionKey = new NamespacedKey(this, "collection_v1");
        gifts = new GiftBoxes(this);
        FurnitureTypes.register("kaleidoscope_doll:lottery", furniture -> new LotteryController(furniture, this));
        FurnitureTypes.register("kaleidoscope_doll:computer", furniture -> new ComputerController(furniture, this));
        FurnitureTypes.register("kaleidoscope_doll:doll", furniture -> new DollController(furniture, this));
        getServer().getPluginManager().registerEvents(gifts, this);
        getServer().getPluginManager().registerEvents(this, this);
        command("kdoll", (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("请在游戏内使用。"); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("kit") && player.hasPermission("kaleidoscopedoll.admin")) {
                attempt(player, () -> {
                    var rewards = List.of(Items.create("kaleidoscope_doll:doll_machine"),
                            Items.create("kaleidoscope_doll:computer"), Items.create("kaleidoscope_doll:tweaks_tool"));
                    if (!InventoryTrade.exchange(player.getInventory(), s -> false, 0, rewards)) player.sendMessage("背包空间不足。");
                });
            } else {
                attempt(player, () -> openCollection(player, 0));
            }
            return true;
        });
    }

    @Override protected void stop() { if (moving != null) moving.close(); }

    public DollCatalog catalog() { return catalog; }
    public GiftBoxes gifts() { return gifts; }

    public void collected(Player player, String id) {
        if (!catalog.dolls().contains(id)) return;
        Set<String> owned = collection(player);
        boolean first = owned.add(id);
        getServer().getPluginManager().callEvent(new DollCollectedEvent(player, id, first));
        if (first) player.getPersistentDataContainer().set(collectionKey, PersistentDataType.STRING,
                String.join("\n", owned.stream().sorted().toList()));
    }

    private Set<String> collection(Player player) {
        String saved = player.getPersistentDataContainer().get(collectionKey, PersistentDataType.STRING);
        return saved == null ? new HashSet<>() : new HashSet<>(Arrays.asList(saved.split("\n")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pickedUp(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && event.getRemaining() < event.getItem().getItemStack().getAmount()) {
            collected(player, Items.id(event.getItem().getItemStack()));
        }
    }

    private void openCollection(Player player, int page) {
        Set<String> owned = collection(player);
        List<String> ids = catalog.dolls();
        Menu menu = new Menu(player, this, "玩偶收藏 " + owned.size() + "/" + ids.size(), 6);
        for (int i = 0; i < 45 && page * 45 + i < ids.size(); i++) {
            String id = ids.get(page * 45 + i);
            String name = catalog.names().getOrDefault(id, id);
            menu.button(i, owned.contains(id) ? Items.create(id)
                    : Items.icon(Material.GRAY_DYE, "未收集 · " + name), null);
        }
        pages(menu, player, page, ids.size(), p -> openCollection(player, p));
        menu.open(player);
    }

    public void openComputer(Player player, int page, Predicate<Player> accessible) {
        if (!accessible.test(player)) return;
        List<String> ids = catalog.players();
        Menu menu = new Menu(player, this, "玩偶电脑 · 每只 1 块羊毛", 6);
        for (int i = 0; i < 45 && page * 45 + i < ids.size(); i++) {
            String id = ids.get(page * 45 + i);
            menu.button(i, Items.create(id), visitor -> {
                if (!accessible.test(visitor)) { visitor.closeInventory(); return; }
                var reward = Items.create(id);
                boolean exchanged = InventoryTrade.exchange(visitor.getInventory(), s ->
                        Items.id(s).startsWith("minecraft:") && org.bukkit.Tag.WOOL.isTagged(s.getType()), 1, List.of(reward));
                if (exchanged) collected(visitor, id);
                else visitor.sendMessage("需要 1 块羊毛和足够的背包空间。");
            });
        }
        pages(menu, player, page, ids.size(), p -> openComputer(player, p, accessible));
        menu.open(player);
    }

    private void pages(Menu menu, Player player, int page, int size, java.util.function.IntConsumer open) {
        if (page > 0) menu.button(45, Items.icon(Material.ARROW, "上一页"), p -> open.accept(page - 1));
        if ((page + 1) * 45 < size) menu.button(53, Items.icon(Material.ARROW, "下一页"), p -> open.accept(page + 1));
    }
}
