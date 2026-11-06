package net.kaleidoscope.cookery.ui;

import net.kaleidoscope.cookery.api.ui.MenuButton;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import net.kaleidoscope.cookery.recipe.edit.BambooTrayRecipeDraft;
import net.kaleidoscope.cookery.recipe.edit.RecipeEditService;
import net.kaleidoscope.cookery.ui.input.MenuInput;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.gui.BasicGuiImpl;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.Ingredient;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.adventure.text.format.NamedTextColor;

import java.util.List;

public final class BambooTrayEditMenu {
    private BambooTrayEditMenu() {}

    public static void open(org.bukkit.entity.Player player, BambooTrayRecipeDraft draft) {
        Player viewer = RecipeMenus.adapt(player);
        if (viewer == null) return;
        GuiLayout layout = new GuiLayout("####?####", "#I#R#M#T#", "###C#N###", "B###S###D");
        layout.addIngredient('#', Ingredient.simple(MenuIcons.filler(viewer)));
        layout.addIngredient('?', MenuIcons.editingHelp(viewer,
                "原料：物品 id 或 #item_tags 标签，整槽同时加工",
                "原料与成品：左键设置，光标持物品可直接选取",
                "晾晒与淋湿使用各自的环境条件",
                "保存后按当前配方重新匹配，不固定旧批次"));
        layout.addIngredient('I', inputSlot(player, viewer, draft));
        layout.addIngredient('R', resultSlot(player, viewer, draft));
        layout.addIngredient('M', modeSlot(player, viewer, draft));
        layout.addIngredient('T', idSlot(player, viewer, draft));
        layout.addIngredient('C', timeSlot(player, viewer, draft));
        layout.addIngredient('N', countSlot(player, viewer, draft));
        layout.addIngredient('B', MenuIcons.back(viewer,
                () -> RecipeListMenu.open(player, ApplianceType.BAMBOO_TRAY, true)));
        layout.addIngredient('S', saveSlot(player, viewer, draft));
        layout.addIngredient('D', deleteSlot(player, viewer, draft));
        BasicGuiImpl.builder().layout(layout).inventoryClickConsumer(RecipeMenus.inventoryGuard()).build()
                .title(MenuIcons.text(draft.isNew() ? "新建竹筛食谱" : "编辑竹筛食谱", NamedTextColor.DARK_GRAY))
                .refresh().open(viewer);
    }

