package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.SnowballFightHud;
import com.hexvane.aetherhaven.ui.SnowballFightHudSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Advances snowball fights: fill leftover pads, teleports, HUD, pile respawn, and win checks. Entity writes use
 * CommandBuffer; block place/clear uses {@code world.execute}.
 */
public final class SnowballFightSystem extends TickingSystem<EntityStore> {
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

        for (Map.Entry<UUID, SnowballSession> entry : SnowballSessionIndex.entries()) {
            UUID townId = entry.getKey();
            SnowballSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            if (town == null
                || !SnowballIds.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                continue;
            }

            tickPileRespawns(world, session, now);

            if (session.getPhase() == SnowballSession.Phase.LOBBY) {
                continue;
            }

            if (session.consumePendingFill()) {
                Set<UUID> hudPlayers = session.hudPlayerUuids();
                List<UUID> players = session.shuffledJoinedPlayers(ThreadLocalRandom.current());
                List<UUID> fillers =
                    SnowballFillerVillagers.pickLive(
                        store,
                        town,
                        players,
                        SnowballFillerVillagers.leftoverPads(session, players.size())
                    );
                if (!session.fillAndAssign(players, fillers, now)) {
                    clearFightHuds(hudPlayers, playersByUuid);
                    continue;
                }
            }

            if (session.consumePendingStartTeleport()) {
                SnowballTeleport.teleportFightersToPads(store, session);
                SnowballRewards.clearPlayerSnowballs(world, session);
            }

            Set<UUID> outs = session.takePendingOutTeleport();
            if (!outs.isEmpty()) {
                SnowballTeleport.teleportToOut(store, session, outs);
            }

            if (session.getPhase() == SnowballSession.Phase.FIGHTING) {
                Vector3d musicAt = squareCenter(plugin, town);
                SnowballAudio.tickFightMusic(store, town, musicAt, session);
                session.tryFinish(now);
            }

            if (session.consumePendingWinAnnounce()) {
                recordFightHits(world, plugin, session, playersByUuid);
                Vector3d at = squareCenter(plugin, town);
                SnowballAnnounce.announceResult(store, town, session.winningTeam(), at);
                SnowballAudio.stopFightMusic(store, null, session);
                SnowballRewards.grantPendingTickets(world, session);
            }

            if (session.getPhase() == SnowballSession.Phase.RESULTS) {
                SnowballTeleport.thawVillagerFighters(store, session);
                Set<UUID> hudPlayers = session.hudPlayerUuids();
                refreshFightHuds(session, playersByUuid, now);
                if (session.tryReturnToLobby(now)) {
                    clearFightHuds(hudPlayers, playersByUuid);
                }
                continue;
            }

            if (session.getPhase() != SnowballSession.Phase.FIGHTING) {
                continue;
            }
            refreshFightHuds(session, playersByUuid, now);
        }
    }

    private static void tickPileRespawns(
        @Nonnull World world,
        @Nonnull SnowballSession session,
        long now
    ) {
        List<SnowballSession.PileSpot> due = session.duePileRespawns(now);
        if (due.isEmpty()) {
            return;
        }
        world.execute(() -> {
            for (SnowballSession.PileSpot spot : due) {
                SnowballPileService.placePile(world, spot);
                session.markPilePresent(spot);
            }
        });
    }

    private static void recordFightHits(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull SnowballSession session,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid
    ) {
        for (Map.Entry<UUID, Integer> hit : session.playerHitsView().entrySet()) {
            if (hit.getKey() == null || hit.getValue() == null) {
                continue;
            }
            PlayerBundle bundle = playersByUuid.get(hit.getKey());
            SnowballLeaderboard.recordFight(
                world,
                plugin,
                hit.getKey(),
                bundle != null ? bundle.playerRef() : null,
                hit.getValue()
            );
        }
    }

    @Nullable
    private static Vector3d squareCenter(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            return null;
        }
        return FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 6, 0);
    }

    private static void refreshFightHuds(
        @Nonnull SnowballSession session,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid,
        long now
    ) {
        float fraction = session.remainingBarFraction(now);
        for (SnowballSession.Fighter fighter : session.fightersView()) {
            if (!fighter.isPlayer()) {
                continue;
            }
            PlayerBundle bundle = playersByUuid.get(fighter.uuid());
            if (bundle == null) {
                continue;
            }
            SnowballFightHud hud = SnowballFightHudSupport.obtainHud(bundle.player(), bundle.playerRef());
            hud.refresh(fraction, fighter.lives());
        }
    }

    private static void clearFightHuds(
        @Nonnull Set<UUID> playerUuids,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid
    ) {
        for (UUID playerUuid : playerUuids) {
            PlayerBundle bundle = playersByUuid.get(playerUuid);
            if (bundle != null && SnowballFightHudSupport.isActive(bundle.player())) {
                SnowballFightHudSupport.removeHud(bundle.player(), bundle.playerRef());
            }
        }
    }

    @Nonnull
    private static Map<UUID, PlayerBundle> indexPlayers(@Nonnull Store<EntityStore> store) {
        Map<UUID, PlayerBundle> out = new HashMap<>();
        Query<EntityStore> query =
            Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                Player player = chunk.getComponent(i, Player.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                if (uc == null || player == null || pr == null) {
                    continue;
                }
                out.put(uc.getUuid(), new PlayerBundle(chunk.getReferenceTo(i), player, pr));
            }
        });
        return out;
    }

    private record PlayerBundle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {}
}
