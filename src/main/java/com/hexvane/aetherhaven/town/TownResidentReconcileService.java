package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditMissingScanService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps story citizen registry rows, loaded NPCs, and reputation keys aligned — one live entity per story role per
 * town. Removes duplicate loaded copies and syncs stale {@link ResidentNpcRecord#getLastEntityUuid()} values.
 */
public final class TownResidentReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TownResidentReconcileService() {}

    public static final class ReconcileReport {
        private int syncedRoles;
        private int removedDuplicateEntities;
        private int removedStaleRegistryRows;

        public int getSyncedRoles() {
            return syncedRoles;
        }

        public int getRemovedDuplicateEntities() {
            return removedDuplicateEntities;
        }

        public int getRemovedStaleRegistryRows() {
            return removedStaleRegistryRows;
        }

        void addSyncedRole() {
            syncedRoles++;
        }

        void addRemovedDuplicate(int count) {
            removedDuplicateEntities += count;
        }

        void addRemovedStaleRegistryRow() {
            removedStaleRegistryRows++;
        }

        public boolean anyChanges() {
            return syncedRoles > 0 || removedDuplicateEntities > 0 || removedStaleRegistryRows > 0;
        }
    }

    private record LoadedStoryResident(
        @Nonnull UUID entityUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String roleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId
    ) {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileAllTownsOnWorldThread(world, plugin));
        plugin.scheduleOnWorld(world, () -> reconcileAllTownsOnWorldThread(world, plugin), 2_000L);
    }

    public static void onTownMemberPlayerReady(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(playerUuid);
        if (town == null) {
            return;
        }
        world.execute(() -> reconcileTownOnWorldThread(world, plugin, town));
    }

    private static void reconcileAllTownsOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (!TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            reconcileTownOnWorldThread(world, plugin, town);
        }
    }

    @Nonnull
    public static ReconcileReport reconcileTownOnWorldThread(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        ReconcileReport report = new ReconcileReport();
        if (!TownTerritoryChunkUtil.isCharterChunkLoaded(world, town)) {
            return report;
        }
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return report;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        reconcileTownInternal(world, plugin, town, tm, store, report);
        return report;
    }

    static void reconcileTownInternal(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull ReconcileReport report
    ) {
        Map<String, List<LoadedStoryResident>> loadedByRole = collectLoadedStoryResidents(store, town);
        LinkedHashSet<String> roleKeys = new LinkedHashSet<>(loadedByRole.keySet());
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (isStoryRegistryRow(r)) {
                roleKeys.add(roleKey(r.getNpcRoleId()));
            }
        }
        if (town.getElderEntityUuid() != null) {
            roleKeys.add(roleKey(AetherhavenConstants.ELDER_NPC_ROLE_ID));
        }
        if (town.getInnkeeperEntityUuid() != null) {
            roleKeys.add(roleKey(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID));
        }

        for (String roleKey : roleKeys) {
            reconcileStoryRole(world, town, tm, store, roleKey, loadedByRole.getOrDefault(roleKey, List.of()), report);
        }

        removeDuplicateRegistryRows(town, tm, report);
        VillagerAuditMissingScanService.scanTown(world, store, plugin, town);

        if (report.anyChanges()) {
            LOGGER.atInfo().log(
                "Resident reconcile for town %s: syncedRoles=%d removedDuplicates=%d removedStaleRegistry=%d",
                town.getTownId(),
                report.getSyncedRoles(),
                report.getRemovedDuplicateEntities(),
                report.getRemovedStaleRegistryRows()
            );
        }
    }

    private static void reconcileStoryRole(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleKey,
        @Nonnull List<LoadedStoryResident> loaded,
        @Nonnull ReconcileReport report
    ) {
        String roleId = resolveRoleIdForKey(roleKey, loaded, town);
        if (roleId.isEmpty()) {
            return;
        }

        ResidentNpcRecord registryRow = findRegistryRowForRole(town, roleKey);
        UUID registryUuid = registryRow != null ? registryRow.getLastEntityUuid() : null;
        UUID preferredUuid = preferredCanonicalUuid(town, roleId, registryUuid, loaded);

        UUID canonical = pickCanonicalUuid(preferredUuid, registryUuid, loaded);
        if (canonical == null) {
            return;
        }

        LoadedStoryResident canonicalLoaded = findLoaded(loaded, canonical);
        String kind = canonicalLoaded != null ? canonicalLoaded.kind() : registryRow != null ? registryRow.getKind() : "";
        UUID jobPlotId = canonicalLoaded != null ? canonicalLoaded.jobPlotId() : registryRow != null ? registryRow.getJobPlotId() : null;

        int dupesRemoved = removeLoadedDuplicatesExcept(store, loaded, canonical);
        if (dupesRemoved > 0) {
            report.addRemovedDuplicate(dupesRemoved);
        }

        if (registryUuid != null && !canonical.equals(registryUuid)) {
            syncEntityUuid(town, tm, registryUuid, canonical, roleId, kind, jobPlotId);
            report.addSyncedRole();
        } else if (registryRow != null && registryRow.isPendingDawnRevival()) {
            registryRow.setPendingDawnRevival(false);
            tm.updateTown(town);
            report.addSyncedRole();
        } else if (registryRow == null && canonicalLoaded != null) {
            ResidentRegistryService.upsert(town, tm, roleId, kind, jobPlotId, canonical);
            report.addSyncedRole();
        }

        syncSpecialTownFields(town, tm, roleId, canonical);
    }

    @Nullable
    private static UUID pickCanonicalUuid(
        @Nullable UUID preferredUuid,
        @Nullable UUID registryUuid,
        @Nonnull List<LoadedStoryResident> loaded
    ) {
        if (loaded.isEmpty()) {
            return null;
        }
        if (preferredUuid != null && findLoaded(loaded, preferredUuid) != null) {
            return preferredUuid;
        }
        if (registryUuid != null && findLoaded(loaded, registryUuid) != null) {
            return registryUuid;
        }
        return loaded.get(0).entityUuid();
    }

    @Nullable
    private static UUID preferredCanonicalUuid(
        @Nonnull TownRecord town,
        @Nonnull String roleId,
        @Nullable UUID registryUuid,
        @Nonnull List<LoadedStoryResident> loaded
    ) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equalsIgnoreCase(roleId)) {
            UUID elder = town.getElderEntityUuid();
            if (elder != null) {
                return elder;
            }
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equalsIgnoreCase(roleId)) {
            UUID innkeeper = town.getInnkeeperEntityUuid();
            if (innkeeper != null) {
                return innkeeper;
            }
        }
        if (registryUuid != null && findLoaded(loaded, registryUuid) != null) {
            return registryUuid;
        }
        return null;
    }

    @Nullable
    private static LoadedStoryResident findLoaded(@Nonnull List<LoadedStoryResident> loaded, @Nonnull UUID uuid) {
        for (LoadedStoryResident r : loaded) {
            if (uuid.equals(r.entityUuid())) {
                return r;
            }
        }
        return null;
    }

    private static int removeLoadedDuplicatesExcept(
        @Nonnull Store<EntityStore> store,
        @Nonnull List<LoadedStoryResident> loaded,
        @Nonnull UUID keepUuid
    ) {
        int removed = 0;
        for (LoadedStoryResident r : loaded) {
            if (keepUuid.equals(r.entityUuid())) {
                continue;
            }
            if (r.ref().isValid()) {
                VillagerAuditContext.runWithSource("resident_duplicate_reconcile", () ->
                    store.removeEntity(r.ref(), RemoveReason.REMOVE)
                );
                removed++;
            }
        }
        return removed;
    }

    static void syncEntityUuid(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID oldUuid,
        @Nonnull UUID newUuid,
        @Nonnull String roleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId
    ) {
        if (oldUuid.equals(newUuid)) {
            return;
        }
        VillagerReputationService.migrateVillagerEntityUuid(town, tm, oldUuid, newUuid);
        ResidentRegistryService.replaceEntityUuidEverywhere(town, tm, oldUuid, newUuid);
        ResidentRegistryService.upsert(town, tm, roleId, kind, jobPlotId, newUuid);
        ResidentLastKnownPositionService.removePosition(town, tm, oldUuid);
    }

    /**
     * When a live story NPC exists for {@code record}'s role but the registry still points at a stale uuid, migrate town
     * data to the live entity. Returns true when town data was updated.
     */
    public static boolean syncRegistryToLiveEntityIfNeeded(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull ResidentNpcRecord record
    ) {
        UUID liveUuid = findLiveEntityUuidForStoryRole(store, town, record);
        if (liveUuid == null) {
            UUID saved = record.getLastEntityUuid();
            if (store != null && EntityPresenceUtil.isLoadedLive(EntityPresenceUtil.resolve(store, saved))) {
                if (record.isPendingDawnRevival()) {
                    record.setPendingDawnRevival(false);
                    tm.updateTown(town);
                    return true;
                }
            }
            return false;
        }
        UUID registryUuid = record.getLastEntityUuid();
        if (liveUuid.equals(registryUuid)) {
            if (record.isPendingDawnRevival()) {
                record.setPendingDawnRevival(false);
                tm.updateTown(town);
                return true;
            }
            return false;
        }
        syncEntityUuid(
            town,
            tm,
            registryUuid,
            liveUuid,
            record.getNpcRoleId().trim(),
            record.getKind(),
            record.getJobPlotId()
        );
        return true;
    }

    /**
     * Returns the entity uuid of a loaded live town NPC matching this story row's role, or null when none is loaded.
     */
    @Nullable
    public static UUID findLiveEntityUuidForStoryRole(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ResidentNpcRecord record
    ) {
        String roleId = record.getNpcRoleId().trim();
        if (roleId.isEmpty()) {
            return null;
        }
        UUID tid = town.getTownId();
        UUID[] found = { null };
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId())) {
                        continue;
                    }
                    if (TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (npc == null || uc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    if (!roleId.equalsIgnoreCase(npc.getRoleName().trim())) {
                        continue;
                    }
                    if (!ResidentRegistryService.isGaiaRevivalEligible(b.getKind(), npc.getRoleName().trim())) {
                        continue;
                    }
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref != null && ref.isValid()) {
                        found[0] = uc.getUuid();
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    @Nonnull
    private static Map<String, List<LoadedStoryResident>> collectLoadedStoryResidents(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        Map<String, List<LoadedStoryResident>> byRole = new LinkedHashMap<>();
        UUID tid = town.getTownId();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId())) {
                        continue;
                    }
                    if (TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (npc == null || uc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    String roleId = npc.getRoleName().trim();
                    if (!ResidentRegistryService.isGaiaRevivalEligible(b.getKind(), roleId)) {
                        continue;
                    }
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    String key = roleKey(roleId);
                    byRole.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new LoadedStoryResident(uc.getUuid(), ref, roleId, b.getKind(), b.getJobPlotId()));
                }
            }
        );
        return byRole;
    }

    private static void removeDuplicateRegistryRows(
        @Nonnull TownRecord town,
        @Nullable TownManager tm,
        @Nonnull ReconcileReport report
    ) {
        Map<String, ResidentNpcRecord> firstByRole = new LinkedHashMap<>();
        Iterator<ResidentNpcRecord> it = town.getResidentNpcRecords().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            ResidentNpcRecord r = it.next();
            if (!isStoryRegistryRow(r)) {
                continue;
            }
            String key = roleKey(r.getNpcRoleId());
            if (firstByRole.containsKey(key)) {
                it.remove();
                changed = true;
                report.addRemovedStaleRegistryRow();
            } else {
                firstByRole.put(key, r);
            }
        }
        if (changed && tm != null) {
            tm.updateTown(town);
        }
    }

    private static void syncSpecialTownFields(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String roleId,
        @Nonnull UUID canonical
    ) {
        boolean changed = false;
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equalsIgnoreCase(roleId) && !canonical.equals(town.getElderEntityUuid())) {
            town.setElderEntityUuid(canonical);
            changed = true;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equalsIgnoreCase(roleId)
            && !canonical.equals(town.getInnkeeperEntityUuid())) {
            town.setInnkeeperEntityUuid(canonical);
            changed = true;
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    @Nullable
    private static ResidentNpcRecord findRegistryRowForRole(@Nonnull TownRecord town, @Nonnull String roleKey) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (roleKey.equals(roleKey(r.getNpcRoleId()))) {
                return r;
            }
        }
        return null;
    }

    @Nonnull
    private static String resolveRoleIdForKey(
        @Nonnull String roleKey,
        @Nonnull List<LoadedStoryResident> loaded,
        @Nonnull TownRecord town
    ) {
        if (!loaded.isEmpty()) {
            return loaded.get(0).roleId();
        }
        ResidentNpcRecord row = findRegistryRowForRole(town, roleKey);
        if (row != null) {
            return row.getNpcRoleId().trim();
        }
        if (roleKey.equals(roleKey(AetherhavenConstants.ELDER_NPC_ROLE_ID))) {
            return AetherhavenConstants.ELDER_NPC_ROLE_ID;
        }
        if (roleKey.equals(roleKey(AetherhavenConstants.INNKEEPER_NPC_ROLE_ID))) {
            return AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
        }
        return "";
    }

    private static boolean isStoryRegistryRow(@Nonnull ResidentNpcRecord r) {
        if (TownVillagerBinding.isVisitorKind(r.getKind())) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && TownResidentEligibility.isTownsfolkPoolKind(r.getKind(), r.getNpcRoleId(), plugin)) {
            return false;
        }
        if (TownVillagerBinding.KIND_GUARD.equals(r.getKind())) {
            return false;
        }
        return ResidentRegistryService.isGaiaRevivalEligible(r.getKind(), r.getNpcRoleId());
    }

    @Nonnull
    private static String roleKey(@Nonnull String roleId) {
        return roleId.trim().toLowerCase(Locale.ROOT);
    }
}
