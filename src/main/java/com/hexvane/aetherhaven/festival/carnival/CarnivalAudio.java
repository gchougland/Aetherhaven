package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceAudio;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftSounds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Whistle / finish / pop / wood-tick cues for carnival minigames. */
public final class CarnivalAudio {
    public static final String SOUND_WHEEL_TICK = "SFX_Wood_Hit";

    private CarnivalAudio() {}

    public static void playBalloonStart(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_WHISTLE_START);
    }

    public static void playBalloonFinish(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_FINISH);
    }

    public static void playBalloonPop(@Nonnull Store<EntityStore> store, @Nonnull Vector3d at) {
        FloatingGiftSounds.playPop3d(at, store);
    }

    public static void playWhackStart(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_WHISTLE_START);
    }

    public static void playWhackFinish(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, PigRaceAudio.SOUND_FINISH);
    }

    public static void playGoblinHurt(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, CarnivalIds.SOUND_GOBLIN_HURT);
    }

    public static void playGoblinAlert(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, CarnivalIds.SOUND_GOBLIN_ALERT);
    }

    public static void playWheelTick(@Nonnull Store<EntityStore> store, @Nullable Vector3d at) {
        play(store, at, SOUND_WHEEL_TICK);
    }

    @Nullable
    public static Vector3d squareCenter(
        @Nonnull com.hexvane.aetherhaven.AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        UUID plotId = town.getActiveFestivalPlotId();
        if (plotId == null) {
            return null;
        }
        PlotInstance square = town.findPlotById(plotId);
        if (square == null) {
            return null;
        }
        return FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 6, 0);
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
