package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Called on the town world thread. Does not load chunks or deduct money automatically. */
public final class TownPayrollService {
    private TownPayrollService() {}

    public static void advance(World world, AetherhavenPlugin plugin, long minutes) {
        if (minutes <= 0) return;
        var online = TownEconomyPresence.onlinePlayers(world.getEntityStore().getStore());
        if (online.isEmpty()) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (var town : manager.allTowns()) {
            if (!TownEconomyPresence.hasMember(town, online)) continue;
            var employed = employedCharacters(town);
            var payroll = town.getPayroll();
            if (employed.isEmpty() && payroll.bills().stream().noneMatch(b -> !b.paid())) continue;
            payroll.advance(minutes, true, employed);
            if (!payroll.takeDueSoonWarnings().isEmpty()) {
                for (var player : Universe.get().getPlayers()) if (online.contains(player.getUuid()) && town.hasMemberOrOwner(player.getUuid())) {
                    NotificationUtil.sendNotification(player.getPacketHandler(),
                        Message.translation("aetherhaven_workforce.billWarning").param("town", town.getDisplayName()),
                        NotificationStyle.Warning);
                }
            }
            manager.updateTown(town);
        }
    }

    /** Same roster for invoice creation and the budget preview; priorities never affect salary. */
    public static Set<String> employedCharacters(TownRecord town) {
        var employed = new HashSet<String>();
        for (var resident : town.getResidentNpcRecords()) {
            var identity = resident.getTownsfolkIdentity();
            if (identity == null || resident.isPendingDawnRevival()
                || TownVillagerBinding.KIND_GUARD.equals(resident.getKind())
                || !TownAdmission.isMember(town, resident.getLastEntityUuid())) continue;
            String id = identity.getCharacterId().toLowerCase(Locale.ROOT);
            var progress = town.getTownsfolkProgress().get(id);
            if (!id.isBlank() && progress != null && progress.isEmployed()) employed.add(id);
        }
        return Set.copyOf(employed);
    }

    public record Budget(int employees, long weeklyGold, long unpaidGold, long overdueGold) {}

    /** Read-only projection. Old debt remains payable even when someone no longer works here. */
    public static Budget budget(TownRecord town) {
        int employees = employedCharacters(town).size();
        long unpaid = 0, overdue = 0;
        var payroll = town.getPayroll();
        for (var bill : payroll.bills()) if (!bill.paid()) {
            unpaid = Math.addExact(unpaid, bill.amount());
            if (bill.dueMinute() <= payroll.onlineMinutes()) overdue = Math.addExact(overdue, bill.amount());
        }
        return new Budget(employees, Math.multiplyExact((long) employees, TownPayroll.WEEKLY_WAGE), unpaid, overdue);
    }

    public static boolean pay(TownRecord town, TownManager manager, UUID player, String billId) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanSpendTreasuryGold(player)) return false;
        long debit = town.getPayroll().settle(billId, town.getTreasuryGoldCoinCount());
        if (debit < 0) return false;
        town.addTreasuryGoldCoins(-debit);
        manager.updateTown(town);
        return true;
    }
}
