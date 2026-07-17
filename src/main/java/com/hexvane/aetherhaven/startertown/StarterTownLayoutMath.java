package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;

/** Pure layout operations kept separate from world access for deterministic tests. */
final class StarterTownLayoutMath {
    record Candidate(int x, int z) {}

    private StarterTownLayoutMath() {}

    static int representativeGround(@Nonnull List<Integer> samples, int maxSpread) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("No terrain samples");
        }
        int min = Collections.min(samples);
        int max = Collections.max(samples);
        if (max - min > maxSpread) {
            throw new IllegalArgumentException("Terrain slope exceeds limit");
        }
        List<Integer> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    static int lineAdvance(@Nonnull PlotFootprintRecord footprint) {
        return Math.max(
            footprint.getMaxX() - footprint.getMinX(),
            footprint.getMaxZ() - footprint.getMinZ()
        ) + 12;
    }

    static boolean overlapsWithSetback(
        @Nonnull PlotFootprintRecord a,
        @Nonnull PlotFootprintRecord b,
        int setback
    ) {
        return !(a.getMaxX() + setback < b.getMinX()
            || a.getMinX() - setback > b.getMaxX()
            || a.getMaxZ() + setback < b.getMinZ()
            || a.getMinZ() - setback > b.getMaxZ());
    }

    @Nonnull
    static Candidate generatedCandidate(long seed, int index, int retry, int originX, int originZ) {
        long mixed = seed
            ^ (0x9E3779B97F4A7C15L * (index + 1L))
            ^ (0xD1B54A32D192ED03L * (retry + 1L));
        Random random = new Random(mixed);
        double angle = index == 0 ? 0.0 : random.nextDouble() * Math.PI * 2.0 + retry * 0.61;
        int ring = index == 0 ? 18 : 28 + ((index + retry / 16) / 6) * 24;
        return new Candidate(
            originX + (int) Math.round(Math.sin(angle) * ring),
            originZ + (int) Math.round(Math.cos(angle) * ring)
        );
    }
}
