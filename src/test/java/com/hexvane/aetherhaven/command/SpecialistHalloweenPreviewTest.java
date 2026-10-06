package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.town.*;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class SpecialistHalloweenPreviewTest {
    @Test void rejectsSavedWorldsEvenWhenTheyUseThePreviewName() {
        assertFalse(SpecialistHalloweenPreview.isPreview("default", false));
        assertFalse(SpecialistHalloweenPreview.isPreview("aetherhaven_specialist_preview_fixture", true));
        assertFalse(SpecialistHalloweenPreview.isPreview(null, false));
        assertTrue(SpecialistHalloweenPreview.isPreview("aetherhaven_specialist_preview_fixture", false));
    }

    @Test void shelterNeedsActualRescueCompletionAndDoesNotCompleteAnything() {
        var town = new TownRecord();
        assertNotNull(SpecialistHalloweenPreview.shelterIssue(town));
        assertFalse(town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST));
        assertTrue(town.getPlotInstances().isEmpty());
        assertTrue(town.getResidentNpcRecords().isEmpty());
        town.completeQuest(HalloweenResident.RESCUE_QUEST);
        assertNull(SpecialistHalloweenPreview.shelterIssue(town));
        assertFalse(town.hasQuestCompleted("q_halloween_shelter"));
        assertNull(town.halloweenGame().active());
    }

    @Test void repeatedSetupCannotReplaceAnExistingShelterOrDuplicateJack() {
        var town = new TownRecord();
        town.completeQuest(HalloweenResident.RESCUE_QUEST);
        var plot = new PlotInstance(UUID.randomUUID(), "plot_halloween", PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 126, 0, 6, 132, 6), 1, 129, 1, 0);
        town.getPlotInstances().add(plot);
        assertNotNull(SpecialistHalloweenPreview.shelterIssue(town));
        assertSame(plot, town.getPlotInstances().getFirst());
        town.getPlotInstances().clear();
        var jack = new ResidentNpcRecord(HalloweenResident.MODEL, HalloweenResident.KIND, null, UUID.randomUUID());
        town.getResidentNpcRecords().add(jack);
        assertNotNull(SpecialistHalloweenPreview.shelterIssue(town));
        assertSame(jack, town.getResidentNpcRecords().getFirst());
    }
}
