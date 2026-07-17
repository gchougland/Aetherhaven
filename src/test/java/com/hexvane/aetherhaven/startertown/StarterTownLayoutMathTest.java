package com.hexvane.aetherhaven.startertown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.command.AetherhavenStarterTownCommand;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class StarterTownLayoutMathTest {
    private final Gson gson = new Gson();

    @Test
    void commandRegistersDefaultAndPositionalUsageVariants() {
        assertDoesNotThrow(AetherhavenStarterTownCommand::new);
    }

    @Test
    void generatedCandidatesAreSeededAndReproducible() {
        var first = StarterTownLayoutMath.generatedCandidate(42L, 3, 7, 100, -20);
        var same = StarterTownLayoutMath.generatedCandidate(42L, 3, 7, 100, -20);
        List<StarterTownLayoutMath.Candidate> sequence =
            java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> StarterTownLayoutMath.generatedCandidate(42L, i + 1, i, 100, -20))
                .toList();
        List<StarterTownLayoutMath.Candidate> differentSequence =
            java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> StarterTownLayoutMath.generatedCandidate(999_999L, i + 1, i, 100, -20))
                .toList();

        assertEquals(first, same);
        assertNotEquals(sequence, differentSequence);
    }

    @Test
    void representativeGroundUsesMedianAndRejectsSteepTerrain() {
        assertEquals(12, StarterTownLayoutMath.representativeGround(List.of(10, 12, 11, 14, 13), 8));
        assertThrows(
            IllegalArgumentException.class,
            () -> StarterTownLayoutMath.representativeGround(List.of(10, 19), 8)
        );
    }

    @Test
    void footprintSetbackPreventsNearCollisions() {
        PlotFootprintRecord first = new PlotFootprintRecord(0, 0, 0, 9, 9, 9);
        PlotFootprintRecord near = new PlotFootprintRecord(13, 0, 0, 20, 9, 9);
        PlotFootprintRecord clear = new PlotFootprintRecord(14, 0, 0, 20, 9, 9);

        assertTrue(StarterTownLayoutMath.overlapsWithSetback(first, near, 4));
        assertFalse(StarterTownLayoutMath.overlapsWithSetback(first, clear, 4));
    }

    @Test
    void lineAdvanceUsesLongestRotatedAxis() {
        PlotFootprintRecord footprint = new PlotFootprintRecord(-2, 0, -10, 6, 8, 15);
        assertEquals(37, StarterTownLayoutMath.lineAdvance(footprint));
    }

    @Test
    void generatedTownPathsUseSixBlockWidth() {
        assertEquals(6, StarterTownPathService.PATH_WIDTH);
    }

    @Test
    void autoCompleteMatchesBuildingQuestsButExcludesHousing() {
        QuestDefinition buildingQuest = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "q_farm",
              "category": "town",
              "grantPlotTokenConstructionId": "plot_farm"
            }
            """,
            QuestDefinition.class
        );
        QuestDefinition housingQuest = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "id": "q_house_farmer",
              "category": "housing",
              "grantPlotTokenConstructionId": "plot_house"
            }
            """,
            QuestDefinition.class
        );

        assertTrue(StarterTownQuestService.shouldAutoComplete(buildingQuest));
        assertTrue(
            StarterTownQuestService.matchesBuiltConstruction(
                ConstructionCatalog.empty(),
                buildingQuest,
                "plot_farm"
            )
        );
        assertFalse(StarterTownQuestService.shouldAutoComplete(housingQuest));
    }

    @Test
    void presetAndWorkplaceMatchingUseCanonicalIds() {
        assertEquals(StarterTownPreset.MINIMAL, StarterTownPreset.parse("minimal"));
        assertEquals(StarterTownPreset.FULL, StarterTownPreset.parse("FULL"));
        assertTrue(StarterTownPreset.fullCanonicalIds().contains("plot_market_stall"));
        assertTrue(StarterTownPreset.fullCanonicalIds().contains("plot_guild_hall"));
        assertTrue(StarterTownPreset.fullCanonicalIds().contains("plot_tourist_portal"));
        assertFalse(StarterTownPreset.fullCanonicalIds().contains("plot_bronze_mechanics_shop"));
        assertTrue(
            StarterTownVillagerProvisioner.workplaceMatches(
                ConstructionCatalog.empty(),
                "plot_farm",
                "plot_farm"
            )
        );
        assertFalse(
            StarterTownVillagerProvisioner.workplaceMatches(
                ConstructionCatalog.empty(),
                "plot_farm",
                "plot_barn"
            )
        );
    }
}
