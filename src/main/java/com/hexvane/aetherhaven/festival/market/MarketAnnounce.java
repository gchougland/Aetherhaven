package com.hexvane.aetherhaven.festival.market;

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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Top-of-screen banners for Market Festival judging. */
public final class MarketAnnounce {
    private static final double ANNOUNCE_RADIUS = 48.0;
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.market.";

    private MarketAnnounce() {}

    public static void announceResults(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull MarketSession session,
        @Nonnull UUID talkingPlayer
    ) {
        String winnerId = MarketScore.winnerId(session.getScore(), town.getDisplayName());
        Message winnerName =
            MarketScore.isRivalWinnerId(winnerId)
                ? Message.translation(LANG + "rival." + winnerId)
                : Message.raw(town.getDisplayName());
        Message title = Message.translation(LANG + "banner.winner.title").param("winner", winnerName);
        Message subtitle = Message.translation(LANG + "banner.place." + session.getPlace());
        int sting = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID);
        Vector3d squareCenter = session.getStallDropPos();
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
                Message playerSubtitle =
                    talkingPlayer.equals(uc.getUuid())
                        ? subtitle
                        : Message.translation(LANG + "banner.winner.subtitle");
                EventTitleUtil.showEventTitleToPlayer(pr, title, playerSubtitle, true, null, 4.0F, 0.7F, 0.9F);
                if (sting != SoundEvent.EMPTY_ID) {
                    SoundUtil.playSoundEvent2dToPlayer(pr, sting, SoundCategory.UI);
                }
            }
        });
    }
}
