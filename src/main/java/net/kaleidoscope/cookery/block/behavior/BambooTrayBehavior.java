package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.block.entity.BambooTrayController;
import net.kaleidoscope.cookery.util.BlockStates;
import net.kaleidoscope.cookery.util.InteractGuard;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.behavior.WorldlyContainerHolder;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;

public final class BambooTrayBehavior extends BukkitBlockBehavior implements EntityBlock, WorldlyContainerHolder {
    public static final BlockBehaviorFactory<BambooTrayBehavior> FACTORY = new Factory();
    private static final Key PLACE_SOUND = Key.of("minecraft:entity.item_frame.add_item");
    private static final Key TAKE_SOUND = Key.of("minecraft:entity.item_frame.remove_item");

    private int controllerId;
    private Property<Boolean> standProperty;

    private BambooTrayBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || player.isSpectatorMode() || context.getHand() != InteractionHand.MAIN_HAND
                || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        if (!InteractGuard.canInteract(player, context.getLevel(), pos)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Item held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.isEmpty() && held.id().equals(blockDefinition.id())) {
            return InteractionResult.PASS;
        }
        BambooTrayController controller = controller(context.getLevel().storageWorld(), pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        Vec3d hit = context.getClickLocation();
        int slot = slotAt(hit.x - pos.x(), hit.z - pos.z());
        boolean taking = held.isEmpty();
        boolean changed;
        if (taking) {
            Item extracted = controller.take(slot, context.isSecondaryUseActive());
            changed = !extracted.isEmpty();
            if (changed) {
                InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, extracted);
            }
        } else {
            int moved = controller.insert(slot, held);
            changed = moved > 0;
            if (changed) {
                InventoryUtils.shrinkHeld(player, held, moved);
            }
        }
        if (changed) {
            player.swingHand(InteractionHand.MAIN_HAND);
            context.getLevel().playSound(Vec3d.atCenterOf(pos), taking ? TAKE_SOUND : PLACE_SOUND,
                    1.0f, 1.0f, SoundSource.BLOCK);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    public static int slotAt(double localX, double localZ) {
        return (localZ >= 0.5 ? 2 : 0) + (localX >= 0.5 ? 1 : 0);
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        ImmutableBlockState below = context.getLevel().getBlock(context.getClickedPos().below()).customBlockState();
        return BlockStates.with(state, standProperty, isThisTray(below));
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object state = args[0];
        if (DirectionUtils.fromNMSDirection(args[updateShape$direction]) != Direction.DOWN) {
            return state;
        }
        ImmutableBlockState custom = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (custom == null) {
            return state;
        }
        ImmutableBlockState below = BlockStateUtils.getOptionalCustomBlockState(args[updateShape$neighborState]).orElse(null);
        return BlockStates.with(custom, standProperty, isThisTray(below)).customBlockState().minecraftState();
    }

    private boolean isThisTray(ImmutableBlockState state) {
        return state != null && !state.isEmpty() && state.owner().value().id().equals(blockDefinition.id());
    }

    @Override
    public Object getContainer(Object thisBlock, Object[] args) {
        CEWorld world = BukkitAdaptor.adapt(LevelProxy.INSTANCE.getWorld(args[1])).storageWorld();
        BambooTrayController controller = controller(world, LocationUtils.fromBlockPos(args[2]));
        return controller == null ? null : controller.minecraftContainer();
    }

    private BambooTrayController controller(CEWorld world, BlockPos pos) {
        BlockEntity entity = world.getBlockEntityAtIfLoaded(pos);
        return entity == null ? null : entity.controller.get(BambooTrayController.class, controllerId);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new BambooTrayController(blockEntity, blockDefinition.id());
    }

    @Override
    public void initControllerId(int id) {
        controllerId = id;
    }

    private static final class Factory implements BlockBehaviorFactory<BambooTrayBehavior> {
        @Override
        public BambooTrayBehavior create(BlockDefinition block, ConfigSection section) {
            BambooTrayBehavior behavior = new BambooTrayBehavior(block);
            behavior.standProperty = BlockBehaviorFactory.getProperty(section.path(), block, "stand", Boolean.class);
            return behavior;
        }
    }
}
