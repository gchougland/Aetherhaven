package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * A festival that ends while nobody is near: its decorations must go even though they are parked in chunks that have
 * stopped ticking. Runs the real swap on the real prefabs in a disposable world.
 */
final class FestivalEntityWorldCheck extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Vector3i ANCHOR = new Vector3i(16, 128, 16);
    private static final String FESTIVAL = "Festivals/Festival_Hallows_Eve.prefab.json";
    private static final int EVERYDAY_PROPS_ALLOWED = 4;

    FestivalEntityWorldCheck() {
        super("entitycheck", "End a festival nobody is near and count the decorations left behind");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false, true)) {
            report(ctx, "A festival entity check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_festival_entity_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
        }
        var config = new WorldConfig();
        InstanceWorldConfig.ensureAndGet(config);
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
                    report(ctx, "Festival entity isolated world check: PASSED. " + String.join(" ", fixture.lines));
                } else {
                    Throwable cause = error;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    report(ctx, "Festival entity isolated world check: FAILED: " + cause.getMessage() + " " + String.join(" ", fixture.lines));
                }
                universe.removeWorld(name);
            });
        }).whenComplete((done, error) -> RUNNING.set(false));
    }

    private static final class Fixture {
        private final World world;
        private final AetherhavenPlugin plugin = AetherhavenPlugin.get();
        private final List<String> lines = new ArrayList<>();
        private ConstructionDefinition definition;
        private TownRecord town;
        private PlotInstance plot;
        private Store<EntityStore> store;

        Fixture(World world) {
            this.world = world;
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 3; y <= 6; y++) {
                        sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
                    }
                }
            }
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenRunAsync(this::setUp, world)
                .thenCompose(done -> paste(definition.getPrefabPath()))
                .thenCompose(done -> swap(definition.getPrefabPath(), FESTIVAL))
                .thenRunAsync(() -> {
                    int up = props();
                    require(up > 800, "The festival should have put up its decorations, found " + up);
                    lines.add("Festival up: " + up + " decorations.");
                    stopTicking();
                    int parked = props();
                    require(parked < up, "Stopping the sections should have parked the decorations, " + parked + " still live");
                    lines.add("Nobody near: " + parked + " live.");
                }, world)
                .thenCompose(done -> swap(FESTIVAL, definition.getPrefabPath()))
                .thenRunAsync(() -> {
                    startTicking();
                }, world)
                .thenRunAsync(() -> {
                    int left = props();
                    lines.add("After the festival: " + left + " decorations.");
                    require(left <= EVERYDAY_PROPS_ALLOWED, "The festival left " + (left - EVERYDAY_PROPS_ALLOWED) + " decorations behind");
                }, world);
        }

        private void setUp() {
            store = world.getEntityStore().getStore();
            definition = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE);
            require(definition != null, "Festival square definition missing");
            town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), world.getName(), ANCHOR.x, ANCHOR.y, ANCHOR.z, 1, 1, 0);
            plot = new PlotInstance(
                UUID.randomUUID(), definition.getId(), PlotInstanceState.COMPLETE,
                FestivalPrefabSize.footprintAt(ANCHOR, Rotation.None), ANCHOR.x, ANCHOR.y + 60, ANCHOR.z, 0
            );
            plot.setPrefabWorldPlacement(ANCHOR.x, ANCHOR.y, ANCHOR.z, Rotation.None);
            town.getPlotInstances().add(plot);
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).putTown(town);
        }

        private CompletableFuture<Void> paste(String prefabPath) {
            var done = new CompletableFuture<Void>();
            world.execute(() -> ConstructionAnimator.start(
                plugin, world, ANCHOR, Rotation.None, true, false, PrefabResolveUtil.resolvePrefabBuffer(prefabPath), store,
                500_000, 1L, () -> done.complete(null)
            ));
            return done;
        }

        private CompletableFuture<Void> swap(String from, String to) {
            var done = new CompletableFuture<Void>();
            world.execute(() -> {
                if (!FestivalPrefabSwapService.swap(world, plugin, town, plot, from, to, () -> done.complete(null))) {
                    done.completeExceptionally(new IllegalStateException("Swap did not start: " + to));
                }
            });
            return done;
        }

        /** What the game does to a section no player is near: its column keeps ticking, its entities are parked. */
        private void stopTicking() {
            Store<ChunkStore> chunks = world.getChunkStore().getStore();
            for (Ref<ChunkStore> section : sections()) {
                chunks.ensureComponent(section, ChunkStore.REGISTRY.getNonTickingComponentType());
            }
        }

        /** A player coming back. */
        private void startTicking() {
            Store<ChunkStore> chunks = world.getChunkStore().getStore();
            for (Ref<ChunkStore> section : sections()) {
                chunks.tryRemoveComponent(section, ChunkStore.REGISTRY.getNonTickingComponentType());
            }
        }

        private List<Ref<ChunkStore>> sections() {
            List<Ref<ChunkStore>> out = new ArrayList<>();
            PlotFootprintRecord box = FestivalPrefabSize.footprintAt(ANCHOR, Rotation.None);
            for (int x = ChunkUtil.chunkCoordinate(box.getMinX()); x <= ChunkUtil.chunkCoordinate(box.getMaxX()); x++) {
                for (int z = ChunkUtil.chunkCoordinate(box.getMinZ()); z <= ChunkUtil.chunkCoordinate(box.getMaxZ()); z++) {
                    for (int y = ChunkUtil.chunkCoordinate(box.getMinY()); y <= ChunkUtil.chunkCoordinate(box.getMaxY()); y++) {
                        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReference(x, y, z);
                        if (section != null && section.isValid()) {
                            out.add(section);
                        }
                    }
                }
            }
            return out;
        }

        private int props() {
            PlotFootprintRecord box = FestivalPrefabSize.footprintAt(ANCHOR, Rotation.None);
            int[] count = {0};
            store.forEachChunk(Query.and(TransformComponent.getComponentType(), PropComponent.getComponentType()), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (transform == null) {
                        continue;
                    }
                    var p = transform.getPosition();
                    if (p.x >= box.getMinX() && p.x <= box.getMaxX() + 1 && p.z >= box.getMinZ() && p.z <= box.getMaxZ() + 1) {
                        count[0]++;
                    }
                }
            });
            return count[0];
        }
    }

    private static void require(boolean yes, @Nonnull String issue) {
        if (!yes) {
            throw new IllegalStateException(issue);
        }
    }

    private static void report(@Nonnull CommandContext ctx, @Nonnull String message) {
        ctx.sendMessage(Message.raw(message));
        HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
