package com.hexvane.aetherhaven.workplace;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;

/** The inventory and saved-tier part of an upgrade, after the page revalidates its world target. */
final class StationUpgradeCommit {
    enum Result { UPGRADED, CHANGED, MISSING_MATERIALS }
    private StationUpgradeCommit() {}

    static Result apply(BenchBlock bench, int expectedTier, MaterialQuantity[] costs,
                        ItemContainer inventory, boolean creative) {
        if (bench == null || bench.getTierLevel() != expectedTier || !bench.getWindows().isEmpty()
            || costs == null || costs.length == 0) return Result.CHANGED;
        if (!creative) {
            var transaction = inventory.removeMaterials(Arrays.asList(costs));
            if (!transaction.succeeded()) return Result.MISSING_MATERIALS;
            var consumed = new ArrayList<ItemStack>();
            for (var material : transaction.getList())
                for (var slot : material.getList()) consumed.add(slot.getOutput());
            bench.addUpgradeItems(consumed);
        }
        bench.setTierLevel(expectedTier + 1);
        return Result.UPGRADED;
    }
}
