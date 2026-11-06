package net.kaleidoscope.series;

import java.util.List;

public final class Ingredients {
    private Ingredients() {}

    public static boolean match(List<List<String>> recipe, List<String> supplied) {
        if (recipe.size() > 9) throw new IllegalArgumentException("Too many ingredient slots");
        return recipe.size() == supplied.size() && assign(recipe, supplied, 0, 0);
    }

    private static boolean assign(List<List<String>> recipe, List<String> supplied, int index, int used) {
        if (index == supplied.size()) return true;
        for (int i = 0; i < recipe.size(); i++) {
            if ((used & (1 << i)) == 0 && recipe.get(i).contains(supplied.get(index))
                    && assign(recipe, supplied, index + 1, used | (1 << i))) return true;
        }
        return false;
    }
}
