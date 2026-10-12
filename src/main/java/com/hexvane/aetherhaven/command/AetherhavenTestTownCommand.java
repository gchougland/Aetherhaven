package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.testtown.TestTownBuilder;
import com.hexvane.aetherhaven.testtown.TestTownCheck;
import com.hexvane.aetherhaven.testtown.TestTownKit;
import com.hexvane.aetherhaven.testtown.TestTownReport;
import com.hexvane.aetherhaven.testtown.TestTownSpec;
import com.hexvane.aetherhaven.testtown.TestTownWorlds;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ah test town}: a ready town to test in, in a world of its own.
 *
 * <pre>
 * /ah test town                   go to the test town, building it first when it does not exist (players)
 * /ah test town build [owner]     build it without going there; works from the console
 * /ah test town reset [owner]     delete the test world and build the town again
 * /ah test town remove            delete the test world and its saved records
 * /ah test town claim <owner>     give a town that has no owner yet to that player (name while online, or id)
 * /ah test town check             re-read the built town and print one PASS or FAIL line
 * /ah test town status            print what the town holds and where each building and workplace is
 * /ah test town kit               hand out the test kit again (players)
 * /ah test town leave             go back to where you were before, or to the default world (players)
 * </pre>
 *
 * Operators only, like every other test command, and it never runs by itself. What the town contains is read from
 * {@code Server/Aetherhaven/TestTown/test_town.json}.
 */