    private static GuiElement inputSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return GuiElement.constant(inputIcon(viewer, draft.input()), (element, click) -> {
            click.cancel();
            Item cursor = click.itemOnCursor();
            if (!ItemUtils.isEmpty(cursor)) {
                draft.input(cursor.id().asString());
                open(player, draft);
                return;
            }
            MenuInput.requestText(player, "竹筛原料", "物品 id 或 #食材标签",
                    draft.input() == null ? "minecraft:" : draft.input(), raw -> {
                        String input = raw.trim();
                        String key = input.startsWith("#") ? input.substring(1) : input;
                        if (RecipeMenus.parseKey(key) == null) RecipeMenus.message(player, "原料 id 或标签格式不正确");
                        else draft.input(input);
                        open(player, draft);
                    }, () -> open(player, draft));
        });
    }

    static Item inputIcon(Player viewer, String input) {
        if (input == null || input.isBlank()) {
            return MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("设置原料", NamedTextColor.GOLD));
        }
        if (input.startsWith("#")) {
            return MenuIcons.icon(MenuButton.MODE, viewer, MenuIcons.text("食材标签", NamedTextColor.AQUA),
                    MenuIcons.lore(input, "匹配 item_tags 中该标签的物品"));
        }
        return MenuIcons.nativePreview(Key.of(input), viewer);
    }

    private static GuiElement resultSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        Item icon = draft.result() == null
                ? MenuIcons.icon(MenuButton.INVALID, viewer, MenuIcons.text("设置成品", NamedTextColor.GOLD))
                : MenuIcons.nativePreview(draft.result(), viewer);
        return GuiElement.constant(icon, (element, click) -> {
            click.cancel();
            AccurateEditMenu.pickItem(player, click, "竹筛成品", draft.result(), draft::result, () -> open(player, draft));
        });
    }

    private static GuiElement modeSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return MenuIcons.button(modeIcon(viewer, draft.mode()), () -> {
            draft.mode(draft.mode() == BambooTrayMode.DRYING ? BambooTrayMode.WETTING : BambooTrayMode.DRYING);
            open(player, draft);
        });
    }

    static Item modeIcon(Player viewer, BambooTrayMode mode) {
        boolean drying = mode == BambooTrayMode.DRYING;
        return MenuIcons.icon(MenuButton.MODE, viewer,
                MenuIcons.text(drying ? "晾晒" : "淋湿", NamedTextColor.AQUA),
                MenuIcons.lore(drying ? "符合晾晒环境时累计加工进度" : "符合淋湿环境时累计加工进度"));
    }

    private static GuiElement idSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return MenuIcons.button(MenuIcons.icon(MenuButton.CREATE, viewer,
                MenuIcons.text("食谱 id", NamedTextColor.GOLD), MenuIcons.lore(draft.id().asString())),
                () -> MenuInput.requestText(player, "食谱 id", "id", draft.id().asString(), raw -> {
                    Key id = RecipeMenus.parseKey(raw);
                    if (id == null) RecipeMenus.message(player, "食谱 id 格式不正确");
                    else draft.id(id);
                    open(player, draft);
                }, () -> open(player, draft)));
    }

    private static GuiElement timeSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return MenuIcons.button(MenuIcons.icon(MenuButton.ROTATION, viewer,
                MenuIcons.text("加工时间：" + RecipeProcessingControls.seconds(draft.durationTicks()) + " 秒", NamedTextColor.GOLD),
                MenuIcons.lore("左键输入秒数，精度 0.05 秒", "按服务器 tick 累计，受当前环境条件限制", "竹筛每 19 tick 检查加工结果")),
                () -> MenuInput.requestText(player, "竹筛加工时间", "秒（0.05 秒的整数倍）",
                        RecipeProcessingControls.seconds(draft.durationTicks()), raw -> {
                            try { draft.durationTicks(RecipeProcessingControls.secondsToTicks(raw)); }
                            catch (ArithmeticException | IllegalArgumentException exception) {
                                RecipeMenus.message(player, "请输入正数，须为 0.05 秒的整数倍，且不超过 2147483647 tick");
                            }
                            open(player, draft);
                        }, () -> open(player, draft)));
    }

    private static GuiElement countSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return MenuIcons.button(MenuIcons.icon(MenuButton.COUNT, viewer,
                MenuIcons.text("产出数量：" + draft.resultCount(), NamedTextColor.GOLD),
                MenuIcons.lore("每件原料的产量，整槽产物不超过最大堆叠数")),
                () -> MenuInput.requestInt(player, "产出数量", "每件原料的产量", draft.resultCount(), 1,
                        BambooTrayRecipeDraft.MAX_COUNT, count -> { draft.resultCount(count); open(player, draft); },
                        () -> open(player, draft)));
    }

    private static GuiElement saveSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        return MenuIcons.button(MenuIcons.icon(MenuButton.SAVE, viewer, MenuIcons.text("保存", NamedTextColor.GREEN)), () -> {
            var saving = RecipeEditSaves.save(draft, () -> RecipeEditService.saveBambooTray(draft));
            if (saving == null) return;
            RecipeMenus.message(player, "正在保存食谱...");
            saving.thenAccept(error -> MenuTasks.runFor(player, () -> {
                if (RecipeEditService.isClosed()) return;
                if (error != null) {
                    RecipeMenus.message(player, error);
                    open(player, draft);
                    return;
                }
                RecipeMenus.message(player, "已保存 " + draft.id().asString());
                RecipeListMenu.open(player, ApplianceType.BAMBOO_TRAY, true);
            }));
        });
    }

    private static GuiElement deleteSlot(org.bukkit.entity.Player player, Player viewer, BambooTrayRecipeDraft draft) {
        if (draft.isNew()) return MenuIcons.filler(viewer);
        return MenuIcons.button(MenuIcons.icon(MenuButton.DELETE, viewer, MenuIcons.text("删除", NamedTextColor.RED)),
                () -> ConfirmMenu.open(player, "删除竹筛食谱", List.of(draft.originalId().asString()),
                        () -> RecipeEditService.deleteBambooTray(draft.originalRecipe()).thenAccept(error ->
                                MenuTasks.runFor(player, () -> {
                                    RecipeMenus.message(player, error == null ? "已删除 " + draft.originalId().asString() : error);
                                    RecipeListMenu.open(player, ApplianceType.BAMBOO_TRAY, true);
                                })), () -> open(player, draft)));
    }
}
