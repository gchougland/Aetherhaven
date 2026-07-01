package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class PoiScoringBardWorkFallbackTest {

    @Test
    void pickBest_bardWorkShiftFallsBackToGenericWorkPoiOnAssignedPlot() {
        UUID plotId = UUID.randomUUID();
        UUID townId = UUID.randomUUID();
        PoiEntry guildMasterDesk =
            poi(plotId, townId, 10, 64, 20, List.of("WORK"), PoiInteractionKind.WORK_SURFACE);
        PoiEntry bardSpot =
            poi(plotId, townId, 12, 64, 22, List.of("WORK", AetherhavenConstants.POI_TAG_BARD), PoiInteractionKind.WORK_SURFACE);

        TownVillagerBinding binding = new TownVillagerBinding(townId, TownVillagerBinding.KIND_BARD, plotId, plotId);
        VillagerNeeds needs = VillagerNeeds.full();

        PoiEntry preferred = PoiScoring.pickBest(
            List.of(guildMasterDesk, bardSpot),
            needs,
            binding,
            Map.of(),
            0.0,
            0.0,
            VillagerScheduleResolver.LOC_WORK
        );
        assertEquals(bardSpot.getId(), preferred.getId());

        PoiEntry relaxed =
            PoiScoring.pickBest(
                List.of(guildMasterDesk),
                needs,
                binding,
                Map.of(),
                0.0,
                0.0,
                VillagerScheduleResolver.LOC_WORK
            );
        assertNotNull(relaxed);
        assertEquals(guildMasterDesk.getId(), relaxed.getId());
    }

    private static PoiEntry poi(
        UUID plotId,
        UUID townId,
        int x,
        int y,
        int z,
        List<String> tags,
        PoiInteractionKind kind
    ) {
        return new PoiEntry(
            UUID.randomUUID(),
            townId,
            x,
            y,
            z,
            Set.copyOf(tags),
            1,
            plotId,
            null,
            kind
        );
    }
}
