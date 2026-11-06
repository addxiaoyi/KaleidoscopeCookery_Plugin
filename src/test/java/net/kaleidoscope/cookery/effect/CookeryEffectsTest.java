package net.kaleidoscope.cookery.effect;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kaleidoscope.cookery.api.event.CookeryEffectChangeEvent;
import net.kaleidoscope.cookery.catalog.ConsumeEffectDefinition;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CookeryEffectsTest {
    private static final NamespacedKey SAVE = new NamespacedKey("kaleidoscopecookery", "effects_v1");
    private final UUID id = UUID.randomUUID();

    private Player player(String saved, AtomicReference<String> written) {
        Player player = mock(Player.class);
        var pdc = mock(PersistentDataContainer.class);
        var scheduler = mock(EntityScheduler.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(player.getScheduler()).thenReturn(scheduler);
        when(pdc.has(SAVE)).thenReturn(saved != null);
        when(pdc.get(SAVE, PersistentDataType.STRING)).thenReturn(saved);
        doAnswer(call -> { written.set(call.getArgument(2)); return null; }).when(pdc).set(eq(SAVE), eq(PersistentDataType.STRING), anyString());
        when(scheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).thenReturn(mock(ScheduledTask.class));
        return player;
    }

    private CookeryEffects service() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cookery-effects-test"));
        return new CookeryEffects(plugin);
    }

    private void setupBukkit(MockedStatic<Bukkit> bukkit) {
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(org.bukkit.entity.Entity.class))).thenReturn(true);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
    }

    @Test void cancelApplyLeavesNoEffectAndDoesNotWritePdc() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            PluginManager manager = Bukkit.getPluginManager();
            doAnswer(call -> { ((CookeryEffectChangeEvent) call.getArgument(0)).setCancelled(true); return null; }).when(manager).callEvent(any());
            AtomicReference<String> written = new AtomicReference<>();
            Player player = player(null, written);
            var service = service();
            assertFalse(service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi"));
            assertFalse(service.has(id, EffectKind.VIGOR));
            assertNull(written.get());
        }
    }

    @Test void corruptPdcRemainsUntouchedAndPausesNewApplications() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            AtomicReference<String> written = new AtomicReference<>();
            Player player = player("{broken}", written);
            var service = service();
            assertFalse(service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi"));
            service.clear(player, "milk");
            assertNull(written.get());
            verify(player.getPersistentDataContainer(), never()).remove(SAVE);
        }
    }

    @Test void logoutSavesDurationAndRejoinRestoresIt() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            AtomicReference<String> written = new AtomicReference<>();
            Player player = player(null, written);
            var service = service();
            assertTrue(service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi"));
            service.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null));
            assertFalse(service.has(id, EffectKind.VIGOR));
            Player returned = player(written.get(), new AtomicReference<>());
            service.onJoin(new PlayerJoinEvent(returned, (net.kyori.adventure.text.Component) null));
            assertEquals(100, service.state(id, EffectKind.VIGOR).remaining());
            service.clear(returned, "milk");
            assertFalse(service.has(id, EffectKind.VIGOR));
        }
    }

    @Test void rejectCrossRegionMutationBeforeReadingPlayerPdc() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = player(null, new AtomicReference<>());
            assertThrows(IllegalStateException.class, () -> service().apply(player,
                    new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi"));
            verify(player, never()).getPersistentDataContainer();
        }
    }

    @Test void clearingOneEffectPreservesEffectsAddedByListeners() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            Player player = player(null, new AtomicReference<>());
            var service = service();
            service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi");
            PluginManager manager = Bukkit.getPluginManager();
            doAnswer(call -> {
                CookeryEffectChangeEvent event = call.getArgument(0);
                if (event.reason().equals("milk")) {
                    service.apply(player, new ConsumeEffectDefinition(EffectKind.HINDER.id(), 200, 0, 1), "kaleidoscopecookery:tea");
                }
                return null;
            }).when(manager).callEvent(any());
            service.clear(player, "milk");
            assertFalse(service.has(id, EffectKind.VIGOR));
            assertEquals(200, service.state(id, EffectKind.HINDER).remaining());
        }
    }

    @Test void listenerReplacementWinsOverStaleOuterApply() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            Player player = player(null, new AtomicReference<>());
            var service = service();
            service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi");
            PluginManager manager = Bukkit.getPluginManager();
            doAnswer(call -> {
                CookeryEffectChangeEvent event = call.getArgument(0);
                if (event.next() != null && event.next().remaining() == 200) {
                    service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 500, 1, 1), "kaleidoscopecookery:tea");
                }
                return null;
            }).when(manager).callEvent(any());
            service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 200, 0, 1), "kaleidoscopecookery:baozi");
            assertEquals(500, service.state(id, EffectKind.VIGOR).remaining());
            assertEquals(1, service.state(id, EffectKind.VIGOR).level());
        }
    }

    @Test void handlerTickPreservesNewEffects() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            Player player = player(null, new AtomicReference<>());
            var service = service();
            AtomicReference<Consumer<ScheduledTask>> tick = new AtomicReference<>();
            when(player.getScheduler().runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).thenAnswer(call -> {
                tick.set(call.getArgument(1));
                return mock(ScheduledTask.class);
            });
            when(player.isSprinting()).thenReturn(true);
            doAnswer(call -> {
                service.apply(player, new ConsumeEffectDefinition(EffectKind.HINDER.id(), 200, 0, 1), "kaleidoscopecookery:tea");
                return null;
            }).when(player).setExhaustion(0);
            service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 100, 0, 1), "kaleidoscopecookery:baozi");
            tick.get().accept(mock(ScheduledTask.class));
            assertEquals(200, service.state(id, EffectKind.HINDER).remaining());
            assertEquals(99, service.state(id, EffectKind.VIGOR).remaining());
        }
    }

    @Test void cancelledSpendKeepsDurationAndSave() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            AtomicReference<String> written = new AtomicReference<>();
            Player player = player(null, written);
            var service = service();
            service.apply(player, new ConsumeEffectDefinition(EffectKind.PROJECTILE_DODGE.id(), 500, 0, 1), "kaleidoscopecookery:tea");
            String before = written.get();
            PluginManager manager = Bukkit.getPluginManager();
            doAnswer(call -> { ((CookeryEffectChangeEvent) call.getArgument(0)).setCancelled(true); return null; })
                    .when(manager).callEvent(any());
            service.spend(player, EffectKind.PROJECTILE_DODGE, 200);
            assertEquals(500, service.state(id, EffectKind.PROJECTILE_DODGE).remaining());
            assertEquals(before, written.get());
        }
    }

    @Test void excessiveHiddenEffectsRejectNewStateWithoutDiscardingOldSave() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            setupBukkit(bukkit);
            AtomicReference<String> written = new AtomicReference<>();
            Player player = player(null, written);
            var service = service();
            for (int level = 0; level < 8; level++) {
                assertTrue(service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 1000 - level * 10, level, 1), "kaleidoscopecookery:baozi"));
            }
            String before = written.get();
            assertFalse(service.apply(player, new ConsumeEffectDefinition(EffectKind.VIGOR.id(), 900, 8, 1), "kaleidoscopecookery:baozi"));
            assertEquals(before, written.get());
            assertEquals(7, service.state(id, EffectKind.VIGOR).level());
        }
    }
}
