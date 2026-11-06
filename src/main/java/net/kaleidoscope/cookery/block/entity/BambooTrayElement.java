package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.entity.render.ItemDisplayPackets;
import net.kaleidoscope.cookery.block.entity.render.ItemDisplaySet;
import net.kaleidoscope.cookery.block.entity.render.TrackedPlayers;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.render.element.BlockEntityElement;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.world.WorldPosition;

public final class BambooTrayElement implements BlockEntityElement {
    private static final float DISPLAY_HEIGHT = 3.0f / 16.0f;
    private static final float DISPLAY_SCALE = 0.4f;
    private final BambooTrayController controller;
    private final ItemDisplaySet displays = new ItemDisplaySet(BambooTrayContainer.SLOTS);

    BambooTrayElement(BambooTrayController controller) {
        this.controller = controller;
    }

    @Override
    public void activate() {
        refresh();
    }

    void refresh() {
        BlockEntity entity = controller.blockEntity();
        for (int slot = 0; slot < displays.size(); slot++) {
            Item item = controller.item(slot);
            if (item.isEmpty()) {
                displays.clear(slot);
                continue;
            }
            WorldPosition pos = new WorldPosition(null,
                    entity.pos.x() + 0.25f + (slot % 2) * 0.5f,
                    entity.pos.y() + DISPLAY_HEIGHT,
                    entity.pos.z() + 0.25f + (slot / 2) * 0.5f);
            ItemDisplayPackets packets = ItemDisplayPackets.at(pos).item(item.copy()).scale(DISPLAY_SCALE)
                    .itemTransform((byte) 0).rotation(0);
            displays.setPackets(slot, packets.spawn(displays.id(slot), displays.uuid(slot)), packets.meta(displays.id(slot)));
        }
    }

    @Override
    public void show(Player player) {
        displays.show(player);
    }

    @Override
    public void update(Player player) {
        displays.update(player);
    }

    @Override
    public void hide(Player player) {
        displays.hide(player);
    }

    @Override
    public void deactivate() {
        if (controller.blockEntity().world != null) {
            TrackedPlayers.forEach(controller.blockEntity(), displays::hide);
        }
    }
}
