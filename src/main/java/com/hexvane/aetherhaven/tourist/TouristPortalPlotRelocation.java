package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Detaches tourist portals when a plot footprint is cleared for relocation or removal. */
public final class TouristPortalPlotRelocation {
    private static final Map<UUID, Map<Long, TouristPortalRecord>> PENDING_BY_PLOT = new ConcurrentHashMap<>();

    private TouristPortalPlotRelocation() {}

    /**
     * Removes portal registry rows before a plot move. Portal ids are keyed by prefab-local cell so
     * {@link TouristPortalExtractor} can rebind them after the building is pasted at the new pose.
     * Tourists already walking home are paused so they do not path to the cleared footprint.
     */
    public static void beginPlotMove(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull UUID plotId,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull TownRecord town
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        purgeFillerPortalRecords(world, plugin, store, registry, tm);

        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        Map<Long, TouristPortalRecord> pending = new HashMap<>();
        Set<UUID> movedPortalIds = new HashSet<>();
        for (TouristPortalRecord record : registry.listForPlot(plotId)) {
            Vector3i local = prefabLocalOffset(anchor, yaw, record.getBlockPosition());
            pending.put(localKey(local.x, local.y, local.z), copyForRelocation(record));
            movedPortalIds.add(record.getPortalId());
            registry.remove(record.getPortalId());
        }
        if (!pending.isEmpty()) {
            PENDING_BY_PLOT.put(plotId, pending);
            TouristPortalPersistence.save(world, plugin, registry);
            pauseReturningTourists(store, town, movedPortalIds);
        }
    }

