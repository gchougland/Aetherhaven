package com.hexvane.aetherhaven.festival.wintertide;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;

/** Keeps Use (F) on a town member who is waiting for a Wintertide gift. */
public final class WintertidePlayerGiftInteractSync {
    private WintertidePlayerGiftInteractSync() {}

    public static void sync(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (commandBuffer.getComponent(ref, Interactable.getComponentType()) == null) {
            commandBuffer.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        }
        Interactions existing = commandBuffer.getComponent(ref, Interactions.getComponentType());
        if (existing == null
            || !WintertideIds.ROOT_INTERACTION_PLAYER_GIFT.equals(existing.getInteractionId(InteractionType.Use))
            || !WintertideIds.INTERACTION_HINT.equals(existing.getInteractionHint())) {
            commandBuffer.putComponent(ref, Interactions.getComponentType(), giftInteractions());
        }
    }

    public static void clear(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Interactions existing = commandBuffer.getComponent(ref, Interactions.getComponentType());
        if (existing != null
            && WintertideIds.ROOT_INTERACTION_PLAYER_GIFT.equals(existing.getInteractionId(InteractionType.Use))) {
            commandBuffer.removeComponent(ref, Interactions.getComponentType());
        }
    }

    @Nonnull
    static Interactions giftInteractions() {
        Interactions interactions =
            new Interactions(Map.of(InteractionType.Use, WintertideIds.ROOT_INTERACTION_PLAYER_GIFT));
        interactions.setInteractionHint(WintertideIds.INTERACTION_HINT);
        return interactions;
    }
}
