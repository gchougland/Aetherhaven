package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.difficulty.BuildingUpgradeCostScaler;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per plot workplace upgrade tiers: slot count, production speed, and storage capacity. */
public final class WorkplaceProductionUpgrades {
    public static final int BASE_SLOT_COUNT = 1;
    public static final int MAX_SLOT_COUNT = 5;
    public static final int MAX_BRANCH_LEVEL = 3;

    public static final String ITEM_IRON = "Ingredient_Bar_Iron";
    public static final String ITEM_THORIUM = "Ingredient_Bar_Thorium";
    public static final String ITEM_COBALT = "Ingredient_Bar_Cobalt";
    public static final String ITEM_ADAMANTITE = "Ingredient_Bar_Adamantite";

    public enum Branch {
        IRON,
        THORIUM,
        COBALT,
        ADAMANTITE
    }

    private WorkplaceProductionUpgrades() {}

    public static int slotCount(@Nonnull PlotProductionState state) {
        state.migrateIfNeeded();
        int n = BASE_SLOT_COUNT + state.getIronUpgrade() + state.getCobaltLevel();
        return Math.min(MAX_SLOT_COUNT, Math.max(BASE_SLOT_COUNT, n));
    }

    public static double speedMultiplier(@Nonnull PlotProductionState state) {
        state.migrateIfNeeded();
        return 1.0 + 0.2 * state.getThoriumLevel();
    }

    public static double capacityMultiplier(@Nonnull PlotProductionState state) {
        state.migrateIfNeeded();
        return 1.0 + 1.0 * state.getAdamantineLevel();
    }

    public static long effectiveMaxStorage(@Nonnull PlotProductionState state, @Nonnull ProductionCatalog.Entry entry, @Nonnull String itemId) {
        double mul = capacityMultiplier(state);
        long base = entry.maxStorageForItem(itemId);
        long scaled = (long) Math.ceil(base * mul);
        return Math.max(1L, Math.min(ProductionCatalog.MAX_STORAGE_PER_OUTPUT, scaled));
    }

    public static int currentLevel(@Nonnull PlotProductionState state, @Nonnull Branch branch) {
        state.migrateIfNeeded();
        return switch (branch) {
            case IRON -> state.getIronUpgrade();
            case THORIUM -> state.getThoriumLevel();
            case COBALT -> state.getCobaltLevel();
            case ADAMANTITE -> state.getAdamantineLevel();
        };
    }

    public static int maxLevel(@Nonnull Branch branch) {
        return switch (branch) {
            case IRON -> 1;
            case THORIUM, COBALT, ADAMANTITE -> MAX_BRANCH_LEVEL;
        };
    }

    public static boolean isMaxed(@Nonnull PlotProductionState state, @Nonnull Branch branch) {
        return currentLevel(state, branch) >= maxLevel(branch);
    }

    /** Next tier to purchase (1 based), or 0 when maxed. */
    public static int nextTier(@Nonnull PlotProductionState state, @Nonnull Branch branch) {
        if (isMaxed(state, branch)) {
            return 0;
        }
        return currentLevel(state, branch) + 1;
    }

    public static boolean prerequisitesMet(@Nonnull PlotProductionState state, @Nonnull Branch branch) {
        state.migrateIfNeeded();
        int tier = nextTier(state, branch);
        if (tier <= 0) {
            return false;
        }
        return switch (branch) {
            case IRON -> true;
            case THORIUM, COBALT -> state.getIronUpgrade() >= 1;
            case ADAMANTITE -> state.getIronUpgrade() >= 1
                && state.getThoriumLevel() >= tier
                && state.getCobaltLevel() >= tier;
        };
    }

    @Nullable
    public static String ingotItemId(@Nonnull Branch branch) {
        return switch (branch) {
            case IRON -> ITEM_IRON;
            case THORIUM -> ITEM_THORIUM;
            case COBALT -> ITEM_COBALT;
            case ADAMANTITE -> ITEM_ADAMANTITE;
        };
    }

