package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
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
     * One row per {@code npcRoleId} in town. Replaces any existing row with the same role id.
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
        list.removeIf(r -> rid.equalsIgnoreCase(r.getNpcRoleId()));
        list.add(new ResidentNpcRecord(rid, kind, jobPlotId, entityUuid));
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
            UUID h = p.getHomeResidentEntityUuid();
            if (h != null && h.equals(oldUuid)) {
                p.setHomeResidentEntityUuid(newUuid);
                changed = true;
            }
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    /**
     * Rows suitable for Gaia revival UI: non-empty role, not inn visitors (stored kinds never use visitor_ prefix here).
     */
    @Nonnull
    public static List<ResidentNpcRecord> revivalCandidates(@Nonnull TownRecord town) {
        List<ResidentNpcRecord> out = new ArrayList<>();
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (r.getNpcRoleId().isBlank()) {
                continue;
            }
            if (TownVillagerBinding.isVisitorKind(r.getKind())) {
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
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    String roleId = npc.getRoleName().trim();
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
            UUID home = plot.getHomeResidentEntityUuid();
            if (home == null) {
                continue;
            }
            ResidentNpcRecord fromHome = recordFromHomeResident(store, town, home);
            if (fromHome == null) {
                continue;
            }
            byRole.putIfAbsent(fromHome.getNpcRoleId().toLowerCase(Locale.ROOT), fromHome);
        }
        List<ResidentNpcRecord> out = new ArrayList<>(byRole.values());
        out.sort(
            Comparator.comparingInt(ResidentRegistryService::revivalRowSortOrder)
                .thenComparing(r -> r.getNpcRoleId(), String.CASE_INSENSITIVE_ORDER)
        );
        return out;
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
        if (!town.getTownId().equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
            return null;
        }
        return new ResidentNpcRecord(npc.getRoleName().trim(), b.getKind(), b.getJobPlotId(), uc.getUuid());
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
            || TownVillagerBinding.KIND_PRIESTESS.equals(kind)) {
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
            || AetherhavenConstants.NPC_PRIESTESS.equals(roleId)) {
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
}
