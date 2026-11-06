package net.kaleidoscope.tavern;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TapSourcesTest {
    @Test void specialSourcesYieldRealRegisteredItems() {
        Block source = mock(Block.class);
        when(source.getType()).thenReturn(Material.DRAGON_HEAD);
        assertEquals("minecraft:dragon_breath", TapSources.output(source));
        when(source.getType()).thenReturn(Material.WATER_CAULDRON);
        assertEquals("minecraft:potion", TapSources.output(source));
        when(source.getType()).thenReturn(Material.MELON);
        assertEquals("kaleidoscope_tavern:watermelon_juice", TapSources.output(source));
    }

    @Test void anEmptyHiveCannotSupplyABottle() {
        Block source = mock(Block.class);
        Beehive hive = mock(Beehive.class);
        when(source.getType()).thenReturn(Material.BEEHIVE);
        when(source.getBlockData()).thenReturn(hive);
        assertNull(TapSources.output(source));
        when(hive.getHoneyLevel()).thenReturn(1);
        assertEquals("minecraft:honey_bottle", TapSources.output(source));
    }

    @Test void savedWaterBottlesRejectOtherPotionsAndAddedEffects() {
        ItemStack drink = mock(ItemStack.class);
        PotionMeta potion = mock(PotionMeta.class);
        when(drink.getItemMeta()).thenReturn(potion);
        assertFalse(TapSources.accepts("minecraft:potion", drink));
        when(potion.getBasePotionType()).thenReturn(PotionType.WATER);
        assertTrue(TapSources.accepts("minecraft:potion", drink));
        when(potion.hasCustomEffects()).thenReturn(true);
        assertFalse(TapSources.accepts("minecraft:potion", drink));
        when(potion.hasCustomEffects()).thenReturn(false);
        when(potion.getBasePotionType()).thenReturn(PotionType.HEALING);
        assertFalse(TapSources.accepts("minecraft:potion", drink));
        assertFalse(TapSources.accepts("kaleidoscope_tavern:water_bottle", drink));
    }
}
