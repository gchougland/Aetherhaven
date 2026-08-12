package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.festival.pigrace.PigRaceAudio;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Race start whistle and finish brass from the pig race sound events. */
public final class TreeClimbAudio {
    private TreeClimbAudio() {}

    public static void playRaceStart(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_WHISTLE_START);
    }

    public static void playRaceFinish(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_FINISH);
    }

    private static void play(@Nonnull Store<EntityStore> store, @Nullable Vector3d at, @Nonnull String soundEventId) {
        if (at == null) {
            return;
        }
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx == SoundEvent.EMPTY_ID || idx == Integer.MIN_VALUE || idx == 0) {
            return;
        }
        SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, at.x, at.y, at.z, 1.0F, 1.0F, store);
    }
}
