package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import net.kaleidoscope.cookery.recipe.BambooTrayRecipe;
import net.momirealms.craftengine.core.util.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BambooTrayRecipeDraftTest {
    @Test
    void editingRetainsTagModeWorkAndOriginalIdentity() {
        BambooTrayRecipe original = new BambooTrayRecipe(Key.of("test:wet_moss"), "#test:stones",
                Key.of("minecraft:mossy_cobblestone"), 2, BambooTrayMode.WETTING, 319);

        BambooTrayRecipeDraft draft = BambooTrayRecipeDraft.editing(original);

        assertFalse(draft.isNew());
        assertSame(original, draft.originalRecipe());
        assertEquals(original.id(), draft.originalId());
        assertEquals(original, draft.toRecipe());

        draft.id(Key.of("test:wet_stone"));
        draft.input("minecraft:stone");
        draft.durationTicks(320);
        assertEquals(original.id(), draft.originalId());
        assertEquals("#test:stones", original.input());
        assertEquals(319, original.durationTicks());
        assertEquals(320, draft.toRecipe().durationTicks());
    }

    @Test
    void newDraftRequiresExplicitItemsAndPositiveWork() {
        BambooTrayRecipeDraft draft = BambooTrayRecipeDraft.creating(Key.of("test:dry_kelp"));
        assertTrue(draft.isNew());
        assertThrows(NullPointerException.class, draft::toRecipe);

        draft.input("minecraft:kelp");
        draft.result(Key.of("minecraft:dried_kelp"));
        draft.durationTicks(19);
        assertEquals(BambooTrayMode.DRYING, draft.toRecipe().mode());
        assertEquals(19, draft.toRecipe().durationTicks());

        draft.durationTicks(0);
        assertThrows(IllegalArgumentException.class, draft::toRecipe);
    }
}
