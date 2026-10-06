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

    /** Mana spent per assembly block placed with the building staff (secondary channel). */
    public static final float MANA_COST_PER_BLOCK = 1f;

    /** After this long without holding secondary, held-staff mana regen increases further. */
    public static final long IDLE_MANA_REGEN_DELAY_NS = 1_000_000_000L;

    private BuildingStaffTiers() {}

    public static boolean isBuildingStaff(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        return AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(itemId)
            || "Aetherhaven_Workplace_Tool".equals(itemId) // Hidden alias for early 4.0 test saves.
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

    /**
     * Additive max {@code Mana} while the staff is held ({@code Weapon.StatModifiers.Mana} on each item JSON).
     * Keep lang strings in sync.
     */
    public static int heldMaxManaBonus(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        if (AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(itemId)) {
            return 150;
        }
        if (STAFF_ITEM_ID_IRON.equals(itemId)) {
            return 250;
        }
        if (STAFF_ITEM_ID_THORIUM.equals(itemId)) {
            return 400;
        }
        if (STAFF_ITEM_ID_COBALT.equals(itemId)) {
            return 600;
        }
        if (STAFF_ITEM_ID_ADAMANTITE.equals(itemId)) {
            return 900;
        }
        return 0;
    }

    /**
     * Bonus {@code Mana} regen per second while this staff is in the main hand and secondary has been idle for
     * {@link #IDLE_MANA_REGEN_DELAY_NS} (added on top of vanilla regen, which only applies when not channeling).
     */
    public static float heldManaRegenPerSecond(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0f;
        }
        if (AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(itemId)) {
            return 30f;
        }
        if (STAFF_ITEM_ID_IRON.equals(itemId)) {
            return 48f;
        }
        if (STAFF_ITEM_ID_THORIUM.equals(itemId)) {
            return 60f;
        }
        if (STAFF_ITEM_ID_COBALT.equals(itemId)) {
            return 90f;
        }
        if (STAFF_ITEM_ID_ADAMANTITE.equals(itemId)) {
            return 120f;
        }
        return 0f;
    }
}
