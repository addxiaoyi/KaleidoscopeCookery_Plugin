package net.kaleidoscope.cookery.block.behavior;

import net.kaleidoscope.cookery.api.event.PotExtractDishEvent;
import net.kaleidoscope.cookery.block.entity.PotController;
import net.kaleidoscope.cookery.util.EventUtils;
import net.kaleidoscope.cookery.util.InventoryUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PotServingTest {
    @Test void inventoryCallbackCannotServeAnAlreadyEmptiedPot() {
        PotBehavior behavior = mock(PotBehavior.class, CALLS_REAL_METHODS);
        PotController controller = mock(PotController.class);
        Player player = mock(Player.class);
        Item dish = mock(Item.class);
        when(dish.copyWithCount(1)).thenReturn(dish);
        Key bowl = Key.of("minecraft:bowl");
        when(controller.resultCarrier()).thenReturn(bowl);
        AtomicInteger portions = new AtomicInteger(3);
        when(controller.resultCount()).thenAnswer(call -> portions.get());
        doAnswer(call -> { portions.addAndGet(-call.<Integer>getArgument(0)); return null; })
                .when(controller).consumeResult(anyInt());
        try (var inventory = mockStatic(InventoryUtils.class)) {
            inventory.when(() -> InventoryUtils.consumeItem(player, bowl, 1)).thenReturn(true);
            // A synchronous inventory/drop callback can consume the remaining batch.
            inventory.when(() -> InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, dish))
                    .thenAnswer(call -> { portions.set(0); return null; });
            behavior.serveDish(controller, player, InteractionHand.MAIN_HAND, dish, bowl, 3);
            inventory.verify(() -> InventoryUtils.consumeItem(player, bowl, 1), times(1));
            inventory.verify(() -> InventoryUtils.giveOrHold(player, InteractionHand.MAIN_HAND, dish), times(1));
            verify(controller, times(1)).consumeResult(1);
        }
    }

    @Test void cancelledEmptyAndReentrantEventsDoNotConsumeOrGive() throws Exception {
        for (String outcome : new String[]{"cancelled", "null", "empty", "changed"}) {
            PotBehavior behavior = mock(PotBehavior.class, CALLS_REAL_METHODS);
            PotController controller = mock(PotController.class);
            when(controller.beginServing()).thenReturn(true);
            Player player = mock(Player.class);
            when(player.platformPlayer()).thenReturn(mock(org.bukkit.entity.Player.class));
            UseOnContext context = mock(UseOnContext.class, RETURNS_DEEP_STUBS);
            when(context.getClickedPos()).thenReturn(new BlockPos(8, 64, 8));
            when(context.getLevel().platformWorld()).thenReturn(mock(org.bukkit.World.class));
            Item dish = mock(Item.class);
            when(dish.copyWithCount(1)).thenReturn(dish);
            when(controller.peekResult()).thenReturn(dish);
            AtomicInteger servings = new AtomicInteger(3);
            when(controller.resultCount()).thenAnswer(call -> servings.get());
            when(controller.resultCarrier()).thenReturn(Key.of("minecraft:bowl"));
            ItemStack stack = mock(ItemStack.class);
            when(stack.isEmpty()).thenReturn(outcome.equals("empty"));
            when(dish.platformItem()).thenReturn(stack);
            try (var events = mockStatic(EventUtils.class);
                 var inventory = mockStatic(InventoryUtils.class)) {
                inventory.when(() -> InventoryUtils.consumeItem(any(), any(), anyInt())).thenReturn(true);
                events.when(() -> EventUtils.fireAndCheckCancel(any())).thenAnswer(call -> {
                    PotExtractDishEvent event = call.getArgument(0);
                    if (outcome.equals("null")) event.setDish(null);
                    if (outcome.equals("changed")) servings.set(2);
                    return outcome.equals("cancelled");
                });
                var serve = PotBehavior.class.getDeclaredMethod("handleExtractDish", UseOnContext.class,
                        PotController.class, Player.class, InteractionHand.class);
                serve.setAccessible(true);
                serve.invoke(behavior, context, controller, player, InteractionHand.MAIN_HAND);
                events.verify(() -> EventUtils.fireAndCheckCancel(any()), times(1));
                inventory.verify(() -> InventoryUtils.consumeItem(any(), any(), anyInt()), never());
                inventory.verify(() -> InventoryUtils.giveOrHold(any(), any(), any()), never());
                verify(controller, never()).consumeResult(anyInt());
                verify(controller).endServing();
            }
        }
    }
}
