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
        var jobs = Map.of("chef", VillagerWorkActivity.MIX, "florist", VillagerWorkActivity.TEND,
            "innkeeper", VillagerWorkActivity.SWEEP, "merchant", VillagerWorkActivity.READ,
            "furniture_merchant", VillagerWorkActivity.READ, "crystal_keeper", VillagerWorkActivity.READ,
            "pyrotechnic", VillagerWorkActivity.READ);
        for (String marker : new String[]{"read", "craft"}) {
            var work = poi("WORK", marker, PoiInteractionKind.WORK_SURFACE);
            for (var job : jobs.entrySet()) assertEquals(job.getValue(), VillagerWorkActivity.resolve(work, job.getKey()), job.getKey());
        }
        assertEquals("Food_Salad_Caesar", VillagerLifeProps.itemFor("Mix", "chef"));
        assertEquals("Tool_Hammer_Iron", VillagerLifeProps.itemFor("Craft", "builder"));
        assertEquals("Plant_Flower_Bushy_Blue", VillagerLifeProps.itemFor("Tend", "florist"));
        assertNull(VillagerLifeProps.itemFor("Inspect", "merchant"));
        assertEquals(VillagerWorkActivity.READ, VillagerWorkActivity.resolve(poi("WORK", "inspect", PoiInteractionKind.WORK_SURFACE), "merchant"));
    }

    @Test void shopkeepersReadOccasionallyAndTakeQuietBreaks() {
        var work = poi("WORK", "read", PoiInteractionKind.WORK_SURFACE);
        int sweeping = 0, reading = 0, quiet = 0;
        for (int i = 0; i < 100; i++) {
            switch (VillagerWorkActivity.chooseBeat(work, "merchant", false, i / 100.0)) {
                case SWEEP -> sweeping++;
                case READ -> reading++;
                case LEISURE -> quiet++;
                default -> fail("Unexpected merchant activity");
            }
        }
        assertEquals(20, sweeping);
        assertEquals(15, reading);
        assertEquals(65, quiet);
    }

    @Test void explicitReadingAndSeatsStillHaveQuietBreaks() {
        for (boolean seated : new boolean[]{false, true}) {
            var marker = poi("WORK", "read", PoiInteractionKind.WORK_SURFACE);
            int reading = 0;
            for (int i = 0; i < 100; i++) {
                var beat = VillagerWorkActivity.chooseBeat(marker, "librarian", seated, i / 100.0);
                if (beat == VillagerWorkActivity.READ) reading++;
                else assertEquals(VillagerWorkActivity.LEISURE, beat);
            }
            assertEquals(15, reading);
        }
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
