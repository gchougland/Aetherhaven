package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.MineshaftMap.Kind;
import com.hexvane.aetherhaven.workplace.MineshaftSelection.Direction;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The planner's map shows a stairway on every floor it goes through, and a route aimed into it is explained before
 * the player tries to add it.
 *
 * <p>The owner's case, October 11: stairs came down heading south to a landing at 511, 104, -370. On that floor the
 * map drew only the landing, so the squares north of it, where the steps climb away overhead, looked like free rock.
 * The square at 510, 104, -373 read "Extend the nearby passage", he aimed a nine block tunnel north, and the planner
 * refused it without saying where the stairs were.
 */
@Tag("town")
class MineshaftStairwellMapTest {
    private static final Point LANDING = new Point(511, 104, -370);
    private static final Point TOP = new Point(511, 111, -379);

    /** His mine: a base to the north, and nine block stairs coming down south from it to the landing. */
    private static MineFixture ownersMine() {
        var mine = MineFixture.facing(TOP, Direction.SOUTH);
        mine.add("Stairs", TOP, Direction.SOUTH, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);
        assertEquals(mine.sites.tunnels(mine.base.id()).getFirst().id(), MineshaftNetwork.mapConnections(mine.sites, mine.base).get(LANDING));
        return mine;
    }

    /** The square of a map centred like his screenshot that covers the given block. */
    private static MineshaftMap.Cell square(List<MineshaftMap.Cell> cells, int x, int z) {
        return cells.stream().filter(cell -> cell.point().x() == x && cell.point().z() == z).findFirst().orElseThrow();
    }

    @Test
    void theLowerFloorShowsTheStairsWhereTheyClimbAwayOverhead() {
        var mine = ownersMine();
        var cells = MineshaftMap.cells(mine.sites, mine.base, 510, -370, 104, null);
        assertEquals(MineshaftMap.SIZE * MineshaftMap.SIZE, cells.size());

        // The square he hovered. It used to be drawn as free rock and to read "Extend the nearby passage".
        var hovered = square(cells, 510, -373);
        assertEquals(Kind.STAIRWELL, hovered.kind());
        assertEquals("510, 104, -373 | Stairs up to 111 pass here", hovered.tooltip());
        assertEquals(Direction.NORTH, hovered.climbs());
        assertEquals(0, hovered.landing());
        // The stairs are three wide; this map's squares split them in two.
        assertEquals(Kind.STAIRWELL, square(cells, 513, -373).kind());
        assertEquals("513, 104, -373 | Stairs up to 111 pass here", square(cells, 513, -373).tooltip());
        // The square east of the landing holds one lane of the last step and two of free rock. A tunnel east from the
        // landing is clear, so that square still extends the passage.
        assertEquals(Kind.ROCK, square(cells, 513, -370).kind());
        assertEquals("513, 104, -370 | Extend the nearby passage", square(cells, 513, -370).tooltip());

        // The landing itself is still a passage to branch from, with its stairs sign.
        var landing = square(cells, 510, -370);
        assertEquals(Kind.PASSAGE, landing.kind());
        assertEquals("Stairs up | 510, 104, -370 | Start a branch here", landing.tooltip());
        assertEquals(1, landing.landing());
        // Past the landing and beside it the rock really is free, and can be extended into.
        assertEquals("510, 104, -367 | Extend the nearby passage", square(cells, 510, -367).tooltip());
        assertEquals(Kind.ROCK, square(cells, 510, -367).kind());
        assertEquals("507, 104, -370 | Extend the nearby passage", square(cells, 507, -370).tooltip());
        // Three blocks further on the steps are high enough to pass under, and the map says rock again.
        assertEquals(Kind.ROCK, square(cells, 510, -376).kind());
        assertEquals("510, 104, -376 | No connecting passage", square(cells, 510, -376).tooltip());
        // Nowhere on this floor is a square the stairs go through offered as a place to extend into.
        for (var cell : cells) {
            assertFalse(cell.kind() == Kind.STAIRWELL && cell.tooltip().contains("Extend"), cell.tooltip());
        }
    }

