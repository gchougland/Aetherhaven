package com.hexvane.aetherhaven.poi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One-time fold of legacy dual-cell POI rows (furniture/work block + separate stand cell) into a single
 * {@code localX/Y/Z} plus yaw. Mount furniture (sit/sleep/bench, or work desks that are chairs) keep the furniture
 * cell; stand-only kinds move local to the old interaction target.
 */
public final class PoiDualCellNormalize {
    private PoiDualCellNormalize() {}

    public static boolean isFurnitureMountKind(@Nonnull PoiInteractionKind kind) {
        return kind == PoiInteractionKind.SIT
            || kind == PoiInteractionKind.SLEEP
            || kind == PoiInteractionKind.USE_BENCH;
    }

    /**
     * True when the POI block itself is mountable furniture (chair behind a desk, bed, etc.), including
     * {@link PoiInteractionKind#WORK_SURFACE} work spots that seat the villager.
     */
    public static boolean keepsFurnitureLocal(
        @Nonnull PoiInteractionKind kind,
        @Nullable String blockTypeId
    ) {
        if (isFurnitureMountKind(kind)) {
            return true;
        }
        return looksLikeMountFurnitureBlock(blockTypeId);
    }

    public static boolean looksLikeMountFurnitureBlock(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        String id = blockTypeId.trim();
        // Crafting stations (Bench_Farming, Bench_Weapon, …) are not sit furniture.
        if (id.startsWith("Bench_")) {
            return false;
        }
        String lower = id.toLowerCase();
        return lower.contains("chair")
            || lower.contains("bench")
            || lower.contains("stool")
            || lower.contains("sofa")
            || lower.contains("seat")
            || lower.contains("throne")
            || lower.contains("bed")
            || lower.contains("cot");
    }

    /**
     * Mutates {@code row} when legacy {@code interactionTargetLocal*} is present. Returns true if fields changed.
     */
    public static boolean normalize(@Nonnull BuildingPoisDefinition.PoiRow row) {
        if (!row.hasInteractionTargetLocal()) {
            return false;
        }
        if (!keepsFurnitureLocal(row.getInteractionKind(), row.getBlockTypeId())) {
            row.setLocal(
                row.getInteractionTargetLocalX(),
                row.getInteractionTargetLocalY(),
                row.getInteractionTargetLocalZ()
            );
        }
        row.clearInteractionTargetLocal();
        return true;
    }
}
