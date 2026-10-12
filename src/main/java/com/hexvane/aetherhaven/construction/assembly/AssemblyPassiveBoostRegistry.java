package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Sites the core Builder is standing at, per world. An entry above one means he is working there: the site steps that
 * many times as often, and each step places as many blocks as his Mastery level (see
 * {@link com.hexvane.aetherhaven.mastery.BuilderMastery}).
 *
 * <p>He renews his entry every tick he works. An entry nobody renewed for {@link #STALE_MS} counts as gone, so a
 * site he walked away from, or whose Builder was unloaded with his chunk, neither keeps his speed nor earns him Mastery.
 */
public final class AssemblyPassiveBoostRegistry {
    /** How long an entry lasts without being renewed. Long enough to sit out a slow tick. */
    public static final long STALE_MS = 3_000L;

    private record Boost(int multiplier, long seenMs) {}

    private static final ConcurrentHashMap<String, ConcurrentHashMap<UUID, Boost>> WORLD_PLOT_BOOST = new ConcurrentHashMap<>();

    private AssemblyPassiveBoostRegistry() {}

    public static void clearForWorld(@Nonnull World world) {
        WORLD_PLOT_BOOST.remove(world.getName());
    }

    /**
     * Sets or renews the entry. {@code multiplier <= 0} removes any entry for the plot.
     *
     * @return true when a live entry was renewed; false when he has just arrived, or his entry had run out, so the
     *     caller knows the site's next step should be brought forward to his pace
     */
    public static boolean setBoost(@Nonnull World world, @Nonnull UUID plotId, int multiplier) {
        return setBoost(world.getName(), plotId, multiplier, System.currentTimeMillis());
    }

    public static int boostFor(@Nonnull World world, @Nonnull UUID plotId) {
        return boostFor(world.getName(), plotId, System.currentTimeMillis());
    }

    /** True while the Builder is working at this site. */
    public static boolean builderAtSite(@Nonnull World world, @Nonnull UUID plotId) {
        return boostFor(world, plotId) > 1;
    }

    static boolean setBoost(@Nonnull String worldName, @Nonnull UUID plotId, int multiplier, long nowMs) {
        if (multiplier <= 0) {
            ConcurrentHashMap<UUID, Boost> m = WORLD_PLOT_BOOST.get(worldName);
            if (m != null) {
                m.remove(plotId);
            }
            return false;
        }
        boolean[] renewed = new boolean[1];
        WORLD_PLOT_BOOST.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
            .merge(
                plotId,
                new Boost(multiplier, nowMs),
                (old, next) -> {
                    renewed[0] = fresh(old, nowMs);
                    return new Boost(renewed[0] ? Math.max(old.multiplier(), next.multiplier()) : next.multiplier(), nowMs);
                }
            );
        return renewed[0];
    }

    static int boostFor(@Nonnull String worldName, @Nonnull UUID plotId, long nowMs) {
        ConcurrentHashMap<UUID, Boost> m = WORLD_PLOT_BOOST.get(worldName);
        if (m == null) {
            return 1;
        }
        Boost boost = m.get(plotId);
        if (boost == null) {
            return 1;
        }
        if (!fresh(boost, nowMs)) {
            m.remove(plotId, boost);
            return 1;
        }
        return Math.max(1, boost.multiplier());
    }

    static void clearForWorld(@Nonnull String worldName) {
        WORLD_PLOT_BOOST.remove(worldName);
    }

    private static boolean fresh(@Nonnull Boost boost, long nowMs) {
        return nowMs - boost.seenMs() <= STALE_MS;
    }

    @Nonnull
    public static Map<UUID, Integer> snapshot(@Nonnull World world) {
        ConcurrentHashMap<UUID, Boost> m = WORLD_PLOT_BOOST.get(world.getName());
        if (m == null) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        return m.entrySet().stream()
            .filter(e -> fresh(e.getValue(), now))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().multiplier()));
    }
}
