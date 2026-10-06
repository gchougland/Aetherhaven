package com.hexvane.aetherhaven.production;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** A single tree's selected extras, collected against storage space at felling time. */
final class TimberProductCollection {
    private final Map<String, Integer> limits;
    private final Map<String, Integer> collected = new LinkedHashMap<>();

    TimberProductCollection(Map<String, Integer> limits) {
        this.limits = Map.copyOf(limits);
    }

    void collect(String item, int available) {
        if (available <= 0) return;
        int current = collected.getOrDefault(item, 0);
        int remaining = Math.max(0, limits.getOrDefault(item, 0) - current);
        int take = Math.min(available, remaining);
        if (take > 0) collected.put(item, current + take);
    }

    Map<String, Integer> products() { return Collections.unmodifiableMap(new LinkedHashMap<>(collected)); }

    void ensureMinimum(String item, int minimum) {
        collect(item, Math.max(0, minimum - collected.getOrDefault(item, 0)));
    }
}
