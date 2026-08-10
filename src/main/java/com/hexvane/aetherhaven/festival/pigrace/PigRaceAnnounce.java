package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Top-of-screen banners for pig race results. */
public final class PigRaceAnnounce {
    private static final double ANNOUNCE_RADIUS = 48.0;
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.pig_race.";

    private PigRaceAnnounce() {}

    public static void announceWinner(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        int winningLane,
        @Nullable Vector3d squareCenter
    ) {
        Message pigName = Message.translation(PigRaceLanes.displayNameLangKey(winningLane));
        Message title = Message.translation(LANG + "banner.winner.title").param("pig", pigName);
        Message subtitle = Message.translation(LANG + "banner.winner.subtitle");
        int finishSting = SoundEvent.getAssetMap().getIndex(PigRaceAudio.SOUND_FINISH);
        final int sting =
            finishSting == SoundEvent.EMPTY_ID || finishSting == Integer.MIN_VALUE
                ? SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID)
                : finishSting;
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                if (uc == null || pr == null) {
                    continue;
                }
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                boolean nearSquare = squareCenter != null
                    && tc != null
                    && tc.getPosition().distanceSquared(squareCenter) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
                if (!town.hasMemberOrOwner(uc.getUuid()) && !nearSquare) {
                    continue;
                }
                EventTitleUtil.showEventTitleToPlayer(pr, title, subtitle, true, null, 4.0F, 0.7F, 0.9F);
                if (sting != SoundEvent.EMPTY_ID) {
                    SoundUtil.playSoundEvent2dToPlayer(pr, sting, SoundCategory.UI);
                }
            }
        });
    }
}
