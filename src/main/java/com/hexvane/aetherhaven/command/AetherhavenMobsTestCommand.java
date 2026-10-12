package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.entity.OwnCreatures;
import com.hexvane.aetherhaven.mobprobe.MobProbe;
import com.hexvane.aetherhaven.mobprobe.MobProbeScenarios;
import com.hexvane.aetherhaven.mobprobe.MobProbeWorld;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotState;
import com.hexvane.aetherhaven.questboard.RaidApproachDirection;
import com.hexvane.aetherhaven.questboard.RaidQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.RaidQuestSpawnService;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRaidEntryJson;
import com.hexvane.aetherhaven.testtown.TestTownBuilder;
import com.hexvane.aetherhaven.testtown.TestTownSpec;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ah test mobs [open|town|raids]}: does the mod leave other creatures alone? The last line ends in PASSED or
 * FAILED, and the whole check takes about two minutes.
 *
 * <p>Places ordinary creatures the way the game or another plugin would (plain spawns, plugin spawns that edit the
 * creature, capture and release, the game's own taming). Every creature carries what the check expects of it:
 *
 * <ul>
 *   <li><b>must live</b>: nothing of the town's may land a blow on it, and it must still be alive in the world when
 *       the watch ends. One blow from a guard fails the check, whether or not the kill has finished.</li>
 *   <li><b>may be fought</b>: a creature the guards are right to fight (a raid attacker, or a hostile creature marked
 *       the way the game's own spawner marks what it places). Alive and dead count the same; the line says how many
 *       were fought. It only fails if it dies with nobody having struck it or is taken out of the world.</li>
 * </ul>
 *
 * The parts:
 *
 * <ul>
 *   <li>{@code open}: on open ground in a world with no town, by day and by night. Every creature must live.</li>
 *   <li>{@code town}: in the test town while it is loaded: beside a guard, on the plaza, at the border and well
 *       outside it, by day and by night.</li>
 *   <li>{@code raids}: starts each of the quest board's raids in the test town and watches every attacker it
 *       places, with a sheep and a bear standing by, and one attacker moved beside a guard.</li>
 * </ul>
 *
 * Operators only, and it never runs by itself. The creatures it places are taken out again. While it runs it holds
 * the test town, so a test town command that arrives in the middle is refused instead of rebuilding the town under it.
 */
final class AetherhavenMobsTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String DESCRIPTION = "Place ordinary creatures with and without a town and say PASSED or FAILED (about two minutes)";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /**
     * How long each group of creatures is watched. A guard that means to strike lands its first blow within four
     * seconds (0.7 to 3.1 in every run so far), and one blow is all the verdict needs.
     */
    private static final int SECONDS = 6;
    /** How long each raid after the first is watched: long enough to see an attacker die as it is placed. */
    private static final int RAID_SECONDS = 2;
    /** The whole check is given up, and FAILED, after this long. */
    private static final int LIMIT_MINUTES = 6;
    /**
     * How long the ground is left empty after a group the guards were fighting is taken out. A guard's swing that is
     * already under way lands on whatever stands where its opponent stood: with no pause, two animals put there took
     * one blow each, 0.04 and 0.42 seconds after they appeared, and none after. That is a stray blow, not a guard
     * choosing a target, and this check is about whom the guards choose. A guard remembers an attacker for ten
     * seconds, so the pause hides nothing a newcomer could inherit.
     */
    private static final long SETTLE_MILLIS = 3000;

    /** Creatures that are in the world together, at every place at once, and what the check expects of them. */
    private record Group(MobProbe.Expect expect, List<String[]> creatures) {}

    /**
     * The groups take turns, because the creatures fight among themselves when they share the ground: a bear hunts
     * sheep and cows and fights trorks and skeletons, and a wolf hunts sheep. Nothing in a group harms another member
     * of it, so a blow on a creature that must live can only have come from the town.
     *
     * <p>First the hostile creatures marked the way the game's own spawner marks what it places, which the guards may
     * fight. Then everything else, which must live, guard's side first, so its creatures are given the entity slots
     * just freed, which is how a newcomer used to inherit a dead attacker's guilt.
     */
    private static final List<Group> GROUPS = List.of(
        new Group(MobProbe.Expect.MAY_BE_FOUGHT, List.of(
            new String[] {"Bear_Grizzly", "wild:" + MobProbeScenarios.BEAR_RULE},
            new String[] {"Skeleton_Fighter", "wild:" + MobProbeScenarios.SKELETON_RULE})),
        new Group(MobProbe.Expect.MUST_LIVE, List.of(
            new String[] {"Sheep", "plain"}, new String[] {"Cow", "plain"}, new String[] {"Sheep", "tame:Tamed_Sheep"},
            new String[] {"Cow", "hooks"}, new String[] {"Sheep", "reload"})),
        new Group(MobProbe.Expect.MUST_LIVE, List.of(
            new String[] {"Bear_Grizzly", "plain"}, new String[] {"Bear_Grizzly", "hooks"}, new String[] {"Bear_Grizzly", "pet"},
            new String[] {"Bear_Grizzly", "reload"})),
        new Group(MobProbe.Expect.MUST_LIVE, List.of(
            new String[] {"Wolf_Black", "plain"}, new String[] {"Trork_Warrior", "plain"}, new String[] {"Skeleton_Fighter", "plain"})));

    /** A place to put a group of creatures: its name for the report and the corner the group is laid out from. */
    private record Spot(String name, int x, int y, int z) {}

    private record RaidWatch(List<UUID> attackers, List<UUID> besideGuard, List<MobProbe.Spawn> standing) {}

    /** What one run gathers: each part's creatures, lines that are not creatures, and parts that did not finish. */
    private record Gathered(Map<String, List<MobProbe.Result>> parts, List<String> notes, List<String> problems) {
        List<MobProbe.Result> part(String name) {
            return parts.computeIfAbsent(name, ignored -> Collections.synchronizedList(new ArrayList<>()));
        }
    }

    AetherhavenMobsTestCommand() {
        super("mobs", DESCRIPTION);
        setPermissionGroups("hytale:WorldEditor");
        addUsageVariant(new Part());
    }

    private static final class Part extends AbstractAsyncCommand {
        private final RequiredArg<String> part = withRequiredArg("part", "open, town, raids or all", ArgTypes.STRING);

        Part() {
            super(DESCRIPTION);
            setPermissionGroups("hytale:WorldEditor");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
            return run(ctx, ctx.get(part));
        }
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
        return run(ctx, "all");
    }

    /** The town's own guards and townsfolk. A raid attacker is the town's enemy, not the town. */
    static boolean isTownRole(String role) {
        return role != null && role.startsWith(OwnCreatures.ROLE_PREFIX) && !role.startsWith(OwnCreatures.ROLE_PREFIX + "Raid_");
    }

    private static CompletableFuture<Void> run(CommandContext ctx, String rawPart) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) return CompletableFuture.completedFuture(null);
        String part = rawPart == null ? "all" : rawPart.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "open", "town", "raids").contains(part)) {
            say(ctx, "Mobs check: use open, town, raids or all. FAILED");
            return CompletableFuture.completedFuture(null);
        }
        if (!RUNNING.compareAndSet(false, true)) {
            say(ctx, "Mobs check: another creature check is still running, so this one did not start. FAILED");
            return CompletableFuture.completedFuture(null);
        }
        boolean open = part.equals("all") || part.equals("open");
        boolean town = part.equals("all") || part.equals("town");
        boolean raids = part.equals("all") || part.equals("raids");
        boolean needsTown = town || raids;
        // A test town command arriving in the middle would rebuild the town under the creatures being watched.
        if (needsTown && !AetherhavenTestTownCommand.tryHold()) {
            RUNNING.set(false);
            say(ctx, "Mobs check: a test town action is still running, so this check did not start. FAILED");
            return CompletableFuture.completedFuture(null);
        }
        long started = System.nanoTime();
        say(ctx, "mobs check started (" + part + "). The whole check takes about two minutes and its last line ends in PASSED or FAILED."
            + (needsTown ? " Test town commands are refused until then." : ""));
        MobProbe.townRoles(AetherhavenMobsTestCommand::isTownRole);
        MobProbe.register(plugin.getEntityStoreRegistry());
        Gathered gathered = new Gathered(Collections.synchronizedMap(new LinkedHashMap<>()), Collections.synchronizedList(new ArrayList<>()),
            Collections.synchronizedList(new ArrayList<>()));
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        if (open) {
            List<MobProbe.Result> found = gathered.part("With the mod and no town");
            chain = chain.thenCompose(ignored -> MobProbeScenarios.flat("mod, no town", SECONDS)).thenAccept(found::addAll)
                .exceptionally(failure -> problem(gathered, "open ground", failure));
        }
        if (needsTown)
            chain = chain.thenCompose(ignored -> town(plugin, town, raids, gathered)).exceptionally(failure -> problem(gathered, "the test town", failure));
        return chain.orTimeout(LIMIT_MINUTES, TimeUnit.MINUTES).handle((ignored, failure) -> {
            if (needsTown) AetherhavenTestTownCommand.release();
            RUNNING.set(false);
            if (failure != null) problem(gathered, "it did not finish within " + LIMIT_MINUTES + " minutes", failure);
            List<MobProbeScenarios.Tally> tallies = new ArrayList<>();
            synchronized (gathered.parts()) {
                gathered.parts().forEach((name, found) -> {
                    List<MobProbe.Result> copy;
                    synchronized (found) {
                        copy = new ArrayList<>(found);
                    }
                    for (MobProbe.Result result : copy) say(ctx, result.line());
                    tallies.add(MobProbeScenarios.tally(name, copy));
                });
            }
            for (String note : new ArrayList<>(gathered.notes())) say(ctx, note);
            say(ctx, MobProbeScenarios.verdictLine(tallies, new ArrayList<>(gathered.problems()), (System.nanoTime() - started) / 1_000_000_000L));
            return null;
        });
    }

    private static Void problem(Gathered gathered, String where, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        LOG.atWarning().withCause(failure).log("mobs check problem in %s", where);
        gathered.problems().add(where + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        return null;
    }

    // ------------------------------------------------------------------------------------------- in the test town

    /** Builds the test town once, wakes its land, then runs the town part, the raids part or both in it. */
    private static CompletableFuture<Void> town(AetherhavenPlugin plugin, boolean spots, boolean raids, Gathered gathered) {
        TestTownSpec spec = TestTownSpec.load(AetherhavenPlugin.class.getClassLoader());
        if (spec == null || spec.problem() != null)
            return CompletableFuture.failedFuture(new IllegalStateException("the test town layout cannot be read"));
        var owner = new TestTownBuilder.Owner(UUID.nameUUIDFromBytes("aetherhaven-test-town".getBytes(StandardCharsets.UTF_8)), "Test Town", true);
        return TestTownBuilder.run(plugin, spec, owner, text -> { }).thenCompose(report -> {
            World world = com.hexvane.aetherhaven.testtown.TestTownWorlds.loaded(spec.world());
            if (world == null) return CompletableFuture.failedFuture(new IllegalStateException("the test town world is not loaded"));
            boolean[] couldUnload = new boolean[1];
            TownRecord[] town = new TownRecord[1];
            return CompletableFuture.supplyAsync(() -> {
                var towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns();
                if (towns.isEmpty()) throw new IllegalStateException("the test town world has no town");
                town[0] = towns.getFirst();
                // The land must stay awake for the whole check with no player to hold it.
                couldUnload[0] = world.getWorldConfig().canUnloadChunks();
                world.getWorldConfig().setCanUnloadChunks(false);
                return claimCorners(town[0]);
            }, world).thenCompose(corners -> MobProbeWorld.wake(world, corners, 112))
                // Villagers and guards the game had stored come back as their land wakes.
                .thenCompose(ignored -> pause(world, 4000))
                .thenCompose(ignored -> spots ? spots(world, town[0], gathered.part("With the mod in a loaded town")) : CompletableFuture.<Void>completedFuture(null))
                .exceptionally(failure -> problem(gathered, "the town part", failure))
                .thenCompose(ignored -> raids ? raids(world, plugin, town[0], gathered) : CompletableFuture.<Void>completedFuture(null))
                .exceptionally(failure -> problem(gathered, "the raids part", failure))
                .thenRunAsync(() -> world.getWorldConfig().setCanUnloadChunks(couldUnload[0]), world);
        });
    }

    /** A point every 96 blocks across the land the town has claimed, so one wake call covers all of it and a ring around it. */
    private static List<Vector3d> claimCorners(TownRecord town) {
        int minX = ChunkUtil.minBlock(TownTerritoryClaims.minClaimChunkX(town)), maxX = ChunkUtil.maxBlock(TownTerritoryClaims.maxClaimChunkX(town));
        int minZ = ChunkUtil.minBlock(TownTerritoryClaims.minClaimChunkZ(town)), maxZ = ChunkUtil.maxBlock(TownTerritoryClaims.maxClaimChunkZ(town));
        List<Vector3d> places = new ArrayList<>();
        for (int x = minX; x <= maxX + 95; x += 96)
            for (int z = minZ; z <= maxZ + 95; z += 96)
                places.add(new Vector3d(Math.min(x, maxX), town.getCharterY(), Math.min(z, maxZ)));
        return places;
    }

    private static CompletableFuture<Void> pause(World world, long millis) {
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)).thenRunAsync(() -> { }, world);
    }

    /** By day and by night: each group in turn, at all four places at once, instead of one creature after another. */
    private static CompletableFuture<Void> spots(World world, TownRecord town, List<MobProbe.Result> results) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (boolean night : new boolean[] {false, true}) {
            String scenario = "mod, town loaded, " + (night ? "night" : "day");
            chain = chain.thenRunAsync(() -> MobProbeWorld.setTime(world, night), world);
            for (Group group : GROUPS) {
                chain = chain.thenCompose(ignored -> CompletableFuture.supplyAsync(() -> layOut(world, town, group.creatures(), group.expect()), world))
                    .thenCompose(spawns -> MobProbe.run(world, scenario, spawns, SECONDS)).thenAccept(results::addAll);
                if (group.expect() == MobProbe.Expect.MAY_BE_FOUGHT) chain = chain.thenCompose(ignored -> pause(world, SETTLE_MILLIS));
            }
        }
        return chain;
    }

    /** One group at every place, two blocks apart in rows of four. World thread. */
    private static List<MobProbe.Spawn> layOut(World world, TownRecord town, List<String[]> group, MobProbe.Expect expect) {
        List<MobProbe.Spawn> spawns = new ArrayList<>();
        for (Spot spot : findSpots(world, town))
            for (int i = 0; i < group.size(); i++)
                spawns.add(new MobProbe.Spawn(spot.name(), group.get(i)[0], group.get(i)[1],
                    stand(world, spot.x() + 2 * (i % 4), spot.y(), spot.z() + 2 * (i / 4)), expect));
        return spawns;
    }

    /**
     * Where creatures are placed: beside a guard, on the plaza, just inside the border and well outside it. The guard
     * comes first, so its creatures are the ones given the entity slots freed a moment before. Found afresh for every
     * group, because a guard walks. World thread.
     */
    private static List<Spot> findSpots(World world, TownRecord town) {
        int y = town.getCharterY();
        List<Spot> spots = new ArrayList<>();
        Vector3d guard = guardPosition(world, town);
        if (guard != null)
            spots.add(new Spot("beside a guard, 3 to 10 blocks away", (int) Math.floor(guard.x) + 3, (int) Math.floor(guard.y), (int) Math.floor(guard.z) - 2));
        else spots.add(new Spot("beside a guard: NO GUARD WAS IN THE WORLD, plaza used", town.getCharterX() - 12, y, town.getCharterZ() + 5));
        spots.add(new Spot("plaza, 6 blocks from the charter", town.getCharterX() + 5, y, town.getCharterZ() + 5));
        // Walk east from the charter until the town's land ends.
        int edge = town.getCharterX();
        int z = town.getCharterZ() + 5;
        while (TownTerritoryClaims.containsBlock(town, edge + 1, z) && edge < town.getCharterX() + 4096) edge++;
        spots.add(new Spot("border, just inside it", edge - 8, y, z));
        spots.add(new Spot("outside, 48 blocks past the border", edge + 48, y, z));
        return spots;
    }

    /** The nearest place a creature can stand, searching outwards. */
    private static Vector3d stand(World world, int x, int y, int z) {
        for (int radius = 0; radius <= 8; radius++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    if (VillagerBlockUtil.isNpcStandColumn(world, x + dx, y, z + dz)) return new Vector3d(x + dx + 0.5, y, z + dz + 0.5);
                }
        return new Vector3d(x + 0.5, y, z + 0.5);
    }

    // ---------------------------------------------------------------------------------------------- during raids

    private static CompletableFuture<Void> raids(World world, AetherhavenPlugin plugin, TownRecord town, Gathered gathered) {
        if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.QUESTS)) {
            gathered.notes().add("mobs mod, raids | left out: the Quests feature is switched off on this server, so it has no raids");
            return CompletableFuture.completedFuture(null);
        }
        List<MobProbe.Result> results = gathered.part("With the mod during raids");
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        for (var villager : plugin.getQuestBoardCatalog().villagers().values()) {
            for (QuestBoardRaidEntryJson entry : villager.raidEntriesOrEmpty()) {
                // A rescue places its captors only when a player reaches the camp, so nothing can be watched here.
                if (entry.isRescue() || entry.id() == null) continue;
                LinkedHashSet<String> roles = new LinkedHashSet<>();
                for (var set : entry.raidSetsOrEmpty()) {
                    set.mobPoolOrEmpty().forEach(mob -> { if (mob.roleId() != null && !mob.roleId().isBlank()) roles.add(mob.roleId().trim()); });
                    set.guaranteedMobsOrEmpty().forEach(mob -> { if (mob.roleId() != null && !mob.roleId().isBlank()) roles.add(mob.roleId().trim()); });
                }
                if (!roles.isEmpty()) rosters.put(entry.id().trim(), new ArrayList<>(roles));
            }
        }
        CompletableFuture<Void> chain = CompletableFuture.runAsync(() -> MobProbeWorld.setTime(world, false), world);
        boolean first = true;
        for (var raid : rosters.entrySet()) {
            boolean bystanders = first;
            first = false;
            chain = chain.thenCompose(ignored -> raid(world, plugin, town, raid.getKey(), raid.getValue(), bystanders, results, gathered));
        }
        return chain;
    }

    /**
     * Starts one raid with one of each of its creatures, watches them, then ends the raid. Guards may fight a raid
     * attacker wherever it stands, so every attacker may be fought. The first raid is watched for longer, with a sheep
     * and a bear standing on the plaza that must live, and one attacker moved beside a guard.
     */
    private static CompletableFuture<Void> raid(World world, AetherhavenPlugin plugin, TownRecord town, String id, List<String> roster,
                                                boolean bystanders, List<MobProbe.Result> results, Gathered gathered) {
        String scenario = "mod, raid " + id;
        int seconds = bystanders ? SECONDS : RAID_SECONDS;
        QuestBoardSlotRecord[] used = new QuestBoardSlotRecord[1];
        return CompletableFuture.supplyAsync(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            var catalog = plugin.getQuestBoardCatalog();
            town.ensureQuestBoardSlotCount(Math.max(1, catalog.slotCount()));
            QuestBoardSlotRecord slot = town.getQuestBoardSlots().getFirst();
            slot.clearToEmpty();
            // The same fields the raid offer sets, then the same call the board makes when the offer is accepted.
            slot.setInstanceId(QuestBoardSlotRecord.newInstanceId());
            slot.setQuestType(RaidQuestBoardHandler.TYPE_ID);
            slot.setQuestRank("E");
            slot.setConfigEntryId(id);
            slot.setRequiredItems(new ArrayList<>());
            slot.setRewards(new ArrayList<>());
            slot.setDaysLimit(1);
            slot.setGenerationSeed(id.hashCode());
            slot.setRaidMobRoleIds(new ArrayList<>(roster));
            slot.setRaidKillRequired(roster.size());
            slot.setRaidKillProgress(0);
            slot.setRaidSpawnedEntityUuids(new ArrayList<>());
            slot.setRaidApproachDirection(RaidApproachDirection.values()[0].id());
            slot.setState(QuestBoardSlotState.ACCEPTED);
            UUID acceptor = town.getOwnerUuid() != null ? town.getOwnerUuid() : UUID.nameUUIDFromBytes("aetherhaven-mobs-check".getBytes(StandardCharsets.UTF_8));
            slot.setAcceptedByPlayerUuid(acceptor.toString());
            slot.setOnlineDaysElapsed(0);
            used[0] = slot;
            RaidQuestSpawnService.RaidStart start = RaidQuestSpawnService.startRaid(town, slot, world, store, acceptor);
            List<UUID> placed = new ArrayList<>();
            for (String raider : slot.raidSpawnedEntityUuidsOrEmpty()) placed.add(UUID.fromString(raider));
            gathered.notes().add("mobs " + scenario + " | raid start | " + roster.size() + " kinds asked for | " + start + ", " + placed.size() + " placed");
            if (placed.isEmpty()) gathered.problems().add("raid " + id + " placed no attackers (" + start + ")");
            int y = town.getCharterY();
            // Raiders appear at the border, out of every guard's reach. One is moved beside a guard so the report also
            // shows that guards still fight the enemies this mod spawns.
            List<UUID> besideGuard = new ArrayList<>();
            Vector3d guard = bystanders && !placed.isEmpty() ? guardPosition(world, town) : null;
            Ref<EntityStore> moved = guard == null ? null : store.getExternalData().getRefFromUUID(placed.getFirst());
            if (moved != null && moved.isValid()) {
                Vector3d spot = stand(world, (int) Math.floor(guard.x) + 4, (int) Math.floor(guard.y), (int) Math.floor(guard.z));
                store.putComponent(moved, Teleport.getComponentType(), Teleport.createExact(spot, Rotation3f.ZERO));
                besideGuard.add(placed.removeFirst());
            }
            return new RaidWatch(placed, besideGuard, !bystanders ? List.of() : List.of(
                new MobProbe.Spawn("plaza, while the raid runs", "Sheep", "plain", stand(world, town.getCharterX() + 5, y, town.getCharterZ() + 5),
                    MobProbe.Expect.MUST_LIVE),
                // Thirty blocks from the sheep: a bear that notices a sheep hunts it.
                new MobProbe.Spawn("plaza, while the raid runs", "Bear_Grizzly", "hooks", stand(world, town.getCharterX() - 25, y, town.getCharterZ() + 6),
                    MobProbe.Expect.MUST_LIVE)));
        }, world).thenCompose(watch -> {
            CompletableFuture<List<MobProbe.Result>> attackers =
                MobProbe.watch(world, scenario, "raid attacker", watch.attackers(), seconds, MobProbe.Expect.MAY_BE_FOUGHT);
            if (!watch.besideGuard().isEmpty())
                attackers = attackers.thenCombine(MobProbe.watch(world, scenario, "raid attacker moved beside a guard", watch.besideGuard(), seconds,
                    MobProbe.Expect.MAY_BE_FOUGHT), AetherhavenMobsTestCommand::both);
            if (watch.standing().isEmpty()) return attackers;
            return attackers.thenCombine(MobProbe.run(world, scenario, watch.standing(), seconds), AetherhavenMobsTestCommand::both);
        }).thenAcceptAsync(found -> {
            results.addAll(found);
            QuestBoardSlotRecord slot = used[0];
            RaidQuestSpawnService.cleanupRaid(slot, world, world.getEntityStore().getStore(), town.getTownId());
            slot.clearToEmpty();
        }, world);
    }

    private static List<MobProbe.Result> both(List<MobProbe.Result> first, List<MobProbe.Result> second) {
        List<MobProbe.Result> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    /** Where the first guard that is in the world stands, or null when none is. World thread. */
    private static Vector3d guardPosition(World world, TownRecord town) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (var guard : town.getHiredGuardRecords()) {
            Ref<EntityStore> ref = guard.getEntityUuid() == null ? null : store.getExternalData().getRefFromUUID(guard.getEntityUuid());
            TransformComponent transform = ref == null || !ref.isValid() ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) return new Vector3d(transform.getPosition());
        }
        return null;
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
        LOG.atInfo().log("%s", text);
    }
}
