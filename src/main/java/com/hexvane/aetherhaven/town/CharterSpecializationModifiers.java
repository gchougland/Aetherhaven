package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nonnull;

/** Central place for specialization bonuses; extend as new production systems ship. */
public final class CharterSpecializationModifiers {
    /** Extra Chebyshev radius for sprinklers when farming specialization and a complete farm plot exists. */
    public static final int FARM_SPRINKLER_EXTRA_RADIUS = 1;

    private CharterSpecializationModifiers() {}

    /**
     * @param constructionId {@link AetherhavenConstants#CONSTRUCTION_PLOT_FARM} etc.
     */
    public static double productionMultiplier(
        @Nonnull TownRecord town,
        @Nonnull String constructionId
    ) {
        CharterSpecialization s = town.getCharterSpecializationEnum();
        if (s == null) {
            return 1.0;
        }
        return switch (s) {
            case FARMING -> AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(constructionId) ? 1.05 : 1.0;
            case SMITHING -> AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP.equals(constructionId) ? 1.05 : 1.0;
            case MINING, LOGGING -> 1.0;
        };
    }

    /** When true, sprinklers get +{@link #FARM_SPRINKLER_EXTRA_RADIUS} effective radius (still capped by code). */
    public static boolean farmSprinklerRadiusBonus(@Nonnull TownRecord town) {
        if (town.getCharterSpecializationEnum() != CharterSpecialization.FARMING) {
            return false;
        }
        return town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_FARM) != null;
    }
}