    @Test
    void theUpperFloorShowsTheStairwellThatOpensBelowIt() {
        var mine = ownersMine();
        var cells = MineshaftMap.cells(mine.sites, mine.base, 510, -370, 111, null);
        var over = square(cells, 510, -373);
        assertEquals(Kind.STAIRWELL, over.kind());
        assertEquals("510, 111, -373 | Stairs down to 104 pass here", over.tooltip());
        assertEquals(Direction.NORTH, over.climbs());
        // The top landing is a passage of this floor.
        assertEquals("Stairs down | 510, 111, -376 | Start a branch here", square(cells, 510, -376).tooltip());
        // Over the bottom of the stairs there are four blocks of rock above their headroom: free ground on this floor.
        assertEquals(Kind.ROCK, square(cells, 510, -370).kind());
        // A floor the stairs do not reach shows nothing of them.
        var elsewhere = MineshaftMap.cells(mine.sites, mine.base, 510, -370, 97, null);
        assertTrue(elsewhere.stream().noneMatch(cell -> cell.kind() == Kind.STAIRWELL));
    }

    @Test
    void aSquareTheStairsGoThroughGivesNoStart() {
        var mine = ownersMine();
        var refused = MineshaftMap.choose(mine.sites, mine.base, 104, 510, -373);
        assertNull(refused.start());
        assertEquals("Stairs up to 111 pass here. Choose a square beside or beyond them.", refused.refusal());
        // The landing and the free rock past it behave as before.
        var landing = MineshaftMap.choose(mine.sites, mine.base, 104, 510, -370);
        assertEquals(LANDING, landing.start().anchor());
        assertNull(landing.start().direction());
        var beside = MineshaftMap.choose(mine.sites, mine.base, 104, 513, -370);
        assertEquals(LANDING, beside.start().anchor());
        assertEquals(Direction.EAST, beside.start().direction());
        var beyond = MineshaftMap.choose(mine.sites, mine.base, 104, 510, -367);
        assertEquals(LANDING, beyond.start().anchor());
        assertEquals(Direction.SOUTH, beyond.start().direction());
        var nowhere = MineshaftMap.choose(mine.sites, mine.base, 104, 510, -355);
        assertNull(nowhere.start());
        assertEquals("Choose a passage or the square just beyond its end.", nowhere.refusal());
    }

    @Test
    void hisTunnelNorthShowsWhereItClashesAndSaysWhichWaysWork() {
        var mine = ownersMine();
        var advice = MineshaftNetwork.advice(mine.sites, mine.base, LANDING, Direction.NORTH, 9, ExcavationPlan.Layout.STRAIGHT);
        assertNotNull(advice);
        assertEquals("Stairs up to 111 pass through the first 2 blocks north of here. Dig east, south or west from here.", advice.message());
        // Nothing can be moved or shortened here: the landing is one row, and the steps start at its edge.
        assertNull(advice.fix());
        assertEquals(List.of(1, 2), advice.clashes().stream().map(MineshaftNetwork.Clash::block).toList());
        assertEquals(new Point(511, 104, -371), advice.clashes().get(0).at());
        assertEquals(new Point(511, 105, -372), advice.clashes().get(1).at());
        assertFalse(mine.route(LANDING, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9).ready());

        // The draft is drawn with the clash marked, before anything is added.
        var draft = new MineshaftMap.Draft(LANDING, Direction.NORTH, 9, ExcavationPlan.Layout.STRAIGHT, advice.clashes());
        var cells = MineshaftMap.cells(mine.sites, mine.base, 510, -370, 104, draft);
        assertEquals(Kind.START, square(cells, 510, -370).kind());
        var clash = square(cells, 510, -373);
        assertEquals(Kind.CLASH, clash.kind());
        assertEquals("510, 104, -373 | Stairs up to 111 pass here. The draft cannot go through", clash.tooltip());
        assertEquals(Kind.DRAFT, square(cells, 510, -376).kind());
        assertEquals(Kind.DRAFT, square(cells, 510, -379).kind());
        // The half of the stairs the draft's middle lane does not touch stays drawn as stairs.
        assertEquals(Kind.STAIRWELL, square(cells, 513, -373).kind());

        // Every way the message names is accepted from the same landing.
        for (var way : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            assertNull(MineshaftNetwork.advice(mine.sites, mine.base, LANDING, way, 9, ExcavationPlan.Layout.STRAIGHT), way.toString());
            var route = mine.route(LANDING, way, ExcavationPlan.Layout.STRAIGHT, 9);
            assertTrue(route.ready(), way + ": " + route.issue());
        }
    }

