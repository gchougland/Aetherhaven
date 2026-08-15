package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.HallowsEveMazeHud;
import com.hexvane.aetherhaven.ui.HallowsEveMazeHudSupport;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Advances maze runs: start teleport, countdown freeze, HUD, orb spawn, and race timeout.
 * Entity writes use CommandBuffer inside chunk iteration.
 */
public final class HallowsEveRaceSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }
        String worldName = world.getName();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long now = System.currentTimeMillis();
        Map<UUID, PlayerBundle> playersByUuid = indexPlayers(store);

        for (Map.Entry<UUID, HallowsEveSession> entry : HallowsEveSessionIndex.entries()) {
            UUID townId = entry.getKey();
            HallowsEveSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null
                || !HallowsEveIds.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                continue;
            }

            UUID racer = session.getPlayerUuid();
            PlayerBundle bundle = racer != null ? playersByUuid.get(racer) : null;

            if (session.consumePendingTeleport()) {
                teleportRacer(store, session, racer, true);
            }

            if (session.getPhase() == HallowsEveSession.Phase.COUNTDOWN) {
                holdRacer(store, session, racer);
                if (bundle != null) {
                    HallowsEveMazeHud hud = HallowsEveMazeHudSupport.obtainHud(bundle.player(), bundle.playerRef());
                    hud.refreshCountdown(HallowsEveIds.formatCountdown(session.countdownSecondsLeft(now)));
                }
                if (session.tickCountdown(now)) {
                    HallowsEveAudio.playRaceStart(store, session);
                }
            }

            if (session.consumePendingThaw()) {
                thawRacer(store, racer);
            }

            if (session.consumePendingOrbSpawn()) {
                PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
                FestivalDefinition festival = plugin.getFestivalCatalog().get(HallowsEveIds.FESTIVAL_ID);
                world.execute(() -> {
                    if (square != null && festival != null) {
                        HallowsEveOrbSpawnService.captureMarkers(world, square, festival, session);
                    }
                    HallowsEveOrbSpawnService.spawnRaceOrbs(world, townId, session);
                });
                if (square != null) {
                    markPumpkinGrowing(store, townId);
                }
            }

            if (session.getPhase() == HallowsEveSession.Phase.RACING) {
                if (bundle != null) {
                    HallowsEveMazeHud hud = HallowsEveMazeHudSupport.obtainHud(bundle.player(), bundle.playerRef());
                    hud.refreshRace(
                        HallowsEveIds.formatRaceTime(session.raceRemainingMs(now)),
                        session.getCollected() + " / " + session.getTotalOrbs()
                    );
                }
                boolean timeUp = session.raceRemainingMs(now) <= 0L;
                int collected = session.getCollected();
                int total = session.getTotalOrbs();
                long remainingMs = session.raceRemainingMs(now);
                if (session.tickRace(now)) {
                    HallowsEveAudio.playRaceFinish(store, session);
                    if (bundle != null && racer != null && collected > 0) {
                        HallowsEveLeaderboard.recordRun(
                            world,
                            plugin,
                            racer,
                            bundle.playerRef(),
                            collected,
                            total,
                            remainingMs
                        );
                    }
                    if (bundle != null) {
                        if (total > 0 && collected >= total) {
                            bundle.playerRef()
                                .sendMessage(
                                    Message.translation(
                                            "aetherhaven_festivals.aetherhaven.festival.hallows_eve.chat.collectedAll"
                                        )
                                        .param("time", HallowsEveScore.formatSecondsLeft(remainingMs))
                                );
                        } else if (timeUp && collected > 0) {
                            bundle.playerRef()
                                .sendMessage(
                                    Message.translation(
                                            "aetherhaven_festivals.aetherhaven.festival.hallows_eve.chat.timeUp"
                                        )
                                        .param("collected", String.valueOf(collected))
                                        .param("total", String.valueOf(total))
                                );
                        } else if (timeUp) {
                            bundle.playerRef()
                                .sendMessage(
                                    Message.translation(
                                        "aetherhaven_festivals.aetherhaven.festival.hallows_eve.chat.timeUp.none"
                                    )
                                );
                        }
                    }
                    world.execute(() -> HallowsEveOrbSpawnService.despawnRaceOrbs(world, townId));
                    markPumpkinReadyOrReset(store, townId, session);
                    if (bundle != null) {
                        HallowsEveMazeHudSupport.removeHud(bundle.player(), bundle.playerRef());
                    }
                }
            }

            if (session.getPhase() == HallowsEveSession.Phase.IDLE && bundle != null) {
                HallowsEveMazeHudSupport.removeHud(bundle.player(), bundle.playerRef());
            }
        }
    }

    private static void teleportRacer(
        @Nonnull Store<EntityStore> store,
        @Nonnull HallowsEveSession session,
        @Nullable UUID racer,
        boolean freeze
    ) {
        if (racer == null) {
            return;
        }
        store.forEachChunk(
            Query.and(Player.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !racer.equals(uc.getUuid())) {
                        continue;
                    }
                    HallowsEveTeleport.applyStartPad(
                        chunk.getReferenceTo(i),
                        chunk,
                        i,
                        commandBuffer,
                        session,
                        freeze
                    );
                }
            }
        );
    }

    private static void holdRacer(
        @Nonnull Store<EntityStore> store,
        @Nonnull HallowsEveSession session,
        @Nullable UUID racer
    ) {
        teleportRacer(store, session, racer, true);
    }

    private static void thawRacer(@Nonnull Store<EntityStore> store, @Nullable UUID racer) {
        if (racer == null) {
            return;
        }
        store.forEachChunk(
            Query.and(Player.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !racer.equals(uc.getUuid())) {
                        continue;
                    }
                    HallowsEveTeleport.thaw(chunk.getReferenceTo(i), commandBuffer);
                }
            }
        );
    }

    private static void markPumpkinGrowing(@Nonnull Store<EntityStore> store, @Nonnull UUID townId) {
        store.forEachChunk(
            Query.and(HallowsEvePumpkinComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEvePumpkinComponent pumpkin =
                        chunk.getComponent(i, HallowsEvePumpkinComponent.getComponentType());
                    if (pumpkin == null || !townId.equals(pumpkin.getTownId())) {
                        continue;
                    }
                    pumpkin.setState(HallowsEvePumpkinComponent.STATE_GROWING);
                    commandBuffer.putComponent(
                        chunk.getReferenceTo(i),
                        HallowsEvePumpkinComponent.getComponentType(),
                        pumpkin
                    );
                }
            }
        );
    }

    private static void markPumpkinReadyOrReset(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull HallowsEveSession session
    ) {
        store.forEachChunk(
            Query.and(HallowsEvePumpkinComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HallowsEvePumpkinComponent pumpkin =
                        chunk.getComponent(i, HallowsEvePumpkinComponent.getComponentType());
                    if (pumpkin == null || !townId.equals(pumpkin.getTownId())) {
                        continue;
                    }
                    if (session.getPhase() == HallowsEveSession.Phase.READY_TO_BURST) {
                        pumpkin.setState(HallowsEvePumpkinComponent.STATE_READY);
                    } else {
                        pumpkin.resetForNextRun();
                    }
                    commandBuffer.putComponent(
                        chunk.getReferenceTo(i),
                        HallowsEvePumpkinComponent.getComponentType(),
                        pumpkin
                    );
                }
            }
        );
    }

    @Nonnull
    private static Map<UUID, PlayerBundle> indexPlayers(@Nonnull Store<EntityStore> store) {
        Map<UUID, PlayerBundle> out = new HashMap<>();
        store.forEachChunk(
            Query.and(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    Player player = chunk.getComponent(i, Player.getComponentType());
                    PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
                    if (uc == null || player == null || playerRef == null) {
                        continue;
                    }
                    out.put(uc.getUuid(), new PlayerBundle(player, playerRef));
                }
            }
        );
        return out;
    }

    private record PlayerBundle(@Nonnull Player player, @Nonnull PlayerRef playerRef) {}
}
