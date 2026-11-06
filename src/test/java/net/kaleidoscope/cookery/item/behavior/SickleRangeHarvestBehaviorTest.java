package net.kaleidoscope.cookery.item.behavior;

import net.kaleidoscope.cookery.util.InteractGuard;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.component.value.Enchantment;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SickleRangeHarvestBehaviorTest {
    private final World level = mock(World.class);
    private final org.bukkit.World world = mock(org.bukkit.World.class);
    private final Player player = mock(Player.class);
    private final org.bukkit.entity.Player bukkitPlayer = mock(org.bukkit.entity.Player.class);
    private final Item tool = mock(Item.class);
    private final UseOnContext context = mock(UseOnContext.class);
    private final Block crop = mock(Block.class);

    private SickleRangeHarvestBehavior harvest(int enchantmentLevel) {
        when(context.getPlayer()).thenReturn(player);
        when(context.getLevel()).thenReturn(level);
        when(context.getItem()).thenReturn(tool);
        when(context.getClickedPos()).thenReturn(new BlockPos(8, 64, 8));
        when(level.platformWorld()).thenReturn(world);
        when(player.platformPlayer()).thenReturn(bukkitPlayer);
        when(tool.enchantments()).thenReturn(Optional.of(List.of(
                new Enchantment(Key.of("kaleidoscopecookery:sweep"), enchantmentLevel))));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(crop);
        Material grass = mock(Material.class);
        when(grass.getKey()).thenReturn(NamespacedKey.minecraft("short_grass"));
        when(crop.getType()).thenReturn(grass);
        ConfigSection section = mock(ConfigSection.class);
        when(section.getInt(any(String[].class), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(section.getBoolean(any(String[].class), anyBoolean())).thenAnswer(call -> call.getArgument(1));
        when(section.getStringList(any(String[].class), anyList())).thenAnswer(call -> call.getArgument(1));
        return SickleRangeHarvestBehavior.FACTORY.create(null, null, Key.of("test:sickle"), section);
    }

    @ParameterizedTest
    @CsvSource({"0,2,1", "1,3,2", "2,4,4", "3,5,8", "255,5,8", "-1,2,1"})
    void enchantedSweepVisitsTheExpectedAreaAndChargesOnce(int level, int radius, int durability) {
        var behavior = harvest(level);
        try (var bukkit = mockStatic(Bukkit.class); var blocks = mockStatic(CraftEngineBlocks.class);
             var protection = mockStatic(InteractGuard.class)) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);
            protection.when(() -> InteractGuard.canBreak(eq(player), eq(this.level), anyInt(), anyInt(), anyInt())).thenReturn(true);
            assertEquals(InteractionResult.SUCCESS_AND_CANCEL, behavior.useOnBlock(context));
            verify(world).getBlockAt(8 - radius, 64, 8 - radius);
            verify(world).getBlockAt(8 + radius, 65, 8 + radius);
            verify(player, times((2 * radius + 1) * (2 * radius + 1) * 2)).breakBlock(anyInt(), anyInt(), anyInt());
            verify(tool).hurtAndBreak(durability, player, null);
        }
    }

    @Test
    void foreignRegionIsSkippedBeforeReadingBlocksOrProtection() {
        var behavior = harvest(3);
        try (var bukkit = mockStatic(Bukkit.class); var protection = mockStatic(InteractGuard.class)) {
            assertEquals(InteractionResult.PASS, behavior.useOnBlock(context));
            verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
            verify(tool, never()).hurtAndBreak(anyInt(), any(), any());
            protection.verifyNoInteractions();
        }
    }

    @Test
    void unloadedChunksAreNotLoadedByHarvesting() {
        var behavior = harvest(2);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        try (var bukkit = mockStatic(Bukkit.class); var protection = mockStatic(InteractGuard.class)) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);
            assertEquals(InteractionResult.PASS, behavior.useOnBlock(context));
            verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
            verify(tool, never()).hurtAndBreak(anyInt(), any(), any());
        }
    }

    @Test
    void deniedProtectionDoesNotHarvestOrChargeDurability() {
        var behavior = harvest(3);
        try (var bukkit = mockStatic(Bukkit.class); var protection = mockStatic(InteractGuard.class)) {
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);
            assertEquals(InteractionResult.PASS, behavior.useOnBlock(context));
            verify(player, never()).breakBlock(anyInt(), anyInt(), anyInt());
            verify(tool, never()).hurtAndBreak(anyInt(), any(), any());
            protection.verify(() -> InteractGuard.canBreak(eq(player), eq(level), anyInt(), anyInt(), anyInt()), times(242));
        }
    }
}
