package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Measures where workplace crafting time goes, so its pace can be tuned from numbers and not from impressions.
 *
 * <p>The supply planner shows this class every batch each time it runs. A batch's state is derived from what it holds,
 * so the time spent waiting for a courier, on the road, waiting for a worker and being worked can be read off without
 * touching the batch itself. After every {@link #REPORT_EVERY} finished batches a town logs its averages.
 */
public final class WorkplaceBatchTimings {
    static final int REPORT_EVERY = 10;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    private static final Map<UUID, Totals> TOTALS = new HashMap<>();

    /** One batch being watched: how long it has spent in each stage so far. */
    static final class Tracked {
        final UUID town;
        long lastSeen;
        long awaitingCourier;
        long inTransit;
        long awaitingWorker;
        long working;

        Tracked(UUID town, long now) {
            this.town = town;
            this.lastSeen = now;
        }

        void add(WarehouseBatch.State state, boolean worked, long elapsed) {
            switch (state) {
                case AWAITING_PICKUP -> awaitingCourier += elapsed;
                case IN_TRANSIT -> inTransit += elapsed;
                case READY -> {
                    if (worked) working += elapsed;
                    else awaitingWorker += elapsed;
                }
                default -> { }
            }
        }
    }

    /** Sums for one town since its last report. */
    static final class Totals {
        int batches;
        long awaitingCourier;
        long inTransit;
        long awaitingWorker;
        long working;

        String summary() {
            double n = Math.max(1, batches) * 1000.0;
            return String.format(java.util.Locale.ROOT,
                "%d batches: %.1f s waiting for a courier, %.1f s being carried, %.1f s waiting for a worker, %.1f s being worked",
                batches, awaitingCourier / n, inTransit / n, awaitingWorker / n, working / n);
        }
    }

    private WorkplaceBatchTimings() {}

    /** Call from the world thread each time the planner looks at a town. */
    public static void observe(UUID town, String townName, Iterable<WarehouseBatch> batches, long now) {
        var live = new HashSet<UUID>();
        for (var batch : batches) {
            if (batch.cancelled() || batch.herdFeed() || batch.siteSupply() || batch.smelting() || batch.cultivation()) {
                continue;
            }
            live.add(batch.id());
            var tracked = TRACKED.computeIfAbsent(batch.id(), id -> new Tracked(town, now));
            tracked.add(batch.state(), batch.workSeconds() > 0, Math.max(0, now - tracked.lastSeen));
            tracked.lastSeen = now;
        }
        var finished = TRACKED.entrySet().iterator();
        while (finished.hasNext()) {
            var entry = finished.next();
            var tracked = entry.getValue();
            if (!town.equals(tracked.town) || live.contains(entry.getKey())) {
                continue;
            }
            finished.remove();
            // A batch that was never worked was cancelled or returned, and would only skew the averages.
            if (tracked.working <= 0) {
                continue;
            }
            String report = record(TOTALS.computeIfAbsent(town, id -> new Totals()), tracked);
            if (report != null) {
                LOGGER.atInfo().log("Workplace crafting pace in %s, average of the last %s", townName, report);
            }
        }
    }

    /** Adds a finished batch and returns a summary once enough have been gathered. */
    static String record(Totals totals, Tracked tracked) {
        totals.batches++;
        totals.awaitingCourier += tracked.awaitingCourier;
        totals.inTransit += tracked.inTransit;
        totals.awaitingWorker += tracked.awaitingWorker;
        totals.working += tracked.working;
        if (totals.batches < REPORT_EVERY) {
            return null;
        }
        String summary = totals.summary();
        totals.batches = 0;
        totals.awaitingCourier = totals.inTransit = totals.awaitingWorker = totals.working = 0;
        return summary;
    }
}
