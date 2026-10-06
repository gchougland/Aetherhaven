package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkState;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownsfolkActivityUiTest {
    @Test void oldMiningFlagsCannotDescribeANewProfession() {
        var state = new ApprenticeWorkState();
        state.active = true; state.working = true;
        // Mining's stop path clears its site, while pickup/delivery flags may remain until the next mining trip.
        state.excavationDelivery = true; state.excavationPickup = true;
        state.foodSite = UUID.randomUUID(); state.foodHarvest = true;
        assertEquals("Harvesting crops at Garden", TownsfolkActivityUi.work(state, TownsfolkWorkType.FARMING, "Garden"));
        state.working = false;
        assertEquals("Walking to Garden", TownsfolkActivityUi.work(state, TownsfolkWorkType.FARMING, "Garden"));
    }

    @Test void anActiveMiningTripStillShowsCarryingAndPickupBeforeWorking() {
        var state = new ApprenticeWorkState(); state.active = true; state.excavationSite = UUID.randomUUID();
        state.excavationDelivery = true;
        assertEquals("Delivering mined material", TownsfolkActivityUi.work(state, TownsfolkWorkType.MINING, "Mine"));
        state.excavationDelivery = false; state.excavationPickup = true;
        assertEquals("Collecting mined material", TownsfolkActivityUi.work(state, TownsfolkWorkType.MINING, "Mine"));
    }

    @Test void cancelledBatchCollectionIsNotReportedAsProductionOutput() {
        assertEquals("Collecting returned supplies", TownsfolkActivityUi.delivery("collect"));
        assertEquals("Collecting supplies", TownsfolkActivityUi.delivery("pickup"));
        assertEquals("Delivering supplies", TownsfolkActivityUi.delivery("deliver"));
        assertEquals("Returning supplies", TownsfolkActivityUi.delivery("return"));
    }
}
