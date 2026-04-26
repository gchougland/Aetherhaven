package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * In-memory and disk-backed list of {@link PathCommitRecord} for a world.
 */
public final class PathToolRegistry {
    @Nonnull
    private final World world;
    @Nonnull
    private final List<PathCommitRecord> records = new ArrayList<>();

    public PathToolRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public void replaceAll(@Nonnull List<PathCommitRecord> list) {
        records.clear();
        for (PathCommitRecord r : list) {
            if (r != null && r.id != null) {
                records.add(r);
            }
        }
    }

    public void addRecord(@Nonnull PathCommitRecord r) {
        if (r.id == null) {
            return;
        }
        records.add(r);
    }

    @Nonnull
    public List<PathCommitRecord> all() {
        return new ArrayList<>(records);
    }

    @Nullable
    public PathCommitRecord remove(@Nonnull UUID id) {
        for (int i = 0; i < records.size(); i++) {
            PathCommitRecord e = records.get(i);
            if (e != null && id.toString().equals(e.id)) {
                return records.remove(i);
            }
        }
        return null;
    }
}
