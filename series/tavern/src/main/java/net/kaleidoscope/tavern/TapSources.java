package net.kaleidoscope.tavern;

import net.kaleidoscope.series.Items;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class TapSources {
    private static final java.util.Set<String> BOTTLED = java.util.Set.of("minecraft:potion",
            "minecraft:honey_bottle", "minecraft:dragon_breath",
            "kaleidoscope_tavern:watermelon_juice", "kaleidoscope_tavern:molotov");
    private TapSources() {}

    static String output(Block source) {
        return switch (source.getType()) {
            case MELON -> "kaleidoscope_tavern:watermelon_juice";
            case DRAGON_HEAD, DRAGON_WALL_HEAD -> "minecraft:dragon_breath";
            case LAVA_CAULDRON -> "kaleidoscope_tavern:molotov";
            case BEEHIVE, BEE_NEST -> ((Beehive) source.getBlockData()).getHoneyLevel() > 0 ? "minecraft:honey_bottle" : null;
            default -> water(source) ? "minecraft:potion" : null;
        };
    }

    static boolean accepts(String id, ItemStack drink) {
        if (!BOTTLED.contains(id)) return false;
        return !id.equals("minecraft:potion") || drink.getItemMeta() instanceof PotionMeta potion
                && potion.getBasePotionType() == PotionType.WATER && !potion.hasCustomEffects();
    }

    static ItemStack bottle(String id) {
        if (!BOTTLED.contains(id)) throw new IllegalArgumentException("Unknown tap drink: " + id);
        ItemStack drink = Items.create(id);
        if (id.equals("minecraft:potion")) {
            PotionMeta potion = (PotionMeta) drink.getItemMeta();
            potion.setBasePotionType(PotionType.WATER);
            drink.setItemMeta(potion);
        }
        return drink;
    }

    static boolean water(Block source) {
        return source.getType() == Material.WATER_CAULDRON
                || source.getBlockData() instanceof Waterlogged logged && logged.isWaterlogged();
    }

    static boolean canFillCauldron(Block source, Block destination, boolean infiniteLava) {
        if (water(source)) return destination.getType() == Material.CAULDRON
                || destination.getType() == Material.WATER_CAULDRON
                && ((Levelled) destination.getBlockData()).getLevel() < ((Levelled) destination.getBlockData()).getMaximumLevel();
        return infiniteLava && source.getType() == Material.LAVA_CAULDRON && destination.getType() == Material.CAULDRON;
    }

    static void fillBottle(Block source, BottleController destination) {
        String output = output(source);
        if (output == null || !destination.empty() || !destination.carrier().equals("kaleidoscope_tavern:empty_bottle")) return;
        var drink = bottle(output);
        if (source.getBlockData() instanceof Beehive hive) {
            hive.setHoneyLevel(hive.getHoneyLevel() - 1);
            source.setBlockData(hive);
        }
        destination.fill(drink);
        destination.refreshBottle();
    }
}
