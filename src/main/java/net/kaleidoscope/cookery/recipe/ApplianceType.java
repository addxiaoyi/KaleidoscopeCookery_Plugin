package net.kaleidoscope.cookery.recipe;

// 厨具类型 配方按厨具区分归属
public enum ApplianceType {
    POT,
    STEAMER,
    STOCKPOT,
    SHAWARMA,
    MILLSTONE,
    CHOPPING_BOARD,
    TEAPOT,
    BAMBOO_TRAY;

    // 等效食物表与调味品表只影响这两个厨具。
    public boolean usesFlexRecipes() {
        return this == POT || this == STOCKPOT;
    }
}
