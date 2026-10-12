package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.warehouse.ProductionOutputRows.Row;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class ProductionOutputRowsTest {
    private static PlotProductionState holding(Object... itemsAndAmounts) {
        var state = new Gson().fromJson("{}", PlotProductionState.class);
        for (int i = 0; i < itemsAndAmounts.length; i += 2)
            state.addAmount((String) itemsAndAmounts[i], (Integer) itemsAndAmounts[i + 1], 1000);
        return state;
    }

    /** A new lumbermill has every output slot set to the same wood. The warehouse printed that wood once per slot. */
    @Test void slotsSetToTheSameResourceAreListedOnceWithTheirTotal() {
        var rows = ProductionOutputRows.rows(List.of("Wood_Oak_Trunk", "Wood_Oak_Trunk", "Wood_Oak_Trunk"),
            holding("Wood_Oak_Trunk", 42), item -> true);
        assertEquals(List.of(new Row(0, "Wood_Oak_Trunk", 42)), rows);
    }

    @Test void differentResourcesKeepTheirOwnSlotsInOrder() {
        var rows = ProductionOutputRows.rows(List.of("Wood_Oak_Trunk", "Wood_Birch_Trunk", "Wood_Oak_Trunk"),
            holding("Wood_Oak_Trunk", 7, "Wood_Birch_Trunk", 3), item -> true);
        assertEquals(List.of(new Row(0, "Wood_Oak_Trunk", 7), new Row(1, "Wood_Birch_Trunk", 3)), rows);
    }

    /** Switching production must not hide what the building still holds, and must not repeat what a slot shows. */
    @Test void heldResourcesNoSlotIsSetToFollowOnceAndRemovedItemsAreLeftOut() {
        var rows = ProductionOutputRows.rows(List.of("Wood_Oak_Trunk"),
            holding("Wood_Oak_Trunk", 5, "Ingredient_Tree_Sap", 9, "Removed_Item", 4), item -> !item.startsWith("Removed"));
        assertEquals(List.of(new Row(0, "Wood_Oak_Trunk", 5), new Row(-1, "Ingredient_Tree_Sap", 9)), rows);
        assertEquals(rows.size(), rows.stream().map(Row::item).distinct().count(), "No resource appears twice");
    }

    @Test void emptySlotsStayVisibleAndAnEmptyStoreShowsZero() {
        var rows = ProductionOutputRows.rows(Arrays.asList("Wood_Oak_Trunk", null, null), holding(), item -> true);
        assertEquals(List.of(new Row(0, "Wood_Oak_Trunk", 0), new Row(1, null, 0), new Row(2, null, 0)), rows);
    }
}
