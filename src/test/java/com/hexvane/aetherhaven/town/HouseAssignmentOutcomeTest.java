package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.HouseResidentAssignment.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P18: giving a waiting townsfolk a home must either work or say why, never report a home that was not given. */
@Tag("town")
class HouseAssignmentOutcomeTest {
    private static final String HOUSE_QUEST = AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK;
    private final Gson gson = new Gson();
    private final ConstructionCatalog catalog = ConstructionCatalog.empty();

    private PlotInstance plot(String construction, String state) {
        return gson.fromJson("{\"plotId\":\"" + UUID.randomUUID() + "\",\"constructionId\":\"" + construction
            + "\",\"state\":\"" + state + "\"}", PlotInstance.class);
    }

    /** Elder, Innkeeper and {@code workers} named workers: a town of {@code workers + 2}. */
    private TownRecord town(int workers) {
        TownRecord town = new TownRecord();
        town.setElderEntityUuid(UUID.randomUUID());
        town.setInnkeeperEntityUuid(UUID.randomUUID());
        for (int i = 0; i < workers; i++)
            town.getResidentNpcRecords().add(new ResidentNpcRecord("Core_" + i, "worker", null, UUID.randomUUID()));
        return town;
    }

    private PlotInstance house(TownRecord town) {
        PlotInstance house = plot(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE, "COMPLETE");
        town.getPlotInstances().add(house);
        return house;
    }

    /** A portal visitor whose house quest the player has accepted, as Mertie was. */
    private UUID waitingTownsfolk(TownRecord town) {
        UUID mertie = UUID.randomUUID();
        town.getTouristRecords().add(new TouristRecord("mertie", mertie, UUID.randomUUID(), true, false, 1L, 20));
        town.addActiveQuest(HOUSE_QUEST);
        town.setQuestTargetEntityUuid(HOUSE_QUEST, mertie);
        return mertie;
    }

    @Test
    void aWaitingTownsfolkIsHousedWhenTheTownCanFeedThem() {
        TownRecord town = town(2);
        town.getPlotInstances().add(plot("plot_inn", "COMPLETE"));
        PlotInstance house = house(town);
        UUID mertie = waitingTownsfolk(town);
        assertEquals(4, TownPopulation.count(town));
        assertTrue(TownPopulation.snapshot(town, catalog).canAdmit());
        assertEquals(Outcome.ASSIGNED, HouseResidentAssignment.check(town, house.getPlotId(), 0, mertie, catalog));
        assertNull(house.getHomeResidentAt(0), "Checking changes nothing");
    }

    @Test
    void aFoodShortfallIsReportedInsteadOfASilentRefusal() {
        // The quest was accepted while there was room. Someone else then took the last place the food allows.
        TownRecord town = town(3);
        town.getPlotInstances().add(plot("plot_inn", "COMPLETE"));
        PlotInstance house = house(town);
        UUID mertie = waitingTownsfolk(town);
        assertEquals(5, TownPopulation.count(town));
        assertFalse(TownPopulation.snapshot(town, catalog).canAdmit());

        // Before: the move was dropped without a word and the page still said the home was updated.
        Outcome outcome = HouseResidentAssignment.check(town, house.getPlotId(), 0, mertie, catalog);
        assertEquals(Outcome.SHORT_FOOD, outcome);
        assertFalse(outcome.changed());
        assertEquals(2, TownPopulation.snapshot(town, catalog).foodShortfallForNextResident());

        // More food, and the same click works.
        town.getPlotInstances().add(plot("plot_farm", "COMPLETE"));
        assertEquals(Outcome.ASSIGNED, HouseResidentAssignment.check(town, house.getPlotId(), 0, mertie, catalog));
    }

    @Test
    void aTownBelowLevelTwoSaysSo() {
        TownRecord town = town(1);
        town.getPlotInstances().add(plot("plot_inn", "COMPLETE"));
        PlotInstance house = house(town);
        UUID mertie = waitingTownsfolk(town);
        assertEquals(Outcome.TOWN_TOO_SMALL, HouseResidentAssignment.check(town, house.getPlotId(), 0, mertie, catalog));
    }

    @Test
    void someoneWhoAlreadyBelongsToTheTownCanAlwaysBeMoved() {
        TownRecord town = town(3); // no food buildings at all
        PlotInstance house = house(town);
        assertFalse(TownPopulation.snapshot(town, catalog).canAdmit());
        UUID elder = town.getElderEntityUuid();
        UUID worker = town.getResidentNpcRecords().getFirst().getLastEntityUuid();
        UUID invited = UUID.randomUUID(); // welcomed through the guild hall or a rescue: a citizen from the start
        town.getTouristRecords().add(new TouristRecord("guest", invited, new UUID(0, 0), true, true));
        for (UUID member : new UUID[] {elder, worker, invited})
            assertEquals(Outcome.ASSIGNED, HouseResidentAssignment.check(town, house.getPlotId(), 0, member, catalog));
    }

    @Test
    void aVisitorWithoutAnAcceptedHouseQuestIsNotEligible() {
        TownRecord town = town(2);
        town.getPlotInstances().add(plot("plot_inn", "COMPLETE"));
        PlotInstance house = house(town);
        UUID visitor = UUID.randomUUID();
        town.getTouristRecords().add(new TouristRecord("visitor", visitor, UUID.randomUUID(), false, false, 1L, 20));
        assertEquals(Outcome.NOT_ELIGIBLE, HouseResidentAssignment.check(town, house.getPlotId(), 0, visitor, catalog));
    }

    @Test
    void clearingABedAlwaysWorksAndABadHouseIsNamed() {
        TownRecord town = town(2);
        PlotInstance house = house(town);
        PlotInstance unfinished = plot(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE, "BLUEPRINTING");
        PlotInstance inn = plot("plot_inn", "COMPLETE");
        town.getPlotInstances().add(unfinished);
        town.getPlotInstances().add(inn);
        UUID elder = town.getElderEntityUuid();

        assertEquals(Outcome.CLEARED, HouseResidentAssignment.check(town, house.getPlotId(), 0, null, catalog));
        assertTrue(Outcome.CLEARED.changed());
        assertEquals(Outcome.HOUSE_UNAVAILABLE, HouseResidentAssignment.check(town, house.getPlotId(), 1, elder, catalog), "One bed only");
        assertEquals(Outcome.HOUSE_UNAVAILABLE, HouseResidentAssignment.check(town, house.getPlotId(), -1, elder, catalog));
        assertEquals(Outcome.HOUSE_UNAVAILABLE, HouseResidentAssignment.check(town, unfinished.getPlotId(), 0, elder, catalog));
        assertEquals(Outcome.HOUSE_UNAVAILABLE, HouseResidentAssignment.check(town, inn.getPlotId(), 0, elder, catalog));
        assertEquals(Outcome.HOUSE_UNAVAILABLE, HouseResidentAssignment.check(town, UUID.randomUUID(), 0, elder, catalog));
    }
}
