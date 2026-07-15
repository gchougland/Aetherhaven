package com.hexvane.aetherhaven.npctelemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyDebugTag;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolCheckoutRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.villager.AetherhavenNpcSpawnOrigin;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class NpcTelemetryCollector {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final double NEARBY_RADIUS_SQ = 64.0 * 64.0;

    private NpcTelemetryCollector() {}

    @Nonnull
    public static Map<String, Object> collect(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull PlayerRef dumper
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        UUIDComponent uuidComp = store.getComponent(targetRef, UUIDComponent.getComponentType());
        UUID entityUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (entityUuid == null) {
            report.put("error", "Target entity has no UUID");
            return report;
        }

        report.put("meta", buildMeta(plugin, world, entityUuid, dumper));
        report.put("entity", buildEntitySection(store, targetRef, entityUuid));
        report.put("townPersistence", buildTownPersistence(plugin, world, store, entityUuid));
        report.put("townsfolkPool", buildPoolSection(world, plugin, entityUuid));
        report.put("inferredSpawnOrigin", inferSpawnOrigin(plugin, world, store, entityUuid));
        report.put("diagnostics", buildDiagnostics(plugin, world, store, targetRef, entityUuid));
        return report;
    }

    @Nonnull
    private static Map<String, Object> buildMeta(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull UUID entityUuid,
        @Nonnull PlayerRef dumper
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("aetherhavenVersion", plugin.getManifest().getVersion().toString());
        meta.put("worldName", world.getName());
        meta.put("dumpEpochMs", System.currentTimeMillis());
        meta.put("dumpIsoUtc", Instant.now().toString());
        meta.put("targetEntityUuid", entityUuid.toString());
        meta.put("dumperUsername", dumper.getUsername());
        UUID dumperUuid = dumper.getUuid();
        if (dumperUuid != null) {
            meta.put("dumperUuid", dumperUuid.toString());
        }
        return meta;
    }

    @Nonnull
    private static Map<String, Object> buildEntitySection(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID entityUuid
    ) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("uuid", entityUuid.toString());
        entity.put("refValid", ref.isValid());

        NetworkId networkId = store.getComponent(ref, NetworkId.getComponentType());
        if (networkId != null) {
            entity.put("networkId", networkId.getId());
        }

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            Map<String, Object> npcMap = new LinkedHashMap<>();
            npcMap.put("roleName", npc.getRoleName());
            npcMap.put("roleDebugFlags", npc.getRoleDebugFlags().toString());
            entity.put("npc", npcMap);
        } else {
            entity.put("npc", null);
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d p = tc.getPosition();
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("x", p.x);
            pos.put("y", p.y);
            pos.put("z", p.z);
            entity.put("position", pos);
            entity.put("rotation", tc.getRotation().toString());
        }

        PersistentDisplayName displayName = store.getComponent(ref, PersistentDisplayName.getComponentType());
        if (displayName != null && displayName.getDisplayName() != null) {
            entity.put("persistentDisplayName", displayName.getDisplayName().getRawText());
        }
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        if (nameplate != null) {
            entity.put("nameplate", nameplate.getText());
        }

        entity.put("aetherhavenVillagerHandle", componentHandle(store, ref));
        entity.put("townVillagerBinding", componentBinding(store, ref));
        entity.put("townsfolkCharacterBinding", componentTownsfolk(store, ref));
        entity.put("villagerNeeds", componentNeeds(store, ref));
        entity.put("villagerAutonomyState", componentPresent(store, ref, VillagerAutonomyState.getComponentType()));
        entity.put("touristAutonomyState", componentTouristAutonomy(store, ref));
        entity.put("spawnOrigin", componentSpawnOrigin(store, ref));
        entity.put("villagerAutonomyDebugTag", store.getComponent(ref, VillagerAutonomyDebugTag.getComponentType()) != null);
        entity.put("guildHallDisplayAnchor", store.getComponent(ref, GuildHallDisplayAnchor.getComponentType()) != null);
        return entity;
    }

    @Nullable
    private static Map<String, Object> componentHandle(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenVillagerHandle h = store.getComponent(ref, AetherhavenVillagerHandle.getComponentType());
        if (h == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handle", h.getHandle());
        return m;
    }

    @Nullable
    private static Map<String, Object> componentBinding(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (b == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("townId", b.getTownId().toString());
        m.put("kind", b.getKind());
        UUID pref = b.getPreferredPlotId();
        if (pref != null) {
            m.put("preferredPlotId", pref.toString());
        }
        UUID job = b.getJobPlotId();
        if (job != null) {
            m.put("jobPlotId", job.toString());
        }
        return m;
    }

    @Nullable
    private static Map<String, Object> componentTownsfolk(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TownsfolkCharacterBinding b = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (b == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("characterId", b.getCharacterId());
        m.put("assignmentKind", b.getAssignmentKind());
        m.put("activePersonalityId", b.getActivePersonalityId());
        m.put("modelAssetId", b.getModelAssetId());
        m.put("personalityIds", b.getPersonalityIds());
        return m;
    }

    @Nullable
    private static Map<String, Object> componentNeeds(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        VillagerNeeds n = store.getComponent(ref, VillagerNeeds.getComponentType());
        if (n == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hunger", n.getHunger());
        m.put("energy", n.getEnergy());
        m.put("fun", n.getFun());
        m.put("lastSimulatedEpochMs", n.getLastSimulatedEpochMs());
        return m;
    }

    @Nullable
    private static Map<String, Object> componentSpawnOrigin(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenNpcSpawnOrigin o = store.getComponent(ref, AetherhavenNpcSpawnOrigin.getComponentType());
        if (o == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spawnSource", o.getSpawnSource());
        m.put("spawnDetail", o.getSpawnDetail());
        m.put("spawnWorldName", o.getSpawnWorldName());
        m.put("spawnX", o.getSpawnX());
        m.put("spawnY", o.getSpawnY());
        m.put("spawnZ", o.getSpawnZ());
        m.put("spawnEpochMs", o.getSpawnEpochMs());
        m.put("spawnGameEpochDay", o.getSpawnGameEpochDay());
        return m;
    }

    @Nullable
    private static Map<String, Object> componentTouristAutonomy(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TouristAutonomyState s = store.getComponent(ref, TouristAutonomyState.getComponentType());
        if (s == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phase", s.getPhase());
        UUID homePortal = s.getHomePortalId();
        if (homePortal != null) {
            m.put("homePortalId", homePortal.toString());
        }
        UUID visitPlot = s.getVisitPlotUuid();
        if (visitPlot != null) {
            m.put("visitPlotId", visitPlot.toString());
        }
        UUID targetPoi = s.getTargetPoiUuid();
        if (targetPoi != null) {
            m.put("targetPoiId", targetPoi.toString());
        }
        return m;
    }

    private static boolean componentPresent(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull com.hypixel.hytale.component.ComponentType<EntityStore, ?> type
    ) {
        return store.getComponent(ref, type) != null;
    }

    @Nonnull
    private static Map<String, Object> buildTownPersistence(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        List<Map<String, Object>> townHits = new ArrayList<>();

        for (TownRecord town : tm.allTowns()) {
            Map<String, Object> hit = townHitForEntity(town, entityUuid);
            if (!hit.isEmpty()) {
                townHits.add(hit);
            }
        }
        out.put("matchingTowns", townHits);

        TownVillagerBinding binding = findBinding(store, entityUuid);
        if (binding != null) {
            TownRecord boundTown = tm.getTown(binding.getTownId());
            if (boundTown != null) {
                out.put("boundTownSummary", summarizeTown(boundTown, entityUuid));
            }
        }
        return out;
    }

    @Nonnull
    private static Map<String, Object> townHitForEntity(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        Map<String, Object> hit = new LinkedHashMap<>();
        boolean any = false;

        if (entityUuid.equals(town.getElderEntityUuid())) {
            hit.put("isElder", true);
            any = true;
        }
        if (entityUuid.equals(town.getInnkeeperEntityUuid())) {
            hit.put("isInnkeeper", true);
            any = true;
        }

        TouristRecord tourist = TouristPortalTickService.findTouristRecord(town, entityUuid);
        if (tourist != null) {
            hit.put("touristRecord", toJsonMap(tourist));
            any = true;
        }

        List<String> innPool = town.getInnPoolNpcIds();
        for (int i = 0; i < innPool.size(); i++) {
            String s = innPool.get(i);
            if (s != null && entityUuid.toString().equals(s.trim())) {
                hit.put("innPoolSlot", i);
                any = true;
            }
        }
        if (town.getInnLockedEntityUuids().stream().anyMatch(s -> entityUuid.toString().equalsIgnoreCase(s))) {
            hit.put("innLocked", true);
            any = true;
        }

        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (entityUuid.equals(r.getLastEntityUuid())) {
                hit.put("residentNpcRecord", residentRecordMap(r));
                any = true;
            }
        }

        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.hasHomeResident(entityUuid)) {
                hit.put("homePlotId", plot.getPlotId().toString());
                any = true;
            }
        }

        if (!any) {
            return Map.of();
        }
        hit.put("townId", town.getTownId().toString());
        hit.put("townDisplayName", town.getDisplayName());
        hit.put("innPoolNpcIds", new ArrayList<>(town.getInnPoolNpcIds()));
        hit.put("touristRecordCount", town.getTouristRecords().size());
        hit.put("touristPlannedSpawnEpochMinutes", town.getTouristPlannedSpawnEpochMinutes());
        hit.put("touristExecutedSpawnEpochMinutes", town.getTouristExecutedSpawnEpochMinutes());
        return hit;
    }

    @Nonnull
    private static Map<String, Object> summarizeTown(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("townId", town.getTownId().toString());
        s.put("displayName", town.getDisplayName());
        s.put("innActive", town.isInnActive());
        s.put("innPoolNpcIds", new ArrayList<>(town.getInnPoolNpcIds()));
        s.put("touristRecords", gsonList(town.getTouristRecords()));
        return s;
    }

    @Nonnull
    private static Map<String, Object> residentRecordMap(@Nonnull ResidentNpcRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("npcRoleId", r.getNpcRoleId());
        m.put("kind", r.getKind());
        UUID job = r.getJobPlotId();
        if (job != null) {
            m.put("jobPlotId", job.toString());
        }
        m.put("lastEntityUuid", r.getLastEntityUuid().toString());
        m.put("pendingDawnRevival", r.isPendingDawnRevival());
        return m;
    }

    @Nonnull
    private static Map<String, Object> buildPoolSection(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID entityUuid
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        TownsfolkPoolCheckoutRecord checkout = pool.checkoutForEntity(entityUuid);
        if (checkout != null) {
            out.put("checkoutForEntity", toJsonMap(checkout));
        }
        String characterId = checkout != null ? checkout.getCharacterId() : null;
        if (characterId == null || characterId.isBlank()) {
            Ref<EntityStore> ref = world.getEntityStore().getStore().getExternalData().getRefFromUUID(entityUuid);
            if (ref != null && ref.isValid()) {
                TownsfolkCharacterBinding tb =
                    world.getEntityStore().getStore().getComponent(ref, TownsfolkCharacterBinding.getComponentType());
                if (tb != null) {
                    characterId = tb.getCharacterId();
                }
            }
        }
        if (characterId != null && !characterId.isBlank()) {
            List<Map<String, Object>> sameCharacter = new ArrayList<>();
            for (TownsfolkPoolCheckoutRecord r : pool.getCheckouts().values()) {
                if (characterId.equals(r.getCharacterId())) {
                    sameCharacter.add(toJsonMap(r));
                }
            }
            out.put("checkoutsForCharacterId", sameCharacter);
        }
        return out;
    }

    @Nonnull
    private static Map<String, Object> inferSpawnOrigin(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            AetherhavenNpcSpawnOrigin origin = store.getComponent(ref, AetherhavenNpcSpawnOrigin.getComponentType());
            if (origin != null) {
                return Map.of("source", "component", "note", "See entity.spawnOrigin");
            }
        }

        Map<String, Object> inferred = new LinkedHashMap<>();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            TouristRecord tr = TouristPortalTickService.findTouristRecord(town, entityUuid);
            if (tr != null) {
                inferred.put("guess", "TOURIST_PORTAL");
                inferred.put("portalId", tr.getPortalId() != null ? tr.getPortalId().toString() : null);
                inferred.put("characterId", tr.getCharacterId());
                return inferred;
            }
            if (town.getInnPoolNpcIds().contains(entityUuid.toString())) {
                inferred.put("guess", "INN_VISITOR");
                inferred.put("townId", town.getTownId().toString());
                return inferred;
            }
            if (entityUuid.equals(town.getElderEntityUuid())) {
                inferred.put("guess", "CHARTER_ELDER");
                return inferred;
            }
            if (entityUuid.equals(town.getInnkeeperEntityUuid())) {
                inferred.put("guess", "INNKEEPER_QUEST");
                return inferred;
            }
        }
        TownVillagerBinding binding = findBinding(store, entityUuid);
        if (binding != null) {
            inferred.put("guess", "UNKNOWN_AETHERHAVEN_NPC");
            inferred.put("kind", binding.getKind());
        } else {
            inferred.put("guess", "UNKNOWN");
        }
        return inferred;
    }

    @Nonnull
    private static Map<String, Object> buildDiagnostics(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull UUID entityUuid
    ) {
        List<String> issues = new ArrayList<>();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);

        List<UUID> matchingTownIds = new ArrayList<>();
        boolean inTourist = false;
        boolean inInnPool = false;
        for (TownRecord town : tm.allTowns()) {
            boolean hit = false;
            if (TouristPortalTickService.findTouristRecord(town, entityUuid) != null) {
                inTourist = true;
                hit = true;
            }
            if (town.getInnPoolNpcIds().contains(entityUuid.toString())) {
                inInnPool = true;
                hit = true;
            }
            if (entityUuid.equals(town.getElderEntityUuid())
                || entityUuid.equals(town.getInnkeeperEntityUuid())
                || town.getInnLockedEntityUuids().stream().anyMatch(s -> entityUuid.toString().equalsIgnoreCase(s))) {
                hit = true;
            }
            for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
                if (entityUuid.equals(r.getLastEntityUuid())) {
                    hit = true;
                }
            }
            if (hit) {
                matchingTownIds.add(town.getTownId());
            }
        }
        if (matchingTownIds.size() > 1) {
            issues.add("ENTITY_UUID_IN_MULTIPLE_TOWNS: " + matchingTownIds);
        }
        if (inTourist && inInnPool) {
            issues.add("ENTITY_IN_BOTH_TOURIST_RECORDS_AND_INN_POOL");
        }

        TownVillagerBinding binding = store.getComponent(targetRef, TownVillagerBinding.getComponentType());
        if (binding != null) {
            TownRecord bound = tm.getTown(binding.getTownId());
            if (bound != null) {
                Set<UUID> tracked = new HashSet<>();
                bound.collectTrackedNpcEntityUuids(tracked);
                if (!tracked.contains(entityUuid)) {
                    issues.add("ORPHAN_BINDING: TownVillagerBinding present but UUID not in town tracked NPC lists");
                }
            }
        }

        TownsfolkCharacterBinding tb = store.getComponent(targetRef, TownsfolkCharacterBinding.getComponentType());
        if (tb != null && !tb.getCharacterId().isBlank()) {
            int liveSameCharacter = countLiveWithCharacterId(store, tb.getCharacterId(), entityUuid);
            if (liveSameCharacter > 0) {
                issues.add("DUPLICATE_LIVE_CHARACTER_ID: " + tb.getCharacterId() + " (+ " + liveSameCharacter + " other live)");
            }
            for (TownRecord town : tm.allTowns()) {
                int touristDupes = 0;
                for (TouristRecord tr : town.getTouristRecords()) {
                    if (tb.getCharacterId().equals(tr.getCharacterId())) {
                        touristDupes++;
                    }
                }
                if (touristDupes > 1) {
                    issues.add("DUPLICATE_TOURIST_RECORDS_SAME_CHARACTER: town=" + town.getTownId() + " count=" + touristDupes);
                }
            }
        }

        for (TownRecord town : tm.allTowns()) {
            List<String> pool = town.getInnPoolNpcIds();
            if (pool.size() > 2) {
                issues.add("INN_POOL_OVERFLOW: town=" + town.getTownId() + " size=" + pool.size());
            }
            Set<String> seen = new HashSet<>();
            for (String id : pool) {
                if (id != null && !seen.add(id.trim())) {
                    issues.add("INN_POOL_DUPLICATE_UUID: town=" + town.getTownId() + " uuid=" + id);
                }
            }
        }

        for (ResidentNpcRecord r : findAllResidentRows(tm, entityUuid)) {
            UUID last = r.getLastEntityUuid();
            if (!entityUuid.equals(last)) {
                issues.add("RESIDENT_RECORD_POINTS_TO_DIFFERENT_UUID: role=" + r.getNpcRoleId() + " last=" + last);
            }
        }

        List<Map<String, Object>> nearby = scanNearby(store, targetRef, entityUuid);
        if (!nearby.isEmpty()) {
            issues.add("NEARBY_SIMILAR_NPCS: count=" + nearby.size());
        }

        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("issues", issues);
        diag.put("matchingTownCount", matchingTownIds.size());
        diag.put("matchingTownIds", matchingTownIds.stream().map(UUID::toString).toList());
        diag.put("nearbySimilarNpcs", nearby);
        return diag;
    }

    private static int countLiveWithCharacterId(
        @Nonnull Store<EntityStore> store,
        @Nonnull String characterId,
        @Nonnull UUID exclude
    ) {
        int[] count = {0};
        store.forEachChunk(
            Query.and(TownsfolkCharacterBinding.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownsfolkCharacterBinding b = chunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (b == null || uc == null) {
                        continue;
                    }
                    if (characterId.equals(b.getCharacterId()) && !exclude.equals(uc.getUuid())) {
                        count[0]++;
                    }
                }
            }
        );
        return count[0];
    }

    @Nonnull
    private static List<ResidentNpcRecord> findAllResidentRows(@Nonnull TownManager tm, @Nonnull UUID entityUuid) {
        List<ResidentNpcRecord> rows = new ArrayList<>();
        for (TownRecord town : tm.allTowns()) {
            for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
                if (entityUuid.equals(r.getLastEntityUuid())) {
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    @Nonnull
    private static List<Map<String, Object>> scanNearby(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull UUID entityUuid
    ) {
        TransformComponent tc = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (tc == null) {
            return List.of();
        }
        NpcSimilarityProfile targetProfile = similarityProfile(store, targetRef);
        if (!targetProfile.canMatchPeers()) {
            return List.of();
        }

        Vector3d origin = tc.getPosition();
        List<Map<String, Object>> nearby = new ArrayList<>();
        store.forEachChunk(
            Query.and(TransformComponent.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> otherRef = chunk.getReferenceTo(i);
                    if (otherRef == null || !otherRef.isValid() || otherRef.equals(targetRef)) {
                        continue;
                    }
                    UUIDComponent ouc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (ouc == null || entityUuid.equals(ouc.getUuid())) {
                        continue;
                    }
                    TransformComponent otc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (otc == null) {
                        continue;
                    }
                    Vector3d op = otc.getPosition();
                    double dx = op.x - origin.x;
                    double dy = op.y - origin.y;
                    double dz = op.z - origin.z;
                    if (dx * dx + dy * dy + dz * dz > NEARBY_RADIUS_SQ) {
                        continue;
                    }
                    NpcSimilarityProfile otherProfile = similarityProfile(store, otherRef);
                    if (!isSimilarNpc(targetProfile, otherProfile)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", ouc.getUuid().toString());
                    row.put("distance", Math.sqrt(dx * dx + dy * dy + dz * dz));
                    if (otherProfile.handle() != null) {
                        row.put("handle", otherProfile.handle());
                    }
                    if (otherProfile.characterId() != null) {
                        row.put("characterId", otherProfile.characterId());
                    }
                    if (otherProfile.villagerKind() != null) {
                        row.put("kind", otherProfile.villagerKind());
                    }
                    if (otherProfile.npcRoleName() != null) {
                        row.put("npcRoleName", otherProfile.npcRoleName());
                    }
                    if (otherProfile.assignmentKind() != null) {
                        row.put("assignmentKind", otherProfile.assignmentKind());
                    }
                    nearby.add(row);
                }
            }
        );
        return nearby;
    }

    private record NpcSimilarityProfile(
        @Nullable String villagerKind,
        @Nullable String npcRoleName,
        @Nullable String assignmentKind,
        @Nullable String characterId,
        @Nullable String handle
    ) {
        boolean canMatchPeers() {
            if (characterId != null && !characterId.isBlank()) {
                return true;
            }
            if (handle != null && !handle.isBlank()) {
                return true;
            }
            if (villagerKind != null && !villagerKind.isBlank()) {
                return true;
            }
            return npcRoleName != null && !npcRoleName.isBlank();
        }
    }

    @Nonnull
    private static NpcSimilarityProfile similarityProfile(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        String villagerKind = null;
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (binding != null && binding.getKind() != null && !binding.getKind().isBlank()) {
            villagerKind = binding.getKind().trim();
        }
        String npcRoleName = null;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
            npcRoleName = npc.getRoleName().trim();
        }
        String assignmentKind = null;
        String characterId = null;
        TownsfolkCharacterBinding townsfolk = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (townsfolk != null) {
            if (townsfolk.getAssignmentKind() != null && !townsfolk.getAssignmentKind().isBlank()) {
                assignmentKind = townsfolk.getAssignmentKind().trim();
            }
            if (townsfolk.getCharacterId() != null && !townsfolk.getCharacterId().isBlank()) {
                characterId = townsfolk.getCharacterId().trim();
            }
        }
        String handle = null;
        AetherhavenVillagerHandle villagerHandle = store.getComponent(ref, AetherhavenVillagerHandle.getComponentType());
        if (villagerHandle != null && villagerHandle.getHandle() != null && !villagerHandle.getHandle().isBlank()) {
            handle = villagerHandle.getHandle().trim();
        }
        return new NpcSimilarityProfile(villagerKind, npcRoleName, assignmentKind, characterId, handle);
    }

    private static boolean isSimilarNpc(@Nonnull NpcSimilarityProfile target, @Nonnull NpcSimilarityProfile other) {
        if (target.characterId() != null && target.characterId().equals(other.characterId())) {
            return true;
        }
        if (target.handle() != null && target.handle().equals(other.handle())) {
            return true;
        }
        if (target.villagerKind() != null
            && target.villagerKind().equals(other.villagerKind())) {
            if (TownVillagerBinding.KIND_TOWNSFOLK.equals(target.villagerKind())) {
                return target.assignmentKind() != null && target.assignmentKind().equals(other.assignmentKind());
            }
            return true;
        }
        return target.npcRoleName() != null && target.npcRoleName().equals(other.npcRoleName());
    }

    @Nullable
    private static TownVillagerBinding findBinding(@Nonnull Store<EntityStore> store, @Nonnull UUID entityUuid) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return store.getComponent(ref, TownVillagerBinding.getComponentType());
    }

    @Nonnull
    private static Map<String, Object> toJsonMap(@Nonnull Object value) {
        return GSON.fromJson(GSON.toJson(value), STRING_OBJECT_MAP_TYPE);
    }

    @Nonnull
    private static List<Map<String, Object>> gsonList(@Nonnull List<?> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : items) {
            out.add(toJsonMap(item));
        }
        return out;
    }
}
