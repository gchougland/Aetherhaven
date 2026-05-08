package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Mod-local sound events with long {@code MaxDistance} so cues reach players when the balloon spawns far away
 * (vanilla {@code SFX_Attn_Quiet} parents cap around ~15 blocks).
 */
public final class FloatingGiftSounds {
    public static final String SOUND_EVENT_POP = "Aetherhaven_FloatingGift_Pop";
    public static final String SOUND_EVENT_NEARBY = "Aetherhaven_FloatingGift_Nearby";

    /** Seconds between ambient wind cues while floating. */
    public static final float NEARBY_AMBIENT_INTERVAL_SEC = 5.5f;

    private static volatile int popIndexResolved = Integer.MIN_VALUE;
    private static volatile int nearbyIndexResolved = Integer.MIN_VALUE;

    private FloatingGiftSounds() {}

    public static int getPopSoundEventIndex() {
        int local = popIndexResolved;
        if (local != Integer.MIN_VALUE) {
            return Math.max(0, local);
        }
        synchronized (FloatingGiftSounds.class) {
            if (popIndexResolved == Integer.MIN_VALUE) {
                popIndexResolved = SoundEvent.getAssetMap().getIndex(SOUND_EVENT_POP);
            }
            return Math.max(0, popIndexResolved);
        }
    }

    public static int getNearbyAmbientSoundEventIndex() {
        int local = nearbyIndexResolved;
        if (local != Integer.MIN_VALUE) {
            return Math.max(0, local);
        }
        synchronized (FloatingGiftSounds.class) {
            if (nearbyIndexResolved == Integer.MIN_VALUE) {
                nearbyIndexResolved = SoundEvent.getAssetMap().getIndex(SOUND_EVENT_NEARBY);
            }
            return Math.max(0, nearbyIndexResolved);
        }
    }

    public static void playPop3d(@Nonnull Vector3d position, @Nonnull Store<EntityStore> store) {
        int idx = getPopSoundEventIndex();
        if (idx != 0) {
            SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, position.x, position.y, position.z, 1.45F, 1.0F, store);
        }
    }

    public static void playNearbyAmbient3d(@Nonnull Vector3d position, @Nonnull Store<EntityStore> store) {
        int idx = getNearbyAmbientSoundEventIndex();
        if (idx != 0) {
            SoundUtil.playSoundEvent3d(idx, SoundCategory.Ambient, position.x, position.y, position.z, 1.35F, 1.0F, store);
        }
    }
}
