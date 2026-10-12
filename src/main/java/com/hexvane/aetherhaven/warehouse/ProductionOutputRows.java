package com.hexvane.aetherhaven.warehouse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** What a production building lists in the warehouse: every resource once, with all of it that the building holds. */
final class ProductionOutputRows {
    private ProductionOutputRows() {}

    /**
     * @param slot   the first output slot set to this resource, or -1 for stock no slot is set to
     * @param item   null for a slot with nothing chosen
     * @param amount everything of the resource in the building, however many slots share it
     */
    record Row(int slot, String item, long amount) {}

    /**
     * A building keeps one store per resource, not per slot. Two slots set to the same output (a new lumbermill sets
     * them all to the same wood) are therefore one row, not the same total printed twice.
     *
     * @param chosen the resource each listed output slot is set to, in slot order
     * @param stock  what the building holds
     * @param exists whether a held resource is still a real item. Leftovers of removed items are not listed.
     */
    static List<Row> rows(List<String> chosen, ProductionInventory stock, Predicate<String> exists) {
        var rows = new ArrayList<Row>();
        var shown = new HashSet<String>();
        for (int slot = 0; slot < chosen.size(); slot++) {
            String item = chosen.get(slot);
            if (item == null) rows.add(new Row(slot, null, 0));
            else if (shown.add(item)) rows.add(new Row(slot, item, stock.getAmount(item)));
        }
        for (var held : stock.getAmounts().entrySet())
            if (held.getValue() > 0 && !shown.contains(held.getKey()) && exists.test(held.getKey()))
                rows.add(new Row(-1, held.getKey(), held.getValue()));
        return List.copyOf(rows);
    }
}
