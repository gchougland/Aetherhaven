package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.map.TownBorderMapOverlayService;
import com.hexvane.aetherhaven.map.TownMapMarkerCache;
import com.hexvane.aetherhaven.map.TownMapMarkerProvider;
import com.hexvane.aetherhaven.placement.PlotBlockClearMode;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.placement.PrefabVolumeClearSpec;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.prop.PropPlotTeardown;
import com.hexvane.aetherhaven.shopspot.ShopSpotPlotRelocation;
import com.hexvane.aetherhaven.shopspot.ShopSpotRegistry;
import com.hexvane.aetherhaven.tourist.TouristPortalPlotRelocation;
import com.hexvane.aetherhaven.tourist.TouristPortalRegistry;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Destroys a town: removes villagers, clears buildings, POIs, persistence row, and the charter block.
 * Must run on the world thread.
 */
public final class TownDissolutionService {
    private static final int BREAK_SETTINGS = 10;

    private TownDissolutionService() {}

    public static void dissolveTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> entityStore
    ) {
        UUID townId = town.getTownId();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);

        LinkedHashSet<UUID> npcUuids = new LinkedHashSet<>();
        town.collectTrackedNpcEntityUuids(npcUuids);
        collectLoadedBoundNpcUuids(entityStore, townId, npcUuids);
        UUID nil = new UUID(0L, 0L);
        npcUuids.remove(nil);
        PendingEntityRemovalService.scheduleAll(world, new ArrayList<>(npcUuids), "town_dissolution");
        TownsfolkExistenceService.releaseForTown(world, plugin, townId);

        List<PlotInstance> plots = new ArrayList<>(town.getPlotInstances());
        for (PlotInstance p : plots) {
            clearPlotFromWorld(world, plugin, town, p, entityStore, reg);
        }

        // Statues outside any building stay standing; the armor players lent them is set down beside each pedestal.
        com.hexvane.aetherhaven.monument.FounderMonumentStatueRestoreSystem.releaseArmorOfTown(world, townId);
        reg.unregisterAllForTown(townId);
        // Players' items a festival was holding for this town are handed back or set down at the charter, not deleted.
        com.hexvane.aetherhaven.festival.FestivalHeldItemsService.releaseAll(world, entityStore, town);
        tm.removeTown(townId);
        TownBorderMapOverlayService.onWorldTownsChanged(world);
        TownMapMarkerProvider.removeTownMarkerFromAllPlayers(world, townId);
        TownMapMarkerCache.scheduleRebuild(world);
        ChunkSectionBlockUtil.breakBlock(world, town.getCharterX(), town.getCharterY(), town.getCharterZ(), BREAK_SETTINGS);
    }

    private static void collectLoadedBoundNpcUuids(
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull UUID townId,
        @Nonnull LinkedHashSet<UUID> out
    ) {
        entityStore.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    UUIDComponent uuid = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (binding != null && uuid != null && townId.equals(binding.getTownId())) {
                        out.add(uuid.getUuid());
                    }
                }
            }
        );
    }

    /**
     * Breaks the plot sign when present, clears assembly registry, POIs, entities in the footprint, and blocks in the
     * footprint. Does not remove the row from {@link TownRecord} (call {@link TownRecord#removePlotInstance} after).
     */
    public static void clearPlotFromWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance p,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull PoiRegistry reg
    ) {
        clearPlotFromWorld(world, plugin, town, p, entityStore, reg, PlotBlockClearMode.FULL_FOOTPRINT, null, null);
    }

    public static void clearPlotFromWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance p,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull PoiRegistry reg,
        @Nonnull PlotBlockClearMode blockClearMode,
        @Nullable PrefabVolumeClearSpec sparseClear
    ) {
        clearPlotFromWorld(world, plugin, town, p, entityStore, reg, blockClearMode, sparseClear, null);
    }

    public static void clearPlotFromWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance p,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull PoiRegistry reg,
        @Nonnull PlotBlockClearMode blockClearMode,
        @Nullable PrefabVolumeClearSpec sparseClear,
        @Nullable Ref<EntityStore> actingPlayer
    ) {
        clearPlotFromWorld(world, plugin, town, p, entityStore, reg, blockClearMode, sparseClear, actingPlayer, false);
    }

    /**
     * @param rebuildInPlace the same building is about to be pasted again where it stands, so its shop stalls keep
     *     their saved rows and everything players have for sale on them
     */
    public static void clearPlotFromWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance p,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull PoiRegistry reg,
        @Nonnull PlotBlockClearMode blockClearMode,
        @Nullable PrefabVolumeClearSpec sparseClear,
        @Nullable Ref<EntityStore> actingPlayer,
        boolean rebuildInPlace
    ) {
        PropPlotTeardown.packageIntersecting(world, plugin, p.toFootprint(), actingPlayer, entityStore);
        if (p.getState() == PlotInstanceState.BLUEPRINTING) {
            ChunkSectionBlockUtil.breakBlock(world, p.getSignX(), p.getSignY(), p.getSignZ(), BREAK_SETTINGS);
        } else if (p.getState() == PlotInstanceState.ASSEMBLING) {
            AssemblyWorldRegistry.remove(world, p.getPlotId());
            ChunkSectionBlockUtil.breakBlock(world, p.getSignX(), p.getSignY(), p.getSignZ(), BREAK_SETTINGS);
            PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(entityStore, p.toFootprint(), town);
            clearPlotBlocks(world, blockClearFootprint(plugin, p), blockClearMode, sparseClear);
        } else if (p.getState() == PlotInstanceState.COMPLETE) {
            reg.unregisterByPlotId(p.getPlotId());
            ShopSpotRegistry shopRegistry = AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world, plugin);
            if (rebuildInPlace) {
                ShopSpotPlotRelocation.beginInPlaceRebuild(world, plugin, entityStore, shopRegistry, p.getPlotId());
            } else {
                ShopSpotPlotRelocation.clearPlotSpots(world, plugin, entityStore, shopRegistry, p.getPlotId());
            }
            TouristPortalRegistry touristRegistry =
                AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TouristPortalPlotRelocation.clearPlotPortals(
                world, plugin, entityStore, touristRegistry, p.getPlotId(), town, tm
            );
            PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(entityStore, p.toFootprint(), town);
            clearPlotBlocks(world, blockClearFootprint(plugin, p), blockClearMode, sparseClear);
        }
    }

    /**
     * Box whose blocks a full teardown removes. A festival square stores its whole reserved box, five layers of it under
     * the paved floor. What lies there is the ground the square was built on or the fill a festival laid over it, so
     * the teardown stops at the floor and leaves it as ground instead of digging a pit.
     */
    @Nonnull
    private static PlotFootprintRecord blockClearFootprint(@Nonnull AetherhavenPlugin plugin, @Nonnull PlotInstance p) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(p.getConstructionId());
        if (def == null || !FestivalPrefabSize.usesReservedFootprint(def)) {
            return p.toFootprint();
        }
        return FestivalPrefabSize.fromFloorUp(p.toFootprint(), p.resolvePrefabAnchorWorld(def).y);
    }

    private static void clearPlotBlocks(
        @Nonnull World world,
        @Nonnull PlotFootprintRecord footprint,
        @Nonnull PlotBlockClearMode blockClearMode,
        @Nullable PrefabVolumeClearSpec sparseClear
    ) {
        switch (blockClearMode) {
            case NONE -> {}
            case SPARSE_PREFAB -> {
                if (sparseClear == null) {
                    throw new IllegalArgumentException("SPARSE_PREFAB requires PrefabVolumeClearSpec");
                }
                PrefabFootprintClearUtil.clearPrefabCellsAtAnchor(
                    world,
                    sparseClear.anchor(),
                    sparseClear.yaw(),
                    sparseClear.buffer(),
                    sparseClear.preserveWater()
                );
            }
            case FULL_FOOTPRINT -> PrefabFootprintClearUtil.clearFootprint(world, footprint);
        }
    }
}
