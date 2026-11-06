package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.RecipeMenuHooks;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.api.ui.MenuScreen;
import net.kaleidoscope.cookery.api.ui.RecipeMenuStyle;

import net.kaleidoscope.cookery.recipe.AccurateFoodRecipe;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.ChoppingBoardRecipe;
import net.kaleidoscope.cookery.recipe.ChoppingResult;
import net.kaleidoscope.cookery.recipe.FlexFoodRecipe;
import net.kaleidoscope.cookery.recipe.StockpotRecipe;
import net.kaleidoscope.cookery.recipe.PotRecipe;
import net.kaleidoscope.cookery.recipe.TeapotRecipe;
import net.kaleidoscope.cookery.recipe.BambooTrayRecipe;
import net.kaleidoscope.cookery.recipe.WeightedResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.Gui;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

// 只读的食谱详情 原料与成品各占一排真实物品图标 比挤在 lore 里直观
// 没有任何可写入口 所有点击一律 cancel 浏览权限的玩家也能安全打开
public final class RecipeDetailMenu {
    private RecipeDetailMenu() {
    }

    // R 成品 I 原料 H 品质提示 C 盛装容器 L 汤底 B 返回
    private static final String[] LAYOUT = {
            "#########",
            "#IIIIIII#",
            "#########",
            "#HR#C#L##",
            "#B#######"};

    private static final int MAX_SLOTS = 7;

