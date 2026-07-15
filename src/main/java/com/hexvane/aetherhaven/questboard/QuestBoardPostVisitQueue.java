package com.hexvane.aetherhaven.questboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Transient dawn flavor queue: quest givers walk to the board to "post" today's offers. Not persisted; not a Store.
 */
public final class QuestBoardPostVisitQueue {
    /**
     * Spacing between posters through the morning so several givers do not pile onto the board at once.
     * ~75s keeps a handful of posters spread over several minutes after dawn.
     */
    public static final long STAGGER_MS = 75_000L;

    /** First poster may wait a bit after dawn so the town is waking up before anyone walks over. */
    public static final long FIRST_POSTER_DELAY_MS = 20_000L;

    private static final Map<UUID, List<PendingVisit>> BY_TOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ENQUEUE_DAWN_BY_TOWN = new ConcurrentHashMap<>();

    private QuestBoardPostVisitQueue() {}

    /**
     * Enqueues OFFER givers for one dawn crossing per town (extra players the same dawn do not reset the queue).
     */
    public static void enqueueOfferGiversForDawn(
        @Nonnull UUID townId,
        @Nonnull Iterable<String> giverEntityUuidStrings,
        long nowEpochMs,
        long dawnDay
    ) {
        Long prev = LAST_ENQUEUE_DAWN_BY_TOWN.put(townId, dawnDay);
        if (prev != null && prev >= dawnDay) {
            return;
        }
        enqueueOfferGivers(townId, giverEntityUuidStrings, nowEpochMs);
    }

    public static void enqueueOfferGivers(
        @Nonnull UUID townId,
        @Nonnull Iterable<String> giverEntityUuidStrings,
        long nowEpochMs
    ) {
        List<UUID> unique = new ArrayList<>();
        for (String raw : giverEntityUuidStrings) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            UUID u;
            try {
                u = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!unique.contains(u)) {
                unique.add(u);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        List<PendingVisit> queue = BY_TOWN.computeIfAbsent(townId, k -> new ArrayList<>());
        synchronized (queue) {
            // Small per-town phase so towns do not all send the first poster at the same wall-clock second.
            long slot = nowEpochMs + FIRST_POSTER_DELAY_MS + (Math.floorMod(townId.getLeastSignificantBits(), 15_000L));
            for (UUID giver : unique) {
                removeLocked(queue, giver);
                queue.add(new PendingVisit(giver, slot));
                slot += STAGGER_MS;
            }
        }
    }

    /** True when this NPC is queued and its staggered start time has passed. */
    public static boolean isDue(@Nonnull UUID townId, @Nonnull UUID npcUuid, long nowEpochMs) {
        List<PendingVisit> queue = BY_TOWN.get(townId);
        if (queue == null) {
            return false;
        }
        synchronized (queue) {
            for (PendingVisit v : queue) {
                if (v.npcUuid.equals(npcUuid) && nowEpochMs >= v.earliestEpochMs) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nonnull
    public static List<UUID> townIdsWithPending() {
        return List.copyOf(BY_TOWN.keySet());
    }

    /** Due NPC UUIDs for {@code townId} at {@code nowEpochMs} (copy; safe outside the lock). */
    @Nonnull
    public static List<UUID> dueNpcUuids(@Nonnull UUID townId, long nowEpochMs) {
        List<PendingVisit> queue = BY_TOWN.get(townId);
        if (queue == null) {
            return List.of();
        }
        List<UUID> due = new ArrayList<>();
        synchronized (queue) {
            for (PendingVisit v : queue) {
                if (nowEpochMs >= v.earliestEpochMs) {
                    due.add(v.npcUuid);
                }
            }
        }
        return due;
    }

    /**
     * Dawn enqueue used wall-clock millis while autonomy uses {@code TimeResource}. Entries scheduled years ahead of
     * game time never become due — rebase them onto the autonomy clock.
     */
    public static void rebaseIfWallClockSkew(long autonomyNowMs) {
        final long skewThresholdMs = 30L * 24L * 60L * 60L * 1000L;
        for (List<PendingVisit> queue : BY_TOWN.values()) {
            synchronized (queue) {
                boolean skew = false;
                for (PendingVisit v : queue) {
                    if (Math.abs(v.earliestEpochMs - autonomyNowMs) > skewThresholdMs) {
                        skew = true;
                        break;
                    }
                }
                if (!skew || queue.isEmpty()) {
                    continue;
                }
                List<UUID> givers = new ArrayList<>(queue.size());
                for (PendingVisit v : queue) {
                    givers.add(v.npcUuid);
                }
                queue.clear();
                long slot = autonomyNowMs + FIRST_POSTER_DELAY_MS;
                for (UUID giver : givers) {
                    queue.add(new PendingVisit(giver, slot));
                    slot += STAGGER_MS;
                }
            }
        }
    }

    /**
     * Removes a pending visit for {@code npcUuid}. Call after travel begins (or after giving up) so we do not spam.
     */
    public static void consume(@Nonnull UUID townId, @Nonnull UUID npcUuid) {
        List<PendingVisit> queue = BY_TOWN.get(townId);
        if (queue == null) {
            return;
        }
        synchronized (queue) {
            removeLocked(queue, npcUuid);
            if (queue.isEmpty()) {
                BY_TOWN.remove(townId, queue);
            }
        }
    }

    public static void clearTown(@Nonnull UUID townId) {
        BY_TOWN.remove(townId);
        LAST_ENQUEUE_DAWN_BY_TOWN.remove(townId);
    }

    private static void removeLocked(@Nonnull List<PendingVisit> queue, @Nonnull UUID npcUuid) {
        Iterator<PendingVisit> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next().npcUuid.equals(npcUuid)) {
                it.remove();
            }
        }
    }

    private static final class PendingVisit {
        final UUID npcUuid;
        final long earliestEpochMs;

        PendingVisit(@Nonnull UUID npcUuid, long earliestEpochMs) {
            this.npcUuid = npcUuid;
            this.earliestEpochMs = earliestEpochMs;
        }
    }
}
