package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.ui.HouseResidentDirectory.HouseResidentRow;
import com.hypixel.hytale.server.core.Message;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P18: putting house quest residents first must not change which resident a row stands for. */
@Tag("town")
class HouseResidentPickerOrderTest {
    private final Map<String, UUID> ids = new LinkedHashMap<>();

    private HouseResidentRow row(String name) {
        UUID id = UUID.randomUUID();
        ids.put(name, id);
        return new HouseResidentRow(name, id, "portrait/" + name + ".png", Message.raw("role"));
    }

    private static List<String> names(List<HouseResidentRow> rows) {
        return rows.stream().map(HouseResidentRow::displayName).toList();
    }

    @Test
    void waitingResidentsComeFirstAndEveryRowKeepsItsOwnResident() {
        // As the directory lists them: by name.
        var assignable = List.of(row("Aldric"), row("Brena"), row("Corin"), row("Mertie"), row("Tobias"));
        var waiting = Set.of(ids.get("Mertie"), ids.get("Corin"));

        var shown = HouseResidentDirectory.pickerRows(assignable, waiting, Set.of(), "");
        assertEquals(List.of("Corin", "Mertie", "Aldric", "Brena", "Tobias"), names(shown));
        // The third row is Aldric now, where the unsorted list had Corin. A row number would choose the wrong
        // resident; the row's own id cannot.
        assertEquals("Corin", assignable.get(2).displayName());
        for (var row : shown) assertEquals(ids.get(row.displayName()), row.entityUuid(), row.displayName());
        assertEquals(5, shown.stream().map(HouseResidentRow::entityUuid).distinct().count());
    }

    @Test
    void searchingAndOccupiedBedsRemoveRowsWithoutShiftingAnyone() {
        var assignable = List.of(row("Aldric"), row("Brena"), row("Corin"), row("Mertie"), row("Tobias"));
        var waiting = Set.of(ids.get("Mertie"));

        var searched = HouseResidentDirectory.pickerRows(assignable, waiting, Set.of(), "  mer ");
        assertEquals(List.of("Mertie"), names(searched));
        assertEquals(ids.get("Mertie"), searched.getFirst().entityUuid());

        // Brena already has the other bed in this house.
        var twoBeds = HouseResidentDirectory.pickerRows(assignable, waiting, Set.of(ids.get("Brena")), null);
        assertEquals(List.of("Mertie", "Aldric", "Corin", "Tobias"), names(twoBeds));
        for (var row : twoBeds) assertEquals(ids.get(row.displayName()), row.entityUuid());

        assertTrue(HouseResidentDirectory.pickerRows(assignable, waiting, Set.of(), "nobody").isEmpty());
        assertEquals(names(assignable), names(HouseResidentDirectory.pickerRows(assignable, Set.of(), Set.of(), "")),
            "With nobody waiting the list is unchanged");
    }
}
