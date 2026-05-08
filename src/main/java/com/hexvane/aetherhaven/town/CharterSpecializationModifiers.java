package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
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
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String plotOrGameplayConstructionId
    ) {
        CharterSpecialization s = town.getCharterSpecializationEnum();
        if (s == null) {
            return 1.0;
        }
        String g = constructionCatalog.resolveGameplayConstructionId(plotOrGameplayConstructionId);
        return switch (s) {
            case FARMING -> AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(g) ? 1.05 : 1.0;
            case SMITHING -> AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP.equals(g) ? 1.05 : 1.0;
            case MINING, LOGGING -> 1.0;
        };
    }

    /** When true, sprinklers get +{@link #FARM_SPRINKLER_EXTRA_RADIUS} effective radius (still capped by code). */
    public static boolean farmSprinklerRadiusBonus(@Nonnull TownRecord town, @Nonnull ConstructionCatalog constructionCatalog) {
        if (town.getCharterSpecializationEnum() != CharterSpecialization.FARMING) {
            return false;
        }
        return town.findCompletePlotWithConstruction(constructionCatalog, AetherhavenConstants.CONSTRUCTION_PLOT_FARM) != null;
    }
}
