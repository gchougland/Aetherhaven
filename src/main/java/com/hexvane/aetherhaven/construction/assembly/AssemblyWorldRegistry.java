package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** In-memory assembly jobs keyed by plot id (per world). */
public final class AssemblyWorldRegistry {
    private static final Map<String, ConcurrentHashMap<UUID, PlotAssemblyJob>> BY_WORLD = new ConcurrentHashMap<>();

    private AssemblyWorldRegistry() {}

    @Nonnull
    private static ConcurrentHashMap<UUID, PlotAssemblyJob> mapFor(@Nonnull World world) {
        return BY_WORLD.computeIfAbsent(world.getName(), n -> new ConcurrentHashMap<>());
    }

    public static void put(@Nonnull World world, @Nonnull UUID plotId, @Nonnull PlotAssemblyJob job) {
        mapFor(world).put(plotId, job);
    }

    @Nullable
    public static PlotAssemblyJob get(@Nonnull World world, @Nonnull UUID plotId) {
        return mapFor(world).get(plotId);
    }

    public static void remove(@Nonnull World world, @Nonnull UUID plotId) {
        PlotAssemblyJob j = mapFor(world).remove(plotId);
        if (j != null) {
            j.buffer().release();
        }
    }

    @Nonnull
    public static Collection<PlotAssemblyJob> jobs(@Nonnull World world) {
        return mapFor(world).values();
    }

    public static void unloadWorld(@Nonnull String worldName) {
        ConcurrentHashMap<UUID, PlotAssemblyJob> m = BY_WORLD.remove(worldName);
        if (m != null) {
            for (PlotAssemblyJob j : m.values()) {
                j.buffer().release();
            }
        }
    }
}
