package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.RescueCampCondition;
import com.hexvane.aetherhaven.questboard.RescueCampService;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventManager;
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventRecord;
import com.hypixel.hytale.builtin.adventure.worldevents.action.EventStartAction;
import com.hypixel.hytale.builtin.adventure.worldevents.action.EventStopAction;
import com.hypixel.hytale.builtin.adventure.worldevents.event.WorldEventAsset;
import com.hypixel.hytale.builtin.adventure.worldevents.util.PrefabSnapshotUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3i;

/**
 * Explicit operator test. Pastes every rescue camp through the base game's world event in its own temporary void
 * world, then stops the event and checks the land underneath came back. Never touches a player world.
 */
final class AetherhavenRescueTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int FLOOR_Y = 128, CENTRE = 48, HALF = 12;

    AetherhavenRescueTestCommand() {
        super("rescue", "Paste and restore each rescue camp in a separate temporary world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var plugin = AetherhavenPlugin.get();
        require(plugin != null, "Aetherhaven is unavailable");
        require(RescueCampService.isAvailable(), "Rescue camps are not registered with world events");
        var asset = WorldEventAsset.ASSET_MAP.getAsset(RescueCampService.EVENT_ID);
        require(asset != null && asset.valid(), "World event " + RescueCampService.EVENT_ID + " is missing or invalid");
        var camps = new LinkedHashSet<String>();
        int contracts = 0;
        for (var villager : plugin.getQuestBoardCatalog().villagers().values()) {
            for (var raid : villager.raidEntriesOrEmpty()) {
                if (raid.isRescue()) {
                    contracts++;
                    camps.addAll(raid.rescueCampPrefabsOrEmpty());
                }
            }
        }
        require(contracts > 0 && !camps.isEmpty(), "No rescue contracts are loaded");
        for (String camp : camps) require(RescueCampService.prefabPath(camp) != null, "Camp prefab not found: " + camp);
        if (!RUNNING.compareAndSet(false, true)) {
            report(ctx, "A rescue camp check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_rescue_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Fixture path already exists"));
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
        final int loaded = contracts;
        int[] results = new int[2];
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            CompletableFuture<Void> chain = CompletableFuture.allOf(
                world.getChunkStore().getChunkSectionReferenceAsync(1, 3, 1, GetChunkFlags.SET_TICKING),
                world.getChunkStore().getChunkSectionReferenceAsync(1, 4, 1, GetChunkFlags.SET_TICKING));
            for (String camp : camps) {
                chain = chain.thenCompose(ignored -> exercise(world, camp)).handle((done, error) -> {
                    if (error == null) {
                        results[0]++;
                        report(ctx, camp + ": pasted by the world event and the ground was restored.");
                    } else {
                        results[1]++;
                        Throwable cause = error;
                        while (cause.getCause() != null) cause = cause.getCause();
                        report(ctx, camp + ": FAILED: " + cause.getMessage());
                    }
                    return null;
                });
            }
            return chain.orTimeout(120, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                report(ctx, "Rescue camp check: " + results[0] + " passed, " + results[1] + " failed, " + loaded + " contracts loaded"
                    + (error == null ? "." : "; interrupted: " + error.getClass().getSimpleName()));
                universe.removeWorld(name);
            });
        }).whenComplete((done, error) -> RUNNING.set(false));
    }

    private static CompletableFuture<Void> exercise(World world, String camp) {
        UUID eventId = UUID.randomUUID();
        Path prefabPath = RescueCampService.prefabPath(camp);
        return CompletableFuture.runAsync(() -> {
            for (int x = CENTRE - HALF; x <= CENTRE + HALF; x++)
                for (int z = CENTRE - HALF; z <= CENTRE + HALF; z++) {
                    // The previous camp left this ground behind, and writing the same block again reports no change.
                    BlockType existing = ChunkSectionBlockUtil.blockType(world, x, FLOOR_Y, z);
                    if (existing != null && "Soil_Dirt".equals(existing.getId())) continue;
                    require(ChunkSectionBlockUtil.setBlockByKey(world, x, FLOOR_Y, z, "Soil_Dirt", 0), "Fixture ground placement failed");
                }
            require(countAbove(world) == 0, "Fixture was not empty before the camp");
            RescueCampCondition.register(eventId, new RescueCampCondition.Site(prefabPath, new Vector3i(CENTRE, FLOOR_Y, CENTRE), PrefabRotation.ROTATION_0));
            var store = world.getEntityStore().getStore();
            require(EventStartAction.startEvent(store, new WorldEventRecord(eventId, RescueCampService.EVENT_ID, true)) != null, "The world event did not start");
        }, world).thenCompose(ignored -> later(world, 3, () -> {
            var prefab = PrefabSnapshotUtil.getAssetPrefabOrNull(prefabPath);
            require(prefab != null && prefab.getMaxX() - prefab.getMinX() <= 2 * HALF && prefab.getMaxZ() - prefab.getMinZ() <= 2 * HALF, "Camp is larger than the fixture");
            require(countAbove(world) > 20, "The camp was not pasted (" + countAbove(world) + " blocks found)");
            var store = world.getEntityStore().getStore();
            require(store.getResource(WorldEventManager.getResourceType()).get(eventId) != null, "The world event ended before it was stopped");
            EventStopAction.stopEvent(store, eventId, (running, id) -> running.uuid.equals(id));
        })).thenCompose(ignored -> later(world, 4, () -> {
            require(countAbove(world) == 0, "The camp was not removed (" + countAbove(world) + " blocks left)");

            for (int x = CENTRE - HALF; x <= CENTRE + HALF; x++)
                for (int z = CENTRE - HALF; z <= CENTRE + HALF; z++) {
                    BlockType floor = ChunkSectionBlockUtil.blockType(world, x, FLOOR_Y, z);
                    require(floor != null && "Soil_Dirt".equals(floor.getId()), "The ground was not restored at " + x + "," + z);
                }
        }));
    }

    private static int countAbove(World world) {
        int count = 0;
        for (int x = CENTRE - HALF; x <= CENTRE + HALF; x++)
            for (int z = CENTRE - HALF; z <= CENTRE + HALF; z++)
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 20; y++)
                    if (ChunkSectionBlockUtil.blockId(world, x, y, z) != BlockType.EMPTY_ID) count++;
        return count;
    }

    private static CompletableFuture<Void> later(World world, int seconds, Runnable check) {
        return CompletableFuture.runAsync(check, CompletableFuture.delayedExecutor(seconds, TimeUnit.SECONDS, world));
    }

    private static void require(boolean ok, String error) {
        if (!ok) throw new IllegalStateException(error);
    }

    private static void report(CommandContext ctx, String message) {
        ctx.sendMessage(Message.raw(message));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
