package com.hexvane.aetherhaven.poi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** In-memory POI index per world (Week 1 stub). */
public final class PoiRegistry {
    private final Map<UUID, PoiEntry> byId = new ConcurrentHashMap<>();
    private final Map<UUID, List<PoiEntry>> byTownId = new ConcurrentHashMap<>();

    public void register(@Nonnull PoiEntry entry) {
        byId.put(entry.getId(), entry);
        byTownId.computeIfAbsent(entry.getTownId(), k -> new ArrayList<>()).add(entry);
    }

    public void unregister(@Nonnull UUID poiId) {
        PoiEntry removed = byId.remove(poiId);
        if (removed != null) {
            List<PoiEntry> list = byTownId.get(removed.getTownId());
            if (list != null) {
                list.removeIf(e -> e.getId().equals(poiId));
            }
        }
    }

    @Nullable
    public PoiEntry get(@Nonnull UUID poiId) {
        return byId.get(poiId);
    }

    @Nonnull
    public List<PoiEntry> listByTown(@Nonnull UUID townId) {
        List<PoiEntry> list = byTownId.get(townId);
        return list != null ? List.copyOf(list) : List.of();
    }

    @Nonnull
    public List<PoiEntry> listByTownAndTag(@Nonnull UUID townId, @Nonnull String tag) {
        List<PoiEntry> out = new ArrayList<>();
        for (PoiEntry e : listByTown(townId)) {
            if (e.getTags().contains(tag)) {
                out.add(e);
            }
        }
        return out;
    }
}
