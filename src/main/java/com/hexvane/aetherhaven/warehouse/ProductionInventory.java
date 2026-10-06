package com.hexvane.aetherhaven.warehouse;

import java.util.Map;

/** Physical production stock, accessed on its owning world thread. Reservations live in TownWarehouse. */
public interface ProductionInventory {
    Map<String, Long> getAmounts();
    long getAmount(String item);
    long removeAmountUpTo(String item, long count);

    /** Validate the entire pickup before changing any item. Callers must stay on the owning world thread. */
    default boolean withdrawAll(Map<String, Long> items) {
        for (var item : items.entrySet())
            if (item.getValue() <= 0 || getAmount(item.getKey()) < item.getValue()) return false;
        items.forEach(this::removeAmountUpTo);
        return true;
    }
}
