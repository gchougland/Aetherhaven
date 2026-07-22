package com.hexvane.aetherhaven.world;

import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/** Adventure world tier (1–4) from procedural zone names such as {@code Zone2_Taiga}. */
public final class WorldZoneIndex {
    public static final int MIN = 1;
    public static final int MAX = 4;

    /** When zone gen is unavailable, allow full loot tiers (instances, flat worlds). */
    public static final int UNKNOWN_DEFAULT = MAX;

    private static final Pattern ZONE_NUMBER = Pattern.compile("Zone(\\d+)", Pattern.CASE_INSENSITIVE);

    private WorldZoneIndex() {}

    public static int clamp(int zoneIndex) {
        if (zoneIndex < MIN) {
            return MIN;
        }
        return Math.min(MAX, zoneIndex);
    }

    public static int resolveAtBlock(@Nonnull World world, int blockX, int blockZ) {
        var worldGen = world.getChunkStore().getGenerator();
        if (!(worldGen instanceof ChunkGenerator generator)) {
            return UNKNOWN_DEFAULT;
        }
        int seed = (int) world.getWorldConfig().getSeed();
        var result = generator.getZoneBiomeResultAt(seed, MathUtil.floor(blockX), MathUtil.floor(blockZ));
        return resolveFromGeneratorZone(result.getZoneResult().getZone().id(), result.getZoneResult().getZone().name());
    }

    /** Worldgen zone ids are 0-based (0 = first zone); adventure tier is 1–4. */
    static int resolveFromGeneratorZone(int zoneId, @Nonnull String zoneName) {
        int fromId = clamp(zoneId + 1);
        int fromName = parseZoneName(zoneName);
        if (fromName >= MIN && fromName <= MAX) {
            return fromName;
        }
        return fromId;
    }

    static int parseZoneName(@Nonnull String zoneName) {
        Matcher m = ZONE_NUMBER.matcher(zoneName);
        if (!m.find()) {
            return UNKNOWN_DEFAULT;
        }
        try {
            return clamp(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return UNKNOWN_DEFAULT;
        }
    }
}
