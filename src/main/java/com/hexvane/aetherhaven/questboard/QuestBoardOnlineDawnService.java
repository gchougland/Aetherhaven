package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Tracks online dawn crossings per player. Board slot refresh and quest expiry advance only while a town member with
 * quest permission is online when dawn passes.
 */
public final class QuestBoardOnlineDawnService {
    private static final Map<UUID, Long> LAST_ONLINE_DAWN_BY_PLAYER = new ConcurrentHashMap<>();

    private QuestBoardOnlineDawnService() {}

    public static void clearPlayer(@Nonnull UUID playerUuid) {
        LAST_ONLINE_DAWN_BY_PLAYER.remove(playerUuid);
    }

    public static void onPlayerReady(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        long dawn = VillagerReputationService.currentGameEpochDay(store);
        LAST_ONLINE_DAWN_BY_PLAYER.putIfAbsent(uc.getUuid(), dawn);
    }

    public static void tickWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr
    ) {
        long currentDawn = VillagerReputationService.currentGameEpochDay(store);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        QuestBoardCatalog catalog = plugin.getQuestBoardCatalog();

        for (PlayerRef pr : world.getPlayerRefs()) {
            UUID playerUuid = pr.getUuid();
            Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Long last = LAST_ONLINE_DAWN_BY_PLAYER.get(playerUuid);
            if (last == null) {
                LAST_ONLINE_DAWN_BY_PLAYER.put(playerUuid, currentDawn);
                continue;
            }
            if (currentDawn <= last) {
                continue;
            }
            LAST_ONLINE_DAWN_BY_PLAYER.put(playerUuid, currentDawn);

            TownRecord town = tm.findTownForPlayerInWorld(playerUuid);
            if (town == null || !town.playerHasQuestPermission(playerUuid)) {
                continue;
            }

            Random rng = new Random(currentDawn ^ playerUuid.getMostSignificantBits());
            QuestBoardService.refreshUnacceptedSlots(town, store, catalog, rng);
            town.setQuestBoardLastRefreshOnlineDawnDay(currentDawn);

            List<String> posterUuids = new ArrayList<>();
            for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
                if (slot.stateEnum() != QuestBoardSlotState.OFFER) {
                    continue;
                }
                String giver = slot.getGiverEntityUuid();
                if (giver != null && !giver.isBlank()) {
                    posterUuids.add(giver);
                }
            }
            if (!posterUuids.isEmpty()) {
                long nowMs = System.currentTimeMillis();
                QuestBoardPostVisitQueue.enqueueOfferGiversForDawn(town.getTownId(), posterUuids, nowMs, currentDawn);
            }

            boolean townChanged = true;
            for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
                if (!slot.isAccepted()) {
                    continue;
                }
                String acceptor = slot.getAcceptedByPlayerUuid();
                if (acceptor == null || !acceptor.equals(playerUuid.toString())) {
                    continue;
                }
                slot.setOnlineDaysElapsed(slot.getOnlineDaysElapsed() + 1);
                if (slot.getOnlineDaysElapsed() >= slot.getDaysLimit()) {
                    QuestBoardService.failExpiredQuest(town, tm, slot, store, catalog, rng, pr);
                }
            }
            if (townChanged) {
                tm.updateTown(town);
            }
        }
    }
}
