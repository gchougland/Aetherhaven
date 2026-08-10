package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Turns the F prompt on only when the Springheart Lettuce is full enough to pop. Uses the entity Use root (same
 * pattern as purification), not NPC {@code SetInteractable}.
 */
public final class FestivalLettuceInteractSync {
    private FestivalLettuceInteractSync() {}

    public static void sync(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull FestivalLettuceComponent lettuce,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        boolean ready = lettuce.isReadyToBurst();
        boolean hasInteractable = accessor.getComponent(ref, Interactable.getComponentType()) != null;
        if (ready && !hasInteractable) {
            accessor.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
            accessor.putComponent(ref, Interactions.getComponentType(), burstInteractions());
        } else if (!ready && hasInteractable) {
            accessor.removeComponent(ref, Interactable.getComponentType());
        } else if (ready) {
            ensureBurstUse(ref, accessor);
        }
    }

    public static void sync(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull FestivalLettuceComponent lettuce,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        boolean ready = lettuce.isReadyToBurst();
        boolean hasInteractable = commandBuffer.getComponent(ref, Interactable.getComponentType()) != null;
        if (ready && !hasInteractable) {
            commandBuffer.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
            commandBuffer.putComponent(ref, Interactions.getComponentType(), burstInteractions());
        } else if (!ready && hasInteractable) {
            commandBuffer.removeComponent(ref, Interactable.getComponentType());
        } else if (ready) {
            ensureBurstUse(ref, commandBuffer);
        }
    }

    private static void ensureBurstUse(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Interactions existing = accessor.getComponent(ref, Interactions.getComponentType());
        if (isBurstUse(existing)) {
            return;
        }
        accessor.putComponent(ref, Interactions.getComponentType(), burstInteractions());
    }

    private static void ensureBurstUse(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Interactions existing = commandBuffer.getComponent(ref, Interactions.getComponentType());
        if (isBurstUse(existing)) {
            return;
        }
        commandBuffer.putComponent(ref, Interactions.getComponentType(), burstInteractions());
    }

    private static boolean isBurstUse(@javax.annotation.Nullable Interactions existing) {
        return existing != null
            && FestivalLettuceComponent.ROOT_INTERACTION_BURST.equals(existing.getInteractionId(InteractionType.Use))
            && FestivalLettuceComponent.INTERACTION_HINT.equals(existing.getInteractionHint());
    }

    @Nonnull
    private static Interactions burstInteractions() {
        Interactions interactions =
            new Interactions(Map.of(InteractionType.Use, FestivalLettuceComponent.ROOT_INTERACTION_BURST));
        interactions.setInteractionHint(FestivalLettuceComponent.INTERACTION_HINT);
        return interactions;
    }
}
