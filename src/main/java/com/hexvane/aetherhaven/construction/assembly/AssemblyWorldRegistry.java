package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** In-memory assembly jobs keyed by plot id (per world), each with incremental frontier state. */
public final class AssemblyWorldRegistry {
    private static final Map<String, ConcurrentHashMap<UUID, AssemblyEntry>> BY_WORLD = new ConcurrentHashMap<>();

    private record AssemblyEntry(@Nonnull PlotAssemblyJob job, @Nonnull PlotAssemblyFrontierRuntime runtime) {}

    private AssemblyWorldRegistry() {}

    @Nonnull
    private static ConcurrentHashMap<UUID, AssemblyEntry> mapFor(@Nonnull World world) {
        return BY_WORLD.computeIfAbsent(world.getName(), n -> new ConcurrentHashMap<>());
    }

    public static void put(
        @Nonnull World world,
        @Nonnull UUID plotId,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyFrontierRuntime runtime
    ) {
        mapFor(world).put(plotId, new AssemblyEntry(job, runtime));
    }

    @Nullable
    public static PlotAssemblyJob get(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.job() : null;
    }

    @Nullable
    public static PlotAssemblyFrontierRuntime frontierRuntime(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.runtime() : null;
    }

    public static void remove(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).remove(plotId);
        if (e != null) {
            e.job().buffer().release();
        }
    }

    @Nonnull
    public static Collection<PlotAssemblyJob> jobs(@Nonnull World world) {
        ArrayList<PlotAssemblyJob> out = new ArrayList<>(mapFor(world).size());
        for (AssemblyEntry e : mapFor(world).values()) {
            out.add(e.job());
        }
        return out;
    }

    public static void unloadWorld(@Nonnull String worldName) {
        ConcurrentHashMap<UUID, AssemblyEntry> m = BY_WORLD.remove(worldName);
        if (m != null) {
            for (AssemblyEntry e : m.values()) {
                e.job().buffer().release();
            }
        }
    }
}