public final class AetherhavenTestTownCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String DESCRIPTION = "Build or visit the prebuilt Aetherhaven test town in its own world";
    private static final String ACTIONS = "go, build, reset, remove, claim, check, status, kit or leave";
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    /** The game mode each player had before the test town switched them to creative, for {@code leave}. */
    private static final Map<UUID, GameMode> EARLIER_MODE = new ConcurrentHashMap<>();
    /** Where each player stood before they went to the test town, for {@code leave}. Forgotten when the server stops. */
    private static final Map<UUID, Origin> EARLIER_PLACE = new ConcurrentHashMap<>();
    /** No single action may hold the command for longer than this, whatever happens to the test world. */
    private static final long ACTION_TIMEOUT_MINUTES = 15;

    private record Origin(String world, Vector3d position, Rotation3f rotation) {}

    public AetherhavenTestTownCommand() {
        super("town", DESCRIPTION);
        setPermissionGroups("hytale:WorldEditor");
        addUsageVariant(new WithAction());
        addUsageVariant(new WithActionAndOwner());
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
        return run(ctx, "go", null);
    }

    private static final class WithAction extends AbstractAsyncCommand {
        private final RequiredArg<String> action = withRequiredArg("action", ACTIONS, ArgTypes.STRING);

        WithAction() {
            super(DESCRIPTION);
            setPermissionGroups("hytale:WorldEditor");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
            return run(ctx, ctx.get(action), null);
        }
    }

    private static final class WithActionAndOwner extends AbstractAsyncCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "build, reset or claim", ArgTypes.STRING);
        private final RequiredArg<String> owner = withRequiredArg("owner", "Player name or id that will own the town", ArgTypes.STRING);

        WithActionAndOwner() {
            super(DESCRIPTION);
            setPermissionGroups("hytale:WorldEditor");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
            return run(ctx, ctx.get(action), ctx.get(owner));
        }
    }

    private static CompletableFuture<Void> run(CommandContext ctx, String rawAction, String ownerText) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) return done();
        String action = rawAction == null ? "" : rawAction.trim().toLowerCase(Locale.ROOT);
        TestTownSpec spec = TestTownSpec.load(AetherhavenPlugin.class.getClassLoader());
        String problem = spec == null ? "the layout file " + TestTownSpec.PATH + "/" + TestTownSpec.FILE + " could not be read" : spec.problem();
        if (problem != null) { say(ctx, "Test town: the layout cannot be used: " + problem + "."); return done(); }
        PlayerRef player = ctx.isPlayer() ? ctx.senderAs(PlayerRef.class) : null;
        switch (action) {
            case "leave" -> { return player == null ? playersOnly(ctx) : leave(ctx, player); }
            case "kit" -> { return player == null ? playersOnly(ctx) : kit(ctx, plugin, spec, player); }
            case "check" -> {
                return guarded(ctx, () -> TestTownCheck.run(plugin, spec).thenAccept(result -> {
                    for (String line : result.problems()) say(ctx, "Test town check problem: " + line);
                    say(ctx, result.line());
                }));
            }
            case "status" -> { return guarded(ctx, () -> status(ctx, plugin, spec)); }
            case "remove" -> { return guarded(ctx, () -> remove(ctx, plugin, spec)); }
            case "claim" -> { return guarded(ctx, () -> claim(ctx, plugin, spec, player, ownerText)); }
            case "build" -> { return guarded(ctx, () -> build(ctx, plugin, spec, player, ownerText).thenAccept(report -> { })); }
            case "reset" -> {
                return guarded(ctx, () -> remove(ctx, plugin, spec).thenCompose(ignored -> build(ctx, plugin, spec, player, ownerText))
                    .thenCompose(report -> player == null ? done() : arrive(ctx, plugin, spec, player, !report.existing())));
            }
            case "go" -> {
                if (player == null) return playersOnly(ctx);
                return guarded(ctx, () -> build(ctx, plugin, spec, player, ownerText)
                    .thenCompose(report -> arrive(ctx, plugin, spec, player, !report.existing())));
            }
            default -> { say(ctx, "Test town: use " + ACTIONS + "."); return done(); }
        }
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<Void> playersOnly(CommandContext ctx) {
        say(ctx, "Test town: only a player can do that. From the console use build, reset, remove, check or status.");
        return done();
    }

    /**
     * Lets another check that works in the test town keep every test town action out while it runs. True when the town
     * is now held; the holder must call {@link #release()} when it has finished.
     */
    static boolean tryHold() {
        return BUSY.compareAndSet(false, true);
    }

    static void release() {
        BUSY.set(false);
    }

    /** One test town action at a time. Anything that fails is said out loud, never swallowed. */
    private static CompletableFuture<Void> guarded(CommandContext ctx, java.util.function.Supplier<CompletableFuture<Void>> work) {
        if (!BUSY.compareAndSet(false, true)) {
            say(ctx, "Test town: another test town action is still running. Try again when it has finished.");
            return done();
        }
        CompletableFuture<Void> running;
        try {
            running = work.get();
        } catch (RuntimeException failure) {
            running = CompletableFuture.failedFuture(failure);
        }
        return running.orTimeout(ACTION_TIMEOUT_MINUTES, TimeUnit.MINUTES).handle((ignored, failure) -> {
            BUSY.set(false);
            if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                LOG.atWarning().withCause(failure).log("Test town action failed");
                say(ctx, "Test town: FAILED: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
            return null;
        });
    }

    /** Builds the town when the test world has none, then prints the summary. An existing town is only counted. */
    private static CompletableFuture<TestTownReport> build(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec,
                                                           PlayerRef player, String ownerText) {
        TestTownBuilder.Owner owner = owner(player, ownerText);
        return TestTownBuilder.run(plugin, spec, owner, text -> say(ctx, text)).thenApply(report -> {
            for (String line : report.lines(spec.world().name())) say(ctx, line);
            return report;
        });
    }

    /**
     * The sender when a player runs the command with no owner named. A named owner must be an id or a player who is
     * online. Otherwise the town is founded for a stand-in and left for the first operator who visits it to claim.
     */
    private static TestTownBuilder.Owner owner(PlayerRef player, String ownerText) {
        if (ownerText != null && !ownerText.isBlank()) {
            String wanted = ownerText.trim();
            try {
                UUID id = UUID.fromString(wanted);
                PlayerRef online = Universe.get().getPlayer(id);
                return new TestTownBuilder.Owner(id, online == null ? null : online.getUsername(), false);
            } catch (IllegalArgumentException notAnId) {
                World anyWorld = Universe.get().getDefaultWorld();
                UUID id = anyWorld == null ? null : TownPlayerLookup.resolvePlayerUuid(anyWorld, wanted);
                if (id != null) return new TestTownBuilder.Owner(id, wanted, false);
            }
            return new TestTownBuilder.Owner(UUID.nameUUIDFromBytes(("aetherhaven-test-town:" + wanted).getBytes(StandardCharsets.UTF_8)), wanted, true);
        }
        if (player != null) return new TestTownBuilder.Owner(player.getUuid(), player.getUsername(), false);
        return new TestTownBuilder.Owner(UUID.nameUUIDFromBytes("aetherhaven-test-town".getBytes(StandardCharsets.UTF_8)), "Test Town", true);
    }

    private static CompletableFuture<Void> remove(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec) {
        // Stopping a world joins its thread, so this must not run on a world thread.
        return CompletableFuture.runAsync(() -> {
            try {
                say(ctx, "Test town removed from world " + spec.world().name() + ": " + TestTownWorlds.remove(spec.world(), plugin) + ".");
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
    }

    /** Hands an ownerless town to the named player, or to the sender when nobody is named. */
    private static CompletableFuture<Void> claim(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec, PlayerRef player, String ownerText) {
        World world = TestTownWorlds.loaded(spec.world());
        TestTownBuilder.Owner owner = owner(player, ownerText);
        if (world == null) { say(ctx, "Test town claim: world " + spec.world().name() + " does not exist. Build the town first."); return done(); }
        if (owner.claimable()) { say(ctx, "Test town claim: name a player who is online, or give a player id."); return done(); }
        return CompletableFuture.runAsync(() -> {
            String answer = TestTownBuilder.claim(plugin, world, owner.uuid(), owner.name()).message();
            say(ctx, "Test town claim: " + (answer == null ? "that player already belongs to the town." : answer));
        }, world);
    }

    private static CompletableFuture<Void> status(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec) {
        World world = TestTownWorlds.loaded(spec.world());
        if (world == null) {
            say(ctx, "Test town status: world " + spec.world().name() + " does not exist. Run the command to build it.");
            return done();
        }
        return CompletableFuture.runAsync(() -> {
            List<TownRecord> towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns();
            if (towns.isEmpty()) { say(ctx, "Test town status: world " + spec.world().name() + " has no town."); return; }
            TownRecord town = towns.getFirst();
            // Where things are, so a tester can walk or teleport straight to them. Each line gives a spot to stand and
            // the ground the thing covers, west to east and north to south.
            int ground = spec.world().surfaceY() + 1;
            for (var plot : town.getPlotInstances()) {
                var f = plot.toFootprint();
                say(ctx, "Test town place: " + plot.getConstructionId() + " (" + plot.getState().name().toLowerCase(Locale.ROOT) + ") at "
                    + plot.getSignX() + ", " + plot.getSignY() + ", " + plot.getSignZ() + " covering x " + f.getMinX() + " to " + f.getMaxX()
                    + ", z " + f.getMinZ() + " to " + f.getMaxZ());
                var production = town.peekPlotProduction(plot.getPlotId());
                if (production != null && production.usesPhysicalTimber())
                    for (var stone : production.timber().groves().values())
                        say(ctx, "Test town place: Rootstone for " + stone.output() + " at " + stone.root().x() + ", " + stone.root().y()
                            + ", " + stone.root().z());
                if (production != null && production.usesSpiritGarden())
                    for (var planter : production.garden().planters().values())
                        say(ctx, "Test town place: Spirit Planter for " + planter.plant() + " at " + planter.block().x() + ", "
                            + (planter.block().y() + 1) + ", " + planter.block().z());
            }
            for (var site : town.getApprenticeWorkplaces().sites())
                if (site.area() != null) say(ctx, "Test town place: " + site.name() + " (" + (site.type() == null ? "workplace" : site.type().id)
                    + ") at " + (site.area().minX() + site.area().maxX()) / 2 + ", " + ground + ", "
                    + (site.area().minZ() + site.area().maxZ()) / 2 + " covering x " + site.area().minX() + " to " + (site.area().maxX() - 1)
                    + ", z " + site.area().minZ() + " to " + (site.area().maxZ() - 1));
            say(ctx, "Test town owner: " + (town.hasOwner() ? (town.getOwnerUsername() == null ? town.getOwnerUuid().toString() : town.getOwnerUsername())
                : "nobody yet. The first operator to run the command in game becomes its owner."));
            TestTownBuilder.describe(plugin, world, town).lines(spec.world().name()).forEach(line -> say(ctx, line));
        }, world);
    }

    /** Claims an ownerless town for the player, hands out the kit for a town that is newly theirs, and takes them to the plaza. */
    private static CompletableFuture<Void> arrive(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec, PlayerRef player, boolean fresh) {
        World world = TestTownWorlds.loaded(spec.world());
        if (world == null) { say(ctx, "Test town: the world is not loaded, so there is nowhere to go."); return done(); }
        return CompletableFuture.supplyAsync(() -> {
            TestTownBuilder.Claim claim = TestTownBuilder.claim(plugin, world, player.getUuid(), player.getUsername());
            if (claim.message() != null) say(ctx, claim.message());
            return claim.becameOwner();
            // The kit comes with a town this player has just built, or one built from the console that is now theirs.
        }, world).thenCompose(claimed -> fresh || claimed ? kit(ctx, plugin, spec, player) : done()).thenCompose(ignored -> {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) { say(ctx, "Test town: you are not in a world, so you could not be moved."); return done(); }
            var store = ref.getStore();
            World current = store.getExternalData().getWorld();
            // Arrive on the plaza a few steps south of the charter, looking at it.
            Vector3d spot = new Vector3d(0.5, spec.world().surfaceY() + 1, spec.grid().plaza() / 2 + 0.5);
            return CompletableFuture.runAsync(() -> {
                // The player may have moved worlds or left while the town was being built.
                var now = player.getReference();
                if (now == null || !now.isValid() || now.getStore() != store) { say(ctx, "Test town: you moved while travel began. Run the command again."); return; }
                if (Universe.get().getWorld(world.getName()) != world) { say(ctx, "Test town: the world was unloaded. Run the command again."); return; }
                if (store.getComponent(now, Teleport.getComponentType()) != null) { say(ctx, "Test town: another journey is already pending."); return; }
                if (current != world) {
                    // Remember where they came from, so leave can put them back there and not just at a spawn point.
                    TransformComponent here = store.getComponent(now, TransformComponent.getComponentType());
                    if (here != null) EARLIER_PLACE.put(player.getUuid(), new Origin(current.getName(),
                        new Vector3d(here.getPosition()), new Rotation3f(here.getRotation())));
                }
                if (spec.kit().creative()) {
                    GameMode before = TestTownKit.creative(now, store);
                    if (before != null) EARLIER_MODE.putIfAbsent(player.getUuid(), before);
                }
                store.putComponent(now, Teleport.getComponentType(), Teleport.createForPlayer(world, spot, new Rotation3f(0, 0, 0)));
                say(ctx, "Welcome to the test town. Use /ah test town leave to go back.");
            }, current);
        });
    }

    private static CompletableFuture<Void> kit(CommandContext ctx, AetherhavenPlugin plugin, TestTownSpec spec, PlayerRef player) {
        var ref = player.getReference();
        if (ref == null || !ref.isValid()) return done();
        var store = ref.getStore();
        return CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) return;
            List<String> problems = TestTownKit.give(plugin, spec.kit(), ref, store);
            for (String problem : problems) say(ctx, "Test town " + problem);
            say(ctx, "Test town kit: " + (problems.isEmpty() ? "everything was given." : problems.size() + " things could not be given."));
        }, store.getExternalData().getWorld());
    }

    /** Back to where the player came from, or to the default world's spawn when that is no longer known. */
    private static CompletableFuture<Void> leave(CommandContext ctx, PlayerRef player) {
        var ref = player.getReference();
        if (ref == null || !ref.isValid()) { say(ctx, "Test town: you are not in a world."); return done(); }
        var store = ref.getStore();
        World current = store.getExternalData().getWorld();
        Origin origin = EARLIER_PLACE.get(player.getUuid());
        World back = origin == null ? null : Universe.get().getWorld(origin.world());
        World home = back != null && back != current ? back : Universe.get().getDefaultWorld();
        if (home == null) { say(ctx, "Test town: there is no world to go back to."); return done(); }
        if (current == home) { say(ctx, "Test town: you are already back in " + home.getName() + "."); return done(); }
        CompletableFuture<Teleport> journey = home == back
            ? CompletableFuture.completedFuture(Teleport.createForPlayer(home, origin.position(), origin.rotation()))
            : home.getWorldConfig().getSpawnProvider().getSpawnPointAsync(home, player.getUuid()).thenApply(spawn -> Teleport.createForPlayer(home, spawn));
        return journey.thenCompose(teleport -> CompletableFuture.runAsync(() -> {
            var now = player.getReference();
            if (now == null || !now.isValid() || now.getStore() != store) return;
            if (store.getComponent(now, Teleport.getComponentType()) != null) { say(ctx, "Test town: another journey is already pending."); return; }
            GameMode earlier = EARLIER_MODE.remove(player.getUuid());
            if (earlier != null && store.getComponent(now, Player.getComponentType()) != null) Player.setGameMode(now, earlier, store);
            EARLIER_PLACE.remove(player.getUuid());
            store.putComponent(now, Teleport.getComponentType(), teleport);
        }, current));
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
        LOG.atInfo().log("%s", text);
    }
}
