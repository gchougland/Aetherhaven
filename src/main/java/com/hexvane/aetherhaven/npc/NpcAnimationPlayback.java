package com.hexvane.aetherhaven.npc;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Animation playback that is safe to call from entity ticking systems.
 *
 * <p>{@link NPCEntity#playAnimation} / {@link AnimationUtils#playAnimation} fan out packets via
 * {@code PlayerUtil.forEachPlayerThatCanSeeEntity}, which runs {@code Store.forEachChunk} and then
 * consumes a command buffer. That write path is illegal while the store is processing a system tick
 * ({@code Store is currently processing!}). Defer through {@link CommandBuffer#run} — the same pattern
 * as vanilla {@code NPCPreTickSystem}.
 */
public final class NpcAnimationPlayback {
    private NpcAnimationPlayback() {}

    public static void play(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull AnimationSlot slot,
        @Nullable String animationId,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            npc.playAnimation(ref, slot, animationId, store);
        });
    }

    public static void play(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nullable String animationId,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            AnimationUtils.playAnimation(ref, slot, animationId, store);
        });
    }

    public static void playItem(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull ItemPlayerAnimations itemAnimations,
        @Nonnull String animationId,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            AnimationUtils.playAnimation(ref, slot, itemAnimations, animationId, store);
        });
    }

    public static void playItem(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull String itemAnimationsId,
        @Nullable String animationId,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            AnimationUtils.playAnimation(ref, slot, itemAnimationsId, animationId, false, store);
        });
    }

    public static void stop(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            AnimationUtils.stopAnimation(ref, slot, store);
        });
    }

    /** Clear Action / Emote / Status overlays (e.g. leaving autonomy POI travel). */
    public static void clearOverlaySlots(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        commandBuffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            npc.playAnimation(ref, AnimationSlot.Action, null, store);
            npc.playAnimation(ref, AnimationSlot.Emote, null, store);
            npc.playAnimation(ref, AnimationSlot.Status, null, store);
        });
    }
}
