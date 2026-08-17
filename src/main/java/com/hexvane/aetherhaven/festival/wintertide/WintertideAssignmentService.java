package com.hexvane.aetherhaven.festival.wintertide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Unique secret-santa assignments from town members to villagers and other members. */
public final class WintertideAssignmentService {
    private WintertideAssignmentService() {}

    public record Resident(@Nonnull UUID uuid, @Nonnull String kind, @Nonnull String displayName) {}

    public record PlayerMember(@Nonnull UUID uuid, @Nonnull String displayName) {}

    public static void assignAll(
        @Nonnull WintertideSession session,
        @Nonnull List<PlayerMember> players,
        @Nonnull List<Resident> residents,
        long seed
    ) {
        List<PlayerMember> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparing(p -> p.uuid().toString()));
        List<Resident> sortedResidents = new ArrayList<>(residents);
        sortedResidents.sort(Comparator.comparing(r -> r.uuid().toString()));
        Random rnd = new Random(seed);

        List<WintertideTarget> pool = new ArrayList<>();
        for (Resident r : sortedResidents) {
            pool.add(WintertideTarget.villager(r.uuid(), r.kind(), r.displayName()));
        }
        for (PlayerMember p : sortedPlayers) {
            pool.add(WintertideTarget.player(p.uuid(), p.displayName()));
        }
        Collections.shuffle(pool, rnd);

        for (PlayerMember player : sortedPlayers) {
            if (session.hasOutgoing(player.uuid())) {
                continue;
            }
            WintertideTarget picked = pickUniqueOutgoing(session, player.uuid(), pool);
            if (picked == null) {
                picked = pickAnyOutgoing(player.uuid(), pool, sortedResidents);
            }
            if (picked != null) {
                session.putOutgoing(player.uuid(), picked);
            }
        }

        for (PlayerMember player : sortedPlayers) {
            if (session.getIncoming(player.uuid()) != null) {
                continue;
            }
            UUID giverPlayer = playerGiverFor(session, player.uuid());
            if (giverPlayer != null) {
                PlayerMember giver = findPlayer(sortedPlayers, giverPlayer);
                String name = giver != null ? giver.displayName() : "a town member";
                session.putIncoming(player.uuid(), WintertideTarget.player(giverPlayer, name));
                continue;
            }
            WintertideTarget outgoing = session.getOutgoing(player.uuid());
            UUID avoid = outgoing != null && outgoing.isVillager() ? outgoing.getUuid() : null;
            Resident giver = pickIncomingVillager(session, sortedResidents, avoid, rnd);
            if (giver == null && !sortedResidents.isEmpty()) {
                giver = sortedResidents.get(rnd.nextInt(sortedResidents.size()));
            }
            if (giver != null) {
                session.putIncoming(
                    player.uuid(),
                    WintertideTarget.villager(giver.uuid(), giver.kind(), giver.displayName())
                );
            }
        }
    }

    @Nullable
    private static WintertideTarget pickUniqueOutgoing(
        @Nonnull WintertideSession session,
        @Nonnull UUID playerUuid,
        @Nonnull List<WintertideTarget> pool
    ) {
        var used = session.usedOutgoingTargetUuids();
        for (WintertideTarget t : pool) {
            if (playerUuid.equals(t.getUuid())) {
                continue;
            }
            if (used.contains(t.getUuid())) {
                continue;
            }
            return t;
        }
        return null;
    }

    @Nullable
    private static WintertideTarget pickAnyOutgoing(
        @Nonnull UUID playerUuid,
        @Nonnull List<WintertideTarget> pool,
        @Nonnull List<Resident> residents
    ) {
        for (WintertideTarget t : pool) {
            if (!playerUuid.equals(t.getUuid()) && t.isVillager()) {
                return t;
            }
        }
        for (WintertideTarget t : pool) {
            if (!playerUuid.equals(t.getUuid())) {
                return t;
            }
        }
        if (!residents.isEmpty()) {
            Resident r = residents.get(0);
            return WintertideTarget.villager(r.uuid(), r.kind(), r.displayName());
        }
        return null;
    }

    @Nullable
    private static UUID playerGiverFor(@Nonnull WintertideSession session, @Nonnull UUID receiverUuid) {
        for (UUID giver : session.assignedPlayerUuids()) {
            WintertideTarget t = session.getOutgoing(giver);
            if (t != null && t.isPlayer() && receiverUuid.equals(t.getUuid())) {
                return giver;
            }
        }
        return null;
    }

    @Nullable
    private static Resident pickIncomingVillager(
        @Nonnull WintertideSession session,
        @Nonnull List<Resident> residents,
        @Nullable UUID avoid,
        @Nonnull Random rnd
    ) {
        var used = session.usedIncomingVillagerUuids();
        List<Resident> preferred = new ArrayList<>();
        List<Resident> unused = new ArrayList<>();
        for (Resident r : residents) {
            if (avoid != null && avoid.equals(r.uuid())) {
                continue;
            }
            if (!used.contains(r.uuid())) {
                unused.add(r);
            }
            preferred.add(r);
        }
        List<Resident> pickFrom = unused.isEmpty() ? preferred : unused;
        if (pickFrom.isEmpty()) {
            return null;
        }
        return pickFrom.get(rnd.nextInt(pickFrom.size()));
    }

    @Nullable
    private static PlayerMember findPlayer(@Nonnull List<PlayerMember> players, @Nonnull UUID uuid) {
        for (PlayerMember p : players) {
            if (p.uuid().equals(uuid)) {
                return p;
            }
        }
        return null;
    }

    public static long seedFor(@Nonnull UUID townId, long year) {
        return townId.getMostSignificantBits()
            ^ townId.getLeastSignificantBits()
            ^ (year * 31L)
            ^ 0x57A1E71DL;
    }
}
