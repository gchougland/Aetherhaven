package com.hexvane.aetherhaven.hunter;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import java.util.List;
import java.util.UUID;

/** Player purchases on the owning world thread, after the screen validates its revision. */
public final class HuntPurchases {
    private HuntPurchases() {}

    private static String blocked(TownRecord town, UUID player, CombinedItemContainer inventory) {
        if (!town.hasMemberOrOwner(player) || !town.playerCanManageConstructions(player)) return "You need permission to manage buildings.";
        if (inventory == null) return "Your inventory is unavailable.";
        if (town.getHunts().run() != null) return "Finish this run before making another purchase.";
        return null;
    }

    /** Called only after the service validates the hunter and arena. */
    public static String begin(TownRecord town, UUID player, CombinedItemContainer inventory) {
        String error = blocked(town, player, inventory);
        if (error != null) return error;
        var state = town.getHunts();
        if (state.unlocked().stream().noneMatch(id -> HuntFamily.get(id) != null)) return "Unlock an available creature first.";
        if (!GoldCoinPayment.trySpend(town, inventory, TownHunts.ENTRY_GOLD, town.playerCanSpendTreasuryGold(player))) return "You need more gold in your inventory or treasury.";
        state.begin();
        return null;
    }

    public static String unlock(TownRecord town, UUID player, CombinedItemContainer inventory, String familyId) {
        String error = blocked(town, player, inventory);
        if (error != null) return error;
        var state = town.getHunts();
        var family = HuntFamily.get(familyId);
        if (family == null || state.unlocked(familyId)) return "Choose a locked creature.";
        var materials = List.of(new ItemStack(family.icon(), family.unlockItems()));
        if (GoldCoinPayment.trySpendReturningBreakdown(town, inventory, family.unlockGold(), town.playerCanSpendTreasuryGold(player), materials) == null)
            return "You need the listed creature drops and enough gold in your inventory or treasury.";
        state.unlock(familyId);
        return null;
    }

    public static String upgrade(TownRecord town, UUID player, CombinedItemContainer inventory, String upgrade) {
        String error = blocked(town, player, inventory);
        if (error != null) return error;
        var state = town.getHunts();
        if (state.level(upgrade) >= TownHunts.maxLevel(upgrade)) return "Choose an available training upgrade.";
        if (!GoldCoinPayment.trySpend(town, inventory, state.upgradeCost(upgrade), town.playerCanSpendTreasuryGold(player))) return "You need more gold in your inventory or treasury.";
        state.upgrade(upgrade);
        return null;
    }
}
