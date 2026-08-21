package com.hexvane.aetherhaven.production;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class ProductionResourceZoneAffinityTest {
    @Test
    void preferredZone_oresMatchPlan() {
        assertEquals(1, ProductionResourceZoneAffinity.preferredZone("Ore_Copper"));
        assertEquals(1, ProductionResourceZoneAffinity.preferredZone("Ore_Iron"));
        assertEquals(2, ProductionResourceZoneAffinity.preferredZone("Ore_Thorium"));
        assertEquals(3, ProductionResourceZoneAffinity.preferredZone("Ore_Cobalt"));
        assertEquals(4, ProductionResourceZoneAffinity.preferredZone("Ore_Adamantite"));
    }

    @Test
    void preferredZone_woodsAndSaplings() {
        assertEquals(1, ProductionResourceZoneAffinity.preferredZone("Wood_Oak_Trunk"));
        assertEquals(1, ProductionResourceZoneAffinity.preferredZone("Plant_Sapling_Oak"));
        assertEquals(2, ProductionResourceZoneAffinity.preferredZone("Wood_Palm_Trunk"));
        assertEquals(2, ProductionResourceZoneAffinity.preferredZone("Plant_Sapling_Palm"));
        assertEquals(3, ProductionResourceZoneAffinity.preferredZone("Wood_Fir_Trunk"));
        assertEquals(3, ProductionResourceZoneAffinity.preferredZone("Plant_Sapling_Spruce"));
        assertEquals(4, ProductionResourceZoneAffinity.preferredZone("Wood_Jungle_Trunk"));
        assertEquals(4, ProductionResourceZoneAffinity.preferredZone("Plant_Sapling_Fire"));
    }

    @Test
    void preferredZone_unmappedKeepsNone() {
        assertEquals(ProductionResourceZoneAffinity.NONE, ProductionResourceZoneAffinity.preferredZone("Ore_Gold"));
        assertEquals(ProductionResourceZoneAffinity.NONE, ProductionResourceZoneAffinity.preferredZone("Ore_Silver"));
        assertEquals(ProductionResourceZoneAffinity.NONE, ProductionResourceZoneAffinity.preferredZone("Wood_Apple_Trunk"));
        assertEquals(ProductionResourceZoneAffinity.NONE, ProductionResourceZoneAffinity.preferredZone("Rock_Stone"));
        assertEquals(ProductionResourceZoneAffinity.NONE, ProductionResourceZoneAffinity.preferredZone(null));
    }

    @Test
    void timeMultiplier_matchIsOneMismatchUsesConfig() {
        assertEquals(1.0, ProductionResourceZoneAffinity.timeMultiplier(1, 1, 2.0));
        assertEquals(2.0, ProductionResourceZoneAffinity.timeMultiplier(1, 3, 2.0));
        assertEquals(1.0, ProductionResourceZoneAffinity.timeMultiplier(ProductionResourceZoneAffinity.NONE, 4, 2.0));
    }

    @Test
    void timeMultiplierForResolvedZone_nullSkipsPenalty() {
        assertEquals(
            1.0,
            ProductionResourceZoneAffinity.timeMultiplierForResolvedZone(null, "Ore_Thorium", 2.0)
        );
        assertEquals(
            2.0,
            ProductionResourceZoneAffinity.timeMultiplierForResolvedZone(1, "Ore_Thorium", 2.0)
        );
        assertEquals(
            1.0,
            ProductionResourceZoneAffinity.timeMultiplierForResolvedZone(2, "Ore_Thorium", 2.0)
        );
    }

    @Test
    void effectiveTicks_zoneMismatchDoublesInterval() {
        int base = ProductionTimeScaling.effectiveTicks(600, 1.0);
        int slow = ProductionTimeScaling.effectiveTicks(600, 1.0 / 1.0 * 2.0);
        assertEquals(600, base);
        assertEquals(1200, slow);
        assertEquals(
            1200,
            ProductionTimeScaling.effectiveTicksWithWorkplaceSpeedAndZone(
                new com.hexvane.aetherhaven.config.AetherhavenPluginConfig(),
                600,
                1.0,
                2.0
            )
        );
    }
}
