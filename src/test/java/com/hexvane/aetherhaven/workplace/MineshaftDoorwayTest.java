package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.ExcavationPlan.Cell;
import com.hexvane.aetherhaven.workplace.ExcavationPlan.Found;
import com.hexvane.aetherhaven.workplace.MineshaftSelection.Direction;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Where two passages meet: no post stands in the doorway, and rock that turns up there is cleared by whichever of the
 * two is being worked.
 *
 * <p>A gallery's frames stand every sixth block from its second. A branch leaving it at one of them used to start
 * with a post in its doorway, and rock arriving in the one shared column was in neither passage's order.
 */
@Tag("town")
class MineshaftDoorwayTest {
    private static final Point ANCHOR = new Point(520, 111, -370);
    /** The gallery's eighth block, where its second frame stands. */
    private static final Point FRAME = new Point(520, 111, -378);

    private static MineFixture mineWithGallery(ExcavationFittings.Mode fittings) {
        var mine = MineFixture.facing(ANCHOR, Direction.NORTH);
        mine.add("Gallery", ANCHOR, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 12, fittings);
        return mine;
    }

    private static ApprenticeWorkplace gallery(MineFixture mine) {
        return mine.sites.tunnels(mine.base.id()).getFirst();
    }

    /** The rows that still have a frame to put up, read from a copy of the order so nothing is placed. */
    private static Set<Integer> frameRows(ExcavationPlan plan) {
        var rows = new HashSet<Integer>();
        var copy = new Gson().fromJson(new Gson().toJson(plan), ExcavationPlan.class);
        copy.survey(p -> Found.EMPTY, 0);
        while (copy.nextFitting() != null) {
            var fitting = copy.nextFitting();
            if (ExcavationFittings.BEAM.equals(fitting.item())) {
                rows.add(fitting.point().z());
            }
            assertTrue(copy.passFitting(fitting, true));
        }
        return rows;
    }

    @Test
    void aFrameNotYetBuiltIsLeftOutWholeWhereABranchOpens() {
        // At the frame, and one block either side of it, the branch's three block width takes in the post.
        for (int block : new int[] {7, 8, 9}) {
            for (var side : List.of(Direction.EAST, Direction.WEST)) {
                var mine = mineWithGallery(ExcavationFittings.Mode.SUPPORTS);
                var gallery = gallery(mine);
                assertEquals(14, gallery.excavation().fittingsTotal());
                assertEquals(Set.of(-372, -378), frameRows(gallery.excavation()));
                mine.add("Branch", MineFixture.step(ANCHOR, Direction.NORTH, block), side, ExcavationPlan.Layout.STRAIGHT, 9);
                String where = "block " + block + " " + side;
                // Both posts, both corners and the ceiling piece go together. The other frame is untouched.
                assertEquals(7, gallery.excavation().fittingsTotal(), where);
                assertEquals(Map.of(ExcavationFittings.BEAM, 7), gallery.excavation().fittingsNeeded(), where);
                assertEquals(Set.of(-372), frameRows(gallery.excavation()), where);
                assertTrue(gallery.excavation().unbuild().isEmpty(), where);
            }
        }
        // Between the frames nothing is in the doorway and nothing changes.
        var mine = mineWithGallery(ExcavationFittings.Mode.SUPPORTS);
        mine.add("Branch", MineFixture.step(ANCHOR, Direction.NORTH, 5), Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);
        assertEquals(14, gallery(mine).excavation().fittingsTotal());
    }

