package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.festival.pigrace.PigRaceAudio;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Sounds for maze orb pickups, countdown go, and race end. */
public final class HallowsEveAudio {
    private HallowsEveAudio() {}

    public static void playOrbCollect(
        @Nullable Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d at,
        int collectedCount
    ) {
        int idx = SoundEvent.getAssetMap().getIndex(HallowsEveIds.COLLECT_SOUND);
        if (idx == SoundEvent.EMPTY_ID || idx == Integer.MIN_VALUE || idx == 0) {
            return;
        }
        float pitch = 1.0F + (Math.max(0, collectedCount) % 5) * 0.04F;
        if (playerRef != null && playerRef.isValid()) {
            SoundUtil.playSoundEvent2d(playerRef, idx, SoundCategory.SFX, 1.0F, pitch, store);
            return;
        }
        SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, 1.0F, pitch, store);
    }

    public static void playRaceStart(@Nonnull Store<EntityStore> store, @Nonnull HallowsEveSession session) {
        playAtStart(store, session, PigRaceAudio.SOUND_WHISTLE_START);
    }

    public static void playRaceFinish(@Nonnull Store<EntityStore> store, @Nonnull HallowsEveSession session) {
        playAtStart(store, session, PigRaceAudio.SOUND_FINISH);
    }

    private static void playAtStart(
        @Nonnull Store<EntityStore> store,
        @Nonnull HallowsEveSession session,
        @Nonnull String soundEventId
    ) {
        Vector3d at = new Vector3d(session.getStartX(), session.getStartY(), session.getStartZ());
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx == SoundEvent.EMPTY_ID || idx == Integer.MIN_VALUE || idx == 0) {
            return;
        }
        SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, 1.0F, 1.0F, store);
    }
}
