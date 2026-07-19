package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** 2D UI sounds for custom pages and dialogue actions (player-local, no world position). */
public final class UiSoundEffects {
    private UiSoundEffects() {}

    public static void play2dUi(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String soundEventId) {
        play2d(ref, store, soundEventId, SoundCategory.UI, 1f, 1f);
    }

    public static void play2dUi(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String soundEventId,
        float volumeModifier,
        float pitchModifier
    ) {
        play2d(ref, store, soundEventId, SoundCategory.UI, volumeModifier, pitchModifier);
    }

    public static void play2d(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String soundEventId,
        @Nonnull SoundCategory category,
        float volumeModifier,
        float pitchModifier
    ) {
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx == Integer.MIN_VALUE || idx == SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2d(ref, idx, category, volumeModifier, pitchModifier, store);
    }
}
