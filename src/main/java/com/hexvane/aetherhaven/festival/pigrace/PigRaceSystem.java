package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Advances racing pigs along their lanes and keeps idle pigs on the start line. Tick mutations go through
 * {@link CommandBuffer}. Progress is stored on the racer component so physics or AI cannot yank a pig backward.
 */
public final class PigRaceSystem extends EntityTickingSystem<EntityStore> {
    /** Livestock model AnimationSet id shared by Pig / Boar / wild / undead variants. */
    private static final String RACE_MOVE_ANIM = "Run";

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(new SystemDependency<>(Order.AFTER, SteeringSystem.class));

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            PigRaceRacerComponent.getComponentType(),
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PigRaceRacerComponent racer = chunk.getComponent(index, PigRaceRacerComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (ref == null || !ref.isValid() || racer == null || tc == null || npc == null) {
            return;
        }
        UUID townId = racer.getTownId();
        if (townId == null) {
            return;
        }
        PigRaceSession session = PigRaceSessionIndex.get(townId);
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (session.getPhase() == PigRaceSession.Phase.RESULTS) {
            if (session.tryReturnToLobby(now)) {
                // After every pig finishes, hold the finish camera, then restore players and pigs.
                PigRaceCamera.deactivateAll(store, session);
                PigRaceSpawnService.resetRacersToStart(store, commandBuffer, session);
                return;
            }
            // Winner already settled; let the rest keep running to the line instead of snapping there.
            UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
            PigRaceSession.Racer sessionRacer =
                uuidComponent != null ? session.racerForEntity(uuidComponent.getUuid()) : null;
            double speedMult =
                sessionRacer != null ? sessionRacer.speedMultiplier() : racer.getSpeedMultiplier();
            double trackLen = racer.trackLength();
            if (trackLen < 1.0e-3) {
                session.markLaneFinished(racer.getLaneIndex(), now);
                return;
            }
            double speed = PigRaceLanes.BASE_SPEED_BLOCKS_PER_SEC * speedMult;
            double step = speed * Math.max(0f, dt);
            double nextProgress = Math.min(trackLen, racer.getRaceProgress() + step);
            boolean stillRunning = nextProgress + 1.0e-3 < trackLen;
            placeAlongTrack(
                ref,
                store,
                commandBuffer,
                racer,
                npc,
                nextProgress,
                stillRunning ? speed : 0.0
            );
            if (!stillRunning) {
                session.markLaneFinished(racer.getLaneIndex(), now);
            }
            return;
        }
        if (session.getPhase() == PigRaceSession.Phase.LOBBY) {
            if (session.consumeNeedsReturnToStart()) {
                PigRaceSpawnService.resetRacersToStart(store, commandBuffer, session);
            } else {
                placeAlongTrack(ref, store, commandBuffer, racer, npc, 0.0, 0.0);
            }
            return;
        }
        if (session.getPhase() != PigRaceSession.Phase.RACING) {
            return;
        }

        // Dialogue only flips phase; entity writes happen here via CommandBuffer.
        // Music starts immediately; pigs wait through RACE_START_DELAY_MS for whistle / hooves / the run.
        if (session.consumeRaceStartMusic()) {
            PigRaceSpawnService.resetRacersToStart(store, commandBuffer, session);
            PigRaceSpawnService.applyRolledSpeeds(store, commandBuffer, session);
            TownAudioContext startCtx = resolveTownAudio(store, townId);
            if (startCtx != null) {
                PigRaceAudio.onRaceMusicStarted(store, commandBuffer, startCtx.town(), startCtx.center());
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin != null) {
                    PigRaceCamera.activateForBettors(store, session, plugin, startCtx.town());
                }
            }
            placeAlongTrack(ref, store, commandBuffer, racer, npc, 0.0, 0.0);
            return;
        }
        if (racer.getLaneIndex() == 0) {
            TownAudioContext musicCtx = resolveTownAudio(store, townId);
            if (musicCtx != null) {
                PigRaceAudio.tickWhileRacing(store, commandBuffer, session, musicCtx.town(), musicCtx.center());
            }
        }
        if (session.isWaitingToGo(now)) {
            placeAlongTrack(ref, store, commandBuffer, racer, npc, 0.0, 0.0);
            return;
        }
        if (session.consumeRaceGoCue(now)) {
            TownAudioContext goCtx = resolveTownAudio(store, townId);
            if (goCtx != null) {
                PigRaceAudio.onRaceGo(store, commandBuffer, session, goCtx.center());
            }
        }

