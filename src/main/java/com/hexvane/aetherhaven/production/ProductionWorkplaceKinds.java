package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** True when any resolved gameplay id for the stored construction supports worker assignment. */
    public static boolean supportsWorkerAssignmentForPlot(
        @Nonnull ConstructionCatalog catalog,
        @Nullable String plotStoredConstructionId
    ) {
        return !residentBindingKindsForPlot(catalog, plotStoredConstructionId).isEmpty();
    }

    /**
     * All distinct workplace resident kinds this plot can assign (including bard when any id is guild hall).
     */
    @Nonnull
    public static List<String> residentBindingKindsForPlot(
        @Nonnull ConstructionCatalog catalog,
        @Nullable String plotStoredConstructionId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        VillagerDefinitionCatalog villagers =
            plugin != null ? plugin.getVillagerDefinitionCatalog() : VillagerDefinitionCatalog.empty();
        return residentBindingKindsForPlot(catalog, villagers, plotStoredConstructionId);
    }

    @Nonnull
    static List<String> residentBindingKindsForPlot(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nullable String plotStoredConstructionId
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
            if (isNonWorkplaceGameplayConstruction(gid)) {
                continue;
            }
            if (isMultiRoleWorkplace(gid)) {
                String gm = residentBindingKindForGameplayConstruction(gid);
                if (gm != null) {
                    out.add(gm);
                }
                out.add(TownVillagerBinding.KIND_BARD);
                continue;
            }
            String kind = residentBindingKindForGameplayConstruction(gid);
            if (kind != null) {
                out.add(kind);
            }
        }
        String stored = trimOrNull(plotStoredConstructionId);
        if (stored != null) {
            String fromStored = residentKindForStoredPlotConstruction(villagers, catalog, stored);
            if (fromStored != null) {
                out.add(fromStored);
            }
        }
        return new ArrayList<>(out);
    }

    /** Guild hall staffs a guild master and a bard at separate work stations. */
    public static boolean isMultiRoleWorkplace(@Nullable String gameplayConstructionId) {
        return AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(trimOrNull(gameplayConstructionId));
    }

    /** True when any resolved gameplay id for the plot is a multi-role (guild hall) workplace. */
    public static boolean isMultiRoleWorkplacePlot(
        @Nonnull ConstructionCatalog catalog,
        @Nullable String plotStoredConstructionId
    ) {
        for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
            if (isMultiRoleWorkplace(gid)) {
                return true;
            }
        }
        return false;
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

    /**
     * Gameplay construction id whose permanent worker role is {@code residentKind} for this plot (variant counts-as
     * lists, guild hall bard, etc.).
     */
    @Nullable
    public static String gameplayConstructionIdForResidentKind(
        @Nonnull ConstructionCatalog catalog,
        @Nullable String plotStoredConstructionId,
        @Nonnull String residentKind
    ) {
        String want = residentKind.trim();
        if (want.isEmpty()) {
            return null;
        }
        if (TownVillagerBinding.KIND_BARD.equals(want)) {
            for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
                if (isMultiRoleWorkplace(gid)) {
                    return gid;
                }
            }
            return null;
        }
        for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
            if (isNonWorkplaceGameplayConstruction(gid)) {
                continue;
            }
            String kind = residentBindingKindForGameplayConstruction(gid);
            if (want.equals(kind)) {
                return gid;
            }
        }
        String stored = trimOrNull(plotStoredConstructionId);
        if (stored != null) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            VillagerDefinitionCatalog villagers =
                plugin != null ? plugin.getVillagerDefinitionCatalog() : VillagerDefinitionCatalog.empty();
            String workId = workConstructionIdForStoredPlotRole(villagers, catalog, stored, want);
            if (workId != null) {
                return workId;
            }
        }
        return null;
    }

    @Nullable
    public static String gameplayConstructionIdForResidentKindForTests(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nullable String plotStoredConstructionId,
        @Nonnull String residentKind
    ) {
        String want = residentKind.trim();
        if (want.isEmpty()) {
            return null;
        }
        if (TownVillagerBinding.KIND_BARD.equals(want)) {
            for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
                if (isMultiRoleWorkplace(gid)) {
                    return gid;
                }
            }
            return null;
        }
        for (String gid : catalog.resolveGameplayConstructionIds(plotStoredConstructionId)) {
            if (isNonWorkplaceGameplayConstruction(gid)) {
                continue;
            }
            String kind = residentBindingKindForGameplayConstruction(gid);
            if (want.equals(kind)) {
                return gid;
            }
        }
        String stored = trimOrNull(plotStoredConstructionId);
        if (stored != null) {
            return workConstructionIdForStoredPlotRole(villagers, catalog, stored, want);
        }
        return null;
    }

    /**
     * Canonical gameplay ids used for housing, amenities, player commerce, or travel — not permanent villager jobs
     * on the town records workplace shelf.
     */
    public static boolean isNonWorkplaceGameplayConstruction(@Nullable String gameplayConstructionId) {
        String id = trimOrNull(gameplayConstructionId);
        if (id == null) {
            return false;
        }
        return switch (id) {
            case AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE,
                AetherhavenConstants.CONSTRUCTION_PLOT_PARK,
                AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP,
                AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_SEGMENT,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_GATE,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_EASTDOOR_NS,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_EASTDOOR_SW,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_ENDCAP_S,
                AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_OUTERCORNER_SE -> true;
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
                case AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT -> TownVillagerBinding.KIND_CHEF;
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

    /** Test hook for plot role resolution without {@link AetherhavenPlugin}. */
    @Nonnull
    public static List<String> residentBindingKindsForPlotForTests(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nullable String plotStoredConstructionId
    ) {
        return residentBindingKindsForPlot(catalog, villagers, plotStoredConstructionId);
    }

    /** Test hook for crossmod catalog resolution without {@link AetherhavenPlugin}. */
    @Nullable
    public static String residentBindingKindFromVillagerCatalogForTests(
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String gameplayConstructionId
    ) {
        return residentKindFromVillagerCatalog(villagers, constructions, gameplayConstructionId);
    }

    /**
     * Crossmod workplace role when a villager's {@code workConstructionId} matches this plot's stored construction id
     * (not when both only share a generic alias such as {@code plot_house}).
     */
    @Nullable
    static String residentKindForStoredPlotConstruction(
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String plotStoredConstructionId
    ) {
        for (VillagerDefinition def : villagers.allByNpcRoleId().values()) {
            String work = def.getWorkConstructionId();
            if (work == null || !workConstructionMatchesStoredPlotDirectly(constructions, work, plotStoredConstructionId)) {
                continue;
            }
            String kind = residentBindingKindForGameplayConstruction(work);
            if (kind == null) {
                kind = InnVisitorShopPromotion.resolveResidentKind(def);
            }
            if (kind != null && !kind.isBlank()) {
                return kind;
            }
        }
        return null;
    }

    @Nullable
    static String workConstructionIdForStoredPlotRole(
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String plotStoredConstructionId,
        @Nonnull String residentKind
    ) {
        String want = residentKind.trim();
        for (VillagerDefinition def : villagers.allByNpcRoleId().values()) {
            String work = def.getWorkConstructionId();
            if (work == null || !workConstructionMatchesStoredPlotDirectly(constructions, work, plotStoredConstructionId)) {
                continue;
            }
            String kind = residentBindingKindForGameplayConstruction(work);
            if (kind == null) {
                kind = InnVisitorShopPromotion.resolveResidentKind(def);
            }
            if (want.equals(kind)) {
                return work;
            }
        }
        return null;
    }

    /** Plot variant counts-as villager work building, or ids match exactly — not shared house-only aliases. */
    private static boolean workConstructionMatchesStoredPlotDirectly(
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String workConstructionId,
        @Nonnull String plotStoredConstructionId
    ) {
        if (workConstructionId.equals(plotStoredConstructionId)) {
            return true;
        }
        return constructions.matchesGameplayConstruction(plotStoredConstructionId, workConstructionId);
    }

    @Nullable
    static String residentKindFromVillagerCatalog(
        @Nonnull VillagerDefinitionCatalog villagers,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String gameplayConstructionId
    ) {
        if (isNonWorkplaceGameplayConstruction(gameplayConstructionId)) {
            return null;
        }
        for (VillagerDefinition def : villagers.allByNpcRoleId().values()) {
            String work = def.getWorkConstructionId();
            if (work == null || !workConstructionIdIsWorkplaceJob(constructions, work)) {
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

    /** True when {@code workConstructionId} resolves to at least one permanent job gameplay id. */
    private static boolean workConstructionIdIsWorkplaceJob(
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String workConstructionId
    ) {
        List<String> resolved = constructions.resolveGameplayConstructionIds(workConstructionId);
        if (resolved.isEmpty()) {
            return !isNonWorkplaceGameplayConstruction(workConstructionId);
        }
        for (String gid : resolved) {
            if (!isNonWorkplaceGameplayConstruction(gid)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
