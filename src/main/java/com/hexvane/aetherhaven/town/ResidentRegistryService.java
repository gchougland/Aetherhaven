package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Maintains {@link TownRecord#getResidentNpcRecords()} for Gaia statue revival and related features. */
public final class ResidentRegistryService {
    private ResidentRegistryService() {}

    /**
     * Story villagers: one row per {@code npcRoleId}. Guards and townsfolk: one row per entity uuid.
     */
    public static void upsert(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String npcRoleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId,
        @Nonnull UUID entityUuid
    ) {
        String rid = npcRoleId.trim();
        if (rid.isEmpty()) {
            return;
        }
        List<ResidentNpcRecord> list = town.getResidentNpcRecords();
        ResidentNpcRecord incoming = new ResidentNpcRecord(rid, kind, jobPlotId, entityUuid);
        boolean perEntity =
            TownVillagerBinding.KIND_GUARD.equals(kind) || TownVillagerBinding.KIND_TOWNSFOLK.equals(kind);
        boolean[] copied = {false};
        list.removeIf(r -> {
            if (perEntity) {
                if (!entityUuid.equals(r.getLastEntityUuid())) {
                    return false;
                }
            } else if (!rid.equalsIgnoreCase(r.getNpcRoleId())) {
                return false;
            }
            if (!copied[0] && entityUuid.equals(r.getLastEntityUuid()) && r.hasLastKnownNeeds()) {
                incoming.copyLastKnownNeedsFrom(r);
                copied[0] = true;
            }
            return true;
        });
        incoming.setPendingDawnRevival(false);
        list.add(incoming);
        tm.updateTown(town);
    }

    public static void upsertFromBinding(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (b == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return;
        }
        if (!town.getTownId().equals(b.getTownId())) {
            return;
        }
        if (TownVillagerBinding.isVisitorKind(b.getKind())) {
            return;
        }
        var uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }
        upsert(town, tm, npc.getRoleName().trim(), b.getKind(), b.getJobPlotId(), uuidComp.getUuid());
    }

    /**
     * After house management assigns a resident, record their role from the live NPC entity.
     */
    public static void syncHouseAssignment(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID residentUuid
    ) {
        if (residentUuid == null) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(residentUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        upsertFromBinding(town, tm, ref, store);
    }

    /**
     * Replace {@code oldUuid} with {@code newUuid} in registry rows and town elder/innkeeper fields.
     */
    public static void replaceEntityUuidEverywhere(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID oldUuid,
        @Nonnull UUID newUuid
    ) {
        if (oldUuid.equals(newUuid)) {
            return;
        }
        boolean changed = false;
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (oldUuid.equals(r.getLastEntityUuid())) {
                r.setLastEntityUuid(newUuid);
                r.clearLastKnownNeeds();
                r.setPendingDawnRevival(false);
                changed = true;
            }
        }
        if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(oldUuid)) {
            town.setElderEntityUuid(newUuid);
            changed = true;
        }
        if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(oldUuid)) {
            town.setInnkeeperEntityUuid(newUuid);
            changed = true;
        }
        for (PlotInstance p : town.getPlotInstances()) {
            if (!p.hasHomeResident(oldUuid)) {
                continue;
            }
            for (int slot = 0; slot < 8; slot++) {
                if (oldUuid.equals(p.getHomeResidentAt(slot))) {
                    p.setHomeResidentAt(slot, newUuid);
                    changed = true;
                }
            }
        }
        String oldS = oldUuid.toString();
        String newS = newUuid.toString();
        for (int i = 0; i < town.getInnPoolNpcIds().size(); i++) {
            String s = town.getInnPoolNpcIds().get(i);
            if (s != null && oldS.equalsIgnoreCase(s.trim())) {
                town.getInnPoolNpcIds().set(i, newS);
                changed = true;
            }
        }
        for (int i = 0; i < town.getInnLockedEntityUuids().size(); i++) {
            String s = town.getInnLockedEntityUuids().get(i);
            if (s != null && oldS.equalsIgnoreCase(s.trim())) {
                town.getInnLockedEntityUuids().set(i, newS);
                changed = true;
            }
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(oldUuid)) {
                rec.setEntityUuid(newUuid);
                changed = true;
            }
        }
        if (town.replaceEntityUuidInQuestTargets(oldUuid, newUuid)) {
            changed = true;
        }
        town.markEntityUuidSuperseded(oldUuid);
        if (changed) {
            tm.updateTown(town);
        }
    }

    /**
     * Rows suitable for Gaia revival UI: story villagers only — never guards, townsfolk, or inn visitors.
     */
    @Nonnull
    public static List<ResidentNpcRecord> revivalCandidates(@Nonnull TownRecord town) {
        List<ResidentNpcRecord> out = new ArrayList<>();
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!isGaiaRevivalEligible(r.getKind(), r.getNpcRoleId())) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    /**
     * Same as {@link #revivalCandidates(TownRecord)}, plus any resident found in loaded chunks (so the list is not
     * limited to {@link TownRecord#getResidentNpcRecords()}), merged by {@code npcRoleId}. Live entities win over
     * persisted rows so UUIDs stay current when chunks are loaded.
     */
    @Nonnull
    public static List<ResidentNpcRecord> revivalCandidatesMerged(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        LinkedHashMap<String, ResidentNpcRecord> byRole = new LinkedHashMap<>();
        for (ResidentNpcRecord r : revivalCandidates(town)) {
            byRole.put(r.getNpcRoleId().toLowerCase(Locale.ROOT), r);
        }
        UUID tid = town.getTownId();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    String roleId = npc.getRoleName().trim();
                    if (!isGaiaRevivalEligible(b.getKind(), roleId)) {
                        continue;
                    }
                    byRole.put(
                        roleId.toLowerCase(Locale.ROOT),
                        new ResidentNpcRecord(roleId, b.getKind(), b.getJobPlotId(), uc.getUuid())
                    );
                }
            }
        );
        mergeIfAbsentRole(
            byRole,
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_ELDER,
            null,
            town.getElderEntityUuid()
        );
        mergeIfAbsentRole(
            byRole,
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_INNKEEPER,
            null,
            town.getInnkeeperEntityUuid()
        );
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            for (UUID home : plot.getHomeResidentEntityUuids()) {
                ResidentNpcRecord fromHome = recordFromHomeResident(store, town, home);
                if (fromHome == null) {
                    continue;
                }
                byRole.putIfAbsent(fromHome.getNpcRoleId().toLowerCase(Locale.ROOT), fromHome);
            }
        }
        List<ResidentNpcRecord> out = new ArrayList<>(byRole.values());
        out.sort(
            Comparator.comparingInt(ResidentRegistryService::revivalRowSortOrder)
                .thenComparing(r -> r.getNpcRoleId(), String.CASE_INSENSITIVE_ORDER)
        );
        return out;
    }

    /**
     * Rows eligible for dawn auto-revival: {@link ResidentNpcRecord#isPendingDawnRevival()} only (death-confirmed).
     * Clears stale pending flags when a live NPC for that role is already in loaded chunks.
     */
    @Nonnull
    public static List<ResidentNpcRecord> dawnRevivalCandidates(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        reconcileStalePendingDawnRevival(town, tm, store);
        List<ResidentNpcRecord> out = new ArrayList<>();
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!r.isPendingDawnRevival()) {
                continue;
            }
            if (!isGaiaRevivalEligible(r.getKind(), r.getNpcRoleId())) {
                continue;
            }
            out.add(r);
        }
        out.sort(
            Comparator.comparingInt(ResidentRegistryService::revivalRowSortOrder)
                .thenComparing(r -> r.getNpcRoleId(), String.CASE_INSENSITIVE_ORDER)
        );
        return out;
    }

    /**
     * Called from {@link com.hexvane.aetherhaven.guild.VillagerDeathHandlerSystem} when a revival-eligible villager
     * dies. Dawn revival must not run without this flag.
     */
    public static void markPendingDawnRevivalOnDeath(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId
    ) {
        String rid = roleId.trim();
        if (rid.isEmpty() || !isGaiaRevivalEligible(kind, rid)) {
            return;
        }
        ResidentNpcRecord record = findRecordForDeathMark(town, entityUuid, rid);
        if (record == null) {
            record = new ResidentNpcRecord(rid, kind, jobPlotId, entityUuid);
            town.getResidentNpcRecords().add(record);
        } else {
            record.setNpcRoleId(rid);
            record.setKind(kind);
            if (jobPlotId != null) {
                record.setJobPlotId(jobPlotId);
            }
            if (!entityUuid.equals(record.getLastEntityUuid())) {
                record.setLastEntityUuid(entityUuid);
                record.clearLastKnownNeeds();
            }
        }
        record.setPendingDawnRevival(true);
        tm.updateTown(town);
    }

    /**
     * True when any loaded town NPC matches this revival row's role (and entity uuid for guards). Used as a final guard
     * before spawning a replacement.
     */
    public static boolean hasLiveTownRevivalNpcForRole(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ResidentNpcRecord record
    ) {
        String roleId = record.getNpcRoleId().trim();
        if (roleId.isEmpty()) {
            return false;
        }
        UUID tid = town.getTownId();
        boolean perEntity = TownVillagerBinding.KIND_GUARD.equals(record.getKind());
        UUID recordUuid = record.getLastEntityUuid();
        boolean[] found = { false };
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found[0]) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId())) {
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
                    if (!isGaiaRevivalEligible(b.getKind(), npc.getRoleName().trim())) {
                        continue;
                    }
                    if (perEntity && !recordUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref != null && ref.isValid()) {
                        found[0] = true;
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    /** Clears stale registry rows when a live NPC for the same story role is already in loaded chunks. */
    public static void reconcileStalePendingDawnRevival(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!isGaiaRevivalEligible(r.getKind(), r.getNpcRoleId())) {
                continue;
            }
            if (!r.isPendingDawnRevival()) {
                UUID saved = r.getLastEntityUuid();
                if (EntityPresenceUtil.isLoadedLive(EntityPresenceUtil.resolve(store, saved))) {
                    continue;
                }
                if (findLiveEntityUuidForStoryRole(store, town, r) == null) {
                    continue;
                }
            }
            TownResidentReconcileService.syncRegistryToLiveEntityIfNeeded(town, tm, store, r);
        }
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
        return TownResidentReconcileService.findLiveEntityUuidForStoryRole(store, town, record);
    }

    @Nullable
    private static ResidentNpcRecord findRecordForDeathMark(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String roleId
    ) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (entityUuid.equals(r.getLastEntityUuid())) {
                return r;
            }
        }
        String key = roleId.toLowerCase(Locale.ROOT);
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (key.equals(r.getNpcRoleId().toLowerCase(Locale.ROOT))) {
                return r;
            }
        }
        return null;
    }

    /**
     * True when {@link TownRecord} still tracks this uuid but the entity is absent from the loaded index — usually an
     * unloaded chunk, not a confirmed death (see {@link com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService}).
     */
    public static boolean isRevivalRecordLikelyUnloadedNotDead(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull ResidentNpcRecord record
    ) {
        UUID uuid = record.getLastEntityUuid();
        if (uuid.getLeastSignificantBits() == 0L && uuid.getMostSignificantBits() == 0L) {
            return false;
        }
        if (!townStillReferencesEntityUuid(town, uuid)) {
            return false;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        return ref == null || !ref.isValid();
    }

    private static boolean townStillReferencesEntityUuid(@Nonnull TownRecord town, @Nonnull UUID uuid) {
        if (uuid.equals(town.getElderEntityUuid()) || uuid.equals(town.getInnkeeperEntityUuid())) {
            return true;
        }
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.hasHomeResident(uuid)) {
                return true;
            }
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (uuid.equals(rec.getEntityUuid())) {
                return true;
            }
        }
        return false;
    }

    private static void mergeIfAbsentRole(
        LinkedHashMap<String, ResidentNpcRecord> byRole,
        @Nonnull String roleId,
        @Nonnull String kind,
        @Nullable UUID jobPlotId,
        @Nullable UUID entityUuid
    ) {
        if (entityUuid == null) {
            return;
        }
        String key = roleId.toLowerCase(Locale.ROOT);
        if (byRole.containsKey(key)) {
            return;
        }
        byRole.put(key, new ResidentNpcRecord(roleId, kind, jobPlotId, entityUuid));
    }

    @Nullable
    private static ResidentNpcRecord recordFromHomeResident(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID homeEntityUuid
    ) {
        Ref<EntityStore> pref = store.getExternalData().getRefFromUUID(homeEntityUuid);
        if (pref == null || !pref.isValid()) {
            return null;
        }
        TownVillagerBinding b = store.getComponent(pref, TownVillagerBinding.getComponentType());
        NPCEntity npc = store.getComponent(pref, NPCEntity.getComponentType());
        UUIDComponent uc = store.getComponent(pref, UUIDComponent.getComponentType());
        if (b == null || npc == null || uc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return null;
        }
        if (!town.getTownId().equals(b.getTownId())) {
            return null;
        }
        String roleId = npc.getRoleName().trim();
        if (!isGaiaRevivalEligible(b.getKind(), roleId)) {
            return null;
        }
        return new ResidentNpcRecord(roleId, b.getKind(), b.getJobPlotId(), uc.getUuid());
    }

    /**
     * Gaia revival UI and dawn auto-revival: story villagers only — never guards or townsfolk (including citizen guards
     * and housed townsfolk).
     */
    /** Story villagers only; guards and townsfolk are never revivable at the Gaia statue. */
    public static boolean isGaiaRevivalEligible(@Nullable String kind, @Nonnull String roleId) {
        if (roleId.isBlank()) {
            return false;
        }
        if (kind != null && TownVillagerBinding.isVisitorKind(kind)) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && TownResidentEligibility.isTownsfolkPoolKind(kind != null ? kind : "", roleId, plugin)) {
            return false;
        }
        return true;
    }

    /**
     * Canonical entity uuid for a Gaia-eligible story role after respawn/revive: elder/innkeeper town fields first,
     * then the registry row's {@link ResidentNpcRecord#getLastEntityUuid()}. Null when the town has no tracked
     * uuid for that role yet.
     */
    @Nullable
    public static UUID findCanonicalEntityUuidForGaiaRole(@Nonnull TownRecord town, @Nonnull String roleId) {
        String rid = roleId.trim();
        if (rid.isEmpty()) {
            return null;
        }
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equalsIgnoreCase(rid)) {
            UUID elder = town.getElderEntityUuid();
            if (elder != null) {
                return elder;
            }
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equalsIgnoreCase(rid)) {
            UUID innkeeper = town.getInnkeeperEntityUuid();
            if (innkeeper != null) {
                return innkeeper;
            }
        }
        String key = rid.toLowerCase(Locale.ROOT);
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!key.equals(r.getNpcRoleId().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!isGaiaRevivalEligible(r.getKind(), r.getNpcRoleId())) {
                continue;
            }
            UUID uuid = r.getLastEntityUuid();
            if (uuid.getLeastSignificantBits() == 0L && uuid.getMostSignificantBits() == 0L) {
                return null;
            }
            return uuid;
        }
        return null;
    }

    private static int revivalRowSortOrder(@Nonnull ResidentNpcRecord r) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            VillagerDefinition d = plugin.getVillagerDefinitionCatalog().byNpcRoleId(r.getNpcRoleId());
            if (d != null) {
                return d.getUiSortOrder();
            }
        }
        String kind = r.getKind();
        if (TownVillagerBinding.KIND_ELDER.equals(kind)) {
            return 0;
        }
        if (TownVillagerBinding.KIND_INNKEEPER.equals(kind)) {
            return 1;
        }
        if (TownVillagerBinding.KIND_MERCHANT.equals(kind)
            || TownVillagerBinding.KIND_FARMER.equals(kind)
            || TownVillagerBinding.KIND_BLACKSMITH.equals(kind)
            || TownVillagerBinding.KIND_PRIESTESS.equals(kind)
            || TownVillagerBinding.KIND_MINER.equals(kind)
            || TownVillagerBinding.KIND_LOGGER.equals(kind)
            || TownVillagerBinding.KIND_RANCHER.equals(kind)) {
            return 2;
        }
        return kindOrderFallbackByRoleId(r.getNpcRoleId());
    }

    private static int kindOrderFallbackByRoleId(@Nonnull String roleId) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)) {
            return 0;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)) {
            return 1;
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)
            || AetherhavenConstants.NPC_FARMER.equals(roleId)
            || AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)
            || AetherhavenConstants.NPC_PRIESTESS.equals(roleId)
            || AetherhavenConstants.NPC_MINER.equals(roleId)
            || AetherhavenConstants.NPC_LOGGER.equals(roleId)
            || AetherhavenConstants.NPC_RANCHER.equals(roleId)
            || AetherhavenConstants.NPC_CRYSTAL_KEEPER.equals(roleId)
            || AetherhavenConstants.NPC_PYROTECHNIC.equals(roleId)
            || AetherhavenConstants.NPC_CLOWN.equals(roleId)
            || AetherhavenConstants.NPC_FLORIST.equals(roleId)) {
            return 2;
        }
        return 3;
    }

    /** Remove registry row for a role (e.g. if unused). */
    public static void removeByRole(@Nonnull TownRecord town, @Nonnull TownManager tm, @Nonnull String npcRoleId) {
        String rid = npcRoleId.trim();
        if (rid.isEmpty()) {
            return;
        }
        Iterator<ResidentNpcRecord> it = town.getResidentNpcRecords().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            if (rid.equalsIgnoreCase(it.next().getNpcRoleId())) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            tm.updateTown(town);
        }
    }

    /**
     * Persists last hunger/energy/fun for a roster row matching {@code entityUuid} when values move enough to matter.
     * Called while the NPC is loaded and ticking.
     */
    public static void writeLastKnownNeedsIfChanged(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid,
        float hunger,
        float energy,
        float fun
    ) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (!entityUuid.equals(r.getLastEntityUuid())) {
                continue;
            }
            if (TownVillagerBinding.KIND_GUARD.equals(r.getKind()) || TownVillagerBinding.KIND_TOWNSFOLK.equals(r.getKind())) {
                return;
            }
            if (r.setLastKnownNeedsIfChanged(hunger, energy, fun, 0.5f)) {
                tm.updateTown(town);
            }
            return;
        }
    }
}
