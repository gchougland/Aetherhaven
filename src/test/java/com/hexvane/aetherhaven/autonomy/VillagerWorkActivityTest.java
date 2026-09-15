package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerWorkActivityTest {
    private static PoiEntry poi(String purpose, String activity, PoiInteractionKind interaction) {
        return new PoiEntry(UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0,
            Set.of(purpose, "workActivity:" + activity), 1, null, null, interaction);
    }

    @Test void oldReadAndCraftShopMarkersUseTheResidentsJobItems() {
        var jobs = Map.of("chef", VillagerWorkActivity.CRAFT, "florist", VillagerWorkActivity.TEND,
            "innkeeper", VillagerWorkActivity.SWEEP, "merchant", VillagerWorkActivity.INSPECT,
            "furniture_merchant", VillagerWorkActivity.INSPECT, "crystal_keeper", VillagerWorkActivity.INSPECT,
            "pyrotechnic", VillagerWorkActivity.INSPECT);
        for (String marker : new String[]{"read", "craft"}) {
            var work = poi("WORK", marker, PoiInteractionKind.WORK_SURFACE);
            for (var job : jobs.entrySet()) assertEquals(job.getValue(), VillagerWorkActivity.resolve(work, job.getKey()), job.getKey());
        }
        assertTrue(VillagerLifeProps.itemFor("Craft", "chef").endsWith("Spoon"));
        assertTrue(VillagerLifeProps.itemFor("Craft", "builder").endsWith("Mallet"));
        assertTrue(VillagerLifeProps.itemFor("Tend", "florist").endsWith("Plant"));
        assertTrue(VillagerLifeProps.itemFor("Inspect", "merchant").endsWith("Stone"));
    }

    @Test void standingShopkeepersOccasionallySweepButMostlyDoTheirJob() {
        var work = poi("WORK", "read", PoiInteractionKind.WORK_SURFACE);
        int sweeping = 0, reading = 0, inspecting = 0;
        for (int i = 0; i < 100; i++) {
            switch (VillagerWorkActivity.chooseBeat(work, "merchant", false, i / 100.0)) {
                case SWEEP -> sweeping++;
                case READ -> reading++;
                case INSPECT -> inspecting++;
                default -> fail("Unexpected merchant activity");
            }
        }
        assertEquals(25, sweeping);
        assertEquals(15, reading);
        assertEquals(60, inspecting);
    }

    @Test void seatsMealsLeisureAndExplicitActivitiesDoNotBecomeSweeping() {
        var work = poi("WORK", "read", PoiInteractionKind.WORK_SURFACE);
        assertNotEquals(VillagerWorkActivity.SWEEP, VillagerWorkActivity.chooseBeat(work, "innkeeper", true, 0));
        assertNotEquals(VillagerWorkActivity.SWEEP, VillagerWorkActivity.chooseBeat(work, "merchant", true, 0));
        assertEquals(VillagerWorkActivity.READ, VillagerWorkActivity.chooseBeat(poi("FUN", "read", PoiInteractionKind.SIT), "merchant", false, 0));
        assertEquals(VillagerWorkActivity.LEISURE, VillagerWorkActivity.chooseBeat(poi("WORK", "read", PoiInteractionKind.SLEEP), "merchant", false, 0));
        assertEquals(VillagerWorkActivity.LEISURE, VillagerWorkActivity.chooseBeat(poi("EAT", "read", PoiInteractionKind.SIT), "chef", false, 0));
        assertEquals(VillagerWorkActivity.TEND, VillagerWorkActivity.chooseBeat(poi("WORK", "tend", PoiInteractionKind.WORK_SURFACE), "florist", false, 0));
        assertEquals(VillagerWorkActivity.SMITH, VillagerWorkActivity.chooseBeat(work, "blacksmith", false, 0));
        assertEquals(VillagerWorkActivity.CRAFT, VillagerWorkActivity.resolve(poi("WORK", "craft", PoiInteractionKind.WORK_SURFACE), "builder"));
    }
}
