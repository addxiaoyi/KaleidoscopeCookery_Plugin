package net.kaleidoscope.cookery.item;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.component.value.Enchantment;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CookeryEnchantmentsTest {
    @ParameterizedTest
    @CsvSource({"0,1,2,1", "1,2,3,2", "2,4,4,4", "3,4,5,8",
            "255,4,5,8", "2147483647,4,5,8", "-2147483648,1,2,1"})
    void nativeLevelsUseOfficialBounds(int level, int cuts, int radius, int durability) {
        Item tool = tool(new Enchantment(Key.of("kaleidoscopecookery:quick_knife"), level),
                new Enchantment(Key.of("kaleidoscopecookery:sweep"), level));
        assertEquals(cuts, CookeryEnchantments.quickKnifeWork(CookeryEnchantments.quickKnifeLevel(tool)));
        int sweep = CookeryEnchantments.sweepLevel(tool);
        assertEquals(radius, CookeryEnchantments.sweepRadius(2, sweep));
        assertEquals(durability, CookeryEnchantments.sweepDurability(1, sweep));
    }

    @Test
    void missingEmptyAndUnrelatedEnchantmentsDoNotActivateTools() {
        assertEquals(0, CookeryEnchantments.quickKnifeLevel(null));
        assertEquals(0, CookeryEnchantments.sweepLevel(null));
        Item tool = tool(new Enchantment(Key.of("minecraft:sweeping_edge"), 3));
        assertEquals(0, CookeryEnchantments.sweepLevel(tool));
        assertEquals(0, CookeryEnchantments.quickKnifeLevel(tool));
        when(tool.enchantments()).thenReturn(Optional.empty());
        when(tool.storedEnchantments()).thenReturn(Optional.of(List.of(
                new Enchantment(Key.of("kaleidoscopecookery:quick_knife"), 2))));
        assertEquals(0, CookeryEnchantments.quickKnifeLevel(tool));
        when(tool.isEmpty()).thenReturn(true);
        clearInvocations(tool);
        assertEquals(0, CookeryEnchantments.sweepLevel(tool));
        verify(tool, never()).enchantments();
        verify(tool, never()).storedEnchantments();
    }

    @Test
    void duplicateNativeEntriesCannotMultiplyTheBonus() {
        Item tool = tool(new Enchantment(Key.of("kaleidoscopecookery:sweep"), 2),
                new Enchantment(Key.of("kaleidoscopecookery:sweep"), 1));
        assertEquals(2, CookeryEnchantments.sweepLevel(tool));
    }

    @Test
    void configuredHarvestValuesRemainTheBaseAndCannotWrap() {
        assertEquals(7, CookeryEnchantments.sweepRadius(4, 3));
        assertEquals(24, CookeryEnchantments.sweepDurability(3, 3));
        assertEquals(0, CookeryEnchantments.sweepDurability(0, 3));
        assertThrows(IllegalArgumentException.class, () -> CookeryEnchantments.sweepRadius(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> CookeryEnchantments.sweepDurability(-1, 1));
        assertThrows(ArithmeticException.class, () -> CookeryEnchantments.sweepDurability(Integer.MAX_VALUE, 3));
    }

    private Item tool(Enchantment... enchantments) {
        Item tool = mock(Item.class);
        when(tool.enchantments()).thenReturn(Optional.of(List.of(enchantments)));
        return tool;
    }
}
