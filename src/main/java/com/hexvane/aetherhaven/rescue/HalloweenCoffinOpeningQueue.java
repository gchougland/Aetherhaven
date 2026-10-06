package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Short-lived, world-thread observations of successful Use dispatches, not a persisted spawn queue. */
final class HalloweenCoffinOpeningQueue {
    static final long WAIT_NANOS = 5_000_000_000L;
    private static final int MAX_REQUESTS = 256, MAX_PER_PLAYER = 8;
    record Opening(UUID player, Position position, String closed, String opened, long deadline) {}
    private final Map<Position,Opening> pending = new LinkedHashMap<>();

    boolean offer(UUID player, Position at, String closed, String opened, long now) {
        if (player == null || at == null || closed == null || opened == null || closed.equals(opened)
            || pending.containsKey(at) || pending.size() >= MAX_REQUESTS
            || pending.values().stream().filter(o -> player.equals(o.player())).count() >= MAX_PER_PLAYER) return false;
        pending.put(at,new Opening(player,at,closed,opened,now+WAIT_NANOS));
        return true;
    }
    void poll(long now, boolean october, Function<Position,String> block, Consumer<Opening> opened) {
        if (!october) { pending.clear(); return; }
        var ready = new ArrayList<Opening>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            var request = iterator.next();
            if (now-request.deadline() >= 0) { iterator.remove(); continue; }
            String actual = block.apply(request.position());
            if (request.opened().equals(actual)) { iterator.remove(); ready.add(request); }
            else if (!request.closed().equals(actual)) iterator.remove();
        }
        // Remove before callbacks, so a repeated event or reentrant poll cannot deliver twice.
        ready.forEach(opened);
    }
    boolean isEmpty() { return pending.isEmpty(); }
    int size() { return pending.size(); }
}
