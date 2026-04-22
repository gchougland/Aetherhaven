package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Charter amendments: population counts and tier thresholds. */
public final class TownCharterService {
    public static final int TIER1_RESIDENTS_REQUIRED = 3;
    public static final int TIER2_RESIDENTS_REQUIRED = 5;

    private TownCharterService() {}

    /**
     * Counts non-visitor {@link TownVillagerBinding} NPCs for the town (same basis as morning tax).
     */
    public static int countResidents(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        UUID tid = town.getTownId();
        int[] n = new int[1];
        Query<EntityStore> q =
            Query.and(
                TownVillagerBinding.getComponentType(),
                VillagerNeeds.getComponentType(),
                UUIDComponent.getComponentType(),
                NPCEntity.getComponentType()
            );
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buf) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    n[0]++;
                }
            }
        );
        return n[0];
    }

    /** 0 = none, 1 = tax policy row, 2 = specialization row. */
    public static int unlockedAmendmentTier(int residentCount) {
        if (residentCount >= TIER2_RESIDENTS_REQUIRED) {
            return 2;
        }
        if (residentCount >= TIER1_RESIDENTS_REQUIRED) {
            return 1;
        }
        return 0;
    }
}