    /**
     * Removes registry rows that point at multi-block filler voxels (same block type as the portal base).
     * Older builds registered both cells; only the base cell is a real portal.
     *
     * @return true when any filler entry was removed
     */
    public static boolean purgeFillerPortalRecords(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull TownManager tm
    ) {
        Map<UUID, Set<UUID>> removedByTown = new HashMap<>();
        for (TouristPortalRecord record : new ArrayList<>(registry.allRecords())) {
            if (!world.getName().equals(record.getWorldName())) {
                continue;
            }
            Vector3i pos = record.getBlockPosition();
            // Only touch loaded chunks so we do not mis-classify unloaded portals.
            if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
                continue;
            }
            // Portal type present but not the base cell => filler voxel from the 2-block portal.
            if (!TouristPortalBlockUtil.isTouristPortalBlock(world.getBlockType(pos.x, pos.y, pos.z))) {
                continue;
            }
            if (TouristPortalBlockUtil.isPortalBaseBlock(world, pos.x, pos.y, pos.z)) {
                continue;
            }
            removedByTown.computeIfAbsent(record.getTownId(), ignored -> new HashSet<>()).add(record.getPortalId());
            registry.remove(record.getPortalId());
        }
        if (removedByTown.isEmpty()) {
            return false;
        }
        for (Map.Entry<UUID, Set<UUID>> entry : removedByTown.entrySet()) {
            TownRecord town = tm.getTown(entry.getKey());
            if (town == null) {
                continue;
            }
            rehomeOrDespawnTourists(world, plugin, store, registry, town, tm, entry.getValue());
        }
        TouristPortalPersistence.save(world, plugin, registry);
        return true;
    }

    /**
     * Removes portal registry rows when a plot is destroyed (no rebind). Active portal tourists are
     * reassigned to another town portal when possible, otherwise despawned so they do not walk to the
     * cleared footprint.
     */
    public static void clearPlotPortals(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull UUID plotId,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm
    ) {
        PENDING_BY_PLOT.remove(plotId);
        List<TouristPortalRecord> records = new ArrayList<>(registry.listForPlot(plotId));
        if (records.isEmpty()) {
            return;
        }
        Set<UUID> removedPortalIds = new HashSet<>();
        for (TouristPortalRecord record : records) {
            removedPortalIds.add(record.getPortalId());
            registry.remove(record.getPortalId());
        }
        TouristPortalPersistence.save(world, plugin, registry);
        rehomeOrDespawnTourists(world, plugin, store, registry, town, tm, removedPortalIds);
    }

    @Nullable
    public static TouristPortalRecord takeDetached(
        @Nonnull UUID plotId,
        @Nonnull Vector3i worldPos,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Map<Long, TouristPortalRecord> pending = PENDING_BY_PLOT.get(plotId);
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        Vector3i local = prefabLocalOffset(anchor, yaw, worldPos);
        TouristPortalRecord detached = pending.remove(localKey(local.x, local.y, local.z));
        if (pending.isEmpty()) {
            PENDING_BY_PLOT.remove(plotId);
        }
        return detached;
    }

    public static void finishPlotMove(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Set<UUID> reboundPortalIds
    ) {
        PENDING_BY_PLOT.remove(plotId);
        if (!reboundPortalIds.isEmpty()) {
            resumeLeaveAfterPortalMove(world, plugin, store, town, reboundPortalIds);
        }
    }

    /** Clears in-progress return travel so tourists do not keep walking to a removed portal pose. */
    private static void pauseReturningTourists(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Set<UUID> portalIds
    ) {
        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec.isCitizen() || rec.isInvitedToStay()) {
                continue;
            }
            UUID portalId = rec.getPortalId();
            if (portalId == null || !portalIds.contains(portalId)) {
                continue;
            }
            UUID entityUuid = rec.getEntityUuid();
            if (entityUuid == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
            if (autonomy == null || !TouristAutonomySystem.isReturningHome(autonomy)) {
                continue;
            }
            autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
            autonomy.clearVisitPlot();
            autonomy.clearTravelTarget();
            store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        }
    }

    /**
     * After a portal is rebound, send leave-due tourists (including those paused for the move) to the
     * portal's new block position.
     */
    private static void resumeLeaveAfterPortalMove(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Set<UUID> portalIds
    ) {
        long now = resolveNowMs(store);
        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec.isCitizen() || rec.isInvitedToStay()) {
                continue;
            }
            UUID portalId = rec.getPortalId();
            if (portalId == null || !portalIds.contains(portalId)) {
                continue;
            }
            UUID entityUuid = rec.getEntityUuid();
            if (entityUuid == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
            boolean returning = autonomy != null && TouristAutonomySystem.isReturningHome(autonomy);
            if (!returning && !TouristPortalTickService.shouldTouristLeaveNow(rec, store)) {
                continue;
            }
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }
            if (autonomy == null) {
                autonomy = TouristAutonomyState.fresh(now);
            }
            autonomy.setHomePortalId(portalId);
            if (TouristAutonomySystem.beginReturnToPortalOnStore(
                ref, store, plugin, npc, autonomy, now, town, world
            )) {
                store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                store.putComponent(ref, NPCEntity.getComponentType(), npc);
                TouristAutonomySystem.applyAutonomyRoleStateOnStore(ref, npc, store);
            }
        }
    }

    private static void rehomeOrDespawnTourists(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristPortalRegistry registry,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Set<UUID> removedPortalIds
    ) {
        List<TouristPortalRecord> remaining = registry.recordsForTown(town.getTownId());
        TouristPortalRecord fallback = remaining.isEmpty() ? null : remaining.get(0);
        boolean townDirty = false;
        long now = resolveNowMs(store);

        for (TouristRecord rec : new ArrayList<>(town.getTouristRecords())) {
            UUID portalId = rec.getPortalId();
            if (portalId == null || !removedPortalIds.contains(portalId)) {
                continue;
            }
            if (rec.isCitizen() || rec.isInvitedToStay()) {
                continue;
            }
            UUID entityUuid = rec.getEntityUuid();
            if (fallback != null) {
                rec.setPortalId(fallback.getPortalId());
                townDirty = true;
                if (entityUuid == null) {
                    continue;
                }
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
                if (autonomy == null) {
                    continue;
                }
                autonomy.setHomePortalId(fallback.getPortalId());
                if (TouristAutonomySystem.isReturningHome(autonomy)) {
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc != null
                        && TouristAutonomySystem.beginReturnToPortalOnStore(
                            ref, store, plugin, npc, autonomy, now, town, world
                        )) {
                        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                        store.putComponent(ref, NPCEntity.getComponentType(), npc);
                        TouristAutonomySystem.applyAutonomyRoleStateOnStore(ref, npc, store);
                    } else {
                        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                    }
                } else {
                    store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                }
                continue;
            }
            if (entityUuid != null) {
                TouristPortalTickService.despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
            } else {
                rec.setPortalId(null);
                townDirty = true;
            }
        }
        if (townDirty) {
            tm.updateTown(town);
        }
    }

    @Nonnull
    private static TouristPortalRecord copyForRelocation(@Nonnull TouristPortalRecord src) {
        TouristPortalRecord copy = new TouristPortalRecord();
        copy.setPortalId(src.getPortalId());
        copy.setWorldName(src.getWorldName());
        copy.setBlockPosition(src.getBlockPosition());
        copy.setTownId(src.getTownId());
        copy.setPlotId(src.getPlotId());
        return copy;
    }

    @Nonnull
    private static Vector3i prefabLocalOffset(@Nonnull Vector3i anchor, @Nonnull Rotation yaw, @Nonnull Vector3i worldPos) {
        return PrefabLocalOffset.inverseRotateWorldDelta(
            yaw,
            worldPos.x - anchor.x,
            worldPos.y - anchor.y,
            worldPos.z - anchor.z
        );
    }

    private static long localKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeResource tr = store.getResource(TimeResource.getResourceType());
        return tr != null ? tr.getNow().toEpochMilli() : System.currentTimeMillis();
    }
}
