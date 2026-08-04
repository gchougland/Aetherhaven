package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves the NPC role assigned to a production workplace plot (persisted + live fallbacks). */
final class ProductionAssignedWorker {
    record WorkerRole(@Nonnull String npcRoleId, @Nullable UUID entityUuid) {}

    private ProductionAssignedWorker() {}

    @Nullable
    static WorkerRole resolve(
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull PlotProductionState plotState,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionCatalog ccat,
        @Nonnull Store<EntityStore> store
    ) {
        String persisted = plotState.getAssignedWorkerNpcRoleId();
        if (persisted != null && !persisted.isBlank()) {
            UUID uuid = entityUuidForPlot(town, plotId, persisted.trim());
            return new WorkerRole(persisted.trim(), uuid);
        }

        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            UUID job = r.getJobPlotId();
            if (job == null || !job.equals(plotId)) {
                continue;
            }
            String role = r.getNpcRoleId();
            if (role != null && !role.isBlank()) {
                plotState.setAssignedWorkerNpcRoleId(role.trim());
                return new WorkerRole(role.trim(), r.getLastEntityUuid());
            }
        }

        WorkerRole fromBinding = resolveFromLiveBinding(store, town.getTownId(), plotId, ccat, plot.getConstructionId());
        if (fromBinding != null) {
            plotState.setAssignedWorkerNpcRoleId(fromBinding.npcRoleId());
            return fromBinding;
        }

        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!ProductionCatchUpCursor.isProductionWorkerKind(r.getKind())) {
                continue;
            }
            if (ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                ccat, plot.getConstructionId(), r.getKind()
            ) == null) {
                continue;
            }
            String role = r.getNpcRoleId();
            if (role != null && !role.isBlank()) {
                plotState.setAssignedWorkerNpcRoleId(role.trim());
                return new WorkerRole(role.trim(), r.getLastEntityUuid());
            }
        }
        return null;
    }

    @Nullable
    private static UUID entityUuidForPlot(@Nonnull TownRecord town, @Nonnull UUID plotId, @Nonnull String roleId) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!plotId.equals(r.getJobPlotId())) {
                continue;
            }
            if (roleId.equalsIgnoreCase(r.getNpcRoleId())) {
                return r.getLastEntityUuid();
            }
        }
        return null;
    }

    @Nullable
    private static WorkerRole resolveFromLiveBinding(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        @Nonnull ConstructionCatalog ccat,
        @Nullable String plotStoredConstructionId
    ) {
        AtomicReference<WorkerRole> found = new AtomicReference<>();
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found.get() != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !townId.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUID job = b.getJobPlotId();
                    if (job == null || !job.equals(plotId)) {
                        continue;
                    }
                    if (!ProductionCatchUpCursor.isProductionWorkerKind(b.getKind())
                        && ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                            ccat, plotStoredConstructionId, b.getKind()
                        ) == null) {
                        continue;
                    }
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    found.set(new WorkerRole(npc.getRoleName().trim(), uc != null ? uc.getUuid() : null));
                    return;
                }
            }
        );
        return found.get();
    }
}
