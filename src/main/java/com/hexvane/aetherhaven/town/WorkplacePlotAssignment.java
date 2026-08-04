package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.guild.BardWorkPoiResolver;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.inn.InnBellService;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.production.ProductionCatchUpCursor;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.schedule.VillagerScheduleWorkMinutes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hexvane.aetherhaven.ui.WorkplaceWorkerDirectory;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Management UI: assign a villager whose job matches a completed production workplace plot. */
public final class WorkplacePlotAssignment {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private WorkplacePlotAssignment() {}

    /**
     * Clears the villager currently assigned to work at this plot (management block → Unassigned).
     *
     * @return null on success, or a short English reason for the player
     */
    @Nullable
    public static String tryClearWorker(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID workplacePlotId,
        @Nonnull Store<EntityStore> store
    ) {
        PlotInstance plot = town.findPlotById(workplacePlotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return "Plot is not ready.";
        }
        String gameplayId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
        String residentKind = ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(gameplayId);
        if (residentKind == null) {
            return "This building is not a workplace.";
        }
        return tryClearWorker(world, plugin, town, tm, workplacePlotId, residentKind, store);
    }

    /**
     * Clears one workplace role on this plot (guild master vs bard at the guild hall).
     *
     * @return null on success, or a short English reason for the player
     */
    @Nullable
    public static String tryClearWorker(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind,
        @Nonnull Store<EntityStore> store
    ) {
        PlotInstance plot = town.findPlotById(workplacePlotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return "Plot is not ready.";
        }
        var catalog = plugin.getConstructionCatalog();
        if (!ProductionWorkplaceKinds.supportsWorkerAssignmentForPlot(catalog, plot.getConstructionId())) {
            return "This building is not a workplace.";
        }

        Ref<EntityStore> npcRef = findWorkerOnPlot(store, town.getTownId(), workplacePlotId, residentKind);
        if (npcRef == null || !npcRef.isValid()) {
            String roleGameplayId =
                ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                    catalog,
                    plot.getConstructionId(),
                    residentKind
                );
            if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(roleGameplayId)
                && TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
                GuildHallAdventurerPoolService.clearAdventurersForHall(world, plugin, town, tm, store, plot);
            }
            return null;
        }

        UUIDComponent assignedUuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        String roleGameplayId =
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                catalog,
                plot.getConstructionId(),
                residentKind
            );
        if (roleGameplayId == null || roleGameplayId.isBlank()) {
            return "This building is not a workplace.";
        }
        String clearBlock =
            clearBlockReason(
                plugin,
                store,
                town,
                roleGameplayId,
                workplacePlotId,
                residentKind,
                assignedUuidComp != null ? assignedUuidComp.getUuid() : null
            );
        if (clearBlock != null) {
            return clearBlock;
        }

        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : null;
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        store.putComponent(
            npcRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), residentKind, null, null)
        );
        if (roleId != null && !roleId.isBlank() && uuidComp != null) {
            ResidentRegistryService.upsert(town, tm, roleId, residentKind, null, uuidComp.getUuid());
        }
        if (ProductionCatchUpCursor.isProductionWorkerKind(residentKind)) {
            ProductionCatchUpCursor.clearOnWorkerUnassign(town, workplacePlotId);
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(roleGameplayId)
            && TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
            GuildHallAdventurerPoolService.clearAdventurersForHall(world, plugin, town, tm, store, plot);
        }
        tm.updateTown(town);
        return null;
    }

    /**
     * @return null when clearing is allowed, otherwise a short English reason for the player
     */
    @Nullable
    public static String clearBlockReason(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind,
        @Nullable UUID currentlyAssignedUuid
    ) {
        if (ProductionWorkplaceKinds.isMandatoryWorkplaceResidentKind(residentKind)) {
            return "This villager must stay assigned to the building.";
        }
        String filterNpcRoleId = npcRoleFilterForWorkplaceResidentKind(residentKind);
        if (WorkplaceWorkerDirectory.hasAlternateEligibleWorker(
            store,
            town,
            plugin,
            gameplayWorkplaceId,
            filterNpcRoleId,
            currentlyAssignedUuid
        )) {
            return "Assign another worker before removing this one.";
        }
        return null;
    }

    /** True when the management UI may offer an Unassigned choice for this workplace role. */
    public static boolean allowsWorkplaceUnassign(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind
    ) {
        UUID assigned = findAssignedWorkerUuid(store, town.getTownId(), workplacePlotId, residentKind);
        return clearBlockReason(plugin, store, town, gameplayWorkplaceId, workplacePlotId, residentKind, assigned) == null;
    }

    @Nullable
    private static String npcRoleFilterForWorkplaceResidentKind(@Nonnull String residentKind) {
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
            return AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_BARD.equals(residentKind)) {
            return AetherhavenConstants.BARD_NPC_ROLE_ID;
        }
        return null;
    }

    @Nullable
    private static UUID findAssignedWorkerUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind
    ) {
        Ref<EntityStore> ref = findWorkerOnPlot(store, townId, workplacePlotId, residentKind);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    @Nullable
    private static Ref<EntityStore> findWorkerOnPlot(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind
    ) {
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk, com.hypixel.hytale.component.CommandBuffer<EntityStore> commandBuffer) -> {
                if (found.get() != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !townId.equals(b.getTownId()) || !residentKind.equals(b.getKind())) {
                        continue;
                    }
                    UUID jobPlot = b.getJobPlotId();
                    if (jobPlot == null || !jobPlot.equals(workplacePlotId)) {
                        continue;
                    }
                    found.set(chunk.getReferenceTo(i));
                    return;
                }
            }
        );
        return found.get();
    }

    /**
     * @return null on success, or a short English reason for the player
     */
    @Nullable
    public static String tryAssignWorker(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID workplacePlotId,
        @Nonnull UUID npcEntityUuid,
        @Nonnull Store<EntityStore> store
    ) {
        PlotInstance plot = town.findPlotById(workplacePlotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return "Plot is not ready.";
        }
        var catalog = plugin.getConstructionCatalog();
        if (!ProductionWorkplaceKinds.supportsWorkerAssignmentForPlot(catalog, plot.getConstructionId())) {
            return "This building is not a workplace.";
        }

        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(npcEntityUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return "Villager not found.";
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return "Invalid villager.";
        }
        String kind = ProductionWorkplaceKinds.residentBindingKindForNpcRoleId(plugin, npc.getRoleName().trim());
        if (kind == null) {
            return "No job role maps to this workplace.";
        }
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
        if (vdef == null) {
            return "Unknown villager role.";
        }
        String workId = vdef.getWorkConstructionId();
        if (workId == null) {
            return "That villager does not work at this building type.";
        }
        boolean workMatches = false;
        for (String gid : catalog.resolveGameplayConstructionIds(plot.getConstructionId())) {
            if (workId.equals(gid)
                || catalog.matchesGameplayConstruction(workId, gid)) {
                workMatches = true;
                break;
            }
        }
        if (!workMatches) {
            return "That villager does not work at this building type.";
        }

        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null || !town.getTownId().equals(binding.getTownId())) {
            return "That villager is not in this town.";
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        if (TownVillagerBinding.KIND_BARD.equals(kind)) {
            BardWorkPoiResolver.BardPlacementTarget target =
                BardWorkPoiResolver.resolvePlacement(plugin, town, plot, reg);
            if (target == null) {
                LOGGER.atWarning().log("No bard work station for workplace plot %s", workplacePlotId);
                return "No bard work station found on this plot.";
            }
        } else if (TownVillagerBinding.KIND_GUILD_MASTER.equals(kind)) {
            PoiEntry work = findGuildMasterWorkPoi(reg, town.getTownId(), workplacePlotId);
            if (work == null) {
                LOGGER.atWarning().log("No WORK POI for workplace plot %s", workplacePlotId);
                return "No work station found on this plot.";
            }
        }

        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uuidComp != null) {
            UUID u = uuidComp.getUuid();
            town.getInnPoolNpcIds()
                .removeIf(
                    s -> {
                        try {
                            return u.equals(UUID.fromString(s.trim()));
                        } catch (Exception e) {
                            return false;
                        }
                    }
                );
        }

        store.putComponent(
            npcRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), kind, workplacePlotId, workplacePlotId)
        );
        VillagerAutonomySystem.promptWorkplaceTravel(
            npcRef,
            store,
            VillagerAutonomySystem.resolveAutonomyNowMs(store)
        );
        town.addInnVisitorPoolExcludedRoleId(npc.getRoleName().trim());
        if (uuidComp != null) {
            ResidentRegistryService.upsert(town, tm, npc.getRoleName().trim(), kind, workplacePlotId, uuidComp.getUuid());
        }
        if (ProductionCatchUpCursor.isProductionWorkerKind(kind)) {
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            if (wtr != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
                ProductionCatchUpCursor.initOnWorkerAssign(
                    town,
                    workplacePlotId,
                    VillagerScheduleWorkMinutes.currentEpochMinute(wtr.getGameDateTime()),
                    npc.getRoleName().trim(),
                    wtr.getGameTime()
                );
            }
        }
        tm.updateTown(town);
        String roleGameplayId =
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(catalog, plot.getConstructionId(), kind);
        if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(roleGameplayId)
            && TownVillagerBinding.KIND_GUILD_MASTER.equals(kind)) {
            GuildHallAdventurerPoolService.tryFillAfterGuildMasterAssigned(
                world,
                plugin,
                town,
                tm,
                workplacePlotId,
                store
            );
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(roleGameplayId)
            && TownVillagerBinding.KIND_INNKEEPER.equals(kind)
            && town.isInnActive()) {
            InnBellService.ring(world, plugin, town, tm, store, plot);
        }
        return null;
    }

    @Nullable
    private static PoiEntry findGuildMasterWorkPoi(@Nonnull PoiRegistry reg, @Nonnull UUID townId, @Nonnull UUID plotId) {
        for (PoiEntry e : reg.listByTown(townId)) {
            if (!plotId.equals(e.getPlotId()) || !isWorkPoi(e)) {
                continue;
            }
            if (e.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                continue;
            }
            return e;
        }
        return null;
    }

    private static boolean isWorkPoi(@Nonnull PoiEntry poi) {
        return poi.getTags().contains("WORK") || poi.getInteractionKind() == PoiInteractionKind.WORK_SURFACE;
    }
}
