package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.warehouse.ProductionInventory;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.util.Map;
import java.util.UUID;

/** A grower site's store, seen as town stock: couriers, the warehouse and the cauldron all draw on it. */
public final class GrowerOutput implements ProductionInventory {
    private final ApprenticeWorkplace site;
    private GrowerOutput(ApprenticeWorkplace site) { this.site = site; }

    /** The store of a grower site, or null when the id is not one or the site has never been worked. */
    public static GrowerOutput find(TownRecord town, UUID id) {
        var site = id == null ? null : town.getApprenticeWorkplaces().find(id);
        return site != null && GrowerPlan.supports(site.type()) && site.existingGrower() != null ? new GrowerOutput(site) : null;
    }

    public ApprenticeWorkplace site() { return site; }
    public WarehouseBatch.Point pickup() { return site.grower().pickup(); }

    @Override public Map<String, Long> getAmounts() { return site.grower().amounts(); }
    @Override public long getAmount(String item) { return site.grower().amount(item); }
    @Override public long removeAmountUpTo(String item, long count) { return site.grower().remove(item, count); }
}
