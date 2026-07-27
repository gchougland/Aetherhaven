package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiExtractor;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Repairs stale workplace job plots and re-registers POIs on completed workplace variants after load. */
public final class WorkplaceJobPlotReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private WorkplaceJobPlotReconcileService() {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileWorld(world, plugin));
        plugin.scheduleOnWorld(world, () -> reconcileWorld(world, plugin), 3_000L);
    }

    private static void reconcileWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            int poiRereg = reregisterWorkplacePois(world, plugin, town, store);
            int repaired = reconcileVillagerJobPlots(world, plugin, town, tm, store);
            if (poiRereg > 0 || repaired > 0) {
                LOGGER.atInfo().log(
                    "Workplace reconcile for town %s: jobPlotRepairs=%d poiReregistrations=%d",
                    town.getTownId(),
                    repaired,
                    poiRereg
                );
            }
        }
    }

    private static int reregisterWorkplacePois(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        int count = 0;
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
                continue;
            }
            ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            if (def == null) {
                continue;
            }
            Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
            if (anchor == null) {
                continue;
            }
            boolean needsPoi = false;
            for (String gameplay :
                plugin.getConstructionCatalog().resolveGameplayConstructionIds(plot.getConstructionId())) {
                if (AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR.equals(gameplay)
                    || AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(gameplay)) {
                    needsPoi = true;
                    break;
                }
            }
            if (!needsPoi) {
                continue;
            }
            Rotation yaw = plot.resolvePrefabYaw();
            PoiExtractor.registerForCompletedBuild(
                plugin,
                world,
                store,
                town,
                plot.getPlotId(),
                plot,
                def.getId(),
                anchor,
                yaw
            );
            count++;
        }
        return count;
    }

    private static int reconcileVillagerJobPlots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        final int[] holder = {0};
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (binding == null || npc == null || uc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    if (!town.getTownId().equals(binding.getTownId())
                        || TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
                        continue;
                    }
                    VillagerDefinition vdef =
                        plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
                    if (vdef == null || vdef.getWorkConstructionId() == null) {
                        continue;
                    }
                    String expected = vdef.getWorkConstructionId().trim();
                    UUID job = binding.getJobPlotId();
                    boolean valid = false;
                    if (job != null) {
                        PlotInstance pi = town.findPlotById(job);
                        valid =
                            pi != null
                                && VillagerScheduleResolver.isValidWorkPlot(
                                    pi,
                                    expected,
                                    plugin.getConstructionCatalog()
                                );
                    }
                    if (valid) {
                        continue;
                    }
                    var outcome =
                        VillagerScheduleResolver.resolvePlot(
                            town,
                            binding,
                            uc.getUuid(),
                            VillagerScheduleResolver.LOC_WORK,
                            vdef,
                            plugin.getConstructionCatalog(),
                            null,
                            false
                        );
                    UUID fixed = outcome.plotId();
                    if (fixed == null) {
                        continue;
                    }
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    binding.setJobPlotId(fixed);
                    binding.setPreferredPlotId(fixed);
                    commandBuffer.putComponent(ref, TownVillagerBinding.getComponentType(), binding);
                    ResidentRegistryService.upsert(
                        town,
                        tm,
                        npc.getRoleName().trim(),
                        binding.getKind(),
                        fixed,
                        uc.getUuid()
                    );
                    VillagerAutonomySystem.promptWorkplaceTravel(
                        ref,
                        store,
                        VillagerAutonomySystem.resolveAutonomyNowMs(store)
                    );
                    holder[0]++;
                }
            }
        );
        if (holder[0] > 0) {
            tm.updateTown(town);
        }
        return holder[0];
    }
}