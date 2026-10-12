package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.assembly.AssemblyFootprintIndex;
import com.hexvane.aetherhaven.construction.assembly.AssemblyObstructionUtil;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3i;

/**
 * The ground rules of the festival square, on real blocks in a disposable world: building it asks for nothing under
 * the paving, a festival start lays that layout's foundation by itself (the Market pond with it), and a festival end
 * or a removal leaves whatever is under the paving alone. The square stands half on stone and half over open air, the
 * way it does on a slope. Uses the clearing footprint assembly builds, the real swap and the real teardown.
 *
 * <p>What it does not show: water flowing (block ticking is off here), villagers, or a player doing the building.
 */
final class AetherhavenFestivalSquareTestCommand extends AbstractCommandCollection {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Vector3i ANCHOR = new Vector3i(16, 128, 16);
    private static final int FLOOR_Y = ANCHOR.y + FestivalPrefabSize.FLOOR_LOCAL_Y;
    private static final String MARKET = "Festivals/Festival_Market.prefab.json";
    private static final String CARNIVAL = "Festivals/Festival_Carnival.prefab.json";
    private static final String GROUND = "Rock_Stone";
    private static final String FOUNDATION = "Soil_Dirt";

    AetherhavenFestivalSquareTestCommand() {
        super("festivalsquare", "Check the ground under the festival square in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
        addSubCommand(new Check());
    }

    private static final class Check extends AbstractAsyncCommand {
        Check() {
            super("worldcheck", "Build the square on a slope, run two festivals and remove it, watching the ground under it");
            setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if (!RUNNING.compareAndSet(false, true)) {
                report(ctx, "A festival square check is already running.");
                return CompletableFuture.completedFuture(null);
            }
            String name = "aetherhaven_festival_square_check_" + UUID.randomUUID().toString().replace("-", "");
            var universe = Universe.get();
            var path = universe.validateWorldPath(name);
            if (Files.exists(path)) {
                RUNNING.set(false);
                return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
            }
            var config = new WorldConfig();
            com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new VoidWorldGenProvider());
            config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
            config.setSavingPlayers(false);
            config.setCanSaveChunks(false);
            config.setSaveNewChunks(false);
            config.setSpawningNPC(false);
            config.setGameTimePaused(true);
            config.setBlockTicking(false);
            config.setDeleteOnRemove(true);
            config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name, path, config).thenCompose(world -> {
                Fixture fixture = new Fixture(world);
                return fixture.run().orTimeout(120, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                    if (error == null) {
                        report(ctx, "Festival square isolated world check: PASSED. " + String.join(" ", fixture.lines));
                    } else {
                        Throwable cause = error;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        report(ctx, "Festival square isolated world check: FAILED at " + fixture.stage + ": " + cause);
                    }
                    universe.removeWorld(name);
                });
            }).whenComplete((done, error) -> RUNNING.set(false));
        }
    }

    /** What sits in the five layers under the paving: block ids and whether each cell holds a fluid. */
    private record Ground(int[] blocks, boolean[] fluids) {
        boolean sameAs(Ground other) {
            return Arrays.equals(blocks, other.blocks) && Arrays.equals(fluids, other.fluids);
        }

        int filled() {
            int n = 0;
            for (int i = 0; i < blocks.length; i++) {
                if (blocks[i] != BlockType.EMPTY_ID || fluids[i]) {
                    n++;
                }
            }
            return n;
        }

        int count(int blockId) {
            int n = 0;
            for (int id : blocks) {
                if (id == blockId) {
                    n++;
                }
            }
            return n;
        }

        int wet() {
            int n = 0;
            for (boolean fluid : fluids) {
                if (fluid) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final class Fixture {
        private final World world;
        private final AetherhavenPlugin plugin = AetherhavenPlugin.get();
        private final List<String> lines = new ArrayList<>();
        private volatile String stage = "setup";
        private Store<EntityStore> store;
        private ConstructionDefinition definition;
        private IPrefabBuffer everyday;
        private TownRecord town;
        private PlotInstance plot;
        private Ground built;
        private Ground afterMarket;
        private Ground afterCarnival;

        Fixture(World world) {
            this.world = world;
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 3; y <= 5; y++) {
                        sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
                    }
                }
            }
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenRunAsync(this::layGround, world)
                .thenRunAsync(this::clearingAsksForNothingUnderThePaving, world)
                .thenCompose(done -> paste(everyday))
                .thenRunAsync(this::squareIsBuiltOnUntouchedGround, world)
                .thenCompose(done -> swap(definition.getPrefabPath(), MARKET))
                .thenRunAsync(this::marketFoundationIsLaid, world)
                .thenCompose(done -> swap(MARKET, definition.getPrefabPath()))
                .thenRunAsync(this::marketEndLeavesTheGroundAlone, world)
                .thenCompose(done -> swap(definition.getPrefabPath(), CARNIVAL))
                .thenRunAsync(this::carnivalFoundationReplacesTheMarkets, world)
                .thenCompose(done -> swap(CARNIVAL, definition.getPrefabPath()))
                .thenRunAsync(this::secondEndAndRemovalLeaveTheGroundAlone, world);
        }

        // ---- Steps ----

        /** A slope: stone under the west half of the square, open air under the east half. */
        private void layGround() {
            require(plugin != null, "Plugin missing");
            require(!PersistentWorldSupport.shouldPersistWorldData(world), "The fixture world must never be saved");
            require(world.getEntityStore() != null, "Fixture world has no entity store");
            store = world.getEntityStore().getStore();
            definition = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE);
            require(definition != null, "Festival square definition missing");
            everyday = buffer(definition.getPrefabPath());
            for (int x = 0; x <= ANCHOR.x; x++) {
                for (int z = 0; z <= 31; z++) {
                    for (int y = ANCHOR.y; y < FLOOR_Y; y++) {
                        require(ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, GROUND, 0), "Ground placement failed");
                    }
                }
            }
        }

        private void clearingAsksForNothingUnderThePaving() {
            stage = "clearing";
            for (Rotation yaw : Rotation.NORMAL) {
                PlotAssemblyJob job = clearingJob(yaw);
                long under = job.footprintCells().stream().filter(c -> c.y() < FestivalPrefabSize.FLOOR_LOCAL_Y).count();
                require(under == 0, under + " cells under the paving are in the clearing footprint at " + yaw);
                require(
                    !AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job),
                    "Building the square at " + yaw + " asks for the ground under the paving to be cleared"
                );
            }
            // Something standing where the paving goes still has to be cleared, as before.
            PlotAssemblyJob job = clearingJob(Rotation.None);
            require(ChunkSectionBlockUtil.setBlockByKey(world, 10, FLOOR_Y, 10, GROUND, 0), "Obstruction placement failed");
            require(AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job), "A block at paving height is not asked to be cleared");
            ChunkSectionBlockUtil.setBlockEmpty(world, 10, FLOOR_Y, 10, 0);
            require(ChunkSectionBlockUtil.setBlockByKey(world, 20, FLOOR_Y + 20, 20, GROUND, 0), "Obstruction placement failed");
            require(AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job), "A block above the paving is not asked to be cleared");
            ChunkSectionBlockUtil.setBlockEmpty(world, 20, FLOOR_Y + 20, 20, 0);
            require(!AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job), "Site should be clear again");
            lines.add("Building asks for no cell under the paving at any of four rotations, on stone and over open air.");
        }

        private void squareIsBuiltOnUntouchedGround() {
            stage = "build";
            built = ground();
            int stone = BlockType.getAssetMap().getIndex(GROUND);
            // Stone was laid up to the anchor column: the west half of the square, anchor column included.
            int stoneColumns = 1 - FestivalPrefabSize.LOCAL_MIN_X;
            int stoneCells = stoneColumns * FestivalPrefabSize.DEPTH_Z * FestivalPrefabSize.FLOOR_LOCAL_Y;
            require(built.count(stone) == stoneCells, "Stone under the paving after building: " + built.count(stone) + ", laid " + stoneCells);
            require(built.filled() == stoneCells, "Building put " + (built.filled() - stoneCells) + " cells under the paving");
            require(paving() == FestivalPrefabSize.WIDTH_X * FestivalPrefabSize.DEPTH_Z, "Paving has " + paving() + " blocks");

            UUID owner = UUID.randomUUID();
            town = new TownRecord(UUID.randomUUID(), owner, world.getName(), 48, 128, 48, 1, 1, 0);
            town.setDisplayName("Festival Square Fixture");
            plot = new PlotInstance(
                UUID.randomUUID(),
                definition.getId(),
                PlotInstanceState.COMPLETE,
                FestivalPrefabSize.footprintAt(ANCHOR, Rotation.None),
                ANCHOR.x,
                FLOOR_Y + 1,
                ANCHOR.z,
                0
            );
            plot.setPrefabWorldPlacement(ANCHOR.x, ANCHOR.y, ANCHOR.z, Rotation.None);
            town.getPlotInstances().add(plot);
            TownManager manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            manager.putTown(town);
            lines.add("Built: " + stoneCells + " stone cells and " + (built.blocks().length - stoneCells) + " open cells under the paving, all untouched.");
        }

        private void marketFoundationIsLaid() {
            stage = "first festival";
            Ground now = ground();
            List<PendingBlock> listed = underFloorCells(buffer(MARKET));
            require(listed.size() == now.blocks().length, "Market lists " + listed.size() + " cells under its floor");
            require(now.filled() == now.blocks().length, (now.blocks().length - now.filled()) + " cells under the paving are still empty");
            int wrong = 0;
            int water = 0;
            for (PendingBlock cell : listed) {
                int x = ANCHOR.x + cell.x();
                int y = ANCHOR.y + cell.y();
                int z = ANCHOR.z + cell.z();
                boolean solidInWorld = ChunkSectionBlockUtil.blockId(world, x, y, z) != BlockType.EMPTY_ID;
                boolean fluidInWorld = ConstructionPasteOps.worldHasFluidAt(world, x, y, z);
                if (solidInWorld != (cell.blockId() != 0) || fluidInWorld != (cell.fluidId() != 0)) {
                    wrong++;
                }
                if (cell.fluidId() != 0) {
                    water++;
                }
            }
            require(wrong == 0, wrong + " cells under the paving differ from the Market layout");
            require(water > 0 && now.wet() == water, "Pond water under the paving: " + now.wet() + ", layout lists " + water);
            int stone = BlockType.getAssetMap().getIndex(GROUND);
            require(now.count(stone) == 0, now.count(stone) + " stone cells were not replaced by the foundation");
            require(surfaceWater() > 0, "The Market pond has no water at paving level");
            lines.add("Market start: all " + listed.size() + " cells laid by the game, " + water + " of them pond water, stone and open air both replaced.");
        }

        private void marketEndLeavesTheGroundAlone() {
            stage = "festival end";
            afterMarket = ground();
            List<PendingBlock> listed = underFloorCells(buffer(MARKET));
            long water = listed.stream().filter(c -> c.fluidId() != 0).count();
            require(afterMarket.filled() == afterMarket.blocks().length, "Ending the festival left a cavity under the paving");
            require(afterMarket.wet() == water, "Ending the festival changed the water under the paving");
            require(paving() == FestivalPrefabSize.WIDTH_X * FestivalPrefabSize.DEPTH_Z, "Paving has a hole: " + paving() + " blocks");
            require(surfaceWater() == 0, "Pond water is left lying in the paving");
            lines.add("Market end: paving complete, nothing under it changed, the pond's " + water + " water cells stay shut in below.");
        }

        private void carnivalFoundationReplacesTheMarkets() {
            stage = "second festival";
            afterCarnival = ground();
            int dirt = BlockType.getAssetMap().getIndex(FOUNDATION);
            require(afterCarnival.count(dirt) == afterCarnival.blocks().length, "Carnival foundation: " + afterCarnival.count(dirt) + " dirt cells");
            require(afterCarnival.wet() == 0, afterCarnival.wet() + " Market water cells are still under the Carnival");
            lines.add("Carnival start: its " + afterCarnival.blocks().length + " dirt cells replace the Market fill, no water left.");
        }

        private void secondEndAndRemovalLeaveTheGroundAlone() {
            stage = "second festival end";
            require(ground().sameAs(afterCarnival), "Ending the second festival changed the ground under the paving");
            require(paving() == FestivalPrefabSize.WIDTH_X * FestivalPrefabSize.DEPTH_Z, "Paving has a hole: " + paving() + " blocks");
            stage = "removal";
            TownDissolutionService.clearPlotFromWorld(
                world, plugin, town, plot, store, AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin)
            );
            require(ground().sameAs(afterCarnival), "Removing the square dug under the paving");
            require(paving() == 0, "Removing the square left " + paving() + " paving blocks");
            lines.add("Removal: paving and everything above gone, the fill under it left as ground.");
        }

        // ---- Helpers ----

        private IPrefabBuffer buffer(String prefabPath) {
            IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPath);
            require(buffer != null, "Prefab did not open: " + prefabPath);
            return buffer;
        }

        /** The job assembly registers when the build starts, with the same clearing footprint. */
        private PlotAssemblyJob clearingJob(Rotation yaw) {
            var sequence = ConstructionPasteOps.buildSequence(everyday, yaw, null);
            List<PendingBlock> footprint =
                ConstructionPasteOps.withReservedFestivalAirCells(sequence.pendingBlocks(), sequence.prefabRotation(), definition);
            return new PlotAssemblyJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ANCHOR,
                yaw,
                footprint,
                AssemblyFootprintIndex.build(footprint),
                ConstructionPasteOps.withoutPureAirCells(footprint),
                sequence.prefabEntitiesInOrder(),
                everyday,
                sequence.prefabRotation(),
                0,
                1_000_000,
                false,
                definition.getId()
            );
        }

        private static List<PendingBlock> underFloorCells(IPrefabBuffer buffer) {
            List<PendingBlock> cells = new ArrayList<>();
            for (PendingBlock cell : ConstructionPasteOps.buildSequence(buffer, Rotation.None).pendingBlocks()) {
                if (cell.y() < FestivalPrefabSize.FLOOR_LOCAL_Y) {
                    cells.add(cell);
                }
            }
            return cells;
        }

        private CompletableFuture<Void> paste(IPrefabBuffer buffer) {
            var done = new CompletableFuture<Void>();
            ConstructionAnimator.start(plugin, world, ANCHOR, Rotation.None, true, false, buffer, store, 500_000, 1L, () -> done.complete(null));
            return done;
        }

        private CompletableFuture<Void> swap(String from, String to) {
            var done = new CompletableFuture<Void>();
            world.execute(() -> {
                stage = "swap to " + to;
                if (!FestivalPrefabSwapService.swap(world, plugin, town, plot, from, to, () -> done.complete(null))) {
                    done.completeExceptionally(new IllegalStateException("Swap did not start: " + to));
                }
            });
            return done;
        }

        private Ground ground() {
            int width = FestivalPrefabSize.WIDTH_X;
            int depth = FestivalPrefabSize.DEPTH_Z;
            int layers = FestivalPrefabSize.FLOOR_LOCAL_Y;
            int[] blocks = new int[width * depth * layers];
            boolean[] fluids = new boolean[blocks.length];
            int i = 0;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    for (int y = 0; y < layers; y++) {
                        int wx = ANCHOR.x + FestivalPrefabSize.LOCAL_MIN_X + x;
                        int wz = ANCHOR.z + FestivalPrefabSize.LOCAL_MIN_Z + z;
                        blocks[i] = ChunkSectionBlockUtil.blockId(world, wx, ANCHOR.y + y, wz);
                        fluids[i] = ConstructionPasteOps.worldHasFluidAt(world, wx, ANCHOR.y + y, wz);
                        i++;
                    }
                }
            }
            return new Ground(blocks, fluids);
        }

        private int paving() {
            int n = 0;
            for (int x = FestivalPrefabSize.LOCAL_MIN_X; x <= FestivalPrefabSize.LOCAL_MAX_X; x++) {
                for (int z = FestivalPrefabSize.LOCAL_MIN_Z; z <= FestivalPrefabSize.LOCAL_MAX_Z; z++) {
                    if (ChunkSectionBlockUtil.blockId(world, ANCHOR.x + x, FLOOR_Y, ANCHOR.z + z) != BlockType.EMPTY_ID) {
                        n++;
                    }
                }
            }
            return n;
        }

        private int surfaceWater() {
            int n = 0;
            for (int x = FestivalPrefabSize.LOCAL_MIN_X; x <= FestivalPrefabSize.LOCAL_MAX_X; x++) {
                for (int z = FestivalPrefabSize.LOCAL_MIN_Z; z <= FestivalPrefabSize.LOCAL_MAX_Z; z++) {
                    if (ConstructionPasteOps.worldHasFluidAt(world, ANCHOR.x + x, FLOOR_Y, ANCHOR.z + z)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    private static void require(boolean yes, String issue) {
        if (!yes) {
            throw new IllegalStateException(issue);
        }
    }

    private static void report(CommandContext ctx, String message) {
        ctx.sendMessage(Message.raw(message));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
