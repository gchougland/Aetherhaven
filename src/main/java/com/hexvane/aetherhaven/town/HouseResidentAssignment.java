package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** House management block: assign a villager to a residential plot. House quests finish when the player talks to that NPC. */
public final class HouseResidentAssignment {
    private HouseResidentAssignment() {}

    /**
     * Sets the plot's home resident (or clears when {@code residentUuid} is null). Updates town data.
     */
    public static void assignResident(
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nullable UUID residentUuid,
        @Nonnull TownManager tm
    ) {
        PlotInstance pi = town.findPlotById(plotId);
        if (pi == null || !AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(pi.getConstructionId())) {
            return;
        }
        if (pi.getState() != PlotInstanceState.COMPLETE) {
            return;
        }
        if (residentUuid != null) {
            town.clearHomeResidentFromOtherPlots(plotId, residentUuid);
        }
        pi.setHomeResidentEntityUuid(residentUuid);
        tm.updateTown(town);
    }
}
