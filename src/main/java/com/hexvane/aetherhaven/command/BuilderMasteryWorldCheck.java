package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.assembly.AssemblyFootprintIndex;
import com.hexvane.aetherhaven.construction.assembly.AssemblyPassiveBoostRegistry;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.BuriedVillagerRescueSystem;
import com.hexvane.aetherhaven.construction.assembly.SiteBodies;
import com.hexvane.aetherhaven.construction.assembly.SiteCells;
import com.hexvane.aetherhaven.construction.assembly.SiteRefuge;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyFrontierRuntime;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPhase;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.mastery.BuilderMastery;
import com.hexvane.aetherhaven.mastery.CoreVillagerMastery;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkplace;
import com.hexvane.aetherhaven.workplace.NativeBuildingWork;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * The core Builder's Mastery on real building sites, in a disposable world. Four Houses are started the way the
 * building check starts one, and the real passive site tick ({@link PlotAssemblyService#tickPassive}) builds them
 * with real blocks under the world's own clock: one with a level 1 Builder, one with a level 5 Builder, one with
 * nobody there, and one with a level 5 Builder and a townsfolk builder together.
 *
 * <p>What it does not show: the Builder walking to the site and swinging his hammer. No villager is spawned (they
 * need a town member in the world), so "the Builder is at the site" is the one call his own tick makes while he
 * stands there. Each House stops a few pieces short, so the finishing pass (furniture, residents) never runs here.
 */
final class BuilderMasteryWorldCheck extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** How often a site with nobody at it steps in this check, in the world's clock. The Builder's steps come four times as often. */
    private static final long SLOT_MS = 400;

    BuilderMasteryWorldCheck() {
        super("buildercheck", "Check the Builder's Mastery pace and XP on real building sites in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Consumer<String> report = text -> {
            ctx.sendMessage(Message.raw(text));
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
        };
        if (!RUNNING.compareAndSet(false, true)) {
            report.accept("A Builder mastery check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_builder_check_" + UUID.randomUUID().toString().replace("-", "");
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
        // The sites are built one after another over about a minute with no player near. Without this the engine
        // unloads the waiting sites' chunks, and a site in an unloaded chunk is never built (in a town as well).
        config.setCanUnloadChunks(false);
        config.setSpawningNPC(false);
        config.setGameTimePaused(true);
        config.setBlockTicking(false);
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        report.accept("Starting isolated Builder mastery check. No town of yours is used. It takes about three minutes.");
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            Fixture fixture = new Fixture(world);
            return fixture.run().orTimeout(480, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                if (error == null) {
                    report.accept("Builder mastery world check: PASSED. " + String.join(" ", fixture.lines));
                } else {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    report.accept("Builder mastery world check: FAILED at " + fixture.stage + ": " + cause
                        + (fixture.lines.isEmpty() ? "" : " Before that: " + String.join(" ", fixture.lines)));
                }
                universe.removeWorld(name);
            });
        }).whenComplete((done, error) -> RUNNING.set(false));
    }

    private static final class Fixture {
        private final World world;
        private final Store<EntityStore> store;
        private final AetherhavenPlugin plugin = AetherhavenPlugin.get();
        final List<String> lines = new ArrayList<>();
        volatile String stage = "setup";
        private TownManager manager;
        private ConstructionDefinition definition;
        private IPrefabBuffer prefab;
        private Site first, fifth, empty, crew;
        private Crowded crowdedFirst, crowdedTenth;
        private ApprenticeWorkplace yard;
        private int townsfolkPieces;

        Fixture(World world) {
            this.world = world;
            this.store = world.getEntityStore().getStore();
        }

        /** One started House, the town it belongs to, and what happened at it. */
        private final class Site {
            final String name;
            final boolean builderHere;
            final UUID owner = UUID.randomUUID();
            final TownRecord town;
            final PlotInstance plot;
            final PlotAssemblyJob job;
            final List<PendingBlock> pending;
            /** Whether placing this piece changes the world. Furniture and the far halves of large blocks do not: the finishing pass sets them. */
            final boolean[] writes;
            final TreeMap<Integer, Integer> stepSizes = new TreeMap<>();
            final List<Long> gaps = new ArrayList<>();
            final long paidAtStart;
            int steps, stepBlocks, stepWrites;
            long lastStepMs = -1;

            Site(String name, Vector3i anchor, int builderLevel, boolean builderHere) {
                this.name = name;
                this.builderHere = builderHere;
                town = new TownRecord(UUID.randomUUID(), owner, world.getName(), 16, 128, 16, 1, 1, 0);
                town.setDisplayName("Builder check " + name);
                // He reaches his level the way building pays it: through XP, level by level.
                var mastery = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
                long needed = 0;
                for (int level = 1; level < builderLevel; level++) needed += 100 + 50L * (level - 1);
                require(mastery.earnXp(needed) == needed && mastery.level() == builderLevel && mastery.xpInLevel() == 0,
                    needed + " XP did not make a level " + builderLevel + " Builder");
                var sequence = ConstructionPasteOps.buildSequence(prefab, Rotation.None);
                pending = ConstructionPasteOps.withoutPureAirCells(sequence.pendingBlocks());
                plot = new PlotInstance(UUID.randomUUID(), definition.getId(), PlotInstanceState.ASSEMBLING,
                    new PlotFootprintRecord(-31, 96, -31, 63, 191, 63), anchor.x, anchor.y, anchor.z, 0);
                plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, Rotation.None);
                plot.resetAssemblyPlacementProgress();
                plot.setAssemblyOwnerUuid(owner);
                town.getPlotInstances().add(plot);
                job = new PlotAssemblyJob(plot.getPlotId(), owner, anchor, Rotation.None, sequence.pendingBlocks(),
                    AssemblyFootprintIndex.build(sequence.pendingBlocks()), pending, sequence.prefabEntitiesInOrder(), prefab,
                    sequence.prefabRotation(), 0, SLOT_MS, false, definition.getId());
                writes = new boolean[pending.size()];
                for (int i = 0; i < writes.length; i++)
                    writes[i] = !ConstructionPasteOps.isAssemblyPlacementNoOp(world, anchor, pending.get(i), BlockType.getAssetMap());
                paidAtStart = paidBlocks(town);
            }

            /** The site becomes a running project, exactly one at a time so each is measured alone. */
            void open() {
                stage = name;
                manager.putTown(town);
                AssemblyWorldRegistry.put(world, plot.getPlotId(), town.getTownId(), job, PlotAssemblyPhase.PLACING,
                    PlotAssemblyFrontierRuntime.create(pending, plot, null), null, null);
            }

            void close() {
                AssemblyWorldRegistry.remove(world, plot.getPlotId());
                AssemblyPassiveBoostRegistry.setBoost(world, plot.getPlotId(), 0);
                town.getPlotInstances().remove(plot);
                manager.removeTown(town.getTownId());
            }

            IntOpenHashSet placed() {
                var set = new IntOpenHashSet();
                plot.fillAssemblyPlacedSet(set, pending.size());
                return set;
            }

            /** One look at the site by the real site tick. @return the pieces it placed this time, usually none: it is not due. */
            int tick() {
                // What the Builder's own tick does every tick he stands at a site.
                if (builderHere)
                    AssemblyPassiveBoostRegistry.setBoost(world, plot.getPlotId(), BuilderConstructionAssistState.BUILDER_PASSIVE_BOOST);
                var before = placed();
                long now = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
                PlotAssemblyService.tickPassive(world, plugin, store, store);
                require(plot.getState() == PlotInstanceState.ASSEMBLING, "The fixture finished a whole House");
                var after = placed();
                int placed = after.size() - before.size();
                require(placed >= 0 && after.size() == plot.getAssemblyPlacedBlockCount(), "Saved progress does not add up");
                if (placed == 0) return 0;
                steps++;
                stepBlocks += placed;
                stepSizes.merge(placed, 1, Integer::sum);
                if (lastStepMs >= 0) gaps.add(now - lastStepMs);
                lastStepMs = now;
                for (var it = after.iterator(); it.hasNext();) {
                    int index = it.nextInt();
                    if (!before.contains(index) && writes[index]) stepWrites++;
                }
                return placed;
            }

            long medianGap() {
                require(!gaps.isEmpty(), "No two steps happened at " + name);
                var sorted = new ArrayList<>(gaps);
                sorted.sort(null);
                return sorted.get(sorted.size() / 2);
            }

            long paid() {
                return paidBlocks(town) - paidAtStart;
            }
        }

        /** Blocks the Builder has been paid for so far: five for each XP, plus those kept toward the next one. */
        private static long paidBlocks(TownRecord town) {
            var mastery = CoreVillagerMasteryService.progress(town, BuilderMastery.KIND);
            long xp = mastery.xpInLevel();
            for (int level = 1; level < mastery.level(); level++) xp += 100 + 50L * (level - 1);
            return xp * BuilderMastery.BLOCKS_PER_XP + mastery.workTowardXp();
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 3; y <= 5; y++)
                sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
            int[] target = new int[1];
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> onWorld(() -> {
                    setup();
                    // Stop at least ten pieces short of a whole House, on a count both one and five divide.
                    target[0] = (first.pending.size() - 10) / 10 * 10;
                    require(target[0] >= 100, "The House is too small for this check");
                    first.open();
                }))
                .thenCompose(ignored -> pump("the level 1 Builder never finished", () -> first.tick() >= 0 && first.plot.getAssemblyPlacedBlockCount() >= target[0], 120))
                .thenCompose(ignored -> onWorld(() -> {
                    first.close();
                    judgeBuilderSite(first, 1, target[0]);
                    fifth.open();
                }))
                .thenCompose(ignored -> pump("the level 5 Builder never finished", () -> fifth.tick() >= 0 && fifth.plot.getAssemblyPlacedBlockCount() >= target[0], 60))
                .thenCompose(ignored -> onWorld(() -> {
                    fifth.close();
                    judgeBuilderSite(fifth, 5, target[0]);
                    // Both are held to the step time above. Between them only a loose bound: the server shares its machine.
                    require(Math.abs(first.medianGap() - fifth.medianGap()) <= 150,
                        "Steps came every " + first.medianGap() + " ms at level 1 but every " + fifth.medianGap() + " ms at level 5");
                    empty.open();
                }))
                .thenCompose(ignored -> pump("the site with nobody at it never stepped", () -> empty.tick() >= 0 && empty.steps >= 6, 60))
                .thenCompose(ignored -> onWorld(() -> {
                    empty.close();
                    require(empty.stepSizes.size() == 1 && empty.stepSizes.firstKey() == 1, "A site with nobody at it placed " + empty.stepSizes + " a step");
                    require(empty.medianGap() >= SLOT_MS, "A site with nobody at it stepped every " + empty.medianGap() + " ms, sooner than its " + SLOT_MS);
                    require(empty.paid() == 0, "The Builder was paid " + empty.paid() + " blocks for a site he was not at");
                    lines.add("Nobody at the site (the town's Builder is level 5): 1 block a step, a step every " + empty.medianGap()
                        + " ms, " + empty.steps + " steps watched, Builder Mastery unchanged.");
                    crew.open();
                    yard = new ApprenticeWorkplace("Fixture yard", TownsfolkWorkType.BUILDING, new WorkplaceArea(4, 128, 4, 8, 8));
                    require(yard.building().add(crew.plot.getPlotId(), crew.owner) == null, "Project queue failed");
                    require(NativeBuildingWork.issue(world, crew.town, yard.building().list().getFirst()) == null, "The yard cannot work on the started project");
                }))
                .thenCompose(ignored -> pump("the Builder and the townsfolk builder never finished", () -> {
                    if (crew.tick() > 0) townsfolkPlaces();
                    return crew.steps >= 8;
                }, 60))
                .thenCompose(ignored -> onWorld(() -> {
                    crew.close();
                    require(crew.stepSizes.size() == 1 && crew.stepSizes.firstKey() == 5, "Beside a townsfolk builder the Builder placed " + crew.stepSizes + " a step");
                    int total = crew.plot.getAssemblyPlacedBlockCount();
                    require(total == crew.stepBlocks + townsfolkPieces, "The site counts " + total + " pieces, the Builder placed " + crew.stepBlocks + " and the townsfolk " + townsfolkPieces);
                    require(crew.placed().size() == total, "A piece is counted twice");
                    require(yard.building().contributions() == townsfolkPieces, "The yard was credited " + yard.building().contributions() + " pieces for " + townsfolkPieces);
                    require(crew.paid() == crew.stepWrites, "The Builder was paid for " + crew.paid() + " blocks and placed " + crew.stepWrites);
                    lines.add("Level 5 Builder and a townsfolk builder on one site: " + crew.steps + " steps of 5 and " + townsfolkPieces
                        + " single pieces made " + total + ", each counted once. The yard was credited " + yard.building().contributions()
                        + ", the Builder was paid for his " + crew.paid() + " and for none of the townsfolk's.");
                }))
                .thenCompose(ignored -> crowded(crowdedFirst))
                .thenCompose(ignored -> crowded(crowdedTenth))
                .thenCompose(ignored -> pasteOverVillager())
                .thenCompose(ignored -> alreadyBuried());
        }

        /**
         * Villagers who are already inside a block. The rescue that runs on every server must move this mod's villager
         * who is at a building plot of their own town, and must leave alone both one of this mod's villagers buried
         * outside any plot and a creature that is not this mod's, buried at the plot.
         */
        private CompletableFuture<Void> alreadyBuried() {
            var anchor = new Vector3i(16, 128, 48);
            var ours = new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            var elsewhere = new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            var creature = new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), world.getName(), 16, 128, 16, 1, 1, 0);
            long[] movedBefore = new long[1];
            Vector3d[] started = new Vector3d[3];
            String[] creatureKind = new String[1];
            return onWorld(() -> {
                stage = "already buried";
                platform(anchor);
                town.setDisplayName("Builder check rescue");
                manager.putTown(town);
                // A finished building plot of that town, seven cells square. Its outline is all the rescue goes by.
                town.getPlotInstances().add(new PlotInstance(UUID.randomUUID(), definition.getId(), PlotInstanceState.COMPLETE,
                    new PlotFootprintRecord(anchor.x - 3, 120, anchor.z - 3, anchor.x + 3, 140, anchor.z + 3), anchor.x, anchor.y, anchor.z, 0));
                started[0] = new Vector3d(anchor.x + .5, anchor.y + .02, anchor.z + .5);
                started[1] = new Vector3d(anchor.x + 6.5, anchor.y + .02, anchor.z + .5);
                started[2] = new Vector3d(anchor.x - 1.5, anchor.y + .02, anchor.z + 2.5);
                ours.set(villager(started[0], town));
                elsewhere.set(villager(started[1], town));
                // Something that is not this mod's villager: a creature of the game, or failing that an unbound NPC.
                var cow = NPCPlugin.get().spawnNPC(store, "Cow", null, started[2], Rotation3f.ZERO);
                creatureKind[0] = cow != null ? "a cow" : "an NPC that is not one of this mod's villagers";
                var other = cow != null ? cow : NPCPlugin.get().spawnNPC(store, AetherhavenConstants.NPC_BUILDER, null, started[2], Rotation3f.ZERO);
                require(other != null, "No creature could be spawned");
                creature.set(other.first());
                store.putComponent(creature.get(), Frozen.getComponentType(), Frozen.get());
                movedBefore[0] = SiteRefuge.moved();
                // A block through the legs and one through the chest of each.
                for (var at : started) for (int up = 0; up <= 1; up++)
                    require(ChunkSectionBlockUtil.setBlockByKey(world, (int) Math.floor(at.x), anchor.y + up, (int) Math.floor(at.z), "Rock_Stone", 0), "Could not bury a villager");
                require(isBuried(ours.get()) && isBuried(elsewhere.get()), "The villagers were not buried by the blocks placed in them");
            })
                .thenCompose(ignored -> pump("the buried villager at the plot was never moved out", () -> ours.get().isValid() && !isBuried(ours.get()), 30))
                // Longer than the looks the rescue needs, so the other two have had every chance to be moved wrongly.
                .thenCompose(ignored -> later(4_000))
                .thenCompose(ignored -> onWorld(() -> {
                    var feet = store.getComponent(ours.get(), TransformComponent.getComponentType()).getPosition();
                    require(VillagerBlockUtil.isNpcStandColumn(world, (int) Math.floor(feet.x), (int) Math.floor(feet.y + .001), (int) Math.floor(feet.z)),
                        "The rescued villager was put where nobody can stand");
                    require(SiteRefuge.moved() - movedBefore[0] == 1, "One villager was to be moved, " + (SiteRefuge.moved() - movedBefore[0]) + " were");
                    require(elsewhere.get().isValid() && isBuried(elsewhere.get())
                        && store.getComponent(elsewhere.get(), TransformComponent.getComponentType()).getPosition().distance(started[1]) < .05,
                        "A villager buried outside every building plot was moved");
                    boolean creatureAlive = creature.get().isValid();
                    require(!creatureAlive || store.getComponent(creature.get(), TransformComponent.getComponentType()).getPosition().distance(started[2]) < .05,
                        "A creature that is not this mod's villager was moved");
                    for (var ref : List.of(ours.get(), elsewhere.get(), creature.get())) if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
                    manager.removeTown(town.getTownId());
                    lines.add("Already buried: a villager with a block through legs and chest at a finished plot of their town was moved out by the rescue to "
                        + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", feet.x, feet.y, feet.z) + " and stands clear; one of the same town buried outside every plot was left where they were, and so was "
                        + creatureKind[0] + " buried at the plot" + (creatureAlive ? "." : " (it did not outlive the check, and was never moved)."));
                }));
        }

        private Ref<EntityStore> villager(Vector3d feet, TownRecord town) {
            var spawned = NPCPlugin.get().spawnNPC(store, AetherhavenConstants.NPC_BUILDER, null, feet, Rotation3f.ZERO);
            require(spawned != null, "A Builder could not be spawned");
            store.putComponent(spawned.first(), TownVillagerBinding.getComponentType(), new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_BUILDER, null));
            store.putComponent(spawned.first(), Frozen.getComponentType(), Frozen.get());
            return spawned.first();
        }

        private boolean isBuried(Ref<EntityStore> ref) {
            var feet = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
            return BuriedVillagerRescueSystem.buried(feet, SiteBodies.localBox(store, ref), (x, y, z) -> VillagerBlockUtil.isInsideSolidBlock(world, x, y, z));
        }

        // ---- Sites with villagers standing in cells that are due to be filled ----

        /**
         * A House built around three real Builder NPCs who stand where pieces are due: on the first layer, on top of a
         * placed piece, and in the doorway. They are frozen so they stay put; their size is the engine's own bounding
         * box. After every step each block that step placed is held against each villager's body.
         */
        private final class Crowded {
            final Site site;
            final int level;
            final Vector3i anchor;
            final List<Ref<EntityStore>> villagers = new ArrayList<>();
            /** For each villager, the piece they stand in, or -1 when that piece is set by the finishing pass. */
            final List<Integer> standingIn = new ArrayList<>();
            int floorIndex = -1, raisedIndex = -1, doorIndex = -1;
            long deferredAtStart, movedAtStart, movedWhenPiecesWaited = -1;
            int stepsChecked, blocksChecked, placedAfterMove;
            String bodySize = "";

            Crowded(String name, Vector3i anchor, int level) {
                this.anchor = anchor;
                this.level = level;
                platform(anchor);
                site = new Site(name, anchor, level, true);
            }

            void open() {
                site.open();
                deferredAtStart = SiteRefuge.deferred();
                movedAtStart = SiteRefuge.moved();
                var types = BlockType.getAssetMap();
                var pending = site.pending;
                Map<Long, Integer> at = new HashMap<>();
                int lowest = Integer.MAX_VALUE;
                for (int i = 0; i < pending.size(); i++) {
                    at.put(SiteCells.key(pending.get(i).x(), pending.get(i).y(), pending.get(i).z()), i);
                    lowest = Math.min(lowest, pending.get(i).y());
                }
                // The doorway: the door piece, and the floor piece under it.
                for (int i = 0; i < pending.size() && doorIndex < 0; i++) {
                    var type = types.getAsset(pending.get(i).blockId());
                    if (type != null && type.getId().contains("Door") && pending.get(i).filler() == FillerBlockUtil.NO_FILLER) doorIndex = i;
                }
                require(doorIndex >= 0, "The House has no door piece");
                var door = pending.get(doorIndex);
                Integer underDoor = at.get(SiteCells.key(door.x(), door.y() - 1, door.z()));
                require(underDoor != null && fullBlock(underDoor), "Nothing solid is under the House's door");
                // On a placed piece: a wall piece one above the lowest layer with a full block under it, away from the door.
                int underRaised = -1;
                for (int i = 0; i < pending.size() && raisedIndex < 0; i++) {
                    var piece = pending.get(i);
                    Integer under = at.get(SiteCells.key(piece.x(), piece.y() - 1, piece.z()));
                    if (piece.y() == lowest + 1 && fullBlock(i) && under != null && fullBlock(under) && apart(piece, door)) {
                        raisedIndex = i;
                        underRaised = under;
                    }
                }
                require(raisedIndex >= 0, "The House has no wall piece standing on a full floor piece");
                // On the first layer: a floor piece away from both.
                for (int i = 0; i < pending.size() && floorIndex < 0; i++) {
                    var piece = pending.get(i);
                    if (piece.y() == lowest && fullBlock(i) && apart(piece, door) && apart(piece, pending.get(raisedIndex))) floorIndex = i;
                }
                require(floorIndex >= 0, "The House has no floor piece away from the door");
                // The two pieces villagers stand on are placed first, through the same call every placement uses.
                for (int under : new int[] {underRaised, underDoor}) {
                    var outcome = PlotAssemblyService.advancePlacementAtIndex(world, plugin, store, site.town, site.plot, site.job, under, false, null, true, false);
                    require(outcome.progressed() && outcome.wroteBlock(), "Could not place the piece a villager is to stand on");
                }
                spawn(floorIndex, 0);
                spawn(raisedIndex, 0);
                // The door is set by the finishing pass in some builds of the prefab; then nothing is due in the doorway.
                spawn(site.writes[doorIndex] ? doorIndex : -1, doorIndex);
            }

            private boolean fullBlock(int index) {
                var box = site.writes[index] ? SiteBodies.pieceBox(site.pending.get(index), BlockType.getAssetMap()) : null;
                return box != null && box.isUnitBox();
            }

            private boolean apart(PendingBlock a, PendingBlock b) {
                return Math.abs(a.x() - b.x()) >= 2 || Math.abs(a.z() - b.z()) >= 2;
            }

            /** A villager with their feet in the cell of {@code cellIndex} (or of {@code where} when the piece is not due). */
            private void spawn(int pieceIndex, int where) {
                var cell = site.pending.get(pieceIndex >= 0 ? pieceIndex : where);
                var feet = new Vector3d(anchor.x + cell.x() + .5, anchor.y + cell.y() + .02, anchor.z + cell.z() + .5);
                var spawned = NPCPlugin.get().spawnNPC(store, AetherhavenConstants.NPC_BUILDER, null, feet, Rotation3f.ZERO);
                require(spawned != null, "A Builder could not be spawned");
                Ref<EntityStore> ref = spawned.first();
                store.putComponent(ref, TownVillagerBinding.getComponentType(), new TownVillagerBinding(site.town.getTownId(), TownVillagerBinding.KIND_BUILDER, null));
                store.putComponent(ref, Frozen.getComponentType(), Frozen.get());
                villagers.add(ref);
                standingIn.add(pieceIndex);
            }

            List<SiteBodies.Body> bodies() {
                List<SiteBodies.Body> out = new ArrayList<>();
                for (var ref : villagers) {
                    require(ref.isValid(), "A villager of the check is gone");
                    var body = SiteBodies.of(store, ref, false);
                    require(body != null, "A villager of the check has no position");
                    out.add(body);
                }
                return out;
            }

            /** Every villager is seen by the site's own look around, and stands in the piece they were put in. */
            boolean inPlace() {
                var seen = SiteBodies.near(store, anchor.x + .5, anchor.y + 4, anchor.z + .5, 16);
                int found = 0;
                for (var ref : villagers) for (var body : seen) if (ref.equals(body.ref())) found++;
                if (found != villagers.size()) return false;
                var bodies = bodies();
                var types = BlockType.getAssetMap();
                for (int v = 0; v < villagers.size(); v++) {
                    int index = standingIn.get(v);
                    if (index < 0) continue;
                    var piece = site.pending.get(index);
                    var box = SiteBodies.pieceBox(piece, types);
                    require(box != null && bodies.get(v).overlapsBlock(anchor.x + piece.x(), anchor.y + piece.y(), anchor.z + piece.z(), box),
                        "A villager was not put inside the piece they were meant to stand in");
                }
                var local = SiteBodies.localBox(store, villagers.getFirst());
                bodySize = String.format(java.util.Locale.ROOT, "%.2f wide and %.2f tall", local.max.x - local.min.x, local.max.y - local.min.y);
                return true;
            }

            /** One look by the real site tick, then every block it placed is held against every villager. */
            boolean tick() {
                var before = site.placed();
                long movedBefore = SiteRefuge.moved();
                int placed = site.tick();
                if (placed > 0) {
                    var after = site.placed();
                    var bodies = bodies();
                    var types = BlockType.getAssetMap();
                    for (var it = after.iterator(); it.hasNext();) {
                        int index = it.nextInt();
                        if (before.contains(index) || !site.writes[index]) continue;
                        var piece = site.pending.get(index);
                        var box = SiteBodies.pieceBox(piece, types);
                        if (box == null) continue;
                        blocksChecked++;
                        var hit = SiteBodies.blocker(bodies, anchor.x + piece.x(), anchor.y + piece.y(), anchor.z + piece.z(), box);
                        require(hit == null, "A block was placed into a villager at " + (anchor.x + piece.x()) + " " + (anchor.y + piece.y()) + " " + (anchor.z + piece.z()));
                    }
                    stepsChecked++;
                    if (movedWhenPiecesWaited >= 0) placedAfterMove += placed;
                }
                if (movedWhenPiecesWaited < 0 && SiteRefuge.moved() > movedBefore) movedWhenPiecesWaited = SiteRefuge.moved() - movedAtStart;
                boolean done = site.plot.getAssemblyPlacedBlockCount() >= site.pending.size();
                // Every piece is placed. The finishing pass (furniture, residents) is not run in this world.
                if (done) site.plot.setState(PlotInstanceState.COMPLETE);
                return done;
            }

            void judge() {
                site.close();
                long deferred = SiteRefuge.deferred() - deferredAtStart, moved = SiteRefuge.moved() - movedAtStart;
                int due = 0;
                for (int index : standingIn) if (index >= 0) due++;
                require(site.plot.getAssemblyPlacedBlockCount() == site.pending.size(), "Not every piece was placed");
                require(site.stepBlocks + 2 == site.pending.size(), "The steps placed " + site.stepBlocks + " pieces, two were placed by hand, the House has " + site.pending.size());
                require(site.stepSizes.lastKey() == level, "The largest step placed " + site.stepSizes.lastKey() + " at level " + level);
                int small = 0;
                for (var size : site.stepSizes.entrySet()) if (size.getKey() != level) small += size.getValue();
                require(small <= 4, "Too many steps fell short of " + level + " a step: " + site.stepSizes);
                // A Builder at the top level earns nothing more, so there is nothing to compare for him.
                boolean earning = level < CoreVillagerMastery.MAX_LEVEL;
                require(!earning || site.paid() == site.stepWrites, "The Builder was paid for " + site.paid() + " blocks, the steps placed " + site.stepWrites);
                require(earning || site.paid() == 0, "A level " + level + " Builder was paid");
                require(deferred > 0, "No piece ever waited for a villager");
                require(moved >= due, due + " villagers stood in due pieces, " + moved + " were moved");
                var bodies = bodies();
                for (int v = 0; v < villagers.size(); v++) {
                    var ref = villagers.get(v);
                    var feet = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
                    var local = SiteBodies.localBox(store, ref);
                    require(!BuriedVillagerRescueSystem.buried(feet, local, (x, y, z) -> VillagerBlockUtil.isInsideSolidBlock(world, x, y, z)),
                        "A villager ended inside a block at " + feet.x + " " + feet.y + " " + feet.z);
                    require(VillagerBlockUtil.isNpcStandColumn(world, (int) Math.floor(feet.x), (int) Math.floor(feet.y + .001), (int) Math.floor(feet.z)),
                        "A villager ended where nobody can stand, at " + feet.x + " " + feet.y + " " + feet.z);
                }
                for (var ref : villagers) if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
                lines.add("House built around " + villagers.size() + " villagers (bodies " + bodySize + ", from the engine) standing on the first layer, on a placed piece and in the doorway, level "
                    + level + " Builder: all " + site.pending.size() + " pieces placed, steps " + site.stepSizes + " (blocks=steps), " + blocksChecked + " placed blocks in "
                    + stepsChecked + " steps each held against every villager at the step it was placed and none was inside one; a piece was left for later "
                    + deferred + " times, " + moved + " villagers were moved after the wait and the last " + placedAfterMove
                    + " pieces went in behind them; none is inside a block at the end; "
                    + (earning ? "Builder paid for exactly the " + site.stepWrites + " blocks the steps placed." : "at the top level he earns nothing more."));
            }
        }

        /** Stone under and around a site, so villagers have ground to stand on and somewhere to be moved to. */
        private void platform(Vector3i anchor) {
            for (int x = anchor.x - 8; x <= anchor.x + 8; x++) for (int z = anchor.z - 8; z <= anchor.z + 8; z++)
                require(ChunkSectionBlockUtil.setBlockByKey(world, x, anchor.y - 1, z, "Rock_Stone", 0), "Platform placement failed");
        }

        private CompletableFuture<Void> crowded(Crowded crowd) {
            return onWorld(crowd::open)
                .thenCompose(ignored -> pump("the villagers of " + crowd.site.name + " never showed up where they were put", crowd::inPlace, 30))
                .thenCompose(ignored -> pump("the House around villagers never finished at level " + crowd.level, crowd::tick, 150))
                // The moves land a tick or two after they are asked for.
                .thenCompose(ignored -> later(800))
                .thenCompose(ignored -> onWorld(crowd::judge));
        }

        /** A whole building pasted at once, as a rebuild or a move does, over a villager standing where a wall goes. */
        private CompletableFuture<Void> pasteOverVillager() {
            var anchor = new Vector3i(-16, 128, 48);
            var who = new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            long[] movedBefore = new long[1];
            var pasted = new CompletableFuture<Void>();
            // A town with no plots at all: the rescue for buried villagers has no business with its villager, so
            // whatever moves him here is the paste. (A villager of a town the world does not know is not kept.)
            var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), world.getName(), 16, 128, 16, 1, 1, 0);
            return onWorld(() -> {
                stage = "paste";
                platform(anchor);
                var cell = crowdedFirst.site.pending.get(crowdedFirst.floorIndex);
                var feet = new Vector3d(anchor.x + cell.x() + .5, anchor.y + cell.y() + .02, anchor.z + cell.z() + .5);
                var spawned = NPCPlugin.get().spawnNPC(store, AetherhavenConstants.NPC_BUILDER, null, feet, Rotation3f.ZERO);
                require(spawned != null, "A Builder could not be spawned");
                who.set(spawned.first());
                town.setDisplayName("Builder check paste");
                manager.putTown(town);
                store.putComponent(who.get(), TownVillagerBinding.getComponentType(), new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_BUILDER, null));
                store.putComponent(who.get(), Frozen.getComponentType(), Frozen.get());
            })
                .thenCompose(ignored -> pump("the villager for the paste never showed up", () -> {
                    for (var body : SiteBodies.near(store, anchor.x + .5, anchor.y + 2, anchor.z + .5, 12)) if (who.get().equals(body.ref())) return true;
                    return false;
                }, 30))
                .thenCompose(ignored -> onWorld(() -> {
                    movedBefore[0] = SiteRefuge.moved();
                    var path = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
                    ConstructionAnimator.start(plugin, world, anchor, Rotation.None, true, false, PrefabBufferUtil.getCached(path), store, 500_000, 1L, () -> pasted.complete(null));
                }))
                .thenCompose(ignored -> pasted.orTimeout(60, TimeUnit.SECONDS))
                .thenCompose(ignored -> later(800))
                .thenCompose(ignored -> onWorld(() -> {
                    require(who.get().isValid(), "The villager under the paste is gone");
                    var feet = store.getComponent(who.get(), TransformComponent.getComponentType()).getPosition();
                    var local = SiteBodies.localBox(store, who.get());
                    require(SiteRefuge.moved() - movedBefore[0] == 1, "A paste over one villager moved " + (SiteRefuge.moved() - movedBefore[0]));
                    require(!BuriedVillagerRescueSystem.buried(feet, local, (x, y, z) -> VillagerBlockUtil.isInsideSolidBlock(world, x, y, z)),
                        "The paste left the villager inside a block at " + feet.x + " " + feet.y + " " + feet.z);
                    require(VillagerBlockUtil.isNpcStandColumn(world, (int) Math.floor(feet.x), (int) Math.floor(feet.y + .001), (int) Math.floor(feet.z)),
                        "The paste left the villager where nobody can stand");
                    store.removeEntity(who.get(), RemoveReason.REMOVE);
                    manager.removeTown(town.getTownId());
                    lines.add("A whole House pasted at once over a villager standing where its floor goes (what a rebuild or a move does): the villager was moved out first and stands clear.");
                }));
        }

        private static CompletableFuture<Void> later(long millis) {
            return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS));
        }

        private void setup() {
            require(plugin != null, "Plugin missing");
            require(plugin.getConfig().get().isPassivePlotAssemblyEnabled(), "Passive plot assembly is switched off in the config");
            require(!PersistentWorldSupport.shouldPersistWorldData(world), "The fixture world must never be saved");
            manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            definition = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
            require(definition != null, "House definition missing");
            var path = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
            require(path != null, "House prefab missing");
            prefab = PrefabBufferUtil.getCached(path);
            require(prefab != null, "House prefab did not open");
            first = new Site("level 1", new Vector3i(-16, 128, -16), 1, true);
            fifth = new Site("level 5", new Vector3i(16, 128, -16), 5, true);
            empty = new Site("nobody there", new Vector3i(48, 128, -16), 5, false);
            crew = new Site("crew", new Vector3i(16, 128, 16), 5, true);
            crowdedFirst = new Crowded("villagers in the way, level 1", new Vector3i(-16, 128, 16), 1);
            crowdedTenth = new Crowded("villagers in the way, level 10", new Vector3i(48, 128, 16), 10);
            int furniture = 0;
            for (boolean writes : first.writes) if (!writes) furniture++;
            long dayMs = PlotAssemblyService.msPerGameDay(world, plugin.getConfig().get());
            long realSlot = Math.max(1L, (long) Math.ceil(definition.getSelfBuildGameDays() * dayMs) / first.pending.size());
            lines.add("House: " + first.pending.size() + " pieces (" + furniture + " are furniture and large block halves the finishing pass sets); in a town its slot is "
                + realSlot + " ms, here " + SLOT_MS + ".");
        }

        private void judgeBuilderSite(Site site, int level, int target) {
            require(site.stepSizes.size() == 1 && site.stepSizes.firstKey() == level,
                "A level " + level + " Builder placed " + site.stepSizes + " (blocks=steps) instead of " + level + " a step");
            require(site.stepBlocks == target, "Built " + site.stepBlocks + " of the " + target + " pieces asked for");
            require(site.steps == BuilderMastery.steps(target, level), site.steps + " steps instead of " + BuilderMastery.steps(target, level));
            long step = BuilderMastery.stepMs(SLOT_MS);
            require(site.medianGap() >= step, "Steps came every " + site.medianGap() + " ms, sooner than " + step);
            require(site.paid() == site.stepWrites, "Paid for " + site.paid() + " blocks, placed " + site.stepWrites);
            var mastery = CoreVillagerMasteryService.progress(site.town, BuilderMastery.KIND);
            require(mastery.level() == level, "The Builder's level changed during the run");
            lines.add("Level " + level + " Builder: " + level + (level == 1 ? " block" : " blocks") + " a step, " + site.steps + " steps for "
                + target + " pieces, a step every " + site.medianGap() + " ms, " + (site.paid() / BuilderMastery.BLOCKS_PER_XP)
                + " Mastery XP for the " + site.stepWrites + " blocks he placed (" + mastery.xpInLevel() + " / " + mastery.xpToNextLevel()
                + " at level " + mastery.level() + " of " + CoreVillagerMastery.MAX_LEVEL + ").");
        }

        /** A townsfolk builder of a yard finishes one piece of the same project, the way the building check has one do it. */
        private void townsfolkPlaces() {
            var anchor = crew.job.anchor();
            var candidates = NativeBuildingWork.candidates(world, crew.town, yard, new Vector3d(anchor.x, anchor.y, anchor.z));
            require(!candidates.isEmpty(), "No piece for the townsfolk builder");
            NativeBuildingWork.Target target = null;
            Vector3d worker = null;
            for (var candidate : candidates) {
                var c = candidate.cell();
                for (int[] offset : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                    var stand = new Vector3d(c.x() + .5 + offset[0], c.y(), c.z() + .5 + offset[1]);
                    if (NativeBuildingWork.inReach(stand, candidate)) {
                        target = candidate;
                        worker = stand;
                        break;
                    }
                }
                if (target != null) break;
            }
            require(target != null, "No safe standing spot for the townsfolk builder");
            var cell = target.cell();
            require(yard.building().focus(cell), "Focus failed");
            for (int i = 0; i < 20; i++) yard.building().attend(cell, .5, true, 1);
            long paid = crew.paid();
            int before = crew.plot.getAssemblyPlacedBlockCount();
            require(NativeBuildingWork.place(world, plugin, crew.town, yard, target, worker, 1, 1), "The townsfolk builder's piece was refused");
            require(crew.plot.getAssemblyPlacedBlockCount() == before + 1, "A townsfolk piece was not counted once");
            require(crew.paid() == paid, "The Builder was paid for a townsfolk builder's piece");
            townsfolkPieces++;
        }

        private CompletableFuture<Void> onWorld(Runnable action) {
            return CompletableFuture.runAsync(action, world);
        }

        /** Calls {@code tick} on the world thread about once a world tick until it says done. */
        private CompletableFuture<Void> pump(String failure, BooleanSupplier tick, int timeoutSeconds) {
            long startedAt = store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli();
            var result = new CompletableFuture<Void>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            var pause = CompletableFuture.delayedExecutor(12, TimeUnit.MILLISECONDS);
            Runnable[] loop = new Runnable[1];
            loop[0] = () -> {
                try {
                    if (tick.getAsBoolean()) result.complete(null);
                    else if (System.nanoTime() > deadline) result.completeExceptionally(new IllegalStateException(failure + " in " + timeoutSeconds
                        + " s (the world's clock moved " + (store.getResource(TimeResource.getResourceType()).getNow().toEpochMilli() - startedAt) + " ms)"));
                    else pause.execute(() -> world.execute(loop[0]));
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            };
            world.execute(loop[0]);
            return result;
        }

        private static void require(boolean okay, String reason) {
            if (!okay) throw new IllegalStateException(reason);
        }
    }
}
