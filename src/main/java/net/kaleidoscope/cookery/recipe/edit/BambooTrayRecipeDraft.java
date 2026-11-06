package net.kaleidoscope.cookery.recipe.edit;

import net.kaleidoscope.cookery.recipe.BambooTrayMode;
import net.kaleidoscope.cookery.recipe.BambooTrayRecipe;
import net.momirealms.craftengine.core.util.Key;

/** Mutable state owned by one recipe editor. */
public final class BambooTrayRecipeDraft {
    public static final int DEFAULT_TIME = 200;
    public static final int MAX_COUNT = Integer.MAX_VALUE;

    private final BambooTrayRecipe original;
    private Key id;
    private String input;
    private Key result;
    private int resultCount = 1;
    private BambooTrayMode mode = BambooTrayMode.DRYING;
    private int durationTicks = DEFAULT_TIME;

    private BambooTrayRecipeDraft(Key id, BambooTrayRecipe original) {
        this.id = id;
        this.original = original;
    }

    public static BambooTrayRecipeDraft creating(Key id) {
        return new BambooTrayRecipeDraft(id, null);
    }

    public static BambooTrayRecipeDraft editing(BambooTrayRecipe recipe) {
        BambooTrayRecipeDraft draft = new BambooTrayRecipeDraft(recipe.id(), recipe);
        draft.input = recipe.input();
        draft.result = recipe.result();
        draft.resultCount = recipe.resultCount();
        draft.mode = recipe.mode();
        draft.durationTicks = recipe.durationTicks();
        return draft;
    }

    public boolean isNew() { return original == null; }
    public Key originalId() { return original == null ? null : original.id(); }
    public BambooTrayRecipe originalRecipe() { return original; }
    public Key id() { return id; }
    public void id(Key value) { id = value; }
    public String input() { return input; }
    public void input(String value) { input = value == null ? null : value.trim(); }
    public Key result() { return result; }
    public void result(Key value) { result = value; }
    public int resultCount() { return resultCount; }
    public void resultCount(int value) { resultCount = value; }
    public BambooTrayMode mode() { return mode; }
    public void mode(BambooTrayMode value) { mode = value; }
    public int durationTicks() { return durationTicks; }
    public void durationTicks(int value) { durationTicks = value; }

    public BambooTrayRecipe toRecipe() {
        return new BambooTrayRecipe(id, input, result, resultCount, mode, durationTicks);
    }
}
