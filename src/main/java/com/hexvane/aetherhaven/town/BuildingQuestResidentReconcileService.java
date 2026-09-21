package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerSpawner;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Reconciles job villagers whose building quest is active or completed and whose workplace plot is built, but who were
 * never promoted from inn visitors to town residents (or lost registry / workplace assignment).
 *
 * <p>If the matching NPC is not loaded, a fresh resident is spawned at the workplace. Loaded duplicates of the same
 * story role are removed before spawn; {@link TownResidentReconcileService} also keeps the newest tracked copy.
 */
public final class BuildingQuestResidentReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private BuildingQuestResidentReconcileService() {}

    public static final class ReconcileReport {
        private int examined;
        private int promoted;
        private int alreadyOk;
        private int skippedFailed;

        public int getExamined() {
            return examined;
        }

        public int getPromoted() {
            return promoted;
        }

        public int getAlreadyOk() {
            return alreadyOk;
        }

        /** Command message param {@code skippedNoNpc}: spawn/assign failures. */
        public int getSkippedNoNpc() {
            return skippedFailed;
        }

        void addExamined() {
            examined++;
        }

        void addPromoted() {
            promoted++;
        }

        void addAlreadyOk() {
            alreadyOk++;
        }

        void addSkippedFailed() {
            skippedFailed++;
        }
    }

    @Nonnull
    public static ReconcileReport reconcileForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        if (!world.isInThread()) {
            UUID townId = town.getTownId();
            return CompletableFuture.supplyAsync(
                    () -> {
                        Store<EntityStore> liveStore =
                            world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                        if (liveStore == null) {
                            return new ReconcileReport();
                        }
                        TownManager liveTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                        TownRecord liveTown = liveTm.getTown(townId);
                        if (liveTown == null) {
                            return new ReconcileReport();
                        }
                        return reconcileForTownOnWorldThread(world, plugin, liveTown, liveTm, liveStore);
                    },
                    world
                )
                .join();
        }
        return reconcileForTownOnWorldThread(world, plugin, town, tm, store);
    }

    @Nonnull
    private static ReconcileReport reconcileForTownOnWorldThread(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        ReconcileReport report = new ReconcileReport();

        ConstructionCatalog constructions = plugin.getConstructionCatalog();
        for (VillagerDefinition def : plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values()) {
            if (!def.isInnPoolEligible()) {
                continue;
            }
            String workConstructionId = def.getWorkConstructionId();
            if (workConstructionId == null || workConstructionId.isBlank()) {
                continue;
            }
            String roleId = def.getNpcRoleId();
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            String questId =
                InnVisitorShopPromotion.findShopQuestId(
                    plugin.getQuestCatalog(),
                    constructions,
                    def,
                    workConstructionId.trim()
                );
            if (questId == null || questId.isBlank() || !town.hasQuestActiveOrCompleted(questId)) {
                continue;
            }
            PlotInstance plot = town.findCompletePlotForWorkConstruction(constructions, workConstructionId.trim());
            if (plot == null) {
                continue;
            }
            report.addExamined();
            String residentKind = InnVisitorShopPromotion.resolveResidentKind(def);
            if (residentKind == null || residentKind.isBlank()) {
                report.addSkippedFailed();
                continue;
            }
            UUID workplacePlotId = plot.getPlotId();
            if (isAlreadyHealthy(town, store, roleId.trim(), residentKind, workplacePlotId)) {
                report.addAlreadyOk();
                continue;
            }

            Ref<EntityStore> npcRef =
                findPromotionCandidate(store, town, tm, roleId.trim(), def.getVisitorBindingKind(), residentKind);
            if (npcRef == null || !npcRef.isValid()) {
                npcRef = spawnResidentForWorkplace(world, plugin, town, tm, store, def, residentKind, plot);
            }
            if (npcRef == null || !npcRef.isValid()) {
                LOGGER.atWarning().log(
                    "fixresidents: could not find or spawn %s for town %s",
                    roleId,
                    town.getTownId()
                );
                report.addSkippedFailed();
                continue;
            }

            if (promoteCandidate(world, plugin, town, tm, store, roleId, residentKind, workplacePlotId, npcRef)) {
                report.addPromoted();
            } else {
                report.addSkippedFailed();
            }
        }

        TownResidentReconcileService.reconcileTownOnWorldThread(world, plugin, town);
        if (report.getPromoted() > 0 || report.getSkippedNoNpc() > 0) {
            LOGGER.atInfo().log(
                "Building quest resident reconcile for town %s: promoted=%d alreadyOk=%d failed=%d examined=%d",
                town.getTownId(),
                report.getPromoted(),
                report.getAlreadyOk(),
                report.getSkippedNoNpc(),
                report.getExamined()
            );
        }
        return report;
    }

    private static boolean promoteCandidate(
        World world, AetherhavenPlugin plugin, TownRecord town, TownManager tm, Store<EntityStore> store,
        String roleId, String residentKind, UUID workplacePlotId, Ref<EntityStore> npcRef
    ) {
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return false;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null || !town.getTownId().equals(binding.getTownId())) {
            store.putComponent(
                npcRef,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(town.getTownId(), residentKind, null)
            );
        }

        String err =
            WorkplacePlotAssignment.tryAssignWorker(
                world,
                plugin,
                town,
                tm,
                workplacePlotId,
                uuidComp.getUuid(),
                store
            );
        if (err != null) {
            store.putComponent(
                npcRef,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(town.getTownId(), residentKind, workplacePlotId, workplacePlotId)
            );
            ResidentRegistryService.upsert(
                town,
                tm,
                roleId.trim(),
                residentKind,
                workplacePlotId,
                uuidComp.getUuid()
            );
            town.addInnVisitorPoolExcludedRoleId(roleId.trim());
            tm.updateTown(town);
            LOGGER.atWarning().log(
                "fixresidents: assign worker failed for %s on plot %s (%s); forced resident registry instead",
                roleId,
                workplacePlotId,
                err
            );
        }
        town.getInnPoolNpcIds().removeIf(id -> uuidComp.getUuid().toString().equalsIgnoreCase(id));
        town.removeInnLockedEntity(uuidComp.getUuid());
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
            town.setGuildHallActive(true);
        }
        if (store.getComponent(npcRef, VillagerNeeds.getComponentType()) == null) {
            store.putComponent(npcRef, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        }
        if (store.getComponent(npcRef, AetherhavenVillagerHandle.getComponentType()) == null) {
            store.putComponent(npcRef, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(
                TownVillagerSpawner.handle(new TownVillagerBinding(town.getTownId(), residentKind, workplacePlotId))
            ));
        }
        tm.updateTown(town);
        return true;
    }

    /** Use the actual quest speaker, including legacy NPCs that never received a town binding. */
    public static void reconcileQuestSpeaker(
        World world, AetherhavenPlugin plugin, TownRecord town, TownManager tm, Store<EntityStore> store,
        String questId, Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid() || !canRecoverNpc(store, town, npcRef)) {
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) {
            return;
        }
        VillagerDefinition def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
        PlotInstance plot = readyWorkplaceForQuest(plugin.getQuestCatalog(), plugin.getConstructionCatalog(), town, def, questId);
        if (plot == null) {
            return;
        }
        String kind = InnVisitorShopPromotion.resolveResidentKind(def);
        if (kind == null || kind.isBlank() || isAlreadyHealthy(town, store, def.getNpcRoleId(), kind, plot.getPlotId())) {
            return;
        }
        promoteCandidate(world, plugin, town, tm, store, def.getNpcRoleId(), kind, plot.getPlotId(), npcRef);
    }

    static PlotInstance readyWorkplaceForQuest(
        com.hexvane.aetherhaven.quest.QuestCatalog quests, ConstructionCatalog constructions, TownRecord town,
        VillagerDefinition def, String questId
    ) {
        if (def == null || !def.isInnPoolEligible() || def.getWorkConstructionId() == null
            || !town.hasQuestActiveOrCompleted(questId)) {
            return null;
        }
        String work = def.getWorkConstructionId();
        if (!questId.equals(InnVisitorShopPromotion.findShopQuestId(quests, constructions, def, work))) {
            return null;
        }
        return town.findCompletePlotForWorkConstruction(constructions, work);
    }

    private static boolean canRecoverNpc(Store<EntityStore> store, TownRecord town, Ref<EntityStore> ref) {
        if (store.getComponent(ref, WorldNpcBinding.getComponentType()) != null) {
            return false;
        }
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        AetherhavenVillagerHandle handle = store.getComponent(ref, AetherhavenVillagerHandle.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        boolean insideTown = transform != null && TownTerritoryClaims.containsBlock(
            town, (int) Math.floor(transform.getPosition().x), (int) Math.floor(transform.getPosition().z)
        );
        return recoveryBelongsToTown(town.getTownId(), binding != null ? binding.getTownId() : null,
            handle != null ? handle.getHandle() : null, insideTown);
    }

    static boolean recoveryBelongsToTown(UUID townId, UUID boundTownId, String handle, boolean insideTown) {
        if (boundTownId != null) {
            return townId.equals(boundTownId);
        }
        if (handle != null && !handle.isBlank()) {
            return villagerHandleMatchesTownSuffix(townId, handle);
        }
        return insideTown;
    }

    /**
     * Registry already tracks this role on the workplace. Entity need not be loaded; if loaded, it must match.
     */
    private static boolean isAlreadyHealthy(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleId,
        @Nonnull String residentKind,
        @Nonnull UUID workplacePlotId
    ) {
        ResidentNpcRecord registryRow = findRegistryRow(town, roleId);
        if (registryRow == null) {
            return false;
        }
        if (TownVillagerBinding.isVisitorKind(registryRow.getKind())) {
            return false;
        }
        if (!residentKind.equals(registryRow.getKind())) {
            return false;
        }
        UUID registryJobPlotId = registryRow.getJobPlotId();
        if (registryJobPlotId == null || !registryJobPlotId.equals(workplacePlotId)) {
            return false;
        }
        UUID registryUuid = registryRow.getLastEntityUuid();
        if (registryUuid.getMostSignificantBits() == 0L && registryUuid.getLeastSignificantBits() == 0L) {
            return false;
        }
        Ref<EntityStore> live = store.getExternalData().getRefFromUUID(registryUuid);
        if (live == null || !live.isValid()) {
            return true;
        }
        TownVillagerBinding b = store.getComponent(live, TownVillagerBinding.getComponentType());
        NPCEntity npc = store.getComponent(live, NPCEntity.getComponentType());
        if (b == null || npc == null || !roleId.equalsIgnoreCase(npc.getRoleName())) {
            return false;
        }
        if (!town.getTownId().equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
            return false;
        }
        return workplacePlotId.equals(b.getJobPlotId()) && residentKind.equals(b.getKind());
    }

    @Nullable
    private static ResidentNpcRecord findRegistryRow(@Nonnull TownRecord town, @Nonnull String roleId) {
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (roleId.equalsIgnoreCase(r.getNpcRoleId())) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> findPromotionCandidate(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String roleId,
        @Nullable String visitorBindingKind,
        @Nonnull String residentKind
    ) {
        Ref<EntityStore> fromPool = findInnPoolNpcRef(store, town, roleId);
        if (fromPool != null && canRecoverNpc(store, town, fromPool)) {
            return fromPool;
        }
        Ref<EntityStore> fromBinding = findTownBoundNpcRef(store, town, roleId, visitorBindingKind, residentKind);
        if (fromBinding != null && canRecoverNpc(store, town, fromBinding)) {
            return fromBinding;
        }
        Ref<EntityStore> fromHandle = findHandleMatchedNpcRef(store, town, roleId);
        if (fromHandle != null && canRecoverNpc(store, town, fromHandle)) {
            return fromHandle;
        }
        return findOrphanRoleNpcRef(store, town, tm, roleId);
    }

    @Nullable
    private static Ref<EntityStore> spawnResidentForWorkplace(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull VillagerDefinition def,
        @Nonnull String residentKind,
        @Nonnull PlotInstance plot
    ) {
        String roleId = def.getNpcRoleId().trim();
        removeLoadedDuplicatesForRole(store, town, tm, roleId);

        Vector3d position = resolveWorkplaceSpawnPosition(world, plugin, town, plot);
        Ref<EntityStore> npcRef = TownVillagerSpawner.spawn(
            store, roleId, position, new TownVillagerBinding(town.getTownId(), residentKind, plot.getPlotId(), plot.getPlotId()),
            "FIX_RESIDENTS", "roleId=" + roleId + ",plotId=" + plot.getPlotId()
        );
        if (npcRef == null) {
            return null;
        }
        LOGGER.atInfo().log(
            "fixresidents: spawned %s for town %s at workplace plot %s",
            roleId,
            town.getTownId(),
            plot.getPlotId()
        );
        return npcRef;
    }

    @Nonnull
    private static Vector3d resolveWorkplaceSpawnPosition(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot
    ) {
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (plot.getPlotId().equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                return new Vector3d(e.getX() + 0.5, e.getY(), e.getZ() + 0.5);
            }
        }
        if (plot.hasStoredPrefabWorldAnchor()) {
            Vector3i a = plot.getStoredPrefabWorldAnchor();
            return new Vector3d(a.x + 0.5, a.y, a.z + 0.5);
        }
        return new Vector3d(plot.getSignX() + 0.5, plot.getSignY(), plot.getSignZ() + 0.5);
    }

    private static int removeLoadedDuplicatesForRole(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String roleId
    ) {
        String wanted = roleId.trim();
        if (wanted.isEmpty()) {
            return 0;
        }
        Query<EntityStore> q = Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            q,
            (archetypeChunk, commandBuffer) -> {
                int n = archetypeChunk.size();
                for (int i = 0; i < n; i++) {
                    Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                    if (npcRef == null || !npcRef.isValid()) {
                        continue;
                    }
                    NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                    if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    if (!wanted.equalsIgnoreCase(npc.getRoleName().trim())) {
                        continue;
                    }
                    if (canRecoverNpc(store, town, npcRef)) {
                        toRemove.add(npcRef);
                    }
                }
            }
        );
        int count = 0;
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                VillagerAuditContext.removeEntity(store, r, "fixresidents_spawn");
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static Ref<EntityStore> findInnPoolNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId
    ) {
        for (String sid : town.getInnPoolNpcIds()) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null && roleId.equalsIgnoreCase(npc.getRoleName())) {
                    return ref;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> findTownBoundNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId,
        @Nullable String visitorBindingKind,
        @Nonnull String residentKind
    ) {
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        UUID tid = town.getTownId();
        store.forEachEntityParallel(
            TownVillagerBinding.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                if (found.get() != null) {
                    return;
                }
                TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
                if (b == null || !tid.equals(b.getTownId())) {
                    return;
                }
                String kind = b.getKind();
                boolean kindMatch =
                    residentKind.equals(kind)
                        || (visitorBindingKind != null && visitorBindingKind.equals(kind))
                        || TownVillagerBinding.isVisitorKind(kind);
                if (!kindMatch) {
                    return;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
                if (npc == null || !roleId.equalsIgnoreCase(npc.getRoleName())) {
                    return;
                }
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                if (ref != null && ref.isValid()) {
                    found.set(ref);
                }
            }
        );
        return found.get();
    }

    @Nullable
    private static Ref<EntityStore> findHandleMatchedNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId
    ) {
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        store.forEachEntityParallel(
            AetherhavenVillagerHandle.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                if (found.get() != null) {
                    return;
                }
                AetherhavenVillagerHandle handle =
                    archetypeChunk.getComponent(index, AetherhavenVillagerHandle.getComponentType());
                if (handle == null || handle.getHandle().isBlank()) {
                    return;
                }
                if (!villagerHandleMatchesTownSuffix(town.getTownId(), handle.getHandle())) {
                    return;
                }
                TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
                if (b != null && !town.getTownId().equals(b.getTownId())) {
                    return;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
                if (npc == null || !roleId.equalsIgnoreCase(npc.getRoleName())) {
                    return;
                }
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                if (ref != null && ref.isValid()) {
                    found.set(ref);
                }
            }
        );
        return found.get();
    }

    /** Loaded NPC with this role that is unbound or bound to a missing town. */
    @Nullable
    private static Ref<EntityStore> findOrphanRoleNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull String roleId
    ) {
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        Query<EntityStore> q = Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (archetypeChunk, commandBuffer) -> {
                if (found.get() != null) {
                    return;
                }
                int n = archetypeChunk.size();
                for (int i = 0; i < n; i++) {
                    if (found.get() != null) {
                        return;
                    }
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (npc == null || npc.getRoleName() == null || !roleId.equalsIgnoreCase(npc.getRoleName().trim())) {
                        continue;
                    }
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (canRecoverNpc(store, town, ref)) {
                        found.set(ref);
                        return;
                    }
                }
            }
        );
        return found.get();
    }

    private static boolean villagerHandleMatchesTownSuffix(@Nonnull UUID townId, @Nonnull String handle) {
        String hex = townId.toString().replace("-", "");
        if (hex.isEmpty()) {
            return false;
        }
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        return handle.toLowerCase().endsWith(suffix.toLowerCase());
    }
}