    public static int ingotCost(@Nonnull Branch branch, int tier) {
        return switch (branch) {
            case IRON -> 15;
            case THORIUM -> switch (tier) {
                case 1 -> 10;
                case 2 -> 15;
                default -> 25;
            };
            case COBALT -> switch (tier) {
                case 1 -> 10;
                case 2 -> 15;
                default -> 25;
            };
            case ADAMANTITE -> switch (tier) {
                case 1 -> 10;
                case 2 -> 15;
                default -> 25;
            };
        };
    }

    public static long goldCost(@Nonnull Branch branch, int tier) {
        return switch (branch) {
            case IRON -> 25L;
            case THORIUM, COBALT -> switch (tier) {
                case 1 -> 50L;
                case 2 -> 75L;
                default -> 110L;
            };
            case ADAMANTITE -> switch (tier) {
                case 1 -> 150L;
                case 2 -> 200L;
                default -> 275L;
            };
        };
    }

    public static int effectiveIngotCost(@Nonnull Branch branch, int tier, @Nonnull TownRecord town) {
        return BuildingUpgradeCostScaler.scaleResourceCount(
            ingotCost(branch, tier),
            town.effectiveDifficultyForGameplay()
        );
    }

    public static long effectiveGoldCost(@Nonnull Branch branch, int tier, @Nonnull TownRecord town) {
        return BuildingUpgradeCostScaler.scaleGold(goldCost(branch, tier), town.effectiveDifficultyForGameplay());
    }

    public static boolean canAfford(
        @Nonnull PlotProductionState state,
        @Nonnull Branch branch,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        int tier = nextTier(state, branch);
        if (tier <= 0 || !prerequisitesMet(state, branch)) {
            return false;
        }
        String ingot = ingotItemId(branch);
        if (ingot == null) {
            return false;
        }
        int needIngot = effectiveIngotCost(branch, tier, town);
        long needGold = effectiveGoldCost(branch, tier, town);
        if (InventoryMaterials.count(inv, ingot) < needIngot) {
            return false;
        }
        return needGold <= 0L || GoldCoinPayment.canAfford(town, inv, needGold, allowTreasuryGold);
    }

    public enum PurchaseResult {
        OK,
        MAXED,
        PREREQUISITES,
        NEED_INGOT,
        NEED_GOLD,
        TAKE_INGOT_FAILED,
        PAY_GOLD_FAILED
    }

    /**
     * Validates, deducts costs, and increments the branch level on {@code state}. Does not persist town; caller must
     * {@code tm.updateTown(town)}.
     */
    @Nonnull
    public static PurchaseResult tryPurchase(
        @Nonnull PlotProductionState state,
        @Nonnull Branch branch,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        state.migrateIfNeeded();
        int tier = nextTier(state, branch);
        if (tier <= 0) {
            return PurchaseResult.MAXED;
        }
        if (!prerequisitesMet(state, branch)) {
            return PurchaseResult.PREREQUISITES;
        }
        String ingot = ingotItemId(branch);
        if (ingot == null) {
            return PurchaseResult.PREREQUISITES;
        }
        int needIngot = effectiveIngotCost(branch, tier, town);
        long needGold = effectiveGoldCost(branch, tier, town);
        if (InventoryMaterials.count(inv, ingot) < needIngot) {
            return PurchaseResult.NEED_INGOT;
        }
        if (needGold > 0L && !GoldCoinPayment.canAfford(town, inv, needGold, allowTreasuryGold)) {
            return PurchaseResult.NEED_GOLD;
        }
        ItemStackTransaction take = inv.removeItemStack(new ItemStack(ingot, needIngot));
        if (!take.succeeded()) {
            return PurchaseResult.TAKE_INGOT_FAILED;
        }
        if (needGold > 0L && !GoldCoinPayment.trySpend(town, inv, needGold, allowTreasuryGold)) {
            inv.addItemStack(new ItemStack(ingot, needIngot));
            return PurchaseResult.PAY_GOLD_FAILED;
        }
        switch (branch) {
            case IRON -> state.setIronUpgrade(1);
            case THORIUM -> state.setThoriumLevel(state.getThoriumLevel() + 1);
            case COBALT -> state.setCobaltLevel(state.getCobaltLevel() + 1);
            case ADAMANTITE -> state.setAdamantineLevel(state.getAdamantineLevel() + 1);
            default -> {}
        }
        return PurchaseResult.OK;
    }

    @Nullable
    public static Branch branchFromId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Branch.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
