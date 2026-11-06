package net.kaleidoscope.deco;

import net.kaleidoscope.series.InventoryTrade;
import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.Menu;
import net.kaleidoscope.series.Resources;
import net.kaleidoscope.series.SeriesPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class DecoPlugin extends SeriesPlugin {
    private Catalog catalog;
    private java.util.Map<String, Entry> entries;
    private org.bukkit.NamespacedKey stateKey;
    private java.util.Set<String> styles;
    org.bukkit.NamespacedKey stateKey() { return stateKey; }
    java.util.Set<String> styles() { return styles; }
    Entry entry(String id) { return java.util.Objects.requireNonNull(entries.get(id), "Unknown Deco state: " + id); }
    boolean reachable(String origin, String target) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(); queue.add(origin);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (current.equals(target)) return true;
            if (!visited.add(current)) continue;
            for (DecoRule rule : entry(current).rules()) if (!rule.target().isEmpty()) queue.add(rule.target());
        }
        return false;
    }

    public record Entry(String id, String category, String source, List<DecoRule> rules) {
        public Entry { net.kaleidoscope.series.ItemIds.require(id); rules = List.copyOf(rules); }
    }
    public record Catalog(List<Entry> entries, List<String> styles) {
        public Catalog { entries = List.copyOf(entries); styles = List.copyOf(styles); }
    }

    @Override protected void start() {
        catalog = Resources.read(this, "catalog.json", Catalog.class);
        entries = catalog.entries().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Entry::id, e -> e));
        styles = java.util.Set.copyOf(catalog.styles());
        stateKey = new org.bukkit.NamespacedKey(this, "deco_state_v1");
        net.kaleidoscope.series.FurnitureTypes.register("kaleidoscope_deco:interaction", f -> new DecoController(f, this));
        command("kdeco", (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("请在游戏内使用。"); return true; }
            attempt(player, () -> categories(player));
            return true;
        });
    }

    private void categories(Player player) {
        Menu menu = new Menu(player, this, "森罗装饰 · 选择主题", 3);
        int slot = 0;
        for (String category : catalog.entries().stream().map(Entry::category).distinct().sorted().toList()) {
            menu.button(slot++, Items.icon(Material.ITEM_FRAME, category.replaceFirst("^kaleidoscope_", "")), p -> page(p, category, 0));
        }
        if (player.hasPermission("kaleidoscopedeco.give")) menu.button(26, Items.icon(Material.STICK, "领取家具状态工具"), p -> {
            if (!InventoryTrade.give(p.getInventory(), Items.create("kaleidoscope_deco:state_tool"))) p.sendMessage("背包空间不足。");
        });
        menu.open(player);
    }

    private void page(Player player, String category, int page) {
        List<Entry> entries = catalog.entries().stream().filter(e -> e.category().equals(category)).toList();
        Menu menu = new Menu(player, this, "森罗装饰 · " + (page + 1), 6);
        for (int i = 0; i < 45 && page * 45 + i < entries.size(); i++) {
            Entry entry = entries.get(page * 45 + i);
            menu.button(i, Items.create(entry.id()), p -> {
                if (!p.hasPermission("kaleidoscopedeco.give")) { p.sendMessage("图鉴供浏览，领取物品需要管理员权限。"); return; }
                if (!InventoryTrade.give(p.getInventory(), Items.create(entry.id()))) p.sendMessage("背包空间不足。");
            });
        }
        if (page > 0) menu.button(45, Items.icon(Material.ARROW, "上一页"), p -> page(p, category, page - 1));
        menu.button(49, Items.icon(Material.BOOK, "返回主题"), this::categories);
        if ((page + 1) * 45 < entries.size()) menu.button(53, Items.icon(Material.ARROW, "下一页"), p -> page(p, category, page + 1));
        menu.open(player);
    }
}
