package net.kaleidoscope.cookery.acceptance;

import com.google.gson.GsonBuilder;
import net.kaleidoscope.cookery.catalog.CookeryCatalog;
import net.kaleidoscope.cookery.item.CookeryEnchantments;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.item.Item;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

final class EnchantmentSmoke {
    private EnchantmentSmoke() {}

    static void verify(JavaPlugin plugin) {
        var failures = new ArrayList<String>();
        int checked = 0;
        int reloaded = 0;
        for (String name : new String[]{"quick_knife", "sweep"}) {
            var key = new NamespacedKey("kaleidoscopecookery", name);
            var enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment == null) {
                failures.add(key + ": missing native registry entry; install cookery-enchantments before boot");
                continue;
            }
            int maximum = name.equals("quick_knife") ? 2 : 3;
            if (enchantment.getMaxLevel() != maximum) failures.add(key + ": wrong registry maximum");
            for (int level = 1; level <= maximum; level++) {
                try {
                    ItemStack tool = new ItemStack(Material.IRON_SWORD);
                    tool.addUnsafeEnchantment(enchantment, level);
                    assertLevel(name, level, tool);
                    ItemStack restored = ItemStack.deserializeBytes(tool.serializeAsBytes());
                    assertLevel(name, level, restored);
                    var saved = plugin.getDataFolder().toPath().resolve(name + "-" + level + ".bin");
                    if (Files.exists(saved)) {
                        assertLevel(name, level, ItemStack.deserializeBytes(Files.readAllBytes(saved)));
                        reloaded++;
                    }
                    Files.createDirectories(saved.getParent());
                    Files.write(saved, tool.serializeAsBytes());
                    checked++;
                } catch (Exception | LinkageError exception) {
                    failures.add(key + " " + level + ": " + exception);
                }
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("server", Bukkit.getVersion());
        report.put("craftEngine", Bukkit.getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion());
        report.put("sourceCommit", CookeryCatalog.SOURCE_COMMIT);
        report.put("checkedTools", checked);
        report.put("reloadedTools", reloaded);
        report.put("failures", failures);
        report.put("playerFlow", "NOT_TESTED");
        report.put("clientResourcePack", "NOT_TESTED");
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(plugin.getDataFolder().toPath().resolve("enchantment-smoke.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot write enchantment smoke evidence", exception);
        }
        plugin.getLogger().log(failures.isEmpty() ? Level.INFO : Level.SEVERE,
                "COOKERY_ENCHANTMENT_SMOKE checked=" + checked + " reloaded=" + reloaded + " failures=" + failures.size());
        failures.forEach(plugin.getLogger()::severe);
    }

    private static void assertLevel(String name, int level, ItemStack stack) {
        Item tool = ItemStackUtils.wrap(ItemStackUtils.unwrap(stack));
        int actual = name.equals("quick_knife") ? CookeryEnchantments.quickKnifeLevel(tool) : CookeryEnchantments.sweepLevel(tool);
        if (level != actual) throw new IllegalStateException(name + ": CE read " + actual + ", expected " + level);
        if (name.equals("quick_knife")) {
            if (CookeryEnchantments.quickKnifeWork(actual) != 1 << level) throw new IllegalStateException("Wrong quick knife work");
        } else if (CookeryEnchantments.sweepRadius(2, actual) != 2 + level
                || CookeryEnchantments.sweepDurability(1, actual) != 1 << level) {
            throw new IllegalStateException("Wrong sweep range or durability");
        }
    }
}
