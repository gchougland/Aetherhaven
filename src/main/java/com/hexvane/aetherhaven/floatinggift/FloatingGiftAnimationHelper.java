package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mirrors {@link com.hypixel.hytale.server.npc.entities.NPCEntity#playAnimation} bookkeeping: keep
 * {@link ActiveAnimationComponent} per-slot state aligned with {@link AnimationUtils} packets, and mark
 * {@code isNetworkOutdated} so {@code ModelSystems.AnimationEntityTrackerUpdate} can send
 * {@link com.hypixel.hytale.protocol.ActiveAnimationsUpdate} to viewers (see vanilla
 * {@code ModelSystems.AnimationEntityTrackerUpdate#tick}).
 */
public final class FloatingGiftAnimationHelper {
    @Nullable
    private static volatile Field networkOutdatedFieldResolved;

    private FloatingGiftAnimationHelper() {}

    public static void playAnimation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull String animationSetId
    ) {
        if (!applyActiveAnimation(store.getComponent(ref, ActiveAnimationComponent.getComponentType()), slot, animationSetId)) {
            return;
        }
        // AnimationUtils assumes NetworkId (PlayAnimation packets); entities can lack it for a tick after CommandBuffer spawn.
        if (store.getComponent(ref, NetworkId.getComponentType()) != null) {
            AnimationUtils.playAnimation(ref, slot, animationSetId, store);
        }
    }

    /** Safe from entity ticking systems — defers packet fan-out until the store is not processing. */
    public static void playAnimation(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull String animationSetId
    ) {
        if (!applyActiveAnimation(commandBuffer.getComponent(ref, ActiveAnimationComponent.getComponentType()), slot, animationSetId)) {
            return;
        }
        commandBuffer.run(store -> {
            if (!ref.isValid() || store.getComponent(ref, NetworkId.getComponentType()) == null) {
                return;
            }
            AnimationUtils.playAnimation(ref, slot, animationSetId, store);
        });
    }

    public static void stopAnimation(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull AnimationSlot slot) {
        ActiveAnimationComponent aac = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (aac != null && aac.getActiveAnimations()[slot.ordinal()] != null) {
            aac.getActiveAnimations()[slot.ordinal()] = null;
            markAnimationNetworkOutdated(aac);
        }
        if (store.getComponent(ref, NetworkId.getComponentType()) != null) {
            AnimationUtils.stopAnimation(ref, slot, store);
        }
    }

    public static void stopAnimation(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot
    ) {
        ActiveAnimationComponent aac = commandBuffer.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (aac != null && aac.getActiveAnimations()[slot.ordinal()] != null) {
            aac.getActiveAnimations()[slot.ordinal()] = null;
            markAnimationNetworkOutdated(aac);
        }
        commandBuffer.run(store -> {
            if (!ref.isValid() || store.getComponent(ref, NetworkId.getComponentType()) == null) {
                return;
            }
            AnimationUtils.stopAnimation(ref, slot, store);
        });
    }

    /** Stop then play — forces a procedural loop restart on clients. */
    public static void restartAnimation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull String animationSetId
    ) {
        stopAnimation(store, ref, slot);
        playAnimation(store, ref, slot, animationSetId);
    }

    public static void restartAnimation(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AnimationSlot slot,
        @Nonnull String animationSetId
    ) {
        stopAnimation(commandBuffer, ref, slot);
        playAnimation(commandBuffer, ref, slot, animationSetId);
    }

    /** @return false when the slot already plays {@code animationSetId} and no packet should be sent */
    private static boolean applyActiveAnimation(
        @Nullable ActiveAnimationComponent aac,
        @Nonnull AnimationSlot slot,
        @Nonnull String animationSetId
    ) {
        if (aac == null) {
            return true;
        }
        String[] active = aac.getActiveAnimations();
        if (slot != AnimationSlot.Action && Objects.equals(active[slot.ordinal()], animationSetId)) {
            return false;
        }
        aac.setPlayingAnimation(slot, animationSetId);
        markAnimationNetworkOutdated(aac);
        return true;
    }

    private static void markAnimationNetworkOutdated(@Nonnull ActiveAnimationComponent aac) {
        try {
            Field f = networkOutdatedFieldResolved;
            if (f == null) {
                synchronized (FloatingGiftAnimationHelper.class) {
                    f = networkOutdatedFieldResolved;
                    if (f == null) {
                        Field declared = ActiveAnimationComponent.class.getDeclaredField("isNetworkOutdated");
                        declared.setAccessible(true);
                        networkOutdatedFieldResolved = f = declared;
                    }
                }
            }
            f.setBoolean(aac, true);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Renamed field in a future build: PlayAnimation packets still apply.
        }
    }
}
