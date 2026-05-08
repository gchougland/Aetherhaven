package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nullable;

/**
 * Tiered building staff items: larger assembly brush ({@linkplain #assemblyBrushChebyshevRadius Chebyshev radius})
 * for higher material tiers without changing wand reach ({@code InteractionConfig.UseDistance} in item JSON).
 */
public final class BuildingStaffTiers {
    public static final String STAFF_ITEM_ID_IRON = "Aetherhaven_Building_Staff_Iron";
    public static final String STAFF_ITEM_ID_THORIUM = "Aetherhaven_Building_Staff_Thorium";
    public static final String STAFF_ITEM_ID_COBALT = "Aetherhaven_Building_Staff_Cobalt";
    public static final String STAFF_ITEM_ID_ADAMANTITE = "Aetherhaven_Building_Staff_Adamantite";

    private BuildingStaffTiers() {}

    public static boolean isBuildingStaff(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        return AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(itemId)
            || STAFF_ITEM_ID_IRON.equals(itemId)
            || STAFF_ITEM_ID_THORIUM.equals(itemId)
            || STAFF_ITEM_ID_COBALT.equals(itemId)
            || STAFF_ITEM_ID_ADAMANTITE.equals(itemId);
    }

    /**
     * Radius for {@link PlotAssemblyService#frontierPlacementIndicesNearChebyshev}: 1 ⇒ 3×3×3, 2 ⇒ 5×5×5, etc.
     */
    public static int assemblyBrushChebyshevRadius(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS_DEFAULT;
        }
        if (AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(itemId)) {
            return 1;
        }
        if (STAFF_ITEM_ID_IRON.equals(itemId)) {
            return 2;
        }
        if (STAFF_ITEM_ID_THORIUM.equals(itemId)) {
            return 3;
        }
        if (STAFF_ITEM_ID_COBALT.equals(itemId)) {
            return 4;
        }
        if (STAFF_ITEM_ID_ADAMANTITE.equals(itemId)) {
            return 5;
        }
        return AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS_DEFAULT;
    }
}
