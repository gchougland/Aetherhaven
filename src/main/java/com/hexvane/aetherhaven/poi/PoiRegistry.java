package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-world POI index with persistence via {@link PoiPersistence}. */
public final class PoiRegistry {
    @Nonnull
    private final World world;
    private final Map<UUID, PoiEntry> byId = new ConcurrentHashMap<>();
    private final Map<UUID, List<PoiEntry>> byTownId = new ConcurrentHashMap<>();

    public PoiRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    private void persist() {
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p != null) {
            PoiPersistence.save(world, p, this);
        }
    }

    public void register(@Nonnull PoiEntry entry) {
        registerWithoutPersist(entry);
        persist();
    }

    /** Runtime-only POI (e.g. feast table); never written to {@code pois.json}. */
    public void registerEphemeral(@Nonnull PoiEntry entry) {
        registerWithoutPersist(entry);
    }

    public void unregisterEphemeral(@Nonnull UUID poiId) {
        removeInternal(poiId);
    }

    /** Add many entries then write {@code pois.json} once. */
    public void registerAll(@Nonnull Iterable<PoiEntry> entries) {
        for (PoiEntry e : entries) {
            registerWithoutPersist(e);
        }
        persist();
    }

    private void registerWithoutPersist(@Nonnull PoiEntry entry) {
        byId.put(entry.getId(), entry);
        byTownId.computeIfAbsent(entry.getTownId(), k -> new ArrayList<>()).add(entry);
    }

    /** Replace all entries from disk (no save). */
    public void replaceAll(@Nonnull List<PoiEntry> entries) {
        byId.clear();
        byTownId.clear();
        for (PoiEntry e : entries) {
            byId.put(e.getId(), e);
            byTownId.computeIfAbsent(e.getTownId(), k -> new ArrayList<>()).add(e);
        }
    }

    /**
     * Replace an existing POI (same id and town). Used when moving a POI cell.
     */
    public void replace(@Nonnull PoiEntry updated) {
        PoiEntry old = byId.get(updated.getId());
        if (old == null) {
            throw new IllegalArgumentException("Unknown POI id: " + updated.getId());
        }
        if (!old.getTownId().equals(updated.getTownId())) {
            throw new IllegalArgumentException("POI town mismatch");
        }
        byId.put(updated.getId(), updated);
        List<PoiEntry> list = byTownId.get(updated.getTownId());
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(updated.getId())) {
                    list.set(i, updated);
                    break;
                }
            }
        }
        persist();
    }

    public void unregister(@Nonnull UUID poiId) {
        if (removeInternal(poiId)) {
            persist();
        }
    }

    private boolean removeInternal(@Nonnull UUID poiId) {
        PoiEntry removed = byId.remove(poiId);
        if (removed == null) {
            return false;
        }
        List<PoiEntry> list = byTownId.get(removed.getTownId());
        if (list != null) {
            list.removeIf(e -> e.getId().equals(poiId));
        }
        return true;
    }

    /** Remove every POI for this town (e.g. town dissolution). */
    public void unregisterAllForTown(@Nonnull UUID townId) {
        List<PoiEntry> list = listByTown(townId);
        if (list.isEmpty()) {
            return;
        }
        boolean any = false;
        for (PoiEntry e : new ArrayList<>(list)) {
            if (removeInternal(e.getId())) {
                any = true;
            }
        }
        if (any) {
            persist();
        }
    }

    /** Remove every POI tagged with this plot (e.g. before re-registering after rebuild). */
    public void unregisterByPlotId(@Nonnull UUID plotId) {
        List<UUID> toRemove = new ArrayList<>();
        for (PoiEntry e : byId.values()) {
            if (plotId.equals(e.getPlotId())) {
                toRemove.add(e.getId());
            }
        }
        boolean any = false;
        for (UUID id : toRemove) {
            if (removeInternal(id)) {
                any = true;
            }
        }
        if (any) {
            persist();
        }
    }

    @Nullable
    public PoiEntry get(@Nonnull UUID poiId) {
        return byId.get(poiId);
    }

    @Nonnull
    public List<PoiEntry> allEntries() {
        return new ArrayList<>(byId.values());
    }

    /** Entries persisted to disk (excludes {@link AetherhavenConstants#POI_TAG_FEAST_EPHEMERAL}). */
    @Nonnull
    public List<PoiEntry> allPersistentEntries() {
        List<PoiEntry> out = new ArrayList<>();
        for (PoiEntry e : byId.values()) {
            if (!e.getTags().contains(AetherhavenConstants.POI_TAG_FEAST_EPHEMERAL)) {
                out.add(e);
            }
        }
        return out;
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
