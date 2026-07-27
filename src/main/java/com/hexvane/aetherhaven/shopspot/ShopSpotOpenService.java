package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

/**
 * NPC stalls are open during in-game daylight only and need an assigned workplace villager. Player-controlled spots
 * stay open at night whenever they have a listing.
 */
public final class ShopSpotOpenService {
    /** Scaled day band aligned with {@link WorldTimeResource} daylight (approx. sunrise through sunset). */
    private static final double DAY_START = 0.25;
    private static final double DAY_END = 0.75;

    private ShopSpotOpenService() {}

    public static boolean isGameDay(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        return wtr != null && isGameDay(wtr);
    }

    public static boolean isGameDay(@Nonnull WorldTimeResource wtr) {
        return wtr.isScaledDayTimeWithinRange(DAY_START, DAY_END);
    }

    /** True when the floating item prop should be visible above the stall. */
    public static boolean shouldShowDisplay(
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        return isOpen(record, town, world, store);
    }

    /** True when the shop can accept purchases. */
    public static boolean isOpen(
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        if (!record.hasStock()) {
            return false;
        }
        if (record.isPlayerControlled()) {
            return true;
        }
        if (!isGameDay(store)) {
            return false;
        }
        return hasStaffedWorkplace(record, town, store);
    }

    public static boolean hasStaffedWorkplace(
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        if (record.isPlayerControlled()) {
            return true;
        }
        UUID plotId = record.getPlotId();
        if (plotId == null || plotId.equals(new UUID(0L, 0L))) {
            return false;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        var catalog = plugin.getConstructionCatalog();
        if (!ProductionWorkplaceKinds.supportsWorkerAssignmentForPlot(catalog, plot.getConstructionId())) {
            return true;
        }
        for (String kind : ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, plot.getConstructionId())) {
            if (!hasWorkerOnPlot(store, town.getTownId(), plotId, kind)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasWorkerOnPlot(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind
    ) {
        AtomicBoolean found = new AtomicBoolean(false);
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found.get()) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (binding == null || !townId.equals(binding.getTownId()) || !residentKind.equals(binding.getKind())) {
                        continue;
                    }
                    UUID jobPlot = binding.getJobPlotId();
                    if (jobPlot != null && jobPlot.equals(workplacePlotId)) {
                        found.set(true);
                        return;
                    }
                }
            }
        );
        return found.get();
    }
}
