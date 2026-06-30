package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** In-memory assembly jobs keyed by plot id (per world), each with phase and optional frontier state. */
public final class AssemblyWorldRegistry {
    private static final Map<String, ConcurrentHashMap<UUID, AssemblyEntry>> BY_WORLD = new ConcurrentHashMap<>();

    private record AssemblyEntry(
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyPhase phase,
        @Nullable PlotAssemblyFrontierRuntime runtime,
        @Nullable PlotAssemblyClearingRuntime clearingRuntime
    ) {}

    private AssemblyWorldRegistry() {}

    /** Releases a cached prefab accessor; safe when already released or shared across jobs. */
    public static void releasePrefabBufferQuietly(@Nullable IPrefabBuffer buffer) {
        if (buffer == null) {
            return;
        }
        try {
        } catch (NullPointerException ignored) {
            // Already released
        }
    }

    private static void releaseJobBufferQuietly(@Nonnull IPrefabBuffer buffer) {
        releasePrefabBufferQuietly(buffer);
    }

    @Nonnull
    private static ConcurrentHashMap<UUID, AssemblyEntry> mapFor(@Nonnull World world) {
        return BY_WORLD.computeIfAbsent(world.getName(), n -> new ConcurrentHashMap<>());
    }

    public static void put(
        @Nonnull World world,
        @Nonnull UUID plotId,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyPhase phase,
        @Nullable PlotAssemblyFrontierRuntime runtime,
        @Nullable PlotAssemblyClearingRuntime clearingRuntime
    ) {
        AssemblyEntry previous = mapFor(world).put(plotId, new AssemblyEntry(job, phase, runtime, clearingRuntime));
        if (previous != null) {
            releaseJobBufferQuietly(previous.job().buffer());
        }
    }

    @Nullable
    public static PlotAssemblyJob get(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.job() : null;
    }

  /**
     * Active phase for {@code plotId}, or {@code null} when no in-memory job is registered (do not assume PLACING).
     */
    @Nullable
    public static PlotAssemblyPhase phase(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.phase() : null;
    }

    public static boolean hasJob(@Nonnull World world, @Nonnull UUID plotId) {
        return mapFor(world).containsKey(plotId);
    }

    @Nullable
    public static PlotAssemblyFrontierRuntime frontierRuntime(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.runtime() : null;
    }

    @Nullable
    public static PlotAssemblyClearingRuntime clearingRuntime(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).get(plotId);
        return e != null ? e.clearingRuntime() : null;
    }

    public static void transitionToPlacing(
        @Nonnull World world,
        @Nonnull UUID plotId,
        @Nonnull PlotAssemblyFrontierRuntime runtime
    ) {
        ConcurrentHashMap<UUID, AssemblyEntry> map = mapFor(world);
        AssemblyEntry e = map.get(plotId);
        if (e == null) {
            return;
        }
        map.put(plotId, new AssemblyEntry(e.job(), PlotAssemblyPhase.PLACING, runtime, null));
    }

    public static void remove(@Nonnull World world, @Nonnull UUID plotId) {
        AssemblyEntry e = mapFor(world).remove(plotId);
        if (e != null) {
            releaseJobBufferQuietly(e.job().buffer());
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

    public static boolean anyJobInPhase(@Nonnull World world, @Nonnull PlotAssemblyPhase phase) {
        for (AssemblyEntry e : mapFor(world).values()) {
            if (e.phase() == phase) {
                return true;
            }
        }
        return false;
    }

    public static void unloadWorld(@Nonnull String worldName) {
        ConcurrentHashMap<UUID, AssemblyEntry> m = BY_WORLD.remove(worldName);
        if (m != null) {
            for (AssemblyEntry e : m.values()) {
                releaseJobBufferQuietly(e.job().buffer());
            }
        }
    }
}
