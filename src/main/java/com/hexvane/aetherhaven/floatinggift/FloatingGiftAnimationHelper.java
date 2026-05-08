package com.hexvane.aetherhaven.floatinggift;

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
        ActiveAnimationComponent aac = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (aac != null) {
            String[] active = aac.getActiveAnimations();
            if (slot != AnimationSlot.Action && Objects.equals(active[slot.ordinal()], animationSetId)) {
                return;
            }
            aac.setPlayingAnimation(slot, animationSetId);
            markAnimationNetworkOutdated(aac);
        }
        // AnimationUtils assumes NetworkId (PlayAnimation packets); entities can lack it for a tick after CommandBuffer spawn.
        if (store.getComponent(ref, NetworkId.getComponentType()) != null) {
            AnimationUtils.playAnimation(ref, slot, animationSetId, store);
        }
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
