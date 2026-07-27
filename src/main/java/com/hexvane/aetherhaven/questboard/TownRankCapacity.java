package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerState;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Town quest board rank limits for escort followers and hired guards. */
public final class TownRankCapacity {
    private TownRankCapacity() {}

    public static int rankIndexForTown(@Nonnull TownRecord town, @Nonnull QuestBoardCatalog catalog) {
        String tierId = TownQuestBoardRank.tierIdForXp(town.getQuestBoardRankXp(), catalog);
        return TownQuestBoardRank.rankIndex(tierId);
    }

    /** Max NPCs that can follow one player at once (rank tier number). */
    public static int maxFollowers(int rankIndex) {
        return rankIndex + 1;
    }

    public static int maxFollowers(@Nonnull TownRecord town, @Nonnull QuestBoardCatalog catalog) {
        return maxFollowers(rankIndexForTown(town, catalog));
    }

    /** Max hired guards for the town (rank tier number × 2). */
    public static int maxHiredGuards(int rankIndex) {
        return (rankIndex + 1) * 2;
    }

    public static int maxHiredGuards(@Nonnull TownRecord town, @Nonnull QuestBoardCatalog catalog) {
        return maxHiredGuards(rankIndexForTown(town, catalog));
    }

    public static int countActiveFollowers(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        AtomicInteger count = new AtomicInteger(0);
        store.forEachEntityParallel(VillagerFollowPlayerState.getComponentType(), (index, chunk, commandBuffer) -> {
            VillagerFollowPlayerState state = chunk.getComponent(index, VillagerFollowPlayerState.getComponentType());
            if (state != null && state.isFollowing(playerUuid)) {
                count.incrementAndGet();
            }
        });
        store.forEachEntityParallel(GuardFollowPlayerState.getComponentType(), (index, chunk, commandBuffer) -> {
            GuardFollowPlayerState state = chunk.getComponent(index, GuardFollowPlayerState.getComponentType());
            if (state != null && state.isFollowing(playerUuid)) {
                count.incrementAndGet();
            }
        });
        return count.get();
    }

    public static boolean canStartFollow(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull TownRecord town,
        @Nonnull QuestBoardCatalog catalog,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef != null && npcRef.isValid()) {
            VillagerFollowPlayerState villagerFollow =
                store.getComponent(npcRef, VillagerFollowPlayerState.getComponentType());
            if (villagerFollow != null && villagerFollow.isFollowing(playerUuid)) {
                return true;
            }
            GuardFollowPlayerState guardFollow = store.getComponent(npcRef, GuardFollowPlayerState.getComponentType());
            if (guardFollow != null && guardFollow.isFollowing(playerUuid)) {
                return true;
            }
        }
        return countActiveFollowers(store, playerUuid) < maxFollowers(town, catalog);
    }

    public static boolean canHireGuard(@Nonnull TownRecord town, @Nonnull QuestBoardCatalog catalog) {
        return town.getHiredGuardRecords().size() < maxHiredGuards(town, catalog);
    }
}
