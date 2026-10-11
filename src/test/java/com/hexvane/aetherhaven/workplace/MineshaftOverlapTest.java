package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class MineshaftOverlapTest {
    private static final Gson GSON = new Gson();

    private TownRecord town() {
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "Stacked mines", 16, 100, 16, 1, 2, 0);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner", "miner", null, UUID.randomUUID()));
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.MINING));
        var saved = GSON.toJsonTree(town).getAsJsonObject();
        saved.getAsJsonObject("apprenticeWorkplaces").getAsJsonObject("extraSlots").addProperty("miner", 2);
        return GSON.fromJson(saved, TownRecord.class);
    }

    private ApprenticeWorkplace base(TownRecord town, int y) {
        var sites = town.getApprenticeWorkplaces();
        assertNull(sites.save(town, null, TownsfolkWorkType.MINING, new WorkplaceArea(8, y, 8, 14, 14), "Base " + y));
        return sites.sites().getLast();
    }

    private ApprenticeWorkplace tunnel(TownRecord town, int y, MineshaftSelection.Direction direction,
            int length, ExcavationPlan.Layout layout) {
        var area = MineshaftSelection.area(11, y - 1, 11, direction, length, layout);
        var site = new ApprenticeWorkplace("Passage", TownsfolkWorkType.MINING, area);
        var entrance = MineshaftSelection.entrance(direction);
        site.setMineshaftConfiguration(entrance, layout);
        var cells = ExcavationPlan.tunnel(area, entrance, layout).stream()
            .map(p -> new ExcavationPlan.Cell(p, "Rock_Stone", 0, 1)).toList();
        assertNull(site.excavation().start(0, town.getOwnerUuid(), UUID.randomUUID(), area, entrance, layout, cells));
        return site;
    }

    @Test void stackedStairsSevenBlocksApartCanBelongToSeparateMinesInEveryDirection() {
        for (var direction : MineshaftSelection.Direction.values()) {
            var town = town();
            var upperBase = base(town, 107);
            var lowerBase = base(town, 100);
            var upper = tunnel(town, 107, direction, 12, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
            var lower = tunnel(town, 100, direction, 12, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
            var sites = town.getApprenticeWorkplaces();
            assertTrue(upper.area().overlaps(lower.area()), "Padded bounds overlap although the passages do not");
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), upperBase.id(), upper, null));
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), lowerBase.id(), lower, null), direction.name());
            var restored = GSON.fromJson(GSON.toJson(town), TownRecord.class);
            assertEquals(lower.area(), restored.getApprenticeWorkplaces().find(lower.id()).area());
        }
    }

    @Test void lowerTunnelFitsBelowHighEndOfDescendingStairs() {
        for (var direction : MineshaftSelection.Direction.values()) {
            var town = town();
            var upperBase = base(town, 107);
            var lowerBase = base(town, 100);
            var upper = tunnel(town, 107, direction, 30, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
            var lower = tunnel(town, 100, direction, 9, ExcavationPlan.Layout.STRAIGHT);
            var sites = town.getApprenticeWorkplaces();
            assertTrue(upper.area().overlaps(lower.area()));
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), upperBase.id(), upper, null));
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), lowerBase.id(), lower, null), direction.name());
        }
    }

    @Test void actualPassageAndSupportingFloorCollisionsStillRejectWithoutSaving() {
        for (int length : new int[]{21, 30}) {
            var town = town();
            var upperBase = base(town, 107);
            var lowerBase = base(town, 100);
            var upper = tunnel(town, 107, MineshaftSelection.Direction.SOUTH, 30, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
            var lower = tunnel(town, 100, MineshaftSelection.Direction.SOUTH, length, ExcavationPlan.Layout.STRAIGHT);
            var sites = town.getApprenticeWorkplaces();
            assertNull(sites.addTunnel(town, town.getOwnerUuid(), upperBase.id(), upper, null));
            var before = GSON.toJson(sites);
            assertNotNull(sites.addTunnel(town, town.getOwnerUuid(), lowerBase.id(), lower, null));
            assertEquals(before, GSON.toJson(sites));
            assertNull(sites.find(lower.id()));
        }
    }

    @Test void threeBlockTunnelsCanStackWithOneSolidFloorBetweenThem() {
        var town = town();
        for (var direction : MineshaftSelection.Direction.values()) {
            var upper = tunnel(town, 100, direction, 9, ExcavationPlan.Layout.STRAIGHT);
            var lower = tunnel(town, 96, direction, 9, ExcavationPlan.Layout.STRAIGHT);
            assertTrue(upper.area().overlaps(lower.area()));
            assertFalse(WorkplaceOverlap.tunnel(lower, upper));
            assertFalse(WorkplaceOverlap.tunnel(upper, lower));
            var tooHigh = tunnel(town, 97, direction, 9, ExcavationPlan.Layout.STRAIGHT);
            assertTrue(WorkplaceOverlap.tunnel(tooHigh, upper), "Keep the upper passage's supporting floor");
        }
    }

    @Test void ordinaryWorkplaceCanFitBelowTheHighEndButItsWholeSpaceRemainsProtected() {
        var town = town();
        var upper = tunnel(town, 107, MineshaftSelection.Direction.SOUTH, 30, ExcavationPlan.Layout.DEEP_STAIRS_DOWN);
        var lowerArea = new WorkplaceArea(8, 100, 8, 14, 14);
        assertTrue(upper.area().overlaps(lowerArea));
        assertFalse(WorkplaceOverlap.workplace(lowerArea, upper));
        var ordinary = new ApprenticeWorkplace("Workshop", TownsfolkWorkType.MINING, lowerArea);
        assertFalse(WorkplaceOverlap.tunnel(upper, ordinary));
        assertNull(ordinary.existingExcavation(), "Checking occupancy must not allocate a mining order");
        var collision = tunnel(town, 102, MineshaftSelection.Direction.SOUTH, 9, ExcavationPlan.Layout.STRAIGHT);
        assertTrue(WorkplaceOverlap.tunnel(collision, ordinary));
    }
}
