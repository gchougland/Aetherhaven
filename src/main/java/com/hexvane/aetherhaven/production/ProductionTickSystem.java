package com.hexvane.aetherhaven.production;



import com.hexvane.aetherhaven.AetherhavenPlugin;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;

import com.hexvane.aetherhaven.poi.PoiRegistry;

import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;

import com.hexvane.aetherhaven.town.PlotInstance;

import com.hexvane.aetherhaven.town.PlotInstanceState;

import com.hexvane.aetherhaven.town.TownManager;

import com.hexvane.aetherhaven.town.TownRecord;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;

import com.hexvane.aetherhaven.villager.data.VillagerDefinition;

import com.hypixel.hytale.component.ArchetypeChunk;

import com.hypixel.hytale.component.CommandBuffer;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.component.dependency.Dependency;

import com.hypixel.hytale.component.dependency.RootDependency;

import com.hypixel.hytale.component.query.Query;

import com.hypixel.hytale.component.system.tick.EntityTickingSystem;

import com.hexvane.aetherhaven.schedule.VillagerScheduleWorkMinutes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Set;

import java.util.UUID;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;



/**

 * While a farmer, miner, logger, or rancher is on a scheduled {@code work} shift and present at their job plot,

 * advances per-plot production into {@link TownRecord} storage.

 */

public final class ProductionTickSystem extends EntityTickingSystem<EntityStore> {

    private final AetherhavenPlugin plugin;

    @Nonnull

    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();



    public ProductionTickSystem(@Nonnull AetherhavenPlugin plugin) {

        this.plugin = plugin;

    }



    @Nonnull

    @Override

    public Set<Dependency<EntityStore>> getDependencies() {

        return dependencies;

    }



    @Nonnull

    @Override

    public Query<EntityStore> getQuery() {

        return Query.and(TownVillagerBinding.getComponentType(), VillagerAutonomyState.getComponentType(), NPCEntity.getComponentType());

    }



    @Override

    public void tick(

        float dt,

        int index,

        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,

        @Nonnull Store<EntityStore> store,

        @Nonnull CommandBuffer<EntityStore> commandBuffer

    ) {

        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());

        VillagerAutonomyState autonomy = archetypeChunk.getComponent(index, VillagerAutonomyState.getComponentType());

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());

        if (binding == null || autonomy == null || npc == null || TownVillagerBinding.isVisitorKind(binding.getKind())) {

            return;

        }

        String kind = binding.getKind();

        String expectedConstruction = expectedConstructionForKind(kind);

        if (expectedConstruction == null) {

            return;

        }

        UUID jobPlotId = binding.getJobPlotId();

        if (jobPlotId == null) {

            return;

        }

        World world = store.getExternalData().getWorld();

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);

        TownRecord town = tm.getTown(binding.getTownId());

        if (town == null) {

            return;

        }

        PlotInstance plot = town.findPlotById(jobPlotId);

        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {

            return;

        }

        ConstructionCatalog ccat = plugin.getConstructionCatalog();

        if (!ccat.matchesGameplayConstruction(plot.getConstructionId(), expectedConstruction)) {

            return;

        }

        String gameplayPlotId = ccat.resolveGameplayConstructionId(plot.getConstructionId());

        String roleId = npc.getRoleName();

        if (roleId == null || roleId.isBlank()) {

            return;

        }

        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId.trim());

        if (vdef == null) {

            return;

        }

        String workConstructionId = vdef.getWorkConstructionId();

        if (workConstructionId == null) {

            return;

        }

        boolean workMatches = false;

        for (String gid : ccat.resolveGameplayConstructionIds(plot.getConstructionId())) {

            if (workConstructionId.equals(gid) || ccat.matchesGameplayConstruction(workConstructionId, gid)) {

                workMatches = true;

                break;

            }

        }

        if (!workMatches) {

            return;

        }

        ProductionCatalog catalog = plugin.getProductionCatalog();

        PlotProductionState state = town.getOrCreatePlotProduction(jobPlotId);

        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(catalog, plugin.getWorkplaceUnlockCatalog(), gameplayPlotId, state);

        if (entry == null || entry.catalogSize() <= 0) {

            return;

        }

        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr != null) {
            long epochMin = VillagerScheduleWorkMinutes.currentEpochMinute(wtr.getGameDateTime());
            if (state.getLastCatchUpEpochMinute() >= epochMin) {
                return;
            }
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);

        long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);

        if (!ProductionLiveWorkDetection.shouldAccrueEntityTick(

            store,

            ref,

            binding,

            autonomy,

            npc,

            plot,

            jobPlotId,

            reg,

            plugin.getVillagerDefinitionCatalog(),

            plugin.getVillagerScheduleRegistry(),

            now

        )) {

            return;

        }



        if (ProductionAccrualEngine.applyEntityTicks(

            state,

            entry,

            town,

            gameplayPlotId,

            ccat,

            plugin.getConfig().get(),

            world,

            plot.getSignX(),

            plot.getSignZ(),

            1

        )) {

            ProductionTownSaveDebouncer.maybePersist(tm, town, world, now);

        }

    }



    @Nullable

    private static String expectedConstructionForKind(@Nonnull String kind) {

        return switch (kind) {

            case TownVillagerBinding.KIND_FARMER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FARM;

            case TownVillagerBinding.KIND_MINER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;

            case TownVillagerBinding.KIND_LOGGER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL;

            case TownVillagerBinding.KIND_RANCHER -> com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_BARN;

            default -> null;

        };

    }

}