    @Test
    void aBuiltFrameIsTakenDownAgainWhenALaterBranchMakesADoorwayOfIt() {
        var mine = mineWithGallery(ExcavationFittings.Mode.SUPPORTS);
        var gallery = gallery(mine);
        var plan = gallery.excavation();
        mine.dig(gallery);
        mine.fit(gallery);
        assertEquals(14, plan.fitted());
        assertTrue(plan.passageReady());
        assertFalse(plan.unfinished());

        var branch = mine.add("Branch", FRAME, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);
        // The seven pieces of that frame are no longer part of the order, and are listed to come down.
        assertEquals(7, plan.fitted());
        assertEquals(7, plan.fittingsTotal());
        assertEquals(7, plan.unbuild().size());
        assertTrue(plan.unbuild().stream().allMatch(piece -> piece.point().z() == -378));
        assertEquals(4, plan.unbuild().stream().filter(piece -> piece.block().equals(ExcavationFittings.BEAM)).count());
        assertEquals(Set.of(new Point(519, 111, -378), new Point(521, 111, -378)),
            Set.copyOf(plan.unbuild().stream().map(Cell::point).filter(p -> p.y() == 111).toList()));
        // Until they are down the gallery is not finished, and the branch waits for it.
        assertFalse(plan.passageReady());
        assertTrue(plan.unfinished());
        assertTrue(plan.pendingPoints().contains(new Point(521, 111, -378)));
        assertEquals(gallery.id(), branch.minePrerequisiteId());

        // Each piece is queued only if it still stands. One the player already knocked out is simply forgotten.
        var pieces = List.copyOf(plan.unbuild());
        plan.resolveUnbuild(pieces.getFirst(), false);
        for (var piece : pieces.subList(1, pieces.size())) {
            plan.resolveUnbuild(piece, true);
        }
        assertTrue(plan.unbuild().isEmpty());
        assertEquals(pieces.get(1), plan.next());
        int taken = 0;
        while (plan.next() != null) {
            assertTrue(plan.prepare(Map.of(ExcavationFittings.BEAM, 1)));
            for (int tick = 0; tick < 12; tick++) {
                plan.attend(.5, true, 1);
            }
            assertTrue(plan.begin(plan.id(), 6, new Point(520, 111, -378), () -> true));
            assertTrue(plan.settle(plan.id(), true));
            assertTrue(plan.deposit(plan.id()));
            taken++;
        }
        // The beams come back to the mine, and nothing is put up there again.
        assertEquals(6, taken);
        assertEquals(6, plan.output().get(ExcavationFittings.BEAM));
        assertTrue(plan.passageReady());
        assertNull(plan.nextFitting());
        // Looking again changes nothing.
        assertFalse(MineshaftNetwork.openDoorways(mine.sites, mine.base, gallery));
        assertFalse(MineshaftNetwork.openDoorways(mine.sites, mine.base, branch));
    }

    @Test
    void theBranchKeepsItsOwnFramesAndACrossingDraftIsCountedWithoutTheOneInTheDoorway() {
        var mine = mineWithGallery(ExcavationFittings.Mode.NONE);
        var open = MineshaftNetwork.openSpace(mine.sites, mine.base, null);
        // A branch off the gallery: its first frame stands in its own second block, clear of the gallery.
        var branch = MineshaftSelection.area(FRAME.x(), FRAME.y() - 1, FRAME.z(), Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT);
        var east = MineshaftSelection.entrance(Direction.EAST);
        assertEquals(14, ExcavationFittings.needs(branch, east, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.SUPPORTS, open).get(ExcavationFittings.BEAM));
        // A route across the gallery whose first frame would stand in the gallery's middle lane: seven beams, not fourteen.
        var crossing = MineshaftSelection.area(518, 110, -376, Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT);
        assertEquals(14, ExcavationFittings.needs(crossing, east, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.SUPPORTS).get(ExcavationFittings.BEAM));
        var counted = ExcavationFittings.needs(crossing, east, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.ALL_RAILS, open);
        assertEquals(7, counted.get(ExcavationFittings.BEAM));
        // Lanterns and rails never stand in anyone's way and are counted as before.
        assertEquals(ExcavationFittings.needs(crossing, east, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.ALL_RAILS).get(ExcavationFittings.RAIL), counted.get(ExcavationFittings.RAIL));
        assertEquals(ExcavationFittings.needs(crossing, east, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.ALL_RAILS).get(ExcavationFittings.LANTERN), counted.get(ExcavationFittings.LANTERN));
    }

    @Test
    void aWallTorchLeavesTheWallABranchOpens() {
        var mine = mineWithGallery(ExcavationFittings.Mode.BOTH);
        var gallery = gallery(mine);
        var planned = ExcavationFittings.plan(gallery.area(), gallery.excavation().entrance(), ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.BOTH);
        var torch = planned.stream().filter(f -> ExcavationFittings.TORCH.equals(f.item())).findFirst().orElseThrow();
        int torches = (int) planned.stream().filter(f -> ExcavationFittings.TORCH.equals(f.item())).count();
        assertEquals(torches, gallery.excavation().fittingsNeeded().get(ExcavationFittings.TORCH));
        // A branch through the wall the torch hangs on, at the torch's own block.
        var side = torch.point().x() > ANCHOR.x() ? Direction.EAST : Direction.WEST;
        mine.add("Branch", new Point(ANCHOR.x(), ANCHOR.y(), torch.point().z()), side, ExcavationPlan.Layout.STRAIGHT, 9);
        assertEquals(torches - 1, gallery.excavation().fittingsNeeded().get(ExcavationFittings.TORCH));
        // A branch through the opposite wall leaves it where it is.
        var other = mineWithGallery(ExcavationFittings.Mode.BOTH);
        other.add("Branch", new Point(ANCHOR.x(), ANCHOR.y(), torch.point().z()), MineshaftNetwork.opposite(side), ExcavationPlan.Layout.STRAIGHT, 9);
        assertEquals(torches, gallery(other).excavation().fittingsNeeded().get(ExcavationFittings.TORCH));
    }

