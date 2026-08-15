package com.hexvane.aetherhaven.festival.market;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;

/** Keeps Use (F) on the shared stall pad. */
public final class MarketStallInteractSync {
    private MarketStallInteractSync() {}

    public static void sync(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (commandBuffer.getComponent(ref, Interactable.getComponentType()) == null) {
            commandBuffer.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        }
        Interactions existing = commandBuffer.getComponent(ref, Interactions.getComponentType());
        if (existing == null
            || !MarketIds.ROOT_INTERACTION_STALL.equals(existing.getInteractionId(InteractionType.Use))
            || !MarketIds.INTERACTION_HINT.equals(existing.getInteractionHint())) {
            commandBuffer.putComponent(ref, Interactions.getComponentType(), stallInteractions());
        }
    }

    @Nonnull
    static Interactions stallInteractions() {
        Interactions interactions =
            new Interactions(Map.of(InteractionType.Use, MarketIds.ROOT_INTERACTION_STALL));
        interactions.setInteractionHint(MarketIds.INTERACTION_HINT);
        return interactions;
    }
}
