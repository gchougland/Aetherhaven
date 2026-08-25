package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.FestivalActivityRosterBroadcast;
import com.hexvane.aetherhaven.ui.FestivalActivityRosterHud;
import com.hexvane.aetherhaven.ui.TreeClimbRaceHud;
import com.hexvane.aetherhaven.ui.TreeClimbRaceHudSupport;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Advances tree climb races: finish detection, DNF timeout, HUD timer refresh, start/return teleports, and lobby
 * reset. Entity writes use CommandBuffer inside chunk iteration.
 */
public final class TreeClimbRaceSystem extends TickingSystem<EntityStore> {
    private static final String ROSTER_LANG = "aetherhaven_festivals.aetherhaven.festival.roster";

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

        for (Map.Entry<UUID, TreeClimbSession> entry : TreeClimbSessionIndex.entries()) {
            UUID townId = entry.getKey();
            TreeClimbSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            String activityKey =
                FestivalActivityRosterBroadcast.activityKey(townId, TreeClimbIds.FESTIVAL_ID);
            if (town == null
                || !TreeClimbIds.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                FestivalActivityRosterBroadcast.clear(activityKey, uuid -> target(playersByUuid, uuid));
                continue;
            }

            publishRoster(activityKey, session, playersByUuid);
            TreeClimbRewards.grantPendingTickets(world, session);

            if (session.consumePendingStartTeleport()) {
                TreeClimbTeleport.teleportJoinedToStartPads(store, session);
            }

            if (session.getPhase() == TreeClimbSession.Phase.RESULTS) {
                for (UUID playerUuid : session.joinedView()) {
                    clearHud(playersByUuid.get(playerUuid));
                }
                if (session.tryReturnToLobby(now) && session.consumePendingReturnTeleport()) {
                    TreeClimbTeleport.teleportToPads(store, session.takePendingReturnPads());
                }
                continue;
            }

            if (session.getPhase() != TreeClimbSession.Phase.RACING) {
                continue;
            }

            if (session.consumeStartWhistle()) {
                Vector3d at = TreeClimbCourse.finishCenter(session);
                if (at == null && !session.startPadsView().isEmpty()) {
                    TreeClimbSession.StartPad pad = session.startPadsView().get(0);
                    at = new Vector3d(pad.x(), pad.y(), pad.z());
                }
                TreeClimbAudio.playRaceStart(store, at);
            }

            double elapsed = session.elapsedSeconds(now);
            for (UUID playerUuid : session.joinedView()) {
                PlayerBundle bundle = playersByUuid.get(playerUuid);
                if (bundle == null) {
                    // A racer who left the world is out straight away, otherwise everyone else waits three
                    // minutes for the lobby to reopen.
                    session.markDnf(playerUuid, now);
                    continue;
                }

                if (session.hasFinished(playerUuid)) {
                    if (session.consumeFinishSfx(playerUuid)) {
                        TransformComponent tc = store.getComponent(bundle.ref(), TransformComponent.getComponentType());
                        Vector3d at =
                            tc != null
                                ? new Vector3d(tc.getPosition().x, tc.getPosition().y, tc.getPosition().z)
                                : TreeClimbCourse.finishCenter(session);
                        TreeClimbAudio.playRaceFinish(store, at);
                        Double finish = session.finishTimeSeconds(playerUuid);
                        if (finish != null) {
                            TreeClimbLeaderboard.recordTime(world, plugin, playerUuid, bundle.playerRef(), finish);
                        }
                    }
                    clearHud(bundle);
                    continue;
                }

                if (elapsed * 1000.0 >= TreeClimbIds.DNF_TIMEOUT_MS) {
                    session.markDnf(playerUuid, now);
                    clearHud(bundle);
                    continue;
                }

                TransformComponent tc = store.getComponent(bundle.ref(), TransformComponent.getComponentType());
                if (tc != null && isOnFinish(session, tc.getPosition())) {
                    session.markFinished(playerUuid, now);
                    if (session.consumeFinishSfx(playerUuid)) {
                        Vector3d at = new Vector3d(tc.getPosition().x, tc.getPosition().y, tc.getPosition().z);
                        TreeClimbAudio.playRaceFinish(store, at);
                        Double finish = session.finishTimeSeconds(playerUuid);
                        if (finish != null) {
                            TreeClimbLeaderboard.recordTime(world, plugin, playerUuid, bundle.playerRef(), finish);
                        }
                    }
                    clearHud(bundle);
                    continue;
                }

                TreeClimbRaceHud hud = TreeClimbRaceHudSupport.obtainHud(bundle.player(), bundle.playerRef());
                hud.refresh(TreeClimbIds.formatTime(elapsed));
            }
        }
    }

    private static void publishRoster(
        @Nonnull String activityKey,
        @Nonnull TreeClimbSession session,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid
    ) {
        List<UUID> joined = session.joinedView();
        List<FestivalActivityRosterHud.Row> rows = new ArrayList<>(joined.size());
        for (UUID playerUuid : joined) {
            rows.add(rosterRow(session, playerUuid, playersByUuid.get(playerUuid)));
        }
        FestivalActivityRosterBroadcast.publish(
            activityKey,
            Message.translation(ROSTER_LANG + ".title.tree_climbing"),
            rows,
            joined,
            uuid -> target(playersByUuid, uuid)
        );
    }

    @Nonnull
    private static FestivalActivityRosterHud.Row rosterRow(
        @Nonnull TreeClimbSession session,
        @Nonnull UUID playerUuid,
        @javax.annotation.Nullable PlayerBundle bundle
    ) {
        Message name =
            bundle != null
                ? Message.raw(bundle.playerRef().getUsername())
                : Message.translation(ROSTER_LANG + ".villager");
        if (session.getPhase() == TreeClimbSession.Phase.LOBBY) {
            return new FestivalActivityRosterHud.Row(
                name,
                Message.translation(ROSTER_LANG + ".status.waiting"),
                null,
                false
            );
        }
        if (session.isDnf(playerUuid)) {
            return new FestivalActivityRosterHud.Row(
                name,
                Message.translation(ROSTER_LANG + ".status.dnf"),
                null,
                true
            );
        }
        Double finish = session.finishTimeSeconds(playerUuid);
        if (finish != null) {
            return new FestivalActivityRosterHud.Row(
                name,
                Message.translation(ROSTER_LANG + ".status.finished")
                    .param("time", Message.raw(TreeClimbIds.formatTime(finish))),
                null,
                true
            );
        }
        return new FestivalActivityRosterHud.Row(
            name,
            Message.translation(ROSTER_LANG + ".status.racing"),
            null,
            false
        );
    }

    @javax.annotation.Nullable
    private static FestivalActivityRosterBroadcast.Target target(
        @Nonnull Map<UUID, PlayerBundle> playersByUuid,
        @Nonnull UUID playerUuid
    ) {
        PlayerBundle bundle = playersByUuid.get(playerUuid);
        return bundle == null ? null : new FestivalActivityRosterBroadcast.Target(bundle.player(), bundle.playerRef());
    }

    private static boolean isOnFinish(@Nonnull TreeClimbSession session, @Nonnull Vector3d pos) {
        double dx = pos.x - session.getFinishWorldX();
        double dz = pos.z - session.getFinishWorldZ();
        if (dx * dx + dz * dz > TreeClimbIds.FINISH_RADIUS * TreeClimbIds.FINISH_RADIUS) {
            return false;
        }
        double dy = pos.y - session.getFinishWorldY();
        return dy >= TreeClimbIds.FINISH_MIN_Y_OFFSET && dy <= TreeClimbIds.FINISH_MAX_Y_OFFSET;
    }

    private static void clearHud(@javax.annotation.Nullable PlayerBundle bundle) {
        if (bundle == null) {
            return;
        }
        if (TreeClimbRaceHudSupport.isActive(bundle.player())) {
            TreeClimbRaceHudSupport.removeHud(bundle.player(), bundle.playerRef());
        }
    }

    @Nonnull
    private static Map<UUID, PlayerBundle> indexPlayers(@Nonnull Store<EntityStore> store) {
        Map<UUID, PlayerBundle> out = new HashMap<>();
        Query<EntityStore> query =
            Query.and(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
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
        @Nonnull com.hypixel.hytale.component.Ref<EntityStore> ref,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {}
}
