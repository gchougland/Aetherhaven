package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.builtin.adventure.teleporter.component.Teleporter;
import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/** Sanitizes teleporter-owned warp rotations before chunk preload spawns warp marker entities. */
public final class TeleporterWarpSanitizer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TeleporterWarpSanitizer() {}

    /** Run after {@link TeleportPlugin#loadWarps()} on server startup. */
    public static void sanitizeAllTeleporterWarpsOnStartup() {
        TeleportPlugin teleport = TeleportPlugin.get();
        if (teleport == null) {
            return;
        }
        if (!teleport.isWarpsLoaded()) {
            LOGGER.atWarning().log("Skipping teleporter warp rotation sanitize: warps not loaded yet");
            return;
        }
        int repaired = repairTeleporterWarps(teleport.getWarps());
        if (repaired > 0) {
            LOGGER.atInfo().log("Repaired %s teleporter warp rotation(s) at startup", repaired);
        }
    }

    /**
     * Scans a completed plot footprint for teleporter blocks and repairs owned warp rotations.
     * Call on the world thread after vanilla warp creation has had a tick to finish.
     */
    public static void sanitizePlotFootprint(@Nonnull World world, @Nonnull PlotFootprintRecord footprint) {
        TeleportPlugin teleport = TeleportPlugin.get();
        if (teleport == null || !teleport.isWarpsLoaded()) {
            return;
        }
        Set<String> ownedWarpNames = collectOwnedWarpNames(world, footprint);
        if (ownedWarpNames.isEmpty()) {
            return;
        }
        Map<String, Warp> warps = teleport.getWarps();
        int repaired = 0;
        for (String warpName : ownedWarpNames) {
            Warp warp = warps.get(warpName.toLowerCase());
            if (warp != null && Teleporter.CREATOR_IDENTIFIER.equals(warp.getCreator())) {
                if (TeleporterWarpRotationUtil.repairWarpIfNeeded(warp)) {
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            LOGGER.atInfo().log(
                "Repaired %s teleporter warp rotation(s) in plot footprint [%s..%s, %s..%s, %s..%s]",
                repaired,
                footprint.getMinX(),
                footprint.getMaxX(),
                footprint.getMinY(),
                footprint.getMaxY(),
                footprint.getMinZ(),
                footprint.getMaxZ()
            );
        }
    }

    /** Schedules {@link #sanitizePlotFootprint} on the next world-thread pass. */
    public static void schedulePlotFootprintSanitize(@Nonnull World world, @Nonnull PlotFootprintRecord footprint) {
        world.execute(() -> world.execute(() -> sanitizePlotFootprint(world, footprint)));
    }

    private static int repairTeleporterWarps(@Nonnull Map<String, Warp> warps) {
        int repaired = 0;
        for (Warp warp : warps.values()) {
            if (!Teleporter.CREATOR_IDENTIFIER.equals(warp.getCreator())) {
                continue;
            }
            if (TeleporterWarpRotationUtil.repairWarpIfNeeded(warp)) {
                repaired++;
            }
        }
        return repaired;
    }

    @Nonnull
    private static Set<String> collectOwnedWarpNames(@Nonnull World world, @Nonnull PlotFootprintRecord footprint) {
        Set<String> names = new HashSet<>();
        int minX = footprint.getMinX();
        int maxX = footprint.getMaxX();
        int minY = footprint.getMinY();
        int maxY = footprint.getMaxY();
        int minZ = footprint.getMinZ();
        int maxZ = footprint.getMaxZ();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
                    if (blockRef == null) {
                        continue;
                    }
                    Teleporter teleporter =
                        blockRef.getStore().getComponent(blockRef, Teleporter.getComponentType());
                    if (teleporter == null) {
                        continue;
                    }
                    String ownedWarp = teleporter.getOwnedWarp();
                    if (ownedWarp != null && !ownedWarp.isEmpty()) {
                        names.add(ownedWarp);
                    }
                }
            }
        }
        return names;
    }
}
