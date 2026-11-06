package net.kaleidoscope.tavern;

import net.kaleidoscope.series.ItemIds;

import java.util.List;

/** A saved batch freezes its output and keeps upstream's 97-tick stage boundaries. */
public final class BrewBatch {
    public static final int CHECK_INTERVAL = 97;
    public static final int MAX_LEVEL = 6;
    private final BrewRecipe recipe;
    private int level;
    private int servings;
    private long remaining;

    public BrewBatch(BrewRecipe recipe, int servings) {
        this(recipe, servings, 1, recipe.unitTime());
    }

    public BrewBatch(BrewRecipe recipe, int servings, int level, long remaining) {
        if (servings < 1 || servings > 16 || level < 1 || level > MAX_LEVEL
                || remaining < -CHECK_INTERVAL || remaining > (long) recipe.unitTime() * MAX_LEVEL) {
            throw new IllegalArgumentException("Invalid fermentation state");
        }
        this.recipe = recipe;
        this.servings = servings;
        this.level = level;
        this.remaining = remaining;
    }

    public void pulse() {
        if (level == MAX_LEVEL) return;
        if (remaining > 0) {
            remaining -= CHECK_INTERVAL;
            return;
        }
        level++;
        remaining = level == MAX_LEVEL ? -1 : (long) recipe.unitTime() * level;
    }

    public void bottle() {
        if (servings == 0) throw new IllegalStateException("Barrel is empty");
        servings--;
    }

    public BrewRecipe recipe() { return recipe; }
    public int level() { return level; }
    public int servings() { return servings; }
    public long remaining() { return remaining; }
}
