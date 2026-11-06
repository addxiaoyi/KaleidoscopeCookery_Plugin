package net.kaleidoscope.doll;

import net.momirealms.craftengine.bukkit.entity.furniture.element.ItemDisplayFurnitureElementConfig;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import java.util.Map;
import java.util.function.IntConsumer;

final class DollDisplay implements FurnitureElement {
    private final DollController controller;
    private FurnitureElement display;

    DollDisplay(DollController controller) { this.controller = controller; }

    synchronized void rebuild() {
        DollPose pose = controller.pose();
        var config = ItemDisplayFurnitureElementConfig.FACTORY.create(ConfigSection.of("doll.pose", Map.of(
                "item", controller.furniture().id().toString(), "display_transform", "none", "position", "0,0.5,0",
                "scale", pose.vector(0), "translation", pose.vector(3), "rotation", pose.vector(6))));
        FurnitureElement replacement = config.create(controller.furniture());
        FurnitureElement previous = display;
        display = replacement;
        for (Player viewer : controller.furniture().trackedBy()) {
            if (previous != null) previous.hide(viewer);
            replacement.show(viewer);
        }
    }

    @Override public synchronized void activate() { if (display == null) rebuild(); }
    @Override public synchronized void show(Player player) { if (display != null) display.show(player); }
    @Override public synchronized void update(Player player) { if (display != null) display.update(player); }
    @Override public synchronized void hide(Player player) { if (display != null) display.hide(player); }
    @Override public void gatherInteractableEntityId(IntConsumer ids) { /* The furniture keeps its own interaction hitbox. */ }
}
