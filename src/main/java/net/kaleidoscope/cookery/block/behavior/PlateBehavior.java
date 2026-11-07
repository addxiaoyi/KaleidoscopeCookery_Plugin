package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.PlateController;
import net.kaleidoscope.cookery.util.BehaviorConfig;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlocksProxy;

import java.util.List;
import java.util.Set;

/** CraftEngine interaction bridge for the source plate and platter blocks. */
public final class PlateBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<PlateBehavior> FACTORY = new Factory();

    private int controllerId;
    private Property<Integer> servingsProperty;
    private Set<Key> servingItems = Set.of();
    private List<String> drops = List.of();
    private int maxServings = 1;

    private PlateBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() == InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, world, pos)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = world.storageWorld().getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        PlateController controller = blockEntity.controller.get(PlateController.class, controllerId);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        Item held = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean changed = held.isEmpty()
                ? controller.take(player, InteractionHand.MAIN_HAND)
                : controller.place(player, InteractionHand.MAIN_HAND, held);
        if (!changed) {
            return InteractionResult.PASS;
        }
        player.swingHand(InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    public boolean accepts(Key id) {
        return id != null && servingItems.contains(id);
    }

    public int maxServings() {
        return maxServings;
    }

    public List<String> drops() {
        return drops;
    }

    public Property<Integer> servingsProperty() {
        return servingsProperty;
    }

    public void removeAfterConsumed(CEWorld world, BlockPos pos) {
        LevelWriterProxy.INSTANCE.setBlock(world.world().minecraftWorld(), LocationUtils.toBlockPos(pos),
                BlocksProxy.AIR$defaultState, 3);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new PlateController(blockEntity, this);
    }

    @Override
    public void initControllerId(int id) {
        controllerId = id;
    }

    private static final class Factory implements BlockBehaviorFactory<PlateBehavior> {
        @Override
        public PlateBehavior create(BlockDefinition block, ConfigSection section) {
            PlateBehavior behavior = new PlateBehavior(block);
            behavior.servingsProperty = BlockBehaviorFactory.getProperty(section.path(), block, "servings", Integer.class);
            behavior.maxServings = BehaviorConfig.getInt(section, 1, "max_servings", "max-servings");
            behavior.servingItems = BehaviorConfig.getKeySet(section, List.of(), "serving_items", "serving-items");
            behavior.drops = List.copyOf(BehaviorConfig.getStringList(section, List.of(), "drops"));
            return behavior;
        }
    }
}
