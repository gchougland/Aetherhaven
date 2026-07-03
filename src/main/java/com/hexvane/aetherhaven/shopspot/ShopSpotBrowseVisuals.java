package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Ponder emote while NPCs browse shop listings before a purchase decision. */
public final class ShopSpotBrowseVisuals {
    /** Built-in emote from vanilla {@code EmotesInGame.json}. */
    public static final String PONDER_EMOTE_ID = "PonderDismissive";

    private ShopSpotBrowseVisuals() {}

    public static void beginPonder(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        playPonder(npcRef, commandBuffer);
    }

    public static void beginPonder(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        playPonder(npcRef, store);
    }

    public static void endPonder(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        stopPonder(npcRef, store, commandBuffer);
    }

    public static void endPonder(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        stopPonder(npcRef, store, null);
    }

    private static void playPonder(@Nonnull Ref<EntityStore> npcRef, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NpcAnimationPlayback.play(npcRef, AnimationSlot.Emote, PONDER_EMOTE_ID, commandBuffer);
    }

    private static void playPonder(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        AnimationUtils.playAnimation(npcRef, AnimationSlot.Emote, null, PONDER_EMOTE_ID, false, store);
    }

    private static void stopPonder(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (commandBuffer != null) {
            NpcAnimationPlayback.stop(npcRef, AnimationSlot.Emote, commandBuffer);
            if (npc != null) {
                NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Emote, null, commandBuffer);
            }
            return;
        }
        AnimationUtils.stopAnimation(npcRef, AnimationSlot.Emote, store);
        if (npc != null) {
            npc.playAnimation(npcRef, AnimationSlot.Emote, null, store);
        }
    }
}
