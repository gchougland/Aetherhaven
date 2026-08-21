package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.WorldZoneIndex;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Preferred adventure zone (1–4) for workplace ores and woods. Wrong-zone production is slowed; preferred zone is
 * unchanged. Resources with no affinity keep full speed everywhere.
 */
public final class ProductionResourceZoneAffinity {
    /** No preferred zone; always full production speed. */
    public static final int NONE = 0;

    private static final Map<String, Integer> PREFERRED_BY_ITEM = buildPreferredMap();

    private ProductionResourceZoneAffinity() {}

    /**
     * Preferred adventure zone for {@code itemId}, or {@link #NONE} when the item is not zone-gated.
     */
    public static int preferredZone(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return NONE;
        }
        Integer zone = PREFERRED_BY_ITEM.get(itemId);
        return zone != null ? zone : NONE;
    }

    /**
     * Time multiplier for one production cycle at the given adventure zone. {@code 1.0} when there is no affinity or
     * the zone matches; otherwise {@code mismatchTimeMultiplier}.
     */
    public static double timeMultiplier(int preferredZone, int adventureZone, double mismatchTimeMultiplier) {
        if (preferredZone <= NONE) {
            return 1.0;
        }
        if (adventureZone == preferredZone) {
            return 1.0;
        }
        return sanitizeMismatch(mismatchTimeMultiplier);
    }

    /**
     * Time multiplier for {@code itemId} at the workplace plot. Skips the penalty when zone gen is unavailable
     * (instances, flat worlds).
     */
    public static double timeMultiplierForPlot(
        @Nullable World world,
        int blockX,
        int blockZ,
        @Nonnull String itemId,
        double mismatchTimeMultiplier
    ) {
        int preferred = preferredZone(itemId);
        if (preferred <= NONE) {
            return 1.0;
        }
        if (world == null) {
            return 1.0;
        }
        var worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator)) {
            return 1.0;
        }
        int adventureZone = WorldZoneIndex.resolveAtBlock(world, blockX, blockZ);
        return timeMultiplier(preferred, adventureZone, mismatchTimeMultiplier);
    }

    /**
     * Resolves adventure zone once for a batch of accrual ticks, or {@code null} when zone gen is unavailable (no
     * penalty).
     */
    @Nullable
    public static Integer resolveAdventureZoneOrSkip(@Nullable World world, int blockX, int blockZ) {
        if (world == null) {
            return null;
        }
        var worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator)) {
            return null;
        }
        return WorldZoneIndex.resolveAtBlock(world, blockX, blockZ);
    }

    /**
     * Time multiplier using a pre-resolved adventure zone. When {@code adventureZone} is {@code null}, always
     * {@code 1.0}.
     */
    public static double timeMultiplierForResolvedZone(
        @Nullable Integer adventureZone,
        @Nonnull String itemId,
        double mismatchTimeMultiplier
    ) {
        if (adventureZone == null) {
            return 1.0;
        }
        return timeMultiplier(preferredZone(itemId), adventureZone, mismatchTimeMultiplier);
    }

    private static double sanitizeMismatch(double mismatchTimeMultiplier) {
        if (Double.isNaN(mismatchTimeMultiplier) || mismatchTimeMultiplier <= 0.0) {
            return 2.0;
        }
        return Math.max(1.0, Math.min(100.0, mismatchTimeMultiplier));
    }

    private static Map<String, Integer> buildPreferredMap() {
        Map<String, Integer> m = new HashMap<>();

        // Ores
        putOre(m, "Copper", 1);
        putOre(m, "Iron", 1);
        putOre(m, "Thorium", 2);
        putOre(m, "Cobalt", 3);
        putOre(m, "Adamantite", 4);

        // Zone 1 woods
        putWood(m, "Oak", 1);
        putWood(m, "Birch", 1);
        putWood(m, "Beech", 1);
        putWood(m, "Ash", 1);
        putWood(m, "Aspen", 1);
        putWood(m, "Azure", 1);
        putWood(m, "Maple", 1);

        // Zone 2 woods
        putWood(m, "Dry", 2);
        putWood(m, "Gumboab", 2);
        putWood(m, "Bottletree", 2);
        putWood(m, "Palm", 2);
        putWood(m, "Palo", 2);

        // Zone 3 woods
        putWood(m, "Fir", 3);
        putWood(m, "Cedar", 3);
        putWood(m, "Redwood", 3);
        putWood(m, "Spiral", 3);
        putWood(m, "Poisoned", 3);
        m.put("Plant_Sapling_Spruce", 3);

        // Zone 4 woods
        putWood(m, "Burnt", 4);
        putWood(m, "Petrified", 4);
        putWood(m, "Sallow", 4);
        putWood(m, "Bamboo", 4);
        putWood(m, "Jungle", 4);
        putWood(m, "Camphor", 4);
        putWood(m, "Banyan", 4);
        putWood(m, "Crystal", 4);
        putWood(m, "Fig_Blue", 4);
        putWood(m, "Fire", 4);

        return Map.copyOf(m);
    }

    private static void putOre(@Nonnull Map<String, Integer> m, @Nonnull String name, int zone) {
        m.put("Ore_" + name, zone);
    }

    private static void putWood(@Nonnull Map<String, Integer> m, @Nonnull String name, int zone) {
        m.put("Wood_" + name + "_Trunk", zone);
        m.put("Plant_Sapling_" + name, zone);
    }
}
