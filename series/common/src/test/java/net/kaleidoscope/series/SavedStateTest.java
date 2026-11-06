package net.kaleidoscope.series;

import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedStateTest {
    @Test void recoveryCodecPreservesCustomFieldsAndOpaqueItemBytes() throws Exception {
        CompoundTag contents = new CompoundTag();
        contents.putInt("version", 1);
        contents.putByteArray("slot_0", new byte[]{0, 1, 127, -128});
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("test:inventory", contents);
        snapshot.putString("recovery_furniture", "test:barrel");
        byte[] bytes = net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData.of(snapshot).toBytes();
        CompoundTag restored = net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData.fromBytes(bytes).unsafeTag();
        assertEquals("test:barrel", restored.getString("recovery_furniture"));
        assertArrayEquals(new byte[]{0, 1, 127, -128}, restored.getCompound("test:inventory").getByteArray("slot_0"));
    }

    @Test void absentAndWrongTypeCountersAreNotTreatedAsZero() {
        CompoundTag saved = new CompoundTag();
        assertThrows(IllegalArgumentException.class, () -> SavedState.integer(saved, "amount"));
        saved.putString("amount", "0");
        assertThrows(IllegalArgumentException.class, () -> SavedState.integer(saved, "amount"));
        saved.putInt("amount", 0);
        assertEquals(0, SavedState.integer(saved, "amount"));
    }

    @Test void emptyQueueIsDistinctFromMissingQueue() {
        CompoundTag saved = new CompoundTag();
        assertThrows(IllegalArgumentException.class, () -> SavedState.text(saved, "tier_0"));
        saved.putString("tier_0", "");
        assertEquals("", SavedState.text(saved, "tier_0"));
    }

    @Test void itemWithWrongTypeIsNotDiscardedAsAnEmptySlot() {
        CompoundTag saved = new CompoundTag();
        saved.putString("slot", "broken");
        assertThrows(IllegalArgumentException.class, () -> SavedItems.get(saved, "slot", 64));
    }
}
