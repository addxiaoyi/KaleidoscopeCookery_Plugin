package net.kaleidoscope.tavern;

import net.kaleidoscope.series.Items;
import net.kaleidoscope.series.SavedItems;
import net.kaleidoscope.series.SavedState;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class ShakerContents {
    static final String SAVE_KEY = "kaleidoscope_tavern:shaker_v1";
    final ItemStack[] slots;
    final ItemStack cocktail;

    ShakerContents(ItemStack[] slots, ItemStack cocktail) {
        if (slots.length != 3) throw new IllegalArgumentException("A shaker needs three slots");
        this.slots = Arrays.stream(slots).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        this.cocktail = cocktail == null ? null : cocktail.clone();
        if (cocktail != null && !full()) throw new IllegalArgumentException("Incomplete shaker recipe state");
    }

    boolean full() { return Arrays.stream(slots).allMatch(Objects::nonNull); }
    boolean empty() { return cocktail == null && Arrays.stream(slots).allMatch(Objects::isNull); }

    CompoundTag save() {
        CompoundTag root = new CompoundTag();
        CompoundTag saved = new CompoundTag();
        saved.putInt("version", 1);
        for (int i = 0; i < slots.length; i++) SavedItems.put(saved, "slot_" + i, slots[i]);
        SavedItems.put(saved, "cocktail", cocktail);
        root.put(SAVE_KEY, saved);
        return root;
    }

    static ShakerContents read(CompoundTag root) {
        CompoundTag saved = root.getCompound(SAVE_KEY);
        if (saved == null || SavedState.integer(saved, "version") != 1) throw new IllegalArgumentException("Invalid shaker save");
        ItemStack[] slots = new ItemStack[3];
        for (int i = 0; i < slots.length; i++) slots[i] = SavedItems.get(saved, "slot_" + i, 1);
        return new ShakerContents(slots, SavedItems.get(saved, "cocktail", 1));
    }

    ShakerContents mix(TavernPlugin tavern, int ticks) {
        if (!full() || cocktail != null) return this;
        var outcome = ShakerTiming.outcome(ticks);
        if (outcome == ShakerTiming.Outcome.UNFINISHED) return this;
        List<String> supplied = Arrays.stream(slots).map(Items::id).toList();
        String output = switch (outcome) {
            case MYSTERY -> "kaleidoscope_tavern:mystery_cocktail";
            case RECIPE -> tavern.catalog().shaker().stream().filter(recipe -> recipe.matches(supplied))
                    .map(ShakerRecipe::output).findFirst().orElse("kaleidoscope_tavern:signature_cocktail");
            default -> "kaleidoscope_tavern:signature_cocktail";
        };
        return new ShakerContents(slots, tavern.drinks().cocktail(output, List.of(slots)));
    }
}
