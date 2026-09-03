package com.hexvane.aetherhaven.restaurant;

import com.hexvane.aetherhaven.difficulty.BuildingUpgradeCostScaler;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Restaurant plot upgrades: satiety (town hunger decay) and service (meal speed/fill). */
public final class RestaurantUpgrades {
    public static final int MAX_BRANCH_LEVEL = 3;

    public static final String ITEM_CARROT = "Plant_Crop_Carrot_Item";
    public static final String ITEM_CORN = "Plant_Crop_Corn_Item";
    public static final String ITEM_CAULIFLOWER = "Plant_Crop_Cauliflower_Item";
    public static final String ITEM_AUBERGINE = "Plant_Crop_Aubergine_Item";
    public static final String ITEM_POTATO = "Plant_Crop_Potato_Item";
    public static final String ITEM_ONION = "Plant_Crop_Onion_Item";
    public static final String ITEM_BREAD = "Food_Bread";
    public static final String ITEM_CHEESE = "Food_Cheese";
    public static final String ITEM_SALAD_BERRY = "Food_Salad_Berry";
    public static final String ITEM_SALAD_MUSHROOM = "Food_Salad_Mushroom";
    public static final String ITEM_PIE_APPLE = "Food_Pie_Apple";
    public static final String ITEM_PIE_MEAT = "Food_Pie_Meat";
    public static final String ITEM_PIE_PUMPKIN = "Food_Pie_Pumpkin";

    public enum Branch {
        SATIETY,
        SERVICE
    }

    public record IngredientCost(@Nonnull String itemId, int count) {}

    private RestaurantUpgrades() {}

    public static int currentLevel(@Nonnull PlotRestaurantState state, @Nonnull Branch branch) {
        state.migrateIfNeeded();
        return switch (branch) {
            case SATIETY -> state.getSatietyLevel();
            case SERVICE -> state.getServiceLevel();
        };
    }

    public static int maxLevel(@Nonnull Branch branch) {
        return MAX_BRANCH_LEVEL;
    }

    public static boolean isMaxed(@Nonnull PlotRestaurantState state, @Nonnull Branch branch) {
        return currentLevel(state, branch) >= maxLevel(branch);
    }

    public static int nextTier(@Nonnull PlotRestaurantState state, @Nonnull Branch branch) {
        if (isMaxed(state, branch)) {
            return 0;
        }
        return currentLevel(state, branch) + 1;
    }

    public static boolean prerequisitesMet(@Nonnull PlotRestaurantState state, @Nonnull Branch branch) {
        return nextTier(state, branch) > 0;
    }

    @Nonnull
    public static List<IngredientCost> ingredientCosts(@Nonnull Branch branch, int tier) {
        return switch (branch) {
            case SATIETY -> switch (tier) {
                case 1 -> List.of(new IngredientCost(ITEM_CARROT, 12), new IngredientCost(ITEM_CORN, 12));
                case 2 -> List.of(new IngredientCost(ITEM_CAULIFLOWER, 10), new IngredientCost(ITEM_AUBERGINE, 10));
                case 3 -> List.of(new IngredientCost(ITEM_ONION, 8), new IngredientCost(ITEM_POTATO, 8));
                default -> List.of();
            };
            case SERVICE -> switch (tier) {
                case 1 -> List.of(new IngredientCost(ITEM_BREAD, 10), new IngredientCost(ITEM_CHEESE, 10));
                case 2 -> List.of(new IngredientCost(ITEM_SALAD_BERRY, 6), new IngredientCost(ITEM_SALAD_MUSHROOM, 6));
                case 3 -> List.of(
                    new IngredientCost(ITEM_PIE_APPLE, 4),
                    new IngredientCost(ITEM_PIE_MEAT, 4),
                    new IngredientCost(ITEM_PIE_PUMPKIN, 4)
                );
                default -> List.of();
            };
        };
    }

    public static long goldCost(@Nonnull Branch branch, int tier) {
        return switch (tier) {
            case 1 -> 30L;
            case 2 -> 55L;
            case 3 -> 85L;
            default -> 0L;
        };
    }

    @Nonnull
    public static List<IngredientCost> effectiveIngredientCosts(
        @Nonnull Branch branch,
        int tier,
        @Nonnull TownRecord town
    ) {
        List<IngredientCost> base = ingredientCosts(branch, tier);
        if (base.isEmpty()) {
            return base;
        }
        var difficulty = town.effectiveDifficultyForGameplay();
        List<IngredientCost> scaled = new ArrayList<>(base.size());
        for (IngredientCost c : base) {
            scaled.add(new IngredientCost(c.itemId(), BuildingUpgradeCostScaler.scaleResourceCount(c.count(), difficulty)));
        }
        return scaled;
    }

