package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

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
        UUID nil = new UUID(0L, 0L);
        for (UUID u : npcUuids) {
            if (u == null || nil.equals(u)) {
                continue;
            }
            Ref<EntityStore> er = entityStore.getExternalData().getRefFromUUID(u);
            if (er == null || !er.isValid()) {
                continue;
            }
            entityStore.removeEntity(er, RemoveReason.REMOVE);
        }

        List<PlotInstance> plots = new ArrayList<>(town.getPlotInstances());
        for (PlotInstance p : plots) {
            clearPlotFromWorld(world, plugin, town, p, entityStore, reg);
        }

        reg.unregisterAllForTown(townId);
        tm.removeTown(townId);
        world.breakBlock(town.getCharterX(), town.getCharterY(), town.getCharterZ(), BREAK_SETTINGS);
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
        if (p.getState() == PlotInstanceState.BLUEPRINTING) {
            world.breakBlock(p.getSignX(), p.getSignY(), p.getSignZ(), BREAK_SETTINGS);
        } else if (p.getState() == PlotInstanceState.ASSEMBLING) {
            AssemblyWorldRegistry.remove(world, p.getPlotId());
            world.breakBlock(p.getSignX(), p.getSignY(), p.getSignZ(), BREAK_SETTINGS);
            PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(entityStore, p.toFootprint(), town);
            PrefabFootprintClearUtil.clearFootprint(world, p.toFootprint());
        } else if (p.getState() == PlotInstanceState.COMPLETE) {
            reg.unregisterByPlotId(p.getPlotId());
            PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(entityStore, p.toFootprint(), town);
            PrefabFootprintClearUtil.clearFootprint(world, p.toFootprint());
        }
    }
}