    @Test
    void theSharedColumnBelongsToBothPassagesButAStepsBlockToNeither() {
        var mine = mineWithGallery(ExcavationFittings.Mode.NONE);
        var gallery = gallery(mine);
        var branch = mine.add("Branch", FRAME, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);
        var shared = new HashSet<Point>();
        for (int z = -379; z <= -377; z++) {
            for (int y = 111; y <= 113; y++) {
                shared.add(new Point(521, y, z));
            }
        }
        assertEquals(shared, MineshaftNetwork.junction(mine.sites, mine.base, branch));
        assertEquals(shared, MineshaftNetwork.junction(mine.sites, mine.base, gallery));

        // The owner's stairs with a tunnel north off their landing: the step beside the landing is in neither's junction.
        var stairs = MineFixture.facing(new Point(520, 111, -370), Direction.WEST);
        var down = stairs.add("Stairs", new Point(520, 111, -370), Direction.WEST, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);
        var north = stairs.add("Tunnel", new Point(511, 104, -370), Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9);
        var step = new Point(512, 104, -371);
        var meeting = MineshaftNetwork.junction(stairs.sites, stairs.base, north);
        assertTrue(meeting.contains(new Point(511, 104, -371)));
        assertTrue(meeting.contains(new Point(512, 105, -371)));
        assertFalse(meeting.contains(step));
        assertFalse(MineshaftNetwork.junction(stairs.sites, stairs.base, down).contains(step));
        assertTrue(MineshaftNetwork.walkwaysFor(stairs.sites, stairs.base, north.id()).contains(step));
    }

    @Test
    void rockFoundInAJunctionGoesToTheFrontOfTheOrder() {
        var area = new WorkplaceArea(8, 100, 8, 15, 14);
        var first = new Cell(new Point(10, 102, 11), "Rock_Stone", 0, 1);
        var second = new Cell(new Point(11, 102, 11), "Rock_Stone", 0, 1);
        var plan = new ExcavationPlan();
        assertNull(plan.start(0, UUID.randomUUID(), UUID.randomUUID(), area, ExcavationPlan.Entrance.NORTH, List.of(first, second)));
        assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
        for (int tick = 0; tick < 12; tick++) {
            plan.attend(.5, true, 1);
        }
        var arrived = new Cell(new Point(11, 100, 11), "Soil_Gravel", 0, 1);
        assertTrue(plan.requeueFirst(arrived));
        assertEquals(arrived, plan.next());
        assertEquals(3, plan.total());
        // What had been prepared for the block behind it is dropped: the miner starts on the gravel.
        assertFalse(plan.dropsPrepared());
        assertEquals(0, plan.work());
        assertFalse(plan.requeueFirst(arrived));
        assertFalse(plan.requeueFirst(first));
        // Mid swing nothing is moved.
        assertTrue(plan.prepare(Map.of("Soil_Gravel", 1)));
        for (int tick = 0; tick < 12; tick++) {
            plan.attend(.5, true, 1);
        }
        assertTrue(plan.begin(plan.id(), 6, new Point(11, 100, 10), () -> true));
        assertFalse(plan.requeueFirst(new Cell(new Point(12, 100, 11), "Soil_Gravel", 0, 1)));
        assertTrue(plan.settle(plan.id(), true));
        assertEquals(first, plan.next());

        // The look at the junction comes round every three seconds while there is work, not on every glance.
        assertTrue(plan.unfinished());
        assertTrue(plan.junctionCheckDue(10_000));
        assertFalse(plan.junctionCheckDue(11_000));
        assertTrue(plan.junctionCheckDue(13_000));
        assertNotNull(plan.next());
    }

    @Test
    void anOlderSaveLoadsWithNothingToTakeDown() {
        var mine = mineWithGallery(ExcavationFittings.Mode.SUPPORTS);
        var gson = new Gson();
        var saved = gson.toJsonTree(gallery(mine).excavation()).getAsJsonObject();
        assertNotNull(saved.remove("unbuild"));
        var restored = gson.fromJson(saved, ExcavationPlan.class);
        assertTrue(restored.unbuild().isEmpty());
        assertEquals(14, restored.fittingsTotal());
        // Its doorways are looked at once in a session, the first time anyone asks.
        assertTrue(restored.claimDoorwayCheck());
        assertFalse(restored.claimDoorwayCheck());
    }
}
