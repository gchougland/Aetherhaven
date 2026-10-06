package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;

/** Storage belongs to the town. A desk is an access point, never a new container. */
public final class WarehouseAccess {
    public static final String DESK_ITEM = "Aetherhaven_Warehouse_Ledger_Desk";
    public static final String MANAGER_KIND = "warehouse_manager";
    private WarehouseAccess() {}

    public static boolean canBrowse(TownRecord town, UUID player) {
        return town != null && player != null && town.hasMemberOrOwner(player) && town.playerCanOpenContainers(player);
    }

    public static boolean hasManager(TownRecord town) {
        // Residency survives death and unloading. Neither event strands the town's supplies.
        return town != null && town.getResidentNpcRecords().stream().anyMatch(r -> MANAGER_KIND.equals(r.getKind()));
    }

    public static boolean canManageCouriers(TownRecord town, UUID player) {
        return town != null && player != null && town.hasMemberOrOwner(player)
            && town.playerCanManageConstructions(player) && hasManager(town);
    }
}
