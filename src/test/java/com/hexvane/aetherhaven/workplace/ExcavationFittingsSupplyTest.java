package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What the game tells the player about the supplies a tunnel's fittings take.
 *
 * <p>The owner's report from October 11: "The mineshaft needs to be clearer about what type of beams it needs." Every
 * such line now names the exact item by the name the game gives it, how many the tunnel takes, how many the town
 * holds, and where to put them.
 */
@Tag("town")
class ExcavationFittingsSupplyTest {
    /** The names the game's own language file gives these items. */
    private static final Map<String, String> NAMES = Map.of(
        ExcavationFittings.BEAM, "Hardwood - Beam",
        ExcavationFittings.TORCH, "Crude Torch",
        ExcavationFittings.LANTERN, "Lantern",
        ExcavationFittings.RAIL, "Rail"
    );

    private static String name(String item) {
        return NAMES.get(item);
    }

    @Test
    void exactlyOneBeamItemIsAcceptedAndEveryPieceOfAFrameIsCutFromIt() {
        assertEquals(List.of("Wood_Hardwood_Beam", "Furniture_Crude_Torch", "Deco_Lantern", "Rail"), ExcavationFittings.ITEMS);
        for (var entrance : ExcavationPlan.Entrance.values()) {
            for (var layout : ExcavationPlan.Layout.values()) {
                var area = new WorkplaceArea(0, 100, 0, 21, 21, 11);
                var frames = Set.of(
                    ExcavationFittings.BEAM, ExcavationFittings.CORNER, ExcavationFittings.CEILING,
                    ExcavationFittings.CORNER_CROSS, ExcavationFittings.CEILING_CROSS
                );
                for (var fitting : ExcavationFittings.plan(area, entrance, layout, ExcavationFittings.Mode.ALL_RAILS)) {
                    assertTrue(ExcavationFittings.ITEMS.contains(fitting.item()), layout + " " + fitting);
                    // Posts, joining corners and ceiling pieces all cost the same Hardwood Beam, and nothing else does.
                    assertEquals(frames.contains(fitting.block()), ExcavationFittings.BEAM.equals(fitting.item()), layout + " " + fitting);
                }
            }
        }
    }

    @Test
    void theOwnersNineBlockTunnelNeedsFourteenBeams() {
        // North from 511, 104, -370, nine blocks long: the tunnel in his screenshot.
        var area = MineshaftSelection.area(511, 103, -370, MineshaftSelection.Direction.NORTH, 9, ExcavationPlan.Layout.STRAIGHT);
        var entrance = MineshaftSelection.entrance(MineshaftSelection.Direction.NORTH);
        var all = ExcavationFittings.needs(area, entrance, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.ALL_RAILS);
        var expected = new LinkedHashMap<String, Integer>();
        expected.put(ExcavationFittings.BEAM, 14);
        expected.put(ExcavationFittings.TORCH, 1);
        expected.put(ExcavationFittings.LANTERN, 1);
        expected.put(ExcavationFittings.RAIL, 9);
        assertEquals(expected, all);
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(all.keySet()));
        // Each fitting is counted on its own: turning the others off does not change what the beams take.
        assertEquals(14, ExcavationFittings.needs(area, entrance, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.SUPPORTS).get(ExcavationFittings.BEAM));
        assertEquals(0, ExcavationFittings.needs(area, entrance, ExcavationPlan.Layout.STRAIGHT, ExcavationFittings.Mode.SUPPORTS).get(ExcavationFittings.RAIL));
        // Rooms never take rails.
        var room = MineshaftSelection.area(511, 103, -370, MineshaftSelection.Direction.NORTH, 9, ExcavationPlan.Layout.ROOM);
        assertEquals(0, ExcavationFittings.needs(room, entrance, ExcavationPlan.Layout.ROOM, ExcavationFittings.Mode.ALL_RAILS).get(ExcavationFittings.RAIL));
    }

    @Test
    void thePlannersCountLineSaysWhatTheRouteTakesAndWhatTheTownHolds() {
        assertEquals("Needs 14. Town storage has 3.", ExcavationFittings.countLine(true, 14, 3));
        assertEquals("Needs 14. Town storage has 40.", ExcavationFittings.countLine(true, 14, 40));
        assertEquals("Not used. Town storage has 3.", ExcavationFittings.countLine(false, 14, 3));
    }

    @Test
    void aTunnelWaitingForBeamsNamesTheItemTheCountAndThePlace() {
        var needed = new LinkedHashMap<String, Integer>();
        needed.put(ExcavationFittings.BEAM, 14);
        needed.put(ExcavationFittings.TORCH, 1);
        needed.put(ExcavationFittings.RAIL, 9);
        Map<String, Long> stored = Map.of(ExcavationFittings.TORCH, 20L, ExcavationFittings.RAIL, 4L);

        String waiting = ExcavationFittings.waitingLine(ExcavationFittings.BEAM, needed, item -> stored.getOrDefault(item, 0L), ExcavationFittingsSupplyTest::name);
        assertEquals(
            "Digging is done. Waiting for 14 Hardwood - Beam: town storage has none. Also short: 5 Rail."
                + " Put them in General storage at the Warehouse Ledger Desk.",
            waiting
        );
        assertPlain(waiting);

        String fitting = ExcavationFittings.fittingLine(needed, item -> stored.getOrDefault(item, 0L), ExcavationFittingsSupplyTest::name);
        assertEquals(
            "Digging is done. Still to fit: 14 Hardwood - Beam (0 in town storage), 1 Crude Torch (20 in town storage),"
                + " 9 Rail (4 in town storage). Miners take them from General storage at the Warehouse Ledger Desk.",
            fitting
        );
        assertPlain(fitting);
    }

    @Test
    void somethingInTheWayIsNamedWithItsPlaceAndHowTheTunnelCarriesOn() {
        String one = ExcavationFittings.obstructionLine("Small Crude Chest", new Point(511, 104, -372), 1);
        assertEquals("Small Crude Chest at 511, 104, -372 is in the way. Miners dig the rest of the tunnel and finish here once it is gone.", one);
        assertPlain(one);
        String two = ExcavationFittings.obstructionLine("A building plot", new Point(511, 104, -372), 2);
        assertEquals("A building plot at 511, 104, -372 is in the way, with 1 more blocked block near it. Miners dig the rest of the tunnel and finish here once it is gone.", two);
        String many = ExcavationFittings.obstructionLine("A building plot", new Point(511, 104, -372), 9);
        assertTrue(many.contains("with 8 more blocked blocks near it"), many);
        assertPlain(many);
    }

    /** No step the player cannot find, and no word standing in for the item's name. */
    private static void assertPlain(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("review"), line);
        assertFalse(lower.contains("fittings"), line);
        assertFalse(line.contains("—") || line.contains("–"), line);
    }
}
