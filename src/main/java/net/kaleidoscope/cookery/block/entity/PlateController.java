package net.kaleidoscope.cookery.block.entity;

import net.kaleidoscope.cookery.block.behavior.PlateBehavior;
import net.kaleidoscope.cookery.catalog.BakedFood;
import net.kaleidoscope.cookery.catalog.PlateServing;
import net.kaleidoscope.cookery.catalog.PlateServingEngine;
import net.kaleidoscope.cookery.catalog.QualityProfile;
import net.kaleidoscope.cookery.item.CookeryFoodItems;
import net.kaleidoscope.cookery.util.BlockEntityNbt;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.DropUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Persistent serving state for the official Cookery plate and platter blocks. */
public final class PlateController extends BlockEntityController {
    private static final String DATA_KEY = "kaleidoscopecookery:plate";
    private static final int SCHEMA_VERSION = 1;
    private static final String K_SCHEMA = "schema";
    private static final String K_SERVING = "serving";
    private static final String K_SOURCE_ITEM = "source_item";

    private final PlateBehavior behavior;
    private PlateServing serving;
    private Item sourceItem = Item.empty();
    private boolean removed;

    public PlateController(BlockEntity blockEntity, PlateBehavior behavior) {
        super(blockEntity);
        this.behavior = behavior;
    }

    public BlockPos getPos() {
        return blockEntity.pos;
    }

    public CEWorld getWorld() {
        return blockEntity.world;
    }

    public int servings() {
        return serving == null ? 0 : serving.bites().remaining();
    }

    public boolean place(Player player, InteractionHand hand, Item held) {
        if (removed || held == null || held.isEmpty() || !behavior.accepts(held.id())) {
            return false;
        }
        if (serving == null) {
            QualityProfile quality = qualityOf(held);
            serving = PlateServing.of(held.id().toString(), behavior.maxServings(), quality, behavior.drops());
            sourceItem = held.copyWithCount(1);
            InventoryUtils.shrinkHeld(player, held, 1);
            syncServingState();
            return true;
        }
        if (!serving.bites().itemId().equals(held.id().toString()) || serving.full()) {
            return false;
        }
        serving = PlateServingEngine.refill(serving);
        InventoryUtils.shrinkHeld(player, held, 1);
        syncServingState();
        return true;
    }

    public boolean take(Player player, InteractionHand hand) {
        if (removed || serving == null || serving.empty()) {
            return false;
        }
        Item bite = biteItem();
        if (bite.isEmpty()) {
            return false;
        }
        PlateServing next = PlateServingEngine.take(serving);
        InventoryUtils.giveOrHold(player, hand, bite);
        if (next.empty()) {
            serving = null;
            sourceItem = Item.empty();
            for (String drop : behavior.drops()) {
                DropUtils.dropAtCenter(blockEntity, InventoryUtils.createOrEmpty(Key.of(drop)));
            }
            markChanged();
            behavior.removeAfterConsumed(blockEntity.world, blockEntity.pos);
        } else {
            serving = next;
            syncServingState();
        }
        return true;
    }

    private QualityProfile qualityOf(Item item) {
        try {
            BakedFood baked = CookeryFoodItems.read(ItemStackUtils.getBukkitStack(item));
            return baked == null ? QualityProfile.BASE : baked.quality();
        } catch (RuntimeException ignored) {
            return QualityProfile.BASE;
        }
    }

    private Item biteItem() {
        if (sourceItem.isEmpty() || serving == null) {
            return Item.empty();
        }
        Item base = sourceItem.copyWithCount(1);
        Item rebuilt = InventoryUtils.createOrEmpty(Key.of(serving.bites().itemId()));
        if (!rebuilt.isEmpty()) {
            base = rebuilt;
        }
        Item bite = CookeryFoodItems.bakeBite(base, serving.bites().quality());
        return bite == null || bite.isEmpty() ? base : bite.copyWithCount(1);
    }

    private void syncServingState() {
        markChanged();
        ImmutableBlockState state = BlockStates.with(blockEntity.blockState, behavior.servingsProperty(), servings());
        BlockStates.sync(blockEntity, state);
    }

    private void markChanged() {
        if (blockEntity.world != null) {
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world,
                                                                                           ImmutableBlockState blockState) {
        return null;
    }

    @Override
    public void onRemove() {
        if (removed) {
            return;
        }
        removed = true;
        if (serving != null && !serving.empty()) {
            int count = serving.bites().remaining();
            for (int i = 0; i < count; i++) {
                DropUtils.dropOnRemove(blockEntity, biteItem());
            }
            for (String drop : behavior.drops()) {
                DropUtils.dropOnRemove(blockEntity, InventoryUtils.createOrEmpty(Key.of(drop)));
            }
        }
        serving = null;
        sourceItem = Item.empty();
        super.onRemove();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (serving == null) {
            return;
        }
        CompoundTag data = BlockEntityNbt.newData();
        data.putInt(K_SCHEMA, SCHEMA_VERSION);
        data.putString(K_SERVING, serving.encode());
        BlockEntityNbt.putItem(data, K_SOURCE_ITEM, sourceItem);
        tag.put(DATA_KEY, data);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        CompoundTag data = tag.getCompound(DATA_KEY);
        if (data == null || !data.containsKey(K_SERVING)) {
            return;
        }
        int schema = data.getInt(K_SCHEMA, SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported plate schema " + schema + " at " + blockEntity.pos);
        }
        try {
            serving = PlateServing.decode(data.getString(K_SERVING, null));
            sourceItem = BlockEntityNbt.getItem(data, K_SOURCE_ITEM, BlockEntityNbt.dataVersion(data));
            if (sourceItem.isEmpty()) {
                sourceItem = InventoryUtils.createOrEmpty(Key.of(serving.bites().itemId()));
            }
            if (sourceItem.isEmpty()) {
                serving = null;
            }
        } catch (RuntimeException invalid) {
            // A bad optional plate payload must not prevent an old world from loading.
            serving = null;
            sourceItem = Item.empty();
        }
    }
}
