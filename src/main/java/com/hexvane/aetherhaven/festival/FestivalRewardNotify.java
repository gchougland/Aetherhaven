package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Bottom-right item toast when a festival puts a prize in the player's inventory. */
public final class FestivalRewardNotify {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.reward.got";

    private FestivalRewardNotify() {}

    public static void giveAndNotify(
        @Nullable Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack stack
    ) {
        if (player == null || ItemStack.isEmpty(stack)) {
            return;
        }
        player.giveItem(stack, playerRef, store);
        notify(store, playerRef, stack);
    }

    public static void notify(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ItemStack stack
    ) {
        if (ItemStack.isEmpty(stack)) {
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null || pr.getPacketHandler() == null) {
            return;
        }
        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        int count = Math.max(1, stack.getQuantity());
        Message title = Message.translation(LANG)
            .param("count", Integer.toString(count))
            .param("item", UiMaterialLabels.itemNameMessage(itemId));
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            title,
            null,
            stack.toPacket(),
            NotificationStyle.Success
        );
    }
}
