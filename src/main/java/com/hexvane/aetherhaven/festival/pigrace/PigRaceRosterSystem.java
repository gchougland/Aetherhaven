package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.FestivalActivityRosterBroadcast;
import com.hexvane.aetherhaven.ui.FestivalActivityRosterHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-town pass over the live races: shows every bettor and the pig they backed to the players with money on the
 * race, and hands the winnings to whoever is online the moment the race settles. The race itself is advanced by
 * {@link PigRaceSystem}, which ticks per pig and so can drive neither a per-town overlay nor a per-player payout.
 */
public final class PigRaceRosterSystem extends TickingSystem<EntityStore> {
    private static final String ROSTER_LANG = "aetherhaven_festivals.aetherhaven.festival.roster";
    private static final Message TITLE = Message.translation(ROSTER_LANG + ".title.pig_race");
    private static final Message EMPTY = Message.translation(ROSTER_LANG + ".empty.pig_race");

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
        Map<UUID, PlayerBundle> playersByUuid = indexPlayers(store);

        for (Map.Entry<UUID, PigRaceSession> entry : PigRaceSessionIndex.entries()) {
            UUID townId = entry.getKey();
            PigRaceSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            TownRecord town = tm.getTown(townId);
            String activityKey =
                FestivalActivityRosterBroadcast.activityKey(townId, PigRaceLanes.FESTIVAL_ID);
            if (town == null
                || !PigRaceLanes.FESTIVAL_ID.equals(town.getActiveFestivalId())
                || !worldName.equals(town.getWorldName())) {
                FestivalActivityRosterBroadcast.clear(activityKey, uuid -> target(playersByUuid, uuid));
                continue;
            }
            publishRoster(activityKey, session, playersByUuid);
            payOutWinners(store, session, playersByUuid);
        }
    }

    /**
     * Hands over tickets as soon as the race settles. Anyone offline at that point keeps their payout queued, so the
     * merchant's "collect winnings" line still works when they come back.
     */
    private static void payOutWinners(
        @Nonnull Store<EntityStore> store,
        @Nonnull PigRaceSession session,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid
    ) {
        for (UUID playerUuid : session.unsettledBettors()) {
            PlayerBundle bundle = playersByUuid.get(playerUuid);
            if (bundle == null) {
                continue;
            }
            int tickets = session.collectWinnings(playerUuid);
            boolean lost = session.acknowledgeLoss(playerUuid);
            if (tickets > 0) {
                FestivalRewardNotify.giveAndNotify(
                    bundle.player(),
                    bundle.ref(),
                    store,
                    new ItemStack(PigRaceLanes.SPRING_TICKET_ITEM_ID, tickets)
                );
            } else if (lost) {
                FestivalRewardNotify.notifyLoss(store, bundle.ref());
            }
        }
    }

    private static void publishRoster(
        @Nonnull String activityKey,
        @Nonnull PigRaceSession session,
        @Nonnull Map<UUID, PlayerBundle> playersByUuid
    ) {
        boolean settled = session.getPhase() == PigRaceSession.Phase.RESULTS;
        Map<UUID, PigRaceSession.Bet> bets = settled ? session.settledBetsView() : session.betsView();
        int winningLane = settled ? session.getWinningLane() : -1;
        List<UUID> audience = new ArrayList<>(bets.keySet());
        List<FestivalActivityRosterHud.Row> rows = new ArrayList<>(bets.size());
        for (Map.Entry<UUID, PigRaceSession.Bet> bet : bets.entrySet()) {
            rows.add(bettorRow(playersByUuid.get(bet.getKey()), bet.getValue(), winningLane, settled));
        }
        FestivalActivityRosterBroadcast.publish(
            activityKey,
            TITLE,
            rows,
            audience,
            uuid -> target(playersByUuid, uuid),
            EMPTY
        );
    }

    @Nonnull
    private static FestivalActivityRosterHud.Row bettorRow(
        @Nullable PlayerBundle bundle,
        @Nonnull PigRaceSession.Bet bet,
        int winningLane,
        boolean settled
    ) {
        Message name =
            bundle != null
                ? Message.raw(bundle.playerRef().getUsername())
                : Message.translation(ROSTER_LANG + ".offline");
        Message pig = Message.translation(PigRaceLanes.shortNameLangKey(bet.laneIndex()));
        if (!settled) {
            return new FestivalActivityRosterHud.Row(
                name,
                Message.translation(ROSTER_LANG + ".status.bet")
                    .param("pig", pig)
                    .param("amount", String.valueOf(bet.amount())),
                null,
                false
            );
        }
        boolean won = bet.laneIndex() == winningLane;
        return new FestivalActivityRosterHud.Row(
            name,
            Message.translation(ROSTER_LANG + (won ? ".status.bet_won" : ".status.bet_lost")).param("pig", pig),
            null,
            !won
        );
    }

    @Nullable
    private static FestivalActivityRosterBroadcast.Target target(
        @Nonnull Map<UUID, PlayerBundle> playersByUuid,
        @Nonnull UUID playerUuid
    ) {
        PlayerBundle bundle = playersByUuid.get(playerUuid);
        return bundle == null ? null : new FestivalActivityRosterBroadcast.Target(bundle.player(), bundle.playerRef());
    }

    @Nonnull
    private static Map<UUID, PlayerBundle> indexPlayers(@Nonnull Store<EntityStore> store) {
        Map<UUID, PlayerBundle> out = new HashMap<>();
        Query<EntityStore> query =
            Query.and(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType());
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
