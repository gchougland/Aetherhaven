package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.map.TeleporterWarpSanitizer;
import com.hexvane.aetherhaven.placement.CharterRelocationService;
import com.hexvane.aetherhaven.placement.PlotPlacementCommit;
import com.hexvane.aetherhaven.poi.PoiExtractor;
import com.hexvane.aetherhaven.poi.marker.PoiMarkerDedupUtil;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.plot.PlotImportantBlockRestorer;
import com.hexvane.aetherhaven.shopspot.ShopSpotExtractor;
import com.hexvane.aetherhaven.tourist.TouristPortalExtractor;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Repairs drift between {@code towns.json} plot rows and plot-linked block components, including charter and
 * blueprinting plot-sign block entities.
 */
public final class PlotLinkReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long PERIODIC_SCAN_MS = 5 * 60_000L;
    private static final ConcurrentHashMap<String, Boolean> PERIODIC_ARMED = new ConcurrentHashMap<>();

    private PlotLinkReconcileService() {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileWorld(world, plugin, false));
        plugin.scheduleOnWorld(world, () -> reconcileWorld(world, plugin, false), 2_000L);
        plugin.scheduleOnWorld(world, () -> reconcileWorld(world, plugin, true), 10_000L);
        armPeriodicScan(world, plugin);
    }

    public static void clearWorldState(@Nonnull String worldName) {
        PERIODIC_ARMED.remove(worldName);
    }

    private static void armPeriodicScan(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (PERIODIC_ARMED.putIfAbsent(world.getName(), Boolean.TRUE) != null) {
            return;
        }
        scheduleNextPeriodicScan(world, plugin);
    }

    private static void scheduleNextPeriodicScan(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        plugin.scheduleOnWorld(
            world,
            () -> {
                if (!world.getPlayerRefs().isEmpty()) {
                    reconcileWorld(world, plugin, true);
                }
                scheduleNextPeriodicScan(world, plugin);
            },
            PERIODIC_SCAN_MS
        );
    }

    private static void reconcileWorld(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, boolean scanOrphans) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            TownRepairReport rep = repairTown(world, plugin, town, scanOrphans);
            if (rep.getRelinked() > 0) {
                LOGGER.atInfo().log(
                    "Plot link reconcile for town %s: relinked=%d alreadyOk=%d skipped=%d failed=%d orphans=%d",
                    town.getTownId(),
                    rep.getRelinked(),
                    rep.getAlreadyOk(),
                    rep.getSkippedChunkUnloaded(),
                    rep.getFailed(),
                    rep.getOrphans()
                );
            }
        }
    }

    @Nonnull
    public static TownRepairReport repairTown(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, boolean scanOrphans
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRepairReport report = new TownRepairReport();
        Store<EntityStore> entityStore =
            world.getEntityStore() != null ? world.getEntityStore().getStore() : null;

        applyCharterRepair(report, world, town);
        for (PlotInstance plot : town.getPlotInstances()) {
            mergePlotRepair(report, repairPlotInstance(world, plugin, town, plot, entityStore, false));
        }

        if (scanOrphans) {
            report.orphans = countOrphanManagementBlocks(world, plugin, tm);
        }
        if (report.relinked > 0) {
            tm.updateTown(town);
        }
        return report;
    }

    /**
     * Repairs one plot's important spots and town links. When {@code journalRepair} is true, missing blocks are
     * restored and POI/shop/tourist registries are always refreshed (Town Journal hammer action).
     */
    @Nonnull
    public static PlotRepairReport repairPlotInstance(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nullable Store<EntityStore> entityStore,
        boolean journalRepair
    ) {
        PlotRepairReport report = new PlotRepairReport();
        applyPlotSignRepairToPlotReport(report, world, town, plot);
        if (plot.getState() == PlotInstanceState.BLUEPRINTING) {
            report.blueprintingPlot = true;
            return report;
        }
        if (plot.getState() != PlotInstanceState.COMPLETE) {
            return report;
        }
        report.scanned++;
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            report.failed++;
            return report;
        }
        if (def.isWallSegment() || def.isDecorationPlot()) {
            return report;
        }
        if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
            report.skippedChunkUnloaded++;
            return report;
        }
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        if (anchor == null) {
            report.failed++;
            return report;
        }
        Rotation yaw = plot.resolvePrefabYaw();
        if (journalRepair) {
            report.placedBlocks +=
                PlotImportantBlockRestorer.restoreMissingImportantBlocks(world, town, plot, def, anchor, yaw);
        }
        PlotBlockStamper.PlotBlockRepairResult blockResult =
            PlotBlockStamper.verifyAndRepairPlot(world, town, plot, def, anchor, yaw);
        report.relinked += blockResult.getRelinked();
        report.alreadyOk += blockResult.getAlreadyOk();
        report.skippedChunkUnloaded += blockResult.getSkippedChunkUnloaded();
        report.failed += blockResult.getFailed();
        for (String detail : blockResult.getDetails()) {
            LOGGER.atInfo().log(
                "Plot repair town=%s plot=%s construction=%s: re-linked %s",
                town.getTownId(),
                plot.getPlotId(),
                plot.getConstructionId(),
                detail
            );
        }
        boolean registryRefresh =
            journalRepair || blockResult.getRelinked() > 0 || report.placedBlocks > 0;
        if (entityStore != null && registryRefresh) {
            refreshPlotRegistries(plugin, world, entityStore, town, plot, def, anchor, yaw);
        }
        if (journalRepair && (report.relinked > 0 || report.placedBlocks > 0)) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            tm.updateTown(town);
        }
        return report;
    }

    private static void refreshPlotRegistries(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        PoiMarkerDedupUtil.dedupeInPlot(entityStore, plot);
        PoiExtractor.registerForCompletedBuild(
            plugin,
            world,
            entityStore,
            town,
            plot.getPlotId(),
            plot,
            def.getId(),
            anchor,
            yaw
        );
        ShopSpotExtractor.registerForCompletedBuild(world, plugin, entityStore, town, plot.getPlotId(), plot);
        TouristPortalExtractor.registerForCompletedBuild(world, plugin, entityStore, town, plot.getPlotId(), plot);
        TeleporterWarpSanitizer.schedulePlotFootprintSanitize(world, plot.toFootprint());
        String festivalId = town.getActiveFestivalId();
        UUID festivalPlotId = town.getActiveFestivalPlotId();
        if (festivalId != null && plot.getPlotId().equals(festivalPlotId)) {
            FestivalDefinition festival = com.hexvane.aetherhaven.festival.FestivalLookSelection.activeLayout(plugin, town);
            if (festival != null) {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                FestivalService.ensureActiveFestivalSpots(world, entityStore, plugin, tm, town, festival);
            }
        }
    }

    private static void mergePlotRepair(@Nonnull TownRepairReport target, @Nonnull PlotRepairReport source) {
        target.scanned += source.scanned;
        target.relinked += source.relinked;
        target.alreadyOk += source.alreadyOk;
        target.skippedChunkUnloaded += source.skippedChunkUnloaded;
        target.failed += source.failed;
    }

    private static void applyPlotSignRepairToPlotReport(
        @Nonnull PlotRepairReport report,
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot
    ) {
        PlotPlacementCommit.LinkRepairResult result = PlotPlacementCommit.repairPlotSignLink(world, plot);
        if (result == PlotPlacementCommit.LinkRepairResult.NOT_APPLICABLE) {
            return;
        }
        report.scanned++;
        switch (result) {
            case ALREADY_OK -> report.alreadyOk++;
            case RELINKED, PLACED -> {
                report.relinked++;
                LOGGER.atInfo().log(
                    "Plot sign link repair town=%s plot=%s at %s,%s,%s: %s",
                    town.getTownId(),
                    plot.getPlotId(),
                    plot.getSignX(),
                    plot.getSignY(),
                    plot.getSignZ(),
                    result.name()
                );
            }
            case SKIPPED_CHUNK_UNLOADED -> report.skippedChunkUnloaded++;
            case FAILED -> report.failed++;
            case NOT_APPLICABLE -> {
                // handled above
            }
        }
    }

    private static void applyCharterRepair(
        @Nonnull TownRepairReport report, @Nonnull World world, @Nonnull TownRecord town
    ) {
        report.scanned++;
        CharterRelocationService.LinkRepairResult result =
            CharterRelocationService.repairCharterLink(world, town, Rotation.None);
        switch (result) {
            case ALREADY_OK -> report.alreadyOk++;
            case RELINKED, PLACED -> {
                report.relinked++;
                LOGGER.atInfo().log(
                    "Charter link repair town=%s at %s,%s,%s: %s",
                    town.getTownId(),
                    town.getCharterX(),
                    town.getCharterY(),
                    town.getCharterZ(),
                    result.name()
                );
            }
            case SKIPPED_CHUNK_UNLOADED -> report.skippedChunkUnloaded++;
            case FAILED_BLOCKED, FAILED -> report.failed++;
        }
    }


    public static final class PlotRepairReport {
        private int scanned;
        private int relinked;
        private int alreadyOk;
        private int skippedChunkUnloaded;
        private int failed;
        private int placedBlocks;
        private boolean blueprintingPlot;

        public boolean isBlueprintingPlot() {
            return blueprintingPlot;
        }

        public int getScanned() {
            return scanned;
        }

        public int getRelinked() {
            return relinked;
        }

        public int getAlreadyOk() {
            return alreadyOk;
        }

        public int getSkippedChunkUnloaded() {
            return skippedChunkUnloaded;
        }

        public int getFailed() {
            return failed;
        }

        public int getPlacedBlocks() {
            return placedBlocks;
        }

        public boolean hadFixes() {
            return relinked > 0 || placedBlocks > 0;
        }

        public boolean isNothingToDo() {
            return !hadFixes() && failed == 0 && skippedChunkUnloaded == 0;
        }
    }

    private static int countOrphanManagementBlocks(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownManager tm
    ) {
        if (world.getChunkStore() == null) {
            return 0;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        if (chunkStore == null) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        int[] orphans = new int[] {0};
        Query<ChunkStore> q =
            Query.and(ManagementBlock.getComponentType(), BlockModule.BlockStateInfo.getComponentType());
        chunkStore.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            for (int i = 0; i < archetypeChunk.size(); i++) {
                Ref<ChunkStore> blockRef = archetypeChunk.getReferenceTo(i);
                if (blockRef == null || !blockRef.isValid()) {
                    continue;
                }
                ManagementBlock mb = commandBuffer.getComponent(blockRef, ManagementBlock.getComponentType());
                BlockModule.BlockStateInfo bsi =
                    commandBuffer.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
                if (mb == null || bsi == null || mb.getPlotId().isBlank() || mb.getTownId().isBlank()) {
                    continue;
                }
                String key = mb.getTownId().trim() + ":" + mb.getPlotId().trim();
                if (!seen.add(key)) {
                    continue;
                }
                UUID townId;
                UUID plotId;
                try {
                    townId = UUID.fromString(mb.getTownId().trim());
                    plotId = UUID.fromString(mb.getPlotId().trim());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                TownRecord town = tm.getTown(townId);
                if (town == null || !world.getName().equals(town.getWorldName())) {
                    orphans[0]++;
                    LOGGER.atWarning().log(
                        "Orphan management block at loaded chunk: townId=%s plotId=%s (town or plot row missing)",
                        townId,
                        plotId
                    );
                    continue;
                }
                if (town.findPlotById(plotId) == null) {
                    orphans[0]++;
                    LOGGER.atWarning().log(
                        "Orphan management block: townId=%s plotId=%s (plot row missing in towns.json)",
                        townId,
                        plotId
                    );
                }
            }
        });
        return orphans[0];
    }

    @Nonnull
    public static List<PlotDiagnoseRow> diagnoseTown(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town
    ) {
        java.util.ArrayList<PlotDiagnoseRow> rows = new java.util.ArrayList<>();
        for (PlotInstance plot : town.getPlotInstances()) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
            boolean chunksLoaded = PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot);
            String linkStatus = "unknown";
            if (def != null && chunksLoaded && plot.getState() == PlotInstanceState.COMPLETE) {
                Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
                if (anchor != null) {
                    PlotBlockStamper.PlotBlockRepairResult r =
                        PlotBlockStamper.inspectPlotLinks(
                            world, town, plot, def, anchor, plot.resolvePrefabYaw()
                        );
                    if (r.getRelinked() > 0) {
                        linkStatus = "would-relink:" + r.getRelinked();
                    } else if (r.getFailed() > 0 || r.getSkippedChunkUnloaded() > 0) {
                        linkStatus = "broken";
                    } else {
                        linkStatus = "ok";
                    }
                } else {
                    linkStatus = "no-anchor";
                }
            } else if (!chunksLoaded) {
                linkStatus = "chunks-unloaded";
            }
            rows.add(
                new PlotDiagnoseRow(
                    plot.getPlotId(),
                    plot.getConstructionId() != null ? plot.getConstructionId() : "",
                    plot.getState() != null ? plot.getState().name() : "",
                    plot.getSignX(),
                    plot.getSignY(),
                    plot.getSignZ(),
                    linkStatus
                )
            );
        }
        return rows;
    }

    public static final class TownRepairReport {
        private int scanned;
        private int relinked;
        private int alreadyOk;
        private int skippedChunkUnloaded;
        private int failed;
        private int orphans;

        public int getScanned() {
            return scanned;
        }

        public int getRelinked() {
            return relinked;
        }

        public int getAlreadyOk() {
            return alreadyOk;
        }

        public int getSkippedChunkUnloaded() {
            return skippedChunkUnloaded;
        }

        public int getFailed() {
            return failed;
        }

        public int getOrphans() {
            return orphans;
        }
    }

    public record PlotDiagnoseRow(
        @Nonnull UUID plotId,
        @Nonnull String constructionId,
        @Nonnull String state,
        int signX,
        int signY,
        int signZ,
        @Nonnull String linkStatus
    ) {}
}
