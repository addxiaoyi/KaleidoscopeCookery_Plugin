package net.kaleidoscope.series;

import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ItemStateTest {
    private static final NamespacedKey KEY = new NamespacedKey("test", "state");

    @Test void opaqueFurnitureStateRoundTripsWithoutMutatingSourceItem() {
        ItemStack original = mock(ItemStack.class), copy = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(original.clone()).thenReturn(copy);
        when(copy.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        CompoundTag saved = new CompoundTag(); saved.putString("mode", "left"); saved.putByteArray("ingredient", new byte[] {1, 2, 3});
        assertSame(copy, ItemState.write(original, KEY, saved));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(pdc).set(eq(KEY), eq(PersistentDataType.BYTE_ARRAY), bytes.capture());
        verify(original, never()).setItemMeta(any());
        verify(meta).setMaxStackSize(1);
        when(pdc.has(KEY)).thenReturn(true);
        when(pdc.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(bytes.getValue());
        CompoundTag restored = ItemState.read(copy, KEY);
        assertEquals("left", restored.getString("mode"));
        assertArrayEquals(new byte[] {1, 2, 3}, restored.getByteArray("ingredient"));
    }

    @Test void invalidBytesAndWrongPersistentTypeAreNotTreatedAsEmptyContents() {
        ItemStack item = mock(ItemStack.class); ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(KEY)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> ItemState.read(item, KEY));
        when(pdc.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(new byte[262145]);
        assertThrows(IllegalArgumentException.class, () -> ItemState.read(item, KEY));
        when(pdc.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> ItemState.read(item, KEY));
    }
}
