package com.hexvane.aetherhaven.battlehorn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Third-person horn pose for nearby players. Charging {@code ItemAnimationId} only plays for the user.
 */
final class BattleHornAnimations {
    private BattleHornAnimations() {}

    static void playTootForViewers(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable String animationId
    ) {
        if (animationId == null || animationId.isBlank()) {
            return;
        }
        String itemAnimationsId = playerAnimationsId();
        commandBuffer.run(store -> {
            if (!playerRef.isValid()) {
                return;
            }
            AnimationUtils.playAnimation(
                playerRef,
                AnimationSlot.Action,
                itemAnimationsId,
                animationId,
                false,
                store
            );
        });
    }

    static void stopTootForViewers(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!playerRef.isValid()) {
                return;
            }
            AnimationUtils.stopAnimation(playerRef, AnimationSlot.Action, false, store);
        });
    }

    @Nullable
    private static String playerAnimationsId() {
        Item item = Item.getAssetMap().getAsset(AetherhavenConstants.ITEM_BATTLE_HORN);
        if (item == null) {
            return null;
        }
        String id = item.getPlayerAnimationsId();
        return id == null || id.isBlank() ? null : id;
    }
}
