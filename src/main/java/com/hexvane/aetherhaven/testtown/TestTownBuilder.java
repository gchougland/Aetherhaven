package com.hexvane.aetherhaven.testtown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.charter.TownFoundingService;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.placement.FrontFacing;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.startertown.StarterTownBuildService;
import com.hexvane.aetherhaven.startertown.StarterTownLayoutPlan;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.BuildingQuestResidentReconcileService;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownRelinquishService;
import com.hexvane.aetherhaven.town.TownSaveCoordinator;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.town.WorkplacePlotAssignment;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.TownVillagerSpawner;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Builds the test town in its own world, one stage after another on that world's thread. Every stage catches its own
 * failures and writes them to the report, so one building or workplace that cannot be made never stops the rest.
 *
 * <p>The town is made through the same services a player's town uses: the charter founding service, the starter town
 * build service (real plots, linked blocks, completion hooks), the resident reconcile service and the workplace,
 * housing and guard services. The few places that write state directly are listed in the class comment of
 * {@link TestTownOutfitter}.
 */
public final class TestTownBuilder {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final long BUILDING_TIMEOUT_SECONDS = 120;
    /** Blocks kept loaded around the planned town while it is built. */
    private static final int LOAD_MARGIN = 24;
    /** How far below and above the ground the town reaches: the mineshaft, and the tallest building. */
    static final int DEPTH_BELOW = 16, HEIGHT_ABOVE = 64;
    private static final String SITE_PREFIX = "site:";

    /** Who the town belongs to. A claimable town is founded for a stand-in and then left without an owner. */
    public record Owner(UUID uuid, String name, boolean claimable) {}

    /** A building whose prefab has been read and measured, before it has a place in the grid. */
    private record Measured(String constructionId, ConstructionDefinition definition, IPrefabBuffer buffer) {}

    /** A building that has a place in the grid and is ready to be built. */
    record Placement(String key, String constructionId, ConstructionDefinition definition, IPrefabBuffer buffer,
                     Rotation yaw, Vector3i anchor, PlotFootprintRecord footprint, Vector3i roadPoint) {}

    final AetherhavenPlugin plugin;
    final TestTownSpec spec;
    final Owner owner;
    final TestTownReport report = new TestTownReport();
    /** Set instead of building when the test world already holds a town. */
    private TestTownReport existing;
    final Consumer<String> progress;
    World world;
    TownManager manager;
    UUID townId;
    TestTownGrid.Plan grid;
    final Map<String, Placement> placements = new LinkedHashMap<>();
    /** Buildings that are started and left for the Building yard. */
    final Map<String, Placement> sitePlacements = new LinkedHashMap<>();
    final Map<String, UUID> plotIds = new LinkedHashMap<>();

    private TestTownBuilder(AetherhavenPlugin plugin, TestTownSpec spec, Owner owner, Consumer<String> progress) {
        this.plugin = plugin;
        this.spec = spec;
        this.owner = owner;
        this.progress = progress;
    }

    /**
     * Makes sure the test world holds a test town. A town that is already there is left exactly as it is and the report
     * says so.
     */
    public static CompletableFuture<TestTownReport> run(AetherhavenPlugin plugin, TestTownSpec spec, Owner owner,
                                                        Consumer<String> progress) {
        TestTownBuilder builder = new TestTownBuilder(plugin, spec, owner, progress);
        return TestTownWorlds.ensure(spec.world()).thenCompose(world -> {
            builder.world = world;
            return builder.onWorld(builder::begin);
        }).thenCompose(found -> found ? CompletableFuture.completedFuture(builder.existing) : builder.buildAll());
    }

    TownRecord town() {
        return manager.getTown(townId);
    }

    int groundY() {
        return spec.world().surfaceY() + 1;
    }

