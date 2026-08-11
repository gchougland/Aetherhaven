package com.hexvane.aetherhaven.festival.carnival;

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

/** Banners and chat lines for carnival minigame results. */
public final class CarnivalAnnounce {
    private static final double ANNOUNCE_RADIUS = 48.0;
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.carnival.";

    private CarnivalAnnounce() {}

    public static void announceWheelColor(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull CarnivalWheelSession.Outcome outcome,
        @Nullable Vector3d squareCenter
    ) {
        String color = switch (outcome) {
            case WIN -> "red";
            case LOSE -> "white";
            case CLOWN -> "blue";
        };
        Message title = Message.translation(LANG + "banner.wheel." + color + ".title");
        Message subtitle = Message.translation(LANG + "banner.wheel." + color + ".subtitle");
        int sting = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID);
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

    public static void announceBalloonPopCount(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        int popped
    ) {
        Message chat = Message.translation(LANG + "chat.balloons.popped").param("count", String.valueOf(popped));
        sendChatToPlayer(store, playerUuid, chat);
    }

    public static void announceWhackHitCount(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        int hits,
        int total
    ) {
        Message chat =
            Message.translation(LANG + "chat.whack.hits")
                .param("hits", String.valueOf(hits))
                .param("total", String.valueOf(Math.max(total, hits)));
        sendChatToPlayer(store, playerUuid, chat);
    }

    private static void sendChatToPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull Message chat
    ) {
        Query<EntityStore> query = Query.and(PlayerRef.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                if (uc == null || pr == null || !playerUuid.equals(uc.getUuid())) {
                    continue;
                }
                pr.sendMessage(chat);
                return;
            }
        });
    }
}
