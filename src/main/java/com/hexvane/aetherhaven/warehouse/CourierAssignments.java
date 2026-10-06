package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster.Worker;
import java.util.UUID;

/** Logistics carrying capacity and compatibility with older integration calls. The journal owns work priorities. */
public final class CourierAssignments {
    private CourierAssignments() {}

    public static int carryingStacks(int logisticsLevel) {
        return logisticsLevel <= 0 ? 0 : (Math.min(10, logisticsLevel) + 1) / 2;
    }

    public static int carryingStacks(com.hexvane.aetherhaven.townsfolk.TownsfolkProgress progress,
            com.hexvane.aetherhaven.equipment.ProfessionKitCatalog catalog) {
        int base = carryingStacks(progress.skill(TownsfolkSkill.LOGISTICS));
        return base == 0 ? 0 : base + com.hexvane.aetherhaven.equipment.ProfessionKitService.extraStacks(progress, catalog);
    }

    public static String blockedReason(TownRecord town, Worker worker) {
        if (!worker.alive()) return "This resident needs to be revived.";
        if (worker.guard()) return "This resident is on guard duty.";
        if (worker.progress().skill(TownsfolkSkill.LOGISTICS) == 0) return "This resident cannot perform Logistics work.";
        if (town.getPayroll().overdue(worker.characterId())) return "Pay this resident's overdue bill first.";
        return null;
    }

    public static boolean ready(TownRecord town, Worker worker) {
        return worker.progress().canPerform(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.LOGISTICS,
            worker.guard(),town.getPayroll().overdue(worker.characterId()),worker.alive());
    }

    /** Returns feedback. Repeated explicit assignments cannot toggle the role or generate extra bills. */
    @Deprecated public static String set(TownRecord town, UUID player, Worker worker, boolean assigned) {
        if (!WarehouseAccess.canManageCouriers(town, player)) return "You need a resident Warehouse Manager and permission to manage workers.";
        if (worker == null) return "This resident is no longer available.";
        if (assigned && !worker.progress().isCourier()) {
            String blocked = blockedReason(town, worker);
            if (blocked != null) return blocked;
            worker.progress().setCourier(true);
            worker.progress().setEmployed(true);
            town.getPayroll().ensureCurrentBill(worker.characterId());
            return worker.name() + " has Logistics enabled in work priorities. Review wages in Bills.";
        }
        if (!assigned && worker.progress().isCourier()) {
            worker.progress().setCourier(false);
            return worker.name() + " has Logistics disabled. Employment and owed bills are unchanged.";
        }
        return "This assignment is already up to date.";
    }
}
