package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tourist")
class TouristPurgeProtectionTest {

    private static final ConstructionCatalog CATALOG = ConstructionCatalog.empty();

    @Test
    void activeVisitorIsNotProtected() {
        TownRecord town = new TownRecord();
        UUID entityUuid = UUID.randomUUID();
        TouristRecord rec =
            new TouristRecord("char_a", entityUuid, UUID.randomUUID(), false, false, 1L, 20);

        assertFalse(TouristPortalTickService.shouldProtectTouristFromPurge(town, rec, entityUuid, CATALOG));
    }

    @Test
    void invitedToStayTouristIsProtected() {
        TownRecord town = new TownRecord();
        UUID entityUuid = UUID.randomUUID();
        TouristRecord rec =
            new TouristRecord("char_b", entityUuid, UUID.randomUUID(), true, false, 1L, 20);

        assertTrue(TouristPortalTickService.shouldProtectTouristFromPurge(town, rec, entityUuid, CATALOG));
    }

    @Test
    void promotedCitizenTouristIsProtected() {
        TownRecord town = new TownRecord();
        UUID entityUuid = UUID.randomUUID();
        TouristRecord rec =
            new TouristRecord("char_c", entityUuid, UUID.randomUUID(), true, true, 1L, 20);

        assertTrue(TouristPortalTickService.shouldProtectTouristFromPurge(town, rec, entityUuid, CATALOG));
    }

    @Test
    void housedTouristWithoutRecordFlagsIsProtected() {
        TownRecord town = new TownRecord();
        UUID entityUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        PlotInstance plot = new PlotInstance();
        plot.setPlotId(plotId);
        plot.setConstructionId(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
        plot.setState(PlotInstanceState.COMPLETE);
        plot.setHomeResidentEntityUuid(entityUuid);
        town.addPlotInstance(plot);

        assertTrue(TouristPortalTickService.shouldProtectTouristFromPurge(town, null, entityUuid, CATALOG));
    }

    @Test
    void guardBindingCheckReturnsFalseWithoutLiveEntity() {
        assertFalse(TouristPortalTickService.isGuardEntityForPurge(null, null));
    }

    @Test
    void unboundTownsfolkShellIsOrphanForPurge() {
        UUID uuid = UUID.randomUUID();
        assertTrue(
            TouristPortalTickService.isOrphanTownsfolkShellForPurge(
                AetherhavenConstants.NPC_TOWNSFOLK,
                false,
                false,
                false,
                false,
                uuid,
                Set.of()
            )
        );
    }

    @Test
    void townsfolkWithBindingIsNotOrphanShell() {
        UUID uuid = UUID.randomUUID();
        assertFalse(
            TouristPortalTickService.isOrphanTownsfolkShellForPurge(
                AetherhavenConstants.NPC_TOWNSFOLK,
                true,
                false,
                false,
                false,
                uuid,
                Set.of()
            )
        );
    }

    @Test
    void trackedTownsfolkShellIsNotOrphanForPurge() {
        UUID uuid = UUID.randomUUID();
        assertFalse(
            TouristPortalTickService.isOrphanTownsfolkShellForPurge(
                AetherhavenConstants.NPC_TOWNSFOLK,
                false,
                false,
                false,
                false,
                uuid,
                Set.of(uuid)
            )
        );
    }

    @Test
    void plotCreatorSpotPreviewTownsfolkIsNotOrphanShell() {
        UUID uuid = UUID.randomUUID();
        assertFalse(
            TouristPortalTickService.isOrphanTownsfolkShellForPurge(
                AetherhavenConstants.NPC_TOWNSFOLK,
                false,
                false,
                false,
                true,
                uuid,
                Set.of()
            )
        );
    }
}
