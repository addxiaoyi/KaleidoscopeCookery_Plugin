package net.kaleidoscope.cookery.nms.v1_21_R7;

import net.kaleidoscope.cookery.nms.NmsBridge;
import net.kaleidoscope.cookery.nms.BambooTrayWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftEntity;
import org.bukkit.entity.Entity;

public final class NmsV1_21_R7 implements NmsBridge {
    @Override
    public BambooTrayWeather bambooTrayWeather(Object level, int x, int y, int z) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Bamboo tray weather requires a ServerLevel");
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos above = pos.above();
        boolean wetting = serverLevel.isRainingAt(above);
        if (!wetting) {
            BlockPos tip = PointedDripstoneBlock.findStalactiteTipAboveCauldron(serverLevel, pos);
            wetting = tip != null && PointedDripstoneBlock.getCauldronFillFluidType(serverLevel, tip) == Fluids.WATER;
        }
        return new BambooTrayWeather(wetting, serverLevel.isRaining(), serverLevel.canSeeSky(above));
    }

    @Override
    public Object litBlockStateProperty() {
        return BlockStateProperties.LIT;
    }

    @Override
    public Object bukkitEntity(Object nmsEntity) {
        if (nmsEntity instanceof net.minecraft.world.entity.Entity entity) {
            return entity.getBukkitEntity();
        }
        return null;
    }

    @Override
    public Object nmsHandle(Entity entity) {
        if (entity instanceof CraftEntity craftEntity) {
            return craftEntity.getHandle();
        }
        return null;
    }

    @Override
    public boolean isSpectatePacket(Object packet) {
        return packet instanceof ServerboundTeleportToEntityPacket;
    }

    @Override
    public boolean isPerformRespawnPacket(Object packet) {
        return packet instanceof ServerboundClientCommandPacket command
                && command.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN;
    }
}