    <T> CompletableFuture<T> onWorld(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, world);
    }

    /** Runs a stage on the world thread. A stage that throws is reported and the build carries on. */
    private CompletableFuture<Void> stage(String name, Runnable action) {
        return stage(name, action, world);
    }

    private CompletableFuture<Void> stage(String name, Runnable action, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            long started = System.nanoTime();
            try {
                action.run();
            } catch (RuntimeException failure) {
                LOG.atWarning().withCause(failure).log("Test town stage '%s' failed", name);
                report.skip("stage " + name, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            LOG.atInfo().log("Test town stage '%s' took %s ms", name, (System.nanoTime() - started) / 1_000_000);
        }, executor);
    }

    /** @return true when a town is already in the test world */
    private boolean begin() {
        // A build that was cut short by a server stop would leave this off; every later use puts it right.
        world.getWorldConfig().setCanUnloadChunks(true);
        manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        List<TownRecord> towns = manager.allTowns();
        if (towns.isEmpty()) return false;
        TownRecord town = towns.getFirst();
        townId = town.getTownId();
        existing = describe(plugin, world, town);
        return true;
    }

    private CompletableFuture<TestTownReport> buildAll() {
        progress.accept("Building the test town in world " + spec.world().name() + ". This takes a few seconds.");
        // Nobody is in the world during a console build, so its chunks would unload under the builder.
        world.getWorldConfig().setCanUnloadChunks(false);
        TestTownOutfitter outfitter = new TestTownOutfitter(this);
        // The layout only reads prefab files and does arithmetic, so it is planned off the world thread.
        return stage("layout", this::planLayout, ForkJoinPool.commonPool())
            .thenCompose(ignored -> loadTownChunks())
            .thenCompose(ignored -> stage("founding", this::found))
            .thenCompose(ignored -> townId == null ? CompletableFuture.<Void>completedFuture(null) : stage("territory", this::claimTerritory)
                .thenCompose(done -> buildBuildings())
                .thenCompose(done -> stage("villagers", this::settleVillagers))
                .thenCompose(done -> stage("production", outfitter::production))
                .thenCompose(done -> stage("workplaces", outfitter::workplaces))
                .thenCompose(done -> stage("townsfolk", outfitter::townsfolk))
                .thenCompose(done -> stage("building sites", outfitter::buildingSites))
                .thenCompose(done -> stage("stock and orders", outfitter::stockAndOrders))
                .thenCompose(done -> stage("paths", outfitter::paths))
                .thenCompose(done -> stage("handover", this::handOver)))
            .handle((done, failure) -> {
                world.getWorldConfig().setCanUnloadChunks(true);
                if (failure != null) {
                    LOG.atWarning().withCause(failure).log("Test town build stopped early");
                    report.skip("the rest of the build", String.valueOf(failure.getMessage()));
                }
                return report;
            });
    }

    // ---------------------------------------------------------------------------------------------- layout

    private void planLayout() {
        List<TestTownGrid.Item> items = new ArrayList<>();
        Map<String, Measured> pending = new LinkedHashMap<>();
        Map<TestTownSpec.BuildingSpec, String> wanted = new java.util.IdentityHashMap<>();
        List<TestTownSpec.BuildingSpec> order = new ArrayList<>(spec.buildings());
        order.addAll(spec.buildingSites());
        for (TestTownSpec.BuildingSpec site : spec.buildingSites()) wanted.put(site, SITE_PREFIX);
        for (TestTownSpec.BuildingSpec building : order) {
            String prefix = wanted.getOrDefault(building, "");
            ConstructionDefinition definition = plugin.getConstructionCatalog().get(building.id());
            if (definition == null) {
                report.skip("building " + building.id(), "this game has no building with that id (its asset pack may be off)");
                continue;
            }
            if (definition.isWallSegment() || definition.isDecorationPlot()) {
                report.skip("building " + building.id(), "walls and decorations are not kept as town plots");
                continue;
            }
            IPrefabBuffer buffer = definition.getPrefabPath() == null || definition.getPrefabPath().isBlank()
                ? null : PrefabResolveUtil.resolvePrefabBuffer(definition.getPrefabPath());
            if (buffer == null) {
                report.skip("building " + building.id(), "its prefab " + definition.getPrefabPath() + " could not be read");
                continue;
            }
            // Width and depth are the same for the two facings a row can take, so one measurement is enough.
            Rotation yaw = facing(definition, WallCardinal.NORTH);
            PlotFootprintRecord size = PlotFootprintUtil.computeFootprint(new Vector3i(0, 0, 0), yaw, buffer, definition);
            for (int copy = 0; copy < building.copies(); copy++) {
                String key = prefix + (building.copies() == 1 ? building.id() : building.id() + "#" + (copy + 1));
                items.add(new TestTownGrid.Item(key, size.getMaxX() - size.getMinX() + 1, size.getMaxZ() - size.getMinZ() + 1));
                pending.put(key, new Measured(building.id(), definition, buffer));
            }
        }
        for (TestTownSpec.WorkplaceSpec workplace : spec.workplaces())
            items.add(new TestTownGrid.Item(TestTownOutfitter.workplaceKey(workplace), workplace.width(), workplace.depth()));
        for (TestTownOutfitter.Yard yard : TestTownOutfitter.Yard.values())
            items.add(new TestTownGrid.Item(yard.key, yard.width, yard.depth));
        grid = TestTownGrid.pack(items, 0, 0, spec.grid().rowWidth(), spec.grid().street(), spec.grid().plaza());
        for (var entry : pending.entrySet()) {
            TestTownGrid.Cell cell = grid.find(entry.getKey());
            ConstructionDefinition definition = entry.getValue().definition();
            IPrefabBuffer buffer = entry.getValue().buffer();
            // Every building faces the street on the plaza side of its row, and its lowest layer rests on the ground,
            // which is how the starter town command sets buildings down.
            Rotation yaw = facing(definition, cell.south() ? WallCardinal.NORTH : WallCardinal.SOUTH);
            PlotFootprintRecord relative = PlotFootprintUtil.computeFootprint(new Vector3i(0, 0, 0), yaw, buffer, definition);
            Vector3i anchor = new Vector3i(cell.minX() - relative.getMinX(), groundY() - relative.getMinY(),
                cell.minZ() - relative.getMinZ());
            PlotFootprintRecord footprint = PlotFootprintUtil.computeFootprint(anchor, yaw, buffer, definition);
            Vector3i road = new Vector3i(cell.centerX(), groundY(), cell.streetZ(2));
            (entry.getKey().startsWith(SITE_PREFIX) ? sitePlacements : placements).put(entry.getKey(),
                new Placement(entry.getKey(), entry.getValue().constructionId(), definition, buffer, yaw, anchor, footprint, road));
        }
        LOG.atInfo().log("Test town layout: %s items in a %s by %s block grid", items.size(),
            grid.maxX() - grid.minX() + 1, grid.maxZ() - grid.minZ() + 1);
    }

    private static Rotation facing(ConstructionDefinition definition, WallCardinal worldFront) {
        return WallCardinal.prefabYawFromSteps(WallCardinal.stepsAligning(FrontFacing.toCardinal(definition.getFrontFacing()), worldFront));
    }

    /** Loads the ground the town will stand on, from below the mineshaft to above the tallest roof. */
    private CompletableFuture<Void> loadTownChunks() {
        if (grid == null) return CompletableFuture.completedFuture(null);
        return onWorld(() -> TestTownWorlds.loadBox(world, grid.minX() - LOAD_MARGIN, groundY() - DEPTH_BELOW, grid.minZ() - LOAD_MARGIN,
            grid.maxX() + LOAD_MARGIN, groundY() + HEIGHT_ABOVE, grid.maxZ() + LOAD_MARGIN))
            .thenCompose(all -> all).orTimeout(180, TimeUnit.SECONDS).handle((done, failure) -> {
                if (failure != null) report.skip("loading the town's chunks", String.valueOf(failure.getMessage()));
                return null;
            });
    }

    // ---------------------------------------------------------------------------------------------- town

    private void found() {
        if (grid == null) throw new IllegalStateException("there is no layout to build");
        Vector3i charter = new Vector3i(0, groundY(), 0);
        TownRecord town = TownFoundingService.foundWithNewCharter(world, plugin, owner.uuid(), owner.name(), charter,
            Rotation.None, new Random(20261011L));
        if (town == null) {
            report.skip("founding the town", "the charter could not be placed at 0, " + groundY() + ", 0");
            report.markFailed();
            return;
        }
        townId = town.getTownId();
        if (spec.town().displayName() != null && !spec.town().displayName().isBlank())
            manager.trySetDisplayName(town, spec.town().displayName());
        var tier = spec.town().rank() == null ? null : plugin.getQuestBoardCatalog().rankTier(spec.town().rank());
        if (tier == null) report.skip("town rank " + spec.town().rank(), "the quest board has no rank with that id");
        else town.setQuestBoardRankXp(tier.xpRequired());
        town.addTreasuryGoldCoins(spec.town().treasuryGold());
        // Rescues and other quests that no building stands for, so every villager's story is past its start.
        for (String quest : spec.town().completeQuests()) {
            if (plugin.getQuestCatalog().get(quest) == null) { report.skip("quest " + quest, "no quest has that id"); continue; }
            if (!town.hasQuestCompleted(quest)) town.completeQuest(quest);
        }
        manager.updateTown(town);
        LOG.atInfo().log("Test town founded: %s at 0, %s, 0", town.getDisplayName(), groundY());
    }

    /** Buys nothing: adds the 2 by 2 chunk claim blocks the layout needs beyond the starter territory. */
    private void claimTerritory() {
        TownRecord town = town();
        int charterChunkX = TownTerritoryClaims.charterChunkX(town), charterChunkZ = TownTerritoryClaims.charterChunkZ(town);
        int radius = town.getTerritoryChunkRadius(), size = TownTerritoryClaims.CLAIM_BLOCK_CHUNK_SIZE;
        int minX = Math.floorDiv(ChunkUtil.chunkCoordinate(grid.minX() - 8) - (charterChunkX - radius), size);
        int maxX = Math.floorDiv(ChunkUtil.chunkCoordinate(grid.maxX() + 8) - (charterChunkX - radius), size);
        int minZ = Math.floorDiv(ChunkUtil.chunkCoordinate(grid.minZ() - 8) - (charterChunkZ - radius), size);
        int maxZ = Math.floorDiv(ChunkUtil.chunkCoordinate(grid.maxZ() + 8) - (charterChunkZ - radius), size);
        int added = 0;
        boolean grew = true;
        // A claim block must touch the town, so grow outwards until nothing more can be added.
        while (grew) {
            grew = false;
            for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
                int anchorX = charterChunkX - radius + bx * size, anchorZ = charterChunkZ - radius + bz * size;
                if (TownTerritoryClaims.canClaimBlock(town, anchorX, anchorZ, manager.allTowns())
                    && TownTerritoryClaims.addClaimBlock(town, anchorX, anchorZ)) { added++; grew = true; }
            }
        }
        if (added > 0) {
            manager.updateTown(town);
            report.note("Territory: " + added + " extra claim blocks were added so the whole grid is inside town borders.");
        }
    }

    // ---------------------------------------------------------------------------------------------- buildings

    private CompletableFuture<Void> buildBuildings() {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Placement placement : placements.values()) chain = chain.thenCompose(ignored -> buildOne(placement));
        return chain;
    }

    /** One building through the starter town service: real plot, sign, linked blocks, completion hooks and resident. */
    private CompletableFuture<Void> buildOne(Placement placement) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        world.execute(() -> {
            try {
                TownRecord town = town();
                if (!insideTerritory(town, placement.footprint())) {
                    report.skip("building " + placement.key(), "its place in the grid is outside town borders");
                    done.complete(null);
                    return;
                }
                StarterTownLayoutPlan plan = new StarterTownLayoutPlan("line", 0L, List.of(new StarterTownLayoutPlan.Building(
                    placement.constructionId(), placement.anchor(), placement.yaw(), placement.footprint(), placement.roadPoint())));
                StarterTownBuildService.build(world, plugin, owner.uuid(), townId, plan, result -> {
                    PlotInstance plot = newestPlot(placement.constructionId());
                    if (plot != null) plotIds.put(placement.key(), plot.getPlotId());
                    // Walls and decorations aside, a finished building is a COMPLETE plot.
                    if (plot != null && plot.getState() == PlotInstanceState.COMPLETE) report.add(TestTownReport.BUILDINGS);
                    else report.skip("building " + placement.key(), "it was placed but did not reach the complete state");
                    done.complete(null);
                }, reason -> {
                    report.skip("building " + placement.key(), reason);
                    done.complete(null);
                });
            } catch (RuntimeException failure) {
                LOG.atWarning().withCause(failure).log("Test town could not build %s", placement.key());
                report.skip("building " + placement.key(), failure.getClass().getSimpleName() + ": " + failure.getMessage());
                done.complete(null);
            }
        });
        return done.orTimeout(BUILDING_TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(timeout -> {
            report.skip("building " + placement.key(), "it did not finish within " + BUILDING_TIMEOUT_SECONDS + " seconds");
            return null;
        });
    }

    private boolean insideTerritory(TownRecord town, PlotFootprintRecord footprint) {
        return manager.isInsideTerritory(town, footprint.getMinX(), footprint.getMinZ())
            && manager.isInsideTerritory(town, footprint.getMinX(), footprint.getMaxZ())
            && manager.isInsideTerritory(town, footprint.getMaxX(), footprint.getMinZ())
            && manager.isInsideTerritory(town, footprint.getMaxX(), footprint.getMaxZ());
    }

    private PlotInstance newestPlot(String constructionId) {
        PlotInstance newest = null;
        for (PlotInstance plot : town().getPlotInstances())
            if (constructionId.equals(plot.getConstructionId()) && !plotIds.containsValue(plot.getPlotId())) newest = plot;
        return newest;
    }

    PlotInstance plot(String key) {
        UUID id = plotIds.get(key);
        return id == null ? null : town().findPlotById(id);
    }

    /** The first complete plot whose building counts as {@code constructionId}. */
    PlotInstance completePlot(String constructionId) {
        return town().findCompletePlotWithConstruction(plugin.getConstructionCatalog(), constructionId);
    }

    // ---------------------------------------------------------------------------------------------- villagers

    /**
     * The starter town service gives each building one resident. A building with a second resident (the Bard at the
     * guild hall) and anyone it could not place are filled first by the repair the "fix residents" command runs, and
     * then, for villagers the town has too little food to admit, in the way the starter town service itself does.
     */
    private void settleVillagers() {
        TownRecord town = town();
        var store = world.getEntityStore().getStore();
        BuildingQuestResidentReconcileService.reconcileForTown(world, plugin, town, manager, store);
        for (VillagerDefinition definition : TestTownCheck.expectedVillagers(plugin, town)) {
            String role = definition.getNpcRoleId();
            if (TestTownCheck.resident(town, role) != null) continue;
            String label = "villager " + role;
            try {
                PlotInstance plot = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), definition.getWorkConstructionId().trim());
                String kind = InnVisitorShopPromotion.resolveResidentKind(definition);
                if (plot == null || kind == null || kind.isBlank()) { report.skip(label, "no building or resident kind for this villager"); continue; }
                Vector3d position = new Vector3d(plot.getSignX() + 0.5, groundY(), plot.getSignZ() + 0.5);
                for (Placement placement : placements.values())
                    if (plot.getPlotId().equals(plotIds.get(placement.key())))
                        position = new Vector3d(placement.roadPoint().x + 1.5, groundY(), placement.roadPoint().z + 0.5);
                var npc = TownVillagerSpawner.spawn(store, role, position,
                    new TownVillagerBinding(town.getTownId(), kind, plot.getPlotId(), plot.getPlotId()), "TEST_TOWN", "plotId=" + plot.getPlotId());
                var uuid = npc == null ? null : store.getComponent(npc, UUIDComponent.getComponentType());
                if (uuid == null) { report.skip(label, "the villager could not be spawned at " + position); continue; }
                String refused = WorkplacePlotAssignment.tryAssignWorker(world, plugin, town, manager, plot.getPlotId(), uuid.getUuid(), store);
                if (refused != null) {
                    // Past the town's food limit the normal admission says no. The starter town service binds anyway.
                    ResidentRegistryService.upsert(town, manager, role, kind, plot.getPlotId(), uuid.getUuid());
                    report.note(role + " was settled directly at their building; normal admission answered: " + refused);
                }
                town.addInnVisitorPoolExcludedRoleId(role);
            } catch (RuntimeException failure) {
                LOG.atWarning().withCause(failure).log("Test town could not settle %s", role);
                report.skip(label, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        manager.updateTown(town);
        TestTownCheck.Census census = TestTownCheck.census(plugin, town);
        report.add(TestTownReport.VILLAGERS, census.villagers());
        for (String missing : census.missingVillagers())
            report.skip("villager " + missing, "no resident could be bound to their building");
    }

    // ---------------------------------------------------------------------------------------------- handover

    private void handOver() {
        TownRecord town = town();
        if (owner.claimable()) {
            TownRelinquishService.relinquish(town, owner.uuid(), anyone -> false, anyone -> null);
            report.note("The town has no owner yet. The first operator to run the command in game becomes its owner.");
        }
        manager.updateTown(town);
        // Written on the next tick by the town save thread; nothing is read from disk on the world thread.
        TownSaveCoordinator.requestImmediateSave(manager);
        // The summary counts what the town really holds now, not what each stage set out to make.
        TestTownCheck.Census census = TestTownCheck.census(plugin, town);
        census.copyInto(report);
        report.note("Town Level " + census.townLevel() + " with " + census.population() + " residents, rank "
            + census.rank() + ", treasury " + town.getTreasuryGoldCoinCount() + " gold.");
    }

    /** What an existing test town holds, as the report a build would have printed. World thread only. */
    public static TestTownReport describe(AetherhavenPlugin plugin, World world, TownRecord town) {
        TestTownReport report = new TestTownReport();
        report.markExisting();
        TestTownCheck.Census census = TestTownCheck.census(plugin, town);
        census.copyInto(report);
        report.set(TestTownReport.PATHS, TestTownCheck.paths(world, plugin, town));
        report.note("Town Level " + census.townLevel() + " with " + census.population() + " residents, rank "
            + census.rank() + ", treasury " + town.getTreasuryGoldCoinCount() + " gold.");
        return report;
    }

    /**
     * What a claim did.
     *
     * @param becameOwner true when the town had no owner and now belongs to the player
     * @param message     a line to tell the player, or null when there is nothing to say
     */
    public record Claim(boolean becameOwner, String message) {}

    /** Gives an ownerless test town to the operator who has just arrived. World thread only. */
    public static Claim claim(AetherhavenPlugin plugin, World world, UUID player, String name) {
        TownManager manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        List<TownRecord> towns = manager.allTowns();
        if (towns.isEmpty()) return new Claim(false, null);
        TownRecord town = towns.getFirst();
        if (town.hasMemberOrOwner(player)) return new Claim(false, null);
        if (town.hasOwner()) {
            return new Claim(false, "This test town belongs to " + (town.getOwnerUsername() == null ? "another player" : town.getOwnerUsername())
                + ". You can look around, but its townsfolk only work while a member is here. Use reset to build your own.");
        }
        var refused = TownRelinquishService.tryClaim(town, player, name, manager.findTownForOwnerInWorld(player) != null);
        if (refused != null) return new Claim(false, "The test town could not be made yours: " + refused + ".");
        TestTownOutfitter.adoptOwner(world, town, player);
        manager.updateTown(town);
        return new Claim(true, "The test town is now yours.");
    }
}
