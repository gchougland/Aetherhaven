package com.hexvane.aetherhaven.workplace;

import java.util.HashSet;
import java.util.List;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A workplace with several benches of one kind: every listed bench opens itself, from where the page lets a player stand. */
@Tag("town")
class WorkplaceBenchRowsTest {
    /** A smeltery read in the order the game reads it: three furnaces with other benches between them. */
    private static List<WorkplaceEquipment.Bench> smeltery() {
        return List.of(
            new WorkplaceEquipment.Bench("Furnace", 1, 100, 64, 200),
            new WorkplaceEquipment.Bench("Workbench", 1, 101, 64, 200),
            new WorkplaceEquipment.Bench("Furnace", 2, 103, 64, 200),
            new WorkplaceEquipment.Bench("Salvagebench", 1, 104, 64, 203),
            new WorkplaceEquipment.Bench("Furnace", 1, 103, 64, 206));
    }

    @Test void everyFurnaceRowOpensItsOwnFurnace() {
        var benches = smeltery();
        var rows = WorkplaceBenchRows.rows(benches, "Furnace");
        assertEquals(3, rows.size());
        var keys = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            assertEquals(i + 1, row.number());
            assertTrue(keys.add(row.key()), "Two rows must never send the same thing: " + row.key());
            // What the row's button sends comes back as that row's own block, and as no other row.
            var opened = WorkplaceBenchRows.position(row.key());
            assertEquals(new Vector3i(row.bench().x(), row.bench().y(), row.bench().z()), opened);
            assertEquals(row.position(), opened);
            assertSame(row.bench(), WorkplaceBenchRows.find(benches, "Furnace", row.key()).bench());
            assertEquals(row.number(), WorkplaceBenchRows.find(benches, "Furnace", row.key()).number());
        }
        // The second and third furnaces are their own blocks, not the first one again.
        assertEquals(new Vector3i(100, 64, 200), WorkplaceBenchRows.position(rows.get(0).key()));
        assertEquals(new Vector3i(103, 64, 200), WorkplaceBenchRows.position(rows.get(1).key()));
        assertEquals(new Vector3i(103, 64, 206), WorkplaceBenchRows.position(rows.get(2).key()));
    }

    @Test void theSameHoldsForEveryKindOfBenchNotOnlyFurnaces() {
        var benches = List.of(
            new WorkplaceEquipment.Bench("Workbench", 1, 10, 70, 10),
            new WorkplaceEquipment.Bench("Workbench", 3, 14, 70, 10),
            new WorkplaceEquipment.Bench("Weapon_Bench", 1, 12, 70, 12),
            new WorkplaceEquipment.Bench("Weapon_Bench", 1, 12, 70, 16));
        for (String kind : List.of("Workbench", "Weapon_Bench")) {
            var rows = WorkplaceBenchRows.rows(benches, kind);
            assertEquals(2, rows.size(), kind);
            assertNotEquals(rows.get(0).key(), rows.get(1).key(), kind);
            for (var row : rows) {
                assertEquals(kind, row.bench().id());
                assertEquals(row.position(), WorkplaceBenchRows.position(row.key()), kind);
            }
        }
        assertTrue(WorkplaceBenchRows.rows(benches, "Furnace").isEmpty());
    }

    @Test void aFurnaceKeepsItsNumberWhateverElseStandsInTheWorkplace() {
        var alone = List.of(new WorkplaceEquipment.Bench("Furnace", 1, 5, 64, 5), new WorkplaceEquipment.Bench("Furnace", 1, 9, 64, 5));
        var mixed = List.of(new WorkplaceEquipment.Bench("Workbench", 1, 4, 64, 5), new WorkplaceEquipment.Bench("Furnace", 1, 5, 64, 5),
            new WorkplaceEquipment.Bench("Workbench", 1, 7, 64, 5), new WorkplaceEquipment.Bench("Furnace", 1, 9, 64, 5));
        for (int i = 0; i < 2; i++)
            assertEquals(WorkplaceBenchRows.rows(alone, "Furnace").get(i).key(), WorkplaceBenchRows.rows(mixed, "Furnace").get(i).key());
    }

    @Test void aButtonForABlockThatIsNotOneOfTheseBenchesOpensNothing() {
        var benches = smeltery();
        // The Workbench stands in the workplace but is not a furnace.
        assertNull(WorkplaceBenchRows.find(benches, "Furnace", WorkplaceBenchRows.key(101, 64, 200)));
        // A furnace that was broken since the page was drawn.
        assertNull(WorkplaceBenchRows.find(benches, "Furnace", WorkplaceBenchRows.key(120, 64, 200)));
        for (String forged : new String[]{null, "", "1,2", "1,2,3,4", "a,b,c", "100;64;200", "100,64,2e2"}) {
            assertNull(WorkplaceBenchRows.position(forged), String.valueOf(forged));
            assertNull(WorkplaceBenchRows.find(benches, "Furnace", forged), String.valueOf(forged));
        }
        assertEquals(new Vector3i(-12, 64, -300), WorkplaceBenchRows.position(" -12, 64 ,-300"));
    }

    @Test void aBenchThePageLetsYouOpenIsNotShutAgainByTheGamesOwnSevenBlockLimit() {
        double base = 7;
        // The page lets a player use a bench from up to eight blocks from the block's middle. The game measures to
        // the block's corner, which can be most of a block further. Without more room the screen opened and closed.
        for (double fromMiddle = 0; fromMiddle <= 8; fromMiddle += 0.25) {
            double toCorner = fromMiddle + Math.sqrt(3) / 2;
            assertTrue(WorkplaceBenchRows.screenReach(base, toCorner) >= toCorner, "At " + fromMiddle);
        }
        assertTrue(8 + Math.sqrt(3) / 2 > base, "This is the case the base limit used to close at once");
        // A furnace across a large smeltery, opened from its row on the page.
        assertEquals(42, WorkplaceBenchRows.screenReach(base, 40));
        // Close by, the game's own limit stands: walking away still closes the screen.
        assertEquals(7, WorkplaceBenchRows.screenReach(base, 3));
        assertEquals(7, WorkplaceBenchRows.screenReach(base, 5));
    }

    @Test void everyReasonABenchStaysShutIsSaidInPlainWords() {
        assertNull(WorkplaceNativeBench.Outcome.OPENED.message());
        var said = new HashSet<String>();
        for (var outcome : WorkplaceNativeBench.Outcome.values()) {
            if (outcome == WorkplaceNativeBench.Outcome.OPENED) continue;
            String message = outcome.message();
            assertNotNull(message, outcome.name());
            assertTrue(message.endsWith("."), message);
            assertFalse(message.contains(" - ") || message.contains("—") || message.contains("–"), message);
            assertTrue(said.add(message), "Each reason reads differently: " + message);
        }
    }
}