    public static long effectiveGoldCost(@Nonnull Branch branch, int tier, @Nonnull TownRecord town) {
        return BuildingUpgradeCostScaler.scaleGold(goldCost(branch, tier), town.effectiveDifficultyForGameplay());
    }

    /** Hunger decay multiplier after satiety tier (1.0 = no bonus). */
    public static float satietyDecayMultiplier(int satietyLevel) {
        return switch (Math.max(0, Math.min(MAX_BRANCH_LEVEL, satietyLevel))) {
            case 1 -> 0.92f;
            case 2 -> 0.85f;
            case 3 -> 0.78f;
            default -> 1.0f;
        };
    }

    public static float serviceEatDurationSeconds(int serviceLevel) {
        return switch (Math.max(0, Math.min(MAX_BRANCH_LEVEL, serviceLevel))) {
            case 1 -> 9f;
            case 2 -> 8f;
            case 3 -> 6f;
            default -> 10f;
        };
    }

    public static float serviceHungerRestore(int serviceLevel) {
        return switch (Math.max(0, Math.min(MAX_BRANCH_LEVEL, serviceLevel))) {
            case 1 -> 34f;
            case 2 -> 38f;
            case 3 -> 44f;
            default -> 30f;
        };
    }

    public static boolean canAfford(
        @Nonnull PlotRestaurantState state,
        @Nonnull Branch branch,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inv,
        boolean allowTreasuryGold
    ) {
        int tier = nextTier(state, branch);
        if (tier <= 0) {
            return false;
        }
        for (IngredientCost c : effectiveIngredientCosts(branch, tier, town)) {
            if (InventoryMaterials.count(inv, c.itemId()) < c.count()) {
                return false;
            }
        }
        long needGold = effectiveGoldCost(branch, tier, town);
        return needGold <= 0L || GoldCoinPayment.canAfford(town, inv, needGold, allowTreasuryGold);
    }

    public enum PurchaseResult {
        OK,
        MAXED,
        NEED_INGREDIENT,
        NEED_GOLD,
        TAKE_INGREDIENT_FAILED,
        PAY_GOLD_FAILED
    }

    @Nonnull
    public static PurchaseResult tryPurchase(
        @Nonnull PlotRestaurantState state,
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
        List<IngredientCost> costs = effectiveIngredientCosts(branch, tier, town);
        for (IngredientCost c : costs) {
            if (InventoryMaterials.count(inv, c.itemId()) < c.count()) {
                return PurchaseResult.NEED_INGREDIENT;
            }
        }
        long needGold = effectiveGoldCost(branch, tier, town);
        if (needGold > 0L && !GoldCoinPayment.canAfford(town, inv, needGold, allowTreasuryGold)) {
            return PurchaseResult.NEED_GOLD;
        }
        for (IngredientCost c : costs) {
            if (!removeFromInventory(inv, c.itemId(), c.count())) {
                return PurchaseResult.TAKE_INGREDIENT_FAILED;
            }
        }
        if (needGold > 0L && !GoldCoinPayment.trySpend(town, inv, needGold, allowTreasuryGold)) {
            return PurchaseResult.PAY_GOLD_FAILED;
        }
        switch (branch) {
            case SATIETY -> state.setSatietyLevel(tier);
            case SERVICE -> state.setServiceLevel(tier);
        }
        return PurchaseResult.OK;
    }

    private static boolean removeFromInventory(@Nonnull CombinedItemContainer inv, @Nonnull String itemId, int count) {
        int remaining = count;
        for (short slot = 0; slot < inv.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = inv.getItemStack(slot);
            if (stack == null || stack.isEmpty() || !itemId.equals(stack.getItemId())) {
                continue;
            }
            int take = Math.min(remaining, stack.getQuantity());
            ItemStackSlotTransaction tx = inv.removeItemStackFromSlot(slot, take);
            if (tx.succeeded()) {
                remaining -= take;
            }
        }
        return remaining <= 0;
    }

    @Nullable
    public static Branch branchFromId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "satiety" -> Branch.SATIETY;
            case "service" -> Branch.SERVICE;
            default -> null;
        };
    }
}