    public static void openPot(org.bukkit.entity.Player player, PotRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(player, ApplianceType.POT, recipe.id())) return;
        Player viewer = RecipeMenus.adapt(player);
        if (viewer == null) return;
        List<Item> inputs = new ArrayList<>();
        List<Component> explanation = new ArrayList<>();
        explanation.add(Component.text(recipe.mode() == PotRecipe.Mode.EXACT ? "每格消耗一个原料" : "同种物品合并后匹配，全部原料参与烹饪"));
        explanation.add(Component.text("翻炒 " + recipe.stirFryCount() + " 次，最后一次动画结束后出锅"));
        explanation.add(Component.text("产出：" + recipe.resultCount() + " 份"));
        int slot = 0;
        for (var choices : recipe.ingredients()) {
            List<Key> sorted = choices.stream().sorted(java.util.Comparator.comparing(Key::asString)).toList();
            inputs.add(MenuIcons.nativePreview(sorted.getFirst(), viewer));
            explanation.add(Component.text("第 " + (++slot) + " 格可用：" + sorted.stream()
                    .map(MenuIcons::itemNameText).collect(java.util.stream.Collectors.joining(" / "))));
        }
        Item output = MenuIcons.nativePreview(recipe.result(), viewer).copyWithCount(recipe.resultCount());
        Item details = MenuIcons.icon(MenuButton.ROTATION, viewer, MenuIcons.text("加工说明", NamedTextColor.GOLD), explanation);
        openTitled(viewer, Component.text("炒锅 · " + (recipe.mode() == PotRecipe.Mode.EXACT ? "精准配方" : "种类配方")),
                inputs, List.of(output), recipe.carrier(), List.of(), true, back, details);
    }

    public static void openStockpot(org.bukkit.entity.Player player, StockpotRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(player, ApplianceType.STOCKPOT, recipe.id())) return;
        Player viewer = RecipeMenus.adapt(player);
        if (viewer == null) return;
        List<Item> inputs = new ArrayList<>();
        List<Component> explanation = new ArrayList<>();
        explanation.add(Component.text(recipe.mode() == StockpotRecipe.Mode.EXACT ? "每格消耗一个原料" : "同种物品合并后匹配，全部原料参与烹饪"));
        explanation.add(Component.text("烹饪时间：" + RecipeProcessingControls.seconds(recipe.time()) + " 秒"));
        explanation.add(Component.text("产出：" + recipe.resultCount() + " 份"));
        int slot = 0;
        for (var choices : recipe.ingredients()) {
            List<Key> sorted = choices.stream().sorted(java.util.Comparator.comparing(Key::asString)).toList();
            inputs.add(MenuIcons.nativePreview(sorted.getFirst(), viewer));
            explanation.add(Component.text("第 " + (++slot) + " 格可用：" + sorted.stream()
                    .map(MenuIcons::itemNameText).collect(java.util.stream.Collectors.joining(" / "))));
        }
        Item result = MenuIcons.nativePreview(recipe.result(), viewer).copyWithCount(recipe.resultCount());
        Item details = MenuIcons.icon(MenuButton.ROTATION, viewer, MenuIcons.text("加工说明", NamedTextColor.GOLD), explanation);
        openTitled(viewer, Component.text("高汤锅 · " + (recipe.mode() == StockpotRecipe.Mode.EXACT ? "精准配方" : "种类配方")),
                inputs, List.of(result), recipe.carrier(), List.of(recipe.soupBase()), true, back, details);
    }

    public static void openAccurate(org.bukkit.entity.Player bukkitPlayer, AccurateFoodRecipe recipe,
                                    Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, recipe.cook(), recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> results = new ArrayList<>();
        for (WeightedResult r : recipe.results()) {
            results.add(MenuIcons.nativePreview(r.key(), viewer));
        }
        List<Item> inputs = List.of(MenuIcons.nativePreview(recipe.input(), viewer));

        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_ACCURATE, "recipe",
                MenuIcons.itemNameText(recipe.primaryResult())), inputs, results, null, List.of(), false, back);
    }

    public static void openFlex(org.bukkit.entity.Player bukkitPlayer, FlexFoodRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, recipe.cook(), recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = new ArrayList<>();
        for (Key input : recipe.perfect().keySet()) {
            inputs.add(MenuIcons.nativePreview(input, viewer));
        }
        List<Item> results = List.of(MenuIcons.nativePreview(recipe.result(), viewer));

        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_FLEX, "recipe",
                MenuIcons.itemNameText(recipe.result())), inputs, results,
                recipe.carrier(), recipe.liquids(), true, back);
    }


    public static void openChopping(org.bukkit.entity.Player bukkitPlayer,
                                    ChoppingBoardRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, ApplianceType.CHOPPING_BOARD, recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = List.of(MenuIcons.nativePreview(recipe.input(), viewer));
        List<Item> results = new ArrayList<>();
        for (ChoppingResult r : recipe.results()) {
            results.add(MenuIcons.nativePreview(r.key(), viewer));
        }
        Key titleKey = recipe.results().isEmpty() ? recipe.input() : recipe.results().get(0).key();
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_CHOPPING, "recipe",
                MenuIcons.itemNameText(titleKey)), inputs, results, null, List.of(), false, back);
    }

    public static void openTeapot(org.bukkit.entity.Player bukkitPlayer,
                                  TeapotRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(bukkitPlayer, ApplianceType.TEAPOT, recipe.id())) {
            return;
        }
        Player viewer = RecipeMenus.adapt(bukkitPlayer);
        if (viewer == null) {
            return;
        }
        List<Item> inputs = List.of(MenuIcons.nativePreview(recipe.input(), viewer));
        List<Item> results = List.of(MenuIcons.nativePreview(recipe.result(), viewer));
        openTitled(viewer, RecipeMenuStyle.instance().title(MenuScreen.DETAIL_TEAPOT, "recipe",
                MenuIcons.itemNameText(recipe.result())), inputs, results,
                null, List.of(recipe.fluid()), false, back);
    }

    private static void openTitled(Player viewer, Component title, List<Item> inputs, List<Item> results,
                             Key carrier, List<Key> liquids, boolean showQualityHint, Runnable back) {
        openTitled(viewer, title, inputs, results, carrier, liquids, showQualityHint, back, null);
    }

    private static void openTitled(Player viewer, Component title, List<Item> inputs, List<Item> results,
                                  Key carrier, List<Key> liquids, boolean showQualityHint, Runnable back, Item details) {
        GuiLayout layout = new GuiLayout(details != null ? new String[]{"#########", "IIIIIIIII", "#########", "#HR#C#L#T", "#B#######"} : LAYOUT);
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        if (details != null) layout.addIngredient('T', MenuIcons.button(details, () -> {}));

        // 原料栏沿用当前菜单的一排布局。
        int maximum = details != null ? 9 : MAX_SLOTS;
        List<Item> shown = inputs.size() > maximum ? inputs.subList(0, maximum) : inputs;
        List<GuiElement> slots = new ArrayList<>();
        for (Item item : shown) {
            slots.add(MenuIcons.button(item, () -> {}));
        }
        while (slots.size() < maximum) {
            slots.add(MenuIcons.empty());
        }
        // Ingredient 没有列表工厂 按调用顺序逐格取 和编辑页的写法一致
        layout.addIngredient('I', new Ingredient() {
            private int index = 0;

            @Override
            public GuiElement element(Gui gui) {
                return index < slots.size() ? slots.get(index++) : MenuIcons.empty();
            }
        });

        Item result = results.isEmpty()
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("无成品", NamedTextColor.RED))
                : results.get(0);
        layout.addIngredient('R', MenuIcons.button(result, () -> {}));
        layout.addIngredient('H', showQualityHint ? MenuIcons.qualityHint(viewer) : MenuIcons.filler(viewer));

        layout.addIngredient('C', MenuIcons.button(carrierIcon(viewer, carrier), () -> {}));
        layout.addIngredient('L', MenuIcons.button(liquidIcon(viewer, liquids), () -> {}));
        layout.addIngredient('B', MenuIcons.back(viewer, back));

        Gui gui = BasicGuiImpl.builder()
                .layout(layout)
                .inventoryClickConsumer(RecipeMenus.inventoryGuard())
                .build();
        gui.title(title)
                .refresh()
                .open(viewer);
    }

    public static void openBambooTray(org.bukkit.entity.Player player, BambooTrayRecipe recipe, Runnable back) {
        if (RecipeMenuHooks.instance().dispatchDetail(player, ApplianceType.BAMBOO_TRAY, recipe.id())) return;
        Player viewer = RecipeMenus.adapt(player);
        if (viewer == null) return;
        GuiLayout layout = new GuiLayout("#########", "#I##R##M#", "####C####", "#B#######");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('I', MenuIcons.button(BambooTrayEditMenu.inputIcon(viewer, recipe.input()), () -> {}));
        layout.addIngredient('R', MenuIcons.button(MenuIcons.nativePreview(recipe.result(), viewer), () -> {}));
        layout.addIngredient('M', MenuIcons.button(BambooTrayEditMenu.modeIcon(viewer, recipe.mode()), () -> {}));
        layout.addIngredient('C', MenuIcons.button(MenuIcons.icon(MenuButton.ROTATION, viewer,
                MenuIcons.text("加工说明", NamedTextColor.GOLD),
                MenuIcons.lore("每件原料产出 " + recipe.resultCount() + " 件",
                        "累计时间：" + RecipeProcessingControls.seconds(recipe.durationTicks()) + " 秒",
                        "时间由服务器 tick 累计，受环境条件限制", "竹筛每 19 tick 检查加工结果")), () -> {}));
        layout.addIngredient('B', MenuIcons.back(viewer, back));
        BasicGuiImpl.builder().layout(layout).inventoryClickConsumer(RecipeMenus.inventoryGuard()).build()
                .title(RecipeMenuStyle.instance().title(MenuScreen.DETAIL_BAMBOO_TRAY,
                        "recipe", MenuIcons.itemNameText(recipe.result())))
                .refresh().open(viewer);
    }

    private static Item carrierIcon(Player viewer, Key carrier) {
        if (carrier == null) {
            return MenuIcons.icon(MenuButton.FILLER, viewer,
                    MenuIcons.text("空手盛出", NamedTextColor.GREEN),
                    MenuIcons.lore("这道菜不需要容器"));
        }
        return MenuIcons.nativePreview(carrier, viewer);
    }

    private static Item liquidIcon(Player viewer, List<Key> liquids) {
        if (liquids.isEmpty()) {
            return MenuIcons.icon(MenuButton.FILLER, viewer,
                    MenuIcons.text("不限汤底", NamedTextColor.GRAY));
        }
        return MenuIcons.nativePreview(MenuIcons.liquidIconKey(liquids.get(0)), viewer);
    }
}
