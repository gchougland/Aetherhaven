package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps gameplay construction ids to resident {@link TownVillagerBinding} kinds for the management-block
 * workplace worker dropdown. Includes every core building with a permanent villager job ({@code workConstructionId}
 * in {@code Server/Aetherhaven/Villagers/}), not homes, parks, or decoration plots. Crossmod workplaces resolve
 * from villager defs when not in the core switch.
 */
public final class ProductionWorkplaceKinds {
    private ProductionWorkplaceKinds() {}

    /** True when the town records shelf may assign a villager to work at this completed plot. */
    public static boolean supportsWorkerAssignment(@Nullable String gameplayConstructionId) {
        return residentBindingKindForGameplayConstruction(gameplayConstructionId) != null
            || isMultiRoleWorkplace(gameplayConstructionId);
    }

    /** Guild hall staffs a guild master and a bard at separate work stations. */
    public static boolean isMultiRoleWorkplace(@Nullable String gameplayConstructionId) {
        return AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(trimOrNull(gameplayConstructionId));
    }

    /**
     * Elder and innkeeper must stay assigned to town hall / inn; the management UI blocks clearing their workplace.
     */
    public static boolean isMandatoryWorkplaceResidentKind(@Nullable String residentKind) {
        if (residentKind == null || residentKind.isBlank()) {
            return false;
        }
        return switch (residentKind.trim()) {
            case TownVillagerBinding.KIND_ELDER, TownVillagerBinding.KIND_INNKEEPER -> true;
            default -> false;
        };
    }

    @Nullable
    public static String residentBindingKindForGameplayConstruction(@Nullable String gameplayConstructionId) {
        if (gameplayConstructionId == null || gameplayConstructionId.isBlank()) {
            return null;
        }
        String id = gameplayConstructionId.trim();
        String known =
            switch (id) {
                case AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL -> TownVillagerBinding.KIND_ELDER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_INN -> TownVillagerBinding.KIND_INNKEEPER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL -> TownVillagerBinding.KIND_GUILD_MASTER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR -> TownVillagerBinding.KIND_PRIESTESS;
                case AetherhavenConstants.CONSTRUCTION_PLOT_FARM -> TownVillagerBinding.KIND_FARMER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT -> TownVillagerBinding.KIND_MINER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL -> TownVillagerBinding.KIND_LOGGER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_BARN -> TownVillagerBinding.KIND_RANCHER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP -> TownVillagerBinding.KIND_BLACKSMITH;
                case AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL -> TownVillagerBinding.KIND_MERCHANT;
                case AetherhavenConstants.CONSTRUCTION_PLOT_BUILDERS_HUT -> TownVillagerBinding.KIND_BUILDER;
                case AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP -> TownVillagerBinding.KIND_FLORIST;
                case AetherhavenConstants.CONSTRUCTION_PLOT_BOMB_SHOP -> TownVillagerBinding.KIND_PYROTECHNIC;
                case AetherhavenConstants.CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP -> TownVillagerBinding.KIND_CRYSTAL_KEEPER;
                default -> null;
            };
        if (known != null) {
            return known;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        return residentKindFromVillagerCatalog(
            plugin.getVillagerDefinitionCatalog(),
            plugin.getConstructionCatalog(),
            id
        );
    }

    /**
     * Resident binding kind for a loaded NPC role (guild hall bard vs guild master, or default workplace mapping).
     */
    @Nullable
    public static String residentBindingKindForNpcRoleId(
        @Nonnull AetherhavenPlugin plugin,
        @Nullable String npcRoleId
    ) {
        if (npcRoleId == null || npcRoleId.isBlank()) {
            return null;
        }
        String role = npcRoleId.trim();
        if (AetherhavenConstants.BARD_NPC_ROLE_ID.equalsIgnoreCase(role)) {
            return TownVillagerBinding.KIND_BARD;
        }
        if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equalsIgnoreCase(role)) {
            return TownVillagerBinding.KIND_GUILD_MASTER;
        }
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(role);
        if (vdef == null) {
            return null;
        }
        String fromWork = residentBindingKindForGameplayConstruction(vdef.getWorkConstructionId());
        if (fromWork != null) {
            return fromWork;
        }
        return InnVisitorShopPromotion.resolveResidentKind(vdef);
    }

    @Nullable
    static String residentKindFromVillagerCatalog(
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String gameplayConstructionId
    ) {
        for (VillagerDefinition def : villagers.allByNpcRoleId().values()) {
            String work = def.getWorkConstructionId();
            if (work == null) {
                continue;
            }
            if (!InnVisitorShopPromotion.constructionMatchesWork(
                constructions,
                work,
                gameplayConstructionId,
                gameplayConstructionId
            )) {
                continue;
            }
            String kind = InnVisitorShopPromotion.resolveResidentKind(def);
            if (kind != null && !kind.isBlank()) {
                return kind;
            }
        }
        return null;
    }

    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