        // Prefer session-rolled speed after beginRacing; component may lag one buffer consume.
        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
        PigRaceSession.Racer sessionRacer =
            uuidComponent != null ? session.racerForEntity(uuidComponent.getUuid()) : null;
        double speedMult =
            sessionRacer != null ? sessionRacer.speedMultiplier() : racer.getSpeedMultiplier();

        double trackLen = racer.trackLength();
        if (trackLen < 1.0e-3) {
            return;
        }
        double speed = PigRaceLanes.BASE_SPEED_BLOCKS_PER_SEC * speedMult;
        double step = speed * Math.max(0f, dt);
        double nextProgress = Math.min(trackLen, racer.getRaceProgress() + step);
        placeAlongTrack(ref, store, commandBuffer, racer, npc, nextProgress, speed);

        if (session.consumeFinishCamera(nextProgress, trackLen)) {
            TownAudioContext finishCamCtx = resolveTownAudio(store, townId);
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (finishCamCtx != null && plugin != null) {
                PigRaceCamera.switchToFinishCamera(store, session, plugin, finishCamCtx.town());
            }
        }

        if (nextProgress + 1.0e-3 < trackLen) {
            return;
        }

        if (session.finishRace(racer.getLaneIndex(), now, PigRaceLanes.RESULTS_HOLD_MS)) {
            // Banner and finish sting play immediately; other pigs keep running until they finish too.
            TownAudioContext ctx = resolveTownAudio(store, townId);
            if (ctx != null) {
                PigRaceAudio.onRaceFinished(store, commandBuffer, session, ctx.center());
                PigRaceAnnounce.announceWinner(store, ctx.town(), racer.getLaneIndex(), ctx.center());
            }
        }
    }

    @Nullable
    private static TownAudioContext resolveTownAudio(@Nonnull Store<EntityStore> store, @Nonnull UUID townId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return null;
        }
        return new TownAudioContext(town, PigRaceAudio.squareCenter(plugin, town));
    }

    private static void placeAlongTrack(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceRacerComponent racer,
        @Nonnull NPCEntity npc,
        double progress,
        double speed
    ) {
        double trackLen = Math.max(racer.trackLength(), 1.0e-3);
        double clamped = Math.max(0.0, Math.min(trackLen, progress));
        double nx = (racer.getFinishX() - racer.getStartX()) / trackLen;
        double ny = (racer.getFinishY() - racer.getStartY()) / trackLen;
        double nz = (racer.getFinishZ() - racer.getStartZ()) / trackLen;
        Vector3d pos =
            new Vector3d(
                racer.getStartX() + nx * clamped,
                racer.getStartY() + ny * clamped,
                racer.getStartZ() + nz * clamped
            );
        float yaw = PigRaceLanes.facingYawRadians(nx, nz);
        TransformComponentUtil.replacePreservingChunk(ref, store, commandBuffer, pos, new Rotation3f(0f, yaw, 0f));
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            if (speed > 0.0) {
                velocity.set(nx * speed, ny * speed, nz * speed);
            } else {
                velocity.set(0, 0, 0);
            }
            commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
        }
        npc.setLeashPoint(pos);
        commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
        commandBuffer.putComponent(ref, PigRaceRacerComponent.getComponentType(), racer.withProgress(clamped));
        if (speed > 0.0) {
            NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, RACE_MOVE_ANIM, commandBuffer);
        } else {
            NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, null, commandBuffer);
        }
    }

    private record TownAudioContext(@Nonnull TownRecord town, @Nullable Vector3d center) {}
}
