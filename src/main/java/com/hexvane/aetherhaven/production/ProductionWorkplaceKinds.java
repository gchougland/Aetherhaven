package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import javax.annotation.Nullable;

/** Maps gameplay construction ids (production catalog keys) to resident {@link TownVillagerBinding} kinds. */
public final class ProductionWorkplaceKinds {
    private ProductionWorkplaceKinds() {}

    @Nullable
    public static String residentBindingKindForGameplayConstruction(@Nullable String gameplayConstructionId) {
        if (gameplayConstructionId == null || gameplayConstructionId.isBlank()) {
            return null;
        }
        return switch (gameplayConstructionId.trim()) {
            case AetherhavenConstants.CONSTRUCTION_PLOT_FARM -> TownVillagerBinding.KIND_FARMER;
            case AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT -> TownVillagerBinding.KIND_MINER;
            case AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL -> TownVillagerBinding.KIND_LOGGER;
            case AetherhavenConstants.CONSTRUCTION_PLOT_BARN -> TownVillagerBinding.KIND_RANCHER;
            case AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP -> TownVillagerBinding.KIND_BLACKSMITH;
            case AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL -> TownVillagerBinding.KIND_MERCHANT;
            default -> null;
        };
    }
}
