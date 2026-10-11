package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import javax.annotation.Nonnull;

/** Quest-style event title + banner sting when a quest board job is turned in. */
public final class QuestBoardCompletionEffects {
    private QuestBoardCompletionEffects() {}

    public static void notifyCompleted(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message questTitle
    ) {
        EventTitleUtil.showEventTitleToPlayer(
            playerRef,
            questTitle,
            Message.translation("aetherhaven_misc.aetherhaven.banner.quest.completed.primary"),
            EventTitleStyle.Major,
            null,
            4.0F,
            0.7F,
            0.9F
        );
        int sfx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID);
        if (sfx != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2d(playerEntityRef, sfx, SoundCategory.UI, store);
        }
    }
}
