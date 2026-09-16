package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.town.TownRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World-thread queue: the latest request wins, with one deferred task per batch. */
final class ShopSpotDisplayQueue {
    record Update(ShopSpotRecord record, TownRecord town) {
        boolean removal() { return town == null; }
    }
    // Record identity keeps teardown of an old stall separate from its replacement.
    private final Map<ShopSpotRecord, Update> pending = new LinkedHashMap<>();
    private boolean scheduled;

    boolean offer(ShopSpotRecord record, TownRecord town) {
        pending.put(record, new Update(record, town));
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    void cancel(ShopSpotRecord record) { pending.remove(record); }

    List<Update> drain() {
        var updates = List.copyOf(pending.values());
        pending.clear();
        scheduled = false;
        return updates;
    }
}
