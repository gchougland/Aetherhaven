package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalRaceLanes;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns, resets, and despawns the four race pigs. */
public final class PigRaceSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Avoid double spawn attempts when dialogue evaluates bet and start in the same open. */
    private static final long MERCHANT_SPAWN_RETRY_COOLDOWN_MS = 5_000L;
    private static final Map<UUID, Long> LAST_MERCHANT_SPAWN_ATTEMPT_MS = new ConcurrentHashMap<>();

    private PigRaceSpawnService() {}

    @Nonnull
    public static List<PigRaceSession.Racer> spawnRacers(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square
    ) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("Pig race spawn skipped: NPC support is not available");
            return List.of();
        }
        List<PigRaceSession.Racer> racers = new ArrayList<>();
        UUID townId = town.getTownId();
        FestivalDefinition festival = resolveFestival(plugin, town);
        for (PigRaceLanes.Lane lane : FestivalRaceLanes.resolve(festival)) {
            Vector3d start =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    lane.startLocalX(),
                    lane.startLocalY(),
                    lane.startLocalZ()
                );
            Vector3d finish =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    lane.finishLocalX(),
                    lane.finishLocalY(),
                    lane.finishLocalZ()
                );
            double dx = finish.x - start.x;
            double dz = finish.z - start.z;
            float yaw = PigRaceLanes.facingYawRadians(dx, dz);
            Rotation3f rotation = new Rotation3f(0f, yaw, 0f);
            var pair = npcPlugin.spawnNPC(store, lane.npcRoleId(), null, start, rotation);
            if (pair == null) {
                LOGGER.atWarning().log("Pig race could not spawn role %s", lane.npcRoleId());
                continue;
            }
            Ref<EntityStore> ref = pair.first();
            store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            double speed = PigRaceSession.rollSpeedMultiplier();
            store.putComponent(
                ref,
                PigRaceRacerComponent.getComponentType(),
                PigRaceRacerComponent.create(
                    townId,
                    lane.index(),
                    speed,
                    start.x,
                    start.y,
                    start.z,
                    finish.x,
                    finish.y,
                    finish.z
                )
            );
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                npc.setLeashPoint(new Vector3d(start.x, start.y, start.z));
                store.putComponent(ref, NPCEntity.getComponentType(), npc);
            }
            NpcSpawnOriginUtil.attach(store, ref, "FESTIVAL_PIG_RACE", "festival=pig_race", world, start);
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                continue;
            }
            racers.add(
                new PigRaceSession.Racer(
                    lane.index(),
                    uc.getUuid(),
                    speed,
                    start.x,
                    start.y,
                    start.z,
                    finish.x,
                    finish.y,
                    finish.z
                )
            );
        }
        return racers;
    }

    /** Puts every race pig back at its start line and faces it down the track. */
    public static void resetRacersToStart(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session
    ) {
        for (PigRaceSession.Racer racer : session.racersView()) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(racer.entityUuid());
            if (ref == null || !ref.isValid()) {
                continue;
            }
            double dx = racer.finishX() - racer.startX();
            double dz = racer.finishZ() - racer.startZ();
            float yaw = PigRaceLanes.facingYawRadians(dx, dz);
            Vector3d start = new Vector3d(racer.startX(), racer.startY(), racer.startZ());
            Rotation3f rotation = new Rotation3f(0f, yaw, 0f);
            if (commandBuffer != null) {
                TransformComponentUtil.replacePreservingChunk(ref, store, commandBuffer, start, rotation);
                Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
                if (velocity != null) {
                    velocity.set(0, 0, 0);
                    commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
                }
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.setLeashPoint(start);
                    commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
                }
                PigRaceRacerComponent component = store.getComponent(ref, PigRaceRacerComponent.getComponentType());
                if (component != null) {
                    UUID townId = component.getTownId() != null ? component.getTownId() : new UUID(0, 0);
                    commandBuffer.putComponent(
                        ref,
                        PigRaceRacerComponent.getComponentType(),
                        PigRaceRacerComponent.create(
                            townId,
                            racer.laneIndex(),
                            racer.speedMultiplier(),
                            racer.startX(),
                            racer.startY(),
                            racer.startZ(),
                            racer.finishX(),
                            racer.finishY(),
                            racer.finishZ()
                        )
                    );
                }
            } else {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    store.putComponent(ref, TransformComponent.getComponentType(), new TransformComponent(start, rotation));
                }
                Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
                if (velocity != null) {
                    velocity.set(0, 0, 0);
                    store.putComponent(ref, Velocity.getComponentType(), velocity);
                }
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.setLeashPoint(start);
                    store.putComponent(ref, NPCEntity.getComponentType(), npc);
                }
                PigRaceRacerComponent component = store.getComponent(ref, PigRaceRacerComponent.getComponentType());
                if (component != null && component.getTownId() != null) {
                    store.putComponent(
                        ref,
                        PigRaceRacerComponent.getComponentType(),
                        PigRaceRacerComponent.create(
                            component.getTownId(),
                            racer.laneIndex(),
                            racer.speedMultiplier(),
                            racer.startX(),
                            racer.startY(),
                            racer.startZ(),
                            racer.finishX(),
                            racer.finishY(),
                            racer.finishZ()
                        )
                    );
                }
            }
        }
    }

    /** Applies newly rolled race speeds onto the racer components before a race starts. */
    public static void applyRolledSpeeds(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session
    ) {
        for (PigRaceSession.Racer racer : session.racersView()) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(racer.entityUuid());
            if (ref == null || !ref.isValid()) {
                continue;
            }
            PigRaceRacerComponent existing = store.getComponent(ref, PigRaceRacerComponent.getComponentType());
            if (existing == null || existing.getTownId() == null) {
                continue;
            }
            commandBuffer.putComponent(
                ref,
                PigRaceRacerComponent.getComponentType(),
                existing.withSpeed(racer.speedMultiplier()).withProgress(0.0)
            );
        }
    }

    public static void despawnRacers(@Nonnull World world, @Nonnull List<UUID> entityUuids) {
        if (entityUuids.isEmpty()) {
            return;
        }
        PendingEntityRemovalService.scheduleAll(world, entityUuids, "pig_race_despawn");
    }

    public static void despawnSessionRacers(@Nonnull World world, @Nonnull PigRaceSession session) {
        List<UUID> uuids = new ArrayList<>();
        for (PigRaceSession.Racer racer : session.racersView()) {
            uuids.add(racer.entityUuid());
        }
        session.clearRaceEntities();
        despawnRacers(world, uuids);
    }

    /**
     * Used when a player talks to the race merchant after a restart: rebind pigs that are already in the world, or
     * spawn them once if none are present. No-op when the lobby already has living racers.
     */
    public static void ensureRacersForFestival(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square
    ) {
        UUID townId = town.getTownId();
        PigRaceSession session = PigRaceSessionIndex.getOrCreate(townId);
        FestivalDefinition festival = resolveFestival(plugin, town);
        int expected = FestivalRaceLanes.resolve(festival).size();
        if (countLivingSessionRacers(store, session) >= expected) {
            return;
        }
        List<PigRaceSession.Racer> found = collectRacersForTown(store, townId);
        if (found.size() >= expected) {
            session.setRacers(found);
            resetRacersToStart(store, null, session);
            return;
        }
        if (!found.isEmpty()) {
            // Partial set after a restart: keep what exists rather than despawning and respawning.
            session.setRacers(found);
            LAST_MERCHANT_SPAWN_ATTEMPT_MS.remove(townId);
            return;
        }
        long now = System.currentTimeMillis();
        Long lastAttempt = LAST_MERCHANT_SPAWN_ATTEMPT_MS.get(townId);
        if (lastAttempt != null && now - lastAttempt < MERCHANT_SPAWN_RETRY_COOLDOWN_MS) {
            return;
        }
        LAST_MERCHANT_SPAWN_ATTEMPT_MS.put(townId, now);
        List<PigRaceSession.Racer> spawned = spawnRacers(world, store, plugin, town, square);
        session.setRacers(spawned);
        if (spawned.size() >= expected) {
            LAST_MERCHANT_SPAWN_ATTEMPT_MS.remove(townId);
        } else {
            LOGGER.atWarning().log(
                "Pig race merchant ensure spawned %s/%s pigs for town %s",
                spawned.size(),
                expected,
                townId
            );
        }
    }

    private static int countLivingSessionRacers(
        @Nonnull Store<EntityStore> store,
        @Nonnull PigRaceSession session
    ) {
        int living = 0;
        for (PigRaceSession.Racer racer : session.racersView()) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(racer.entityUuid());
            if (ref != null && ref.isValid()) {
                living++;
            }
        }
        return living;
    }

    @Nonnull
    private static List<PigRaceSession.Racer> collectRacersForTown(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        Map<Integer, PigRaceSession.Racer> byLane = new HashMap<>();
        Query<EntityStore> query =
            Query.and(PigRaceRacerComponent.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                PigRaceRacerComponent component = chunk.getComponent(i, PigRaceRacerComponent.getComponentType());
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (component == null || uc == null || !townId.equals(component.getTownId())) {
                    continue;
                }
                byLane.putIfAbsent(
                    component.getLaneIndex(),
                    new PigRaceSession.Racer(
                        component.getLaneIndex(),
                        uc.getUuid(),
                        component.getSpeedMultiplier(),
                        component.getStartX(),
                        component.getStartY(),
                        component.getStartZ(),
                        component.getFinishX(),
                        component.getFinishY(),
                        component.getFinishZ()
                    )
                );
            }
        });
        List<PigRaceSession.Racer> ordered = new ArrayList<>();
        List<Integer> laneIndexes = new ArrayList<>(byLane.keySet());
        laneIndexes.sort(Integer::compareTo);
        for (int index : laneIndexes) {
            PigRaceSession.Racer racer = byLane.get(index);
            if (racer != null) {
                ordered.add(racer);
            }
        }
        return ordered;
    }

    @Nullable
    private static FestivalDefinition resolveFestival(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        String festivalId = town.getActiveFestivalId();
        return festivalId != null ? plugin.getFestivalCatalog().get(festivalId) : null;
    }
}
