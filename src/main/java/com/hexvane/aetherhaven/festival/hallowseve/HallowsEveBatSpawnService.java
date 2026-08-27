package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Spawns ambient bats above the Hallow's Eve square and removes them when the festival ends. */
public final class HallowsEveBatSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HallowsEveBatSpawnService() {}

    public static void scheduleEnsureBats(
        @Nonnull World world,
        @Nonnull UUID townId,
        @Nonnull PlotInstance square
    ) {
        world.execute(() -> ensureBats(world, townId, square));
    }

    public static void ensureBats(@Nonnull World world, @Nonnull UUID townId, @Nonnull PlotInstance square) {
        if (!HallowsEveBatComponent.isRegistered()) {
            return;
        }
        var entityStore = world.getEntityStore();
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (entityStore == null || npcPlugin == null) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        int have = countBats(store, townId);
        int missing = HallowsEveIds.BAT_COUNT - have;
        for (int i = 0; i < missing; i++) {
            spawnOne(world, store, npcPlugin, townId, square);
        }
    }

    public static void despawnBats(@Nonnull World world, @Nonnull UUID townId) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !HallowsEveBatComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        store.forEachChunk(
            Query.and(HallowsEveBatComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEveBatComponent bat = chunk.getComponent(i, HallowsEveBatComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (bat == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (townId.equals(bat.getTownId())) {
                        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
            }
        );
    }

    /** Drops bats whose town is not currently running Hallow's Eve in this world. */
    public static void despawnOrphans(@Nonnull Store<EntityStore> store, @Nonnull Set<UUID> activeTownIds) {
        if (!HallowsEveBatComponent.isRegistered()) {
            return;
        }
        store.forEachChunk(
            Query.and(HallowsEveBatComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEveBatComponent bat = chunk.getComponent(i, HallowsEveBatComponent.getComponentType());
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    if (bat == null || ref == null || !ref.isValid()) {
                        continue;
                    }
                    UUID townId = bat.getTownId();
                    if (townId != null && activeTownIds.contains(townId)) {
                        continue;
                    }
                    commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                }
            }
        );
    }

    @Nonnull
    public static Map<UUID, Integer> countByTown(@Nonnull Store<EntityStore> store) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (!HallowsEveBatComponent.isRegistered()) {
            return counts;
        }
        store.forEachChunk(
            Query.and(HallowsEveBatComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEveBatComponent bat = chunk.getComponent(i, HallowsEveBatComponent.getComponentType());
                    if (bat == null || bat.getTownId() == null) {
                        continue;
                    }
                    counts.merge(bat.getTownId(), 1, Integer::sum);
                }
            }
        );
        return counts;
    }

    private static int countBats(@Nonnull Store<EntityStore> store, @Nonnull UUID townId) {
        return countByTown(store).getOrDefault(townId, 0);
    }

    private static void spawnOne(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCPlugin npcPlugin,
        @Nonnull UUID townId,
        @Nonnull PlotInstance square
    ) {
        Vector3d pos = randomAirPosition(square);
        Vector3d leash = flockAnchor(square);
        var pair = npcPlugin.spawnNPC(store, HallowsEveIds.BAT_NPC_ROLE, null, pos, Rotation3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning().log("Hallow's Eve: could not spawn bat role %s", HallowsEveIds.BAT_NPC_ROLE);
            return;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        HallowsEveBatComponent bat = new HallowsEveBatComponent();
        bat.setTownId(townId);
        store.putComponent(ref, HallowsEveBatComponent.getComponentType(), bat);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            npc.setLeashPoint(leash);
            store.putComponent(ref, NPCEntity.getComponentType(), npc);
        }
        NpcSpawnOriginUtil.attach(store, ref, "FESTIVAL_HALLOWS_EVE_BAT", "festival=hallows_eve", world, pos);
    }

    @Nonnull
    private static Vector3d flockAnchor(@Nonnull PlotInstance square) {
        PlotFootprintRecord fp = square.toFootprint();
        return new Vector3d(centerX(fp), fp.getMaxY() + HallowsEveIds.BAT_HEIGHT_ABOVE_PLOT, centerZ(fp));
    }

    @Nonnull
    private static Vector3d randomAirPosition(@Nonnull PlotInstance square) {
        PlotFootprintRecord fp = square.toFootprint();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double pad = 2.0;
        double minX = fp.getMinX() + pad;
        double maxX = fp.getMaxX() + 1.0 - pad;
        double minZ = fp.getMinZ() + pad;
        double maxZ = fp.getMaxZ() + 1.0 - pad;
        if (maxX <= minX) {
            minX = fp.getMinX() + 0.5;
            maxX = fp.getMaxX() + 0.5;
        }
        if (maxZ <= minZ) {
            minZ = fp.getMinZ() + 0.5;
            maxZ = fp.getMaxZ() + 0.5;
        }
        double x = minX + rng.nextDouble() * Math.max(0.01, maxX - minX);
        double z = minZ + rng.nextDouble() * Math.max(0.01, maxZ - minZ);
        double y =
            fp.getMaxY()
                + HallowsEveIds.BAT_HEIGHT_ABOVE_PLOT
                + rng.nextDouble() * HallowsEveIds.BAT_HEIGHT_JITTER;
        return new Vector3d(x, y, z);
    }

    private static double centerX(@Nonnull PlotFootprintRecord fp) {
        return (fp.getMinX() + fp.getMaxX() + 1) * 0.5;
    }

    private static double centerZ(@Nonnull PlotFootprintRecord fp) {
        return (fp.getMinZ() + fp.getMaxZ() + 1) * 0.5;
    }
}
