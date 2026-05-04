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
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx == Integer.MIN_VALUE || idx == SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2d(ref, idx, SoundCategory.UI, store);
    }
}
