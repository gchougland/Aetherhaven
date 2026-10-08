package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hexvane.aetherhaven.town.TownPopulation;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A saved invitation preference, fulfilled by the normal loaded Inn morning fill. */
public final class InnOutreach {
    public static final int UNLOCK_LEVEL = 3;
    public static final long UNLOCK_COST = 100;
    public static final long INVITE_COST = 50;
    private boolean unlocked;
    private String requestedRole;

    public boolean unlocked() { return unlocked; }
    public String requestedRole() { return requestedRole == null ? "" : requestedRole; }

    public String unlock(TownRecord town, UUID player, CombinedItemContainer inventory) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player))
            return "You need permission to manage buildings.";
        if (!town.isInnActive()) return "Build the Inn first.";
        if (TownPopulation.levelFor(TownPopulation.count(town)) < UNLOCK_LEVEL)
            return "Outreach requires Town Level 3.";
        if (unlocked) return "Outreach is already available.";
        if (inventory == null || !GoldCoinPayment.trySpend(town, inventory, UNLOCK_COST, town.playerCanSpendTreasuryGold(player))) return "You need 100 gold in your inventory or treasury.";
        unlocked = true;
        return null;
    }

    public void request(String role) {
        if (!unlocked) throw new IllegalStateException("Outreach is not unlocked");
        requestedRole = role == null || role.isBlank() ? null : role.trim();
    }

    public void arrived(String role) {
        if (requestedRole().equals(role)) requestedRole = null;
    }

    public String invite(TownRecord town, UUID player, String role, CombinedItemContainer inventory) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player))
            return "You need permission to manage buildings.";
        if (!unlocked || !town.isInnActive()) return "Unlock the Inn guest book first.";
        if (role == null || role.isBlank()) return "Choose a guest first.";
        if (!requestedRole().isEmpty()) return "Wait for your invited guest or cancel their invitation first.";
        if (inventory == null || !GoldCoinPayment.trySpend(town, inventory, INVITE_COST, town.playerCanSpendTreasuryGold(player))) return "You need 50 gold in your inventory or treasury for an invitation.";
        request(role);
        return null;
    }

    /** Preserve every ordinary priority while moving an eligible requested guest to the front. */
    public List<String> prioritize(List<String> roles, boolean eligible) {
        var result = new ArrayList<>(roles);
        if (unlocked && eligible && !requestedRole().isEmpty()) {
            result.removeIf(requestedRole()::equals);
            result.addFirst(requestedRole());
        }
        return result;
    }
}