    @Test
    void aRouteOverAStairwellIsToldToStartBesideItAndThatStartIsAccepted() {
        // A gallery runs north from the base. Stairs go down east from its sixth block.
        var mine = MineFixture.facing(new Point(520, 111, -370), Direction.NORTH);
        mine.add("Gallery", new Point(520, 111, -370), Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 12);
        mine.add("Stairs", new Point(520, 111, -376), Direction.EAST, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);

        // One block north of the stairs' mouth, heading the same way: the route's middle lane is the stairs' north lane.
        var start = new Point(520, 111, -377);
        var advice = MineshaftNetwork.advice(mine.sites, mine.base, start, Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT);
        assertNotNull(advice);
        // One block north the middle lane would be clear, but a side lane would still hang over the stairs. Two is clear.
        assertEquals("Stairs down to 104 open under this route 3 blocks east of here. Start 2 blocks further north, or dig north or west.", advice.message());
        assertEquals(List.of(3, 4, 5, 6), advice.clashes().stream().map(MineshaftNetwork.Clash::block).toList());
        assertTrue(advice.clashes().stream().allMatch(MineshaftNetwork.Clash::under));
        assertFalse(mine.route(start, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9).ready());

        // The button says what it does, and what it does is accepted.
        assertEquals("Move start 2 blocks north", advice.fix().button());
        assertNull(advice.fix().length());
        var moved = advice.fix().start();
        assertEquals(new Point(520, 111, -379), moved);
        assertNull(MineshaftNetwork.advice(mine.sites, mine.base, moved, Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT));
        var route = mine.route(moved, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);
        assertTrue(route.ready(), route.issue());
        mine.add("Branch", moved, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);

        // On the map the stairwell is drawn east of the gallery, and the refused draft marks where it would fall in.
        var draft = new MineshaftMap.Draft(start, Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT, advice.clashes());
        var cells = MineshaftMap.cells(mine.sites, mine.base, 520, -377, 111, draft);
        assertEquals(Kind.CLASH, square(cells, 523, -377).kind());
        assertEquals(Kind.CLASH, square(cells, 526, -377).kind());
        assertEquals(Direction.WEST, square(cells, 526, -377).climbs());
        assertTrue(square(cells, 526, -377).tooltip().endsWith("Stairs down to 104 pass here. The draft cannot go through"), square(cells, 526, -377).tooltip());
    }

    @Test
    void aRouteThatReachesAStairwellFurtherOnIsToldToStopShortAndThatLengthIsAccepted() {
        // A wide base. Stairs go down north from its east end, a gallery runs north from its west end.
        var mine = new MineFixture(new WorkplaceArea(512, 111, -370, 527, -363));
        mine.add("Stairs", new Point(524, 111, -370), Direction.NORTH, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);
        mine.add("Gallery", new Point(516, 111, -370), Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 12);

        var start = new Point(516, 111, -374);
        var advice = MineshaftNetwork.advice(mine.sites, mine.base, start, Direction.EAST, 9, ExcavationPlan.Layout.STRAIGHT);
        assertNotNull(advice);
        assertEquals("Stairs down to 104 open under this route 7 blocks east of here. Make it 6 blocks long, or dig north or west.", advice.message());
        assertEquals("Shorten to 6 blocks", advice.fix().button());
        assertEquals(6, advice.fix().length());
        assertNull(advice.fix().start());
        assertFalse(mine.route(start, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9).ready());
        var route = mine.route(start, Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 6);
        assertTrue(route.ready(), route.issue());
        assertEquals(45, route.cells().size());
    }

    @Test
    void aLevelPassageAtAnotherHeightIsDrawnAndNamedToo() {
        // Stairs of the older, shallow kind end two blocks down, and a tunnel leaves their foot.
        var mine = MineFixture.facing(new Point(520, 111, -370), Direction.NORTH);
        var stairs = mine.add("Old stairs", new Point(520, 111, -370), Direction.NORTH, ExcavationPlan.Layout.STAIRS_DOWN, 12);
        var foot = MineshaftNetwork.mapConnections(mine.sites, mine.base).entrySet().stream()
            .filter(e -> e.getValue().equals(stairs.id()) && e.getKey().y() == 109)
            .map(java.util.Map.Entry::getKey)
            .min(java.util.Comparator.comparingInt(Point::z))
            .orElseThrow();
        mine.add("Low tunnel", foot, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 9);
        var passing = MineshaftNetwork.passing(mine.sites, mine.base, 111);
        var over = passing.get(new Point(520, 111, foot.z() - 5));
        assertNotNull(over);
        assertFalse(over.stairs());
        assertEquals("Low tunnel passes here at height 109", over.hover(111));
    }
}
