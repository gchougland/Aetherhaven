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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
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
        return hasStaffedWorkplace(record, town,
            kind -> hasWorkerOnPlot(store, town.getTownId(), record.getPlotId(), kind));
    }

    static boolean hasStaffedWorkplace(ShopSpotRecord record, TownRecord town, Predicate<String> hasWorker) {
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
        for (String kind : ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, plot.getConstructionId())) {
            if (!hasWorker.test(kind)) {
                return false;
            }
        }
        return true;
    }

    record Assignment(UUID townId, UUID plotId, String kind) {}

    /** One snapshot shared by all stalls in a deferred display refresh. Purchases still check live staffing. */
    static Set<Assignment> captureStaffing(Store<EntityStore> store) {
        Set<Assignment> assignments = new HashSet<>();
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    var binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (binding.getJobPlotId() != null)
                        assignments.add(new Assignment(binding.getTownId(), binding.getJobPlotId(), binding.getKind()));
                }
            });
        return assignments;
    }

    private static boolean hasWorkerOnPlot(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID workplacePlotId,
        @Nonnull String residentKind
    ) {
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType());
        return store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (binding == null || !townId.equals(binding.getTownId()) || !residentKind.equals(binding.getKind())) {
                        continue;
                    }
                    UUID jobPlot = binding.getJobPlotId();
                    if (jobPlot != null && jobPlot.equals(workplacePlotId)) {
                        return true;
                    }
                }
                return false;
            }
        );
    }
}
