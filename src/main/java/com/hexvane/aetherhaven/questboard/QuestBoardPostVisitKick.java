package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Starts dawn quest-board post travel for due givers that are currently loaded. */
public final class QuestBoardPostVisitKick {
    private QuestBoardPostVisitKick() {}

    /**
     * For each town with pending dawn posts, try to start travel for due NPCs that are loaded in this world.
     * Safe to call every game-minute tick.
     */
    public static void kickDuePosters(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);
        QuestBoardPostVisitQueue.rebaseIfWallClockSkew(now);
        List<UUID> townIds = QuestBoardPostVisitQueue.townIdsWithPending();
        if (townIds.isEmpty()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (UUID townId : townIds) {
            List<UUID> due = QuestBoardPostVisitQueue.dueNpcUuids(townId, now);
            if (due.isEmpty()) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null) {
                continue;
            }
            for (UUID npcUuid : due) {
                kickOne(plugin, world, store, town, npcUuid, now);
            }
        }
    }

    private static void kickOne(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID npcUuid,
        long now
    ) {
        store.forEachChunk(
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                int n = chunk.size();
                for (int i = 0; i < n; i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !npcUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    VillagerNeeds needs = chunk.getComponent(i, VillagerNeeds.getComponentType());
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (binding == null || needs == null || npc == null || npc.getRole() == null) {
                        return true;
                    }
                    if (!town.getTownId().equals(binding.getTownId())) {
                        return true;
                    }
                    if (TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
                        QuestBoardPostVisitQueue.consume(town.getTownId(), npcUuid);
                        return true;
                    }
                    if (VillagerAutonomySystem.skipsPoiAutonomy(binding, npc)) {
                        return true;
                    }
                    VillagerAutonomyState autonomy = chunk.getComponent(i, VillagerAutonomyState.getComponentType());
                    if (autonomy == null) {
                        autonomy = VillagerAutonomyState.fresh(now);
                    }
                    // Interrupt whatever they were doing so the dawn post is not stuck behind work USE.
                    if (autonomy.getPhase() != VillagerAutonomyState.PHASE_IDLE) {
                        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);
                        autonomy.setTargetPoiUuid(null);
                        autonomy.clearTravelWaypoints();
                        autonomy.clearPendingDoorClose();
                        autonomy.setPathFailureReason("");
                        autonomy.setTravelStuckTicks(0);
                        autonomy.setNextDecisionEpochMs(now);
                        commandBuffer.putComponent(ref, VillagerAutonomyState.getComponentType(), autonomy);
                    }
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    VillagerAutonomySystem.tryBeginQuestBoardPostTravel(
                        ref, store, commandBuffer, world, npc, binding, autonomy, town, now, plugin, tc
                    );
                    return true;
                }
                return false;
            }
        );
    }
}
