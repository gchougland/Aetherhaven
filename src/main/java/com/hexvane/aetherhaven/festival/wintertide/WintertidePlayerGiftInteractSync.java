package com.hexvane.aetherhaven.festival.wintertide;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
        boolean ours = existing != null
            && WintertideIds.ROOT_INTERACTION_PLAYER_GIFT.equals(existing.getInteractionId(InteractionType.Use));
        if (ours) {
            commandBuffer.removeComponent(ref, Interactions.getComponentType());
        }
        // Interactable is what draws the F prompt, so it has to go too or the hint stays on a player with no gift.
        if ((ours || existing == null) && commandBuffer.getArchetype(ref).contains(Interactable.getComponentType())) {
            commandBuffer.removeComponent(ref, Interactable.getComponentType());
        }
    }

    /** Same as {@link #clear} for callers outside a ticking system, such as dialogue and festival end. */
    public static void clear(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Interactions existing = store.getComponent(ref, Interactions.getComponentType());
        boolean ours = existing != null
            && WintertideIds.ROOT_INTERACTION_PLAYER_GIFT.equals(existing.getInteractionId(InteractionType.Use));
        if (ours) {
            store.tryRemoveComponent(ref, Interactions.getComponentType());
        }
        if (ours || existing == null) {
            store.tryRemoveComponent(ref, Interactable.getComponentType());
        }
    }

    /** Drops the gift prompt from every player, used when Wintertide ends. */
    public static void clearForAllPlayers(@Nonnull Store<EntityStore> store) {
        store.forEachChunk(
            Query.and(Player.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (ref != null && ref.isValid()) {
                        clear(ref, commandBuffer);
                    }
                }
            }
        );
    }

    @Nonnull
    static Interactions giftInteractions() {
        Interactions interactions =
            new Interactions(Map.of(InteractionType.Use, WintertideIds.ROOT_INTERACTION_PLAYER_GIFT));
        interactions.setInteractionHint(WintertideIds.INTERACTION_HINT);
        return interactions;
    }
}
