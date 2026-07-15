package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotBuildingKindRequirements {
    public record SubstepRequirement(
        @Nonnull PlotCreatorSubstepType type,
        int minCount,
        @Nullable String workResidentKind
    ) {
        public SubstepRequirement(@Nonnull PlotCreatorSubstepType type, int minCount) {
            this(type, minCount, null);
        }

        @Nonnull
        public PlotCreatorSpotEntry toSpotEntry() {
            return new PlotCreatorSpotEntry(type, minCount, workResidentKind);
        }
    }

    private PlotBuildingKindRequirements() {}

    /**
     * Substeps for the staff loop: confirmed {@link PlotCreatorDraft#getSelectedSpots()} when set, otherwise defaults
     * from kinds / counts-as.
     */
    @Nonnull
    public static List<SubstepRequirement> forDraft(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (draft.isDecorationOnly()) {
            return List.of();
        }
        if (draft.isImportantSpotsConfirmed() && !draft.getSelectedSpots().isEmpty()) {
            return forSelectedSpots(draft);
        }
        return defaultRequirements(draft, plugin);
    }

    @Nonnull
    public static List<SubstepRequirement> forSelectedSpots(@Nonnull PlotCreatorDraft draft) {
        List<SubstepRequirement> out = new ArrayList<>();
        for (PlotCreatorSpotEntry entry : draft.getSelectedSpots()) {
            out.add(new SubstepRequirement(entry.type(), entry.minCount(), entry.workResidentKind()));
        }
        return out;
    }

    /** Default chooser/preselection from kinds and counts-as (union). */
    @Nonnull
    public static List<SubstepRequirement> defaultRequirements(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (draft.isDecorationOnly()) {
            return List.of();
        }
        List<PlotBuildingKind> kinds = effectiveKinds(draft, plugin);
        if (kinds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<SubstepRequirement> merged = new LinkedHashSet<>();
        boolean needManagement = false;
        for (PlotBuildingKind kind : kinds) {
            if (kind == PlotBuildingKind.DECORATION || kind == PlotBuildingKind.VARIANT) {
                continue;
            }
            if (kind != PlotBuildingKind.TOURIST_PORTAL) {
                needManagement = true;
            }
            for (SubstepRequirement req : requirementsForSingleKind(kind, draft, plugin)) {
                merged.add(req);
            }
        }
        // Tourist portal alone has no town records; combined with other kinds it does.
        if (needManagement || (kinds.size() == 1 && kinds.get(0) == PlotBuildingKind.TOURIST_PORTAL)) {
            // TOURIST_PORTAL alone: no management (legacy). Combined: management already via other kinds.
        }
        if (needManagement) {
            ensureFirst(merged, new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        }
        expandWorkRoles(merged, draft, plugin);
        return new ArrayList<>(merged);
    }

    /**
     * Resolves authoring kinds: selected kinds, expanding VARIANT through each counts-as base; tourist portal kept as
     * an add-on.
     */
    @Nonnull
    public static List<PlotBuildingKind> effectiveKinds(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<PlotBuildingKind> selected = draft.getKinds();
        if (selected.isEmpty()) {
            return List.of();
        }
        if (draft.isDecorationOnly()) {
            return List.of(PlotBuildingKind.DECORATION);
        }
        LinkedHashSet<PlotBuildingKind> out = new LinkedHashSet<>();
        boolean wantsTouristPortal = selected.contains(PlotBuildingKind.TOURIST_PORTAL);
        for (PlotBuildingKind kind : selected) {
            if (kind == PlotBuildingKind.DECORATION) {
                continue;
            }
            if (kind == PlotBuildingKind.VARIANT) {
                for (String baseId : draft.getCountsAsConstructionIds()) {
                    PlotBuildingKind baseKind = resolveBaseKind(baseId, plugin);
                    if (baseKind != null && baseKind != PlotBuildingKind.VARIANT) {
                        out.add(baseKind);
                    }
                }
                continue;
            }
            if (kind == PlotBuildingKind.TOURIST_PORTAL) {
                continue;
            }
            out.add(kind);
        }
        if (wantsTouristPortal) {
            out.add(PlotBuildingKind.TOURIST_PORTAL);
        }
        if (out.isEmpty() && selected.contains(PlotBuildingKind.VARIANT)) {
            out.add(PlotBuildingKind.HOME);
        }
        return new ArrayList<>(out);
    }

    /** Primary kind for save rules that need one value (HOME max residents, etc.). */
    @Nullable
    public static PlotBuildingKind effectiveKind(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        List<PlotBuildingKind> kinds = effectiveKinds(draft, plugin);
        if (kinds.isEmpty()) {
            return draft.getKind();
        }
        for (PlotBuildingKind k : kinds) {
            if (k != PlotBuildingKind.TOURIST_PORTAL) {
                return k;
            }
        }
        return kinds.get(0);
    }

    public static boolean requiresShopSafe(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (draft.hasKind(PlotBuildingKind.PLAYER_SHOP)) {
            return true;
        }
        for (String baseId : draft.getCountsAsConstructionIds()) {
            if (AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP.equals(baseId.trim())) {
                return true;
            }
        }
        return effectiveKinds(draft, plugin).contains(PlotBuildingKind.PLAYER_SHOP);
    }

    /** Restaurant and other eat-tagged shops need dining POIs; plain market stalls do not. */
    public static boolean requiresEatPoi(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (draft.getBuildingTags().contains("eat") || draft.getBuildingTags().contains("restaurant")) {
            return true;
        }
        for (String id : candidateConstructionIds(draft)) {
            if (AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT.equals(id.trim())) {
                return true;
            }
            if (plugin == null) {
                continue;
            }
            ConstructionDefinition def = plugin.getConstructionCatalog().get(id.trim());
            if (def == null) {
                continue;
            }
            var tags = def.getBuildingTags();
            if (tags.contains("eat") || tags.contains("restaurant")) {
                return true;
            }
        }
        return false;
    }

    public static boolean usesRestaurantEatTag(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (draft.getBuildingTags().contains("restaurant")) {
            return true;
        }
        for (String id : candidateConstructionIds(draft)) {
            if (AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT.equals(id.trim())) {
                return true;
            }
            if (plugin == null) {
                continue;
            }
            ConstructionDefinition def = plugin.getConstructionCatalog().get(id.trim());
            if (def != null && def.getBuildingTags().contains("restaurant")) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static List<String> workplaceRolesForDraft(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        if (plugin == null) {
            return List.of();
        }
        for (String gameplayId : gameplayIdsForWorkplaceLookup(draft, plugin)) {
            if (ProductionWorkplaceKinds.isMultiRoleWorkplace(gameplayId)) {
                String gm = ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(gameplayId);
                if (gm != null) {
                    roles.add(gm);
                }
                roles.add(TownVillagerBinding.KIND_BARD);
                continue;
            }
            String kind = ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(gameplayId);
            if (kind != null) {
                roles.add(kind);
            }
        }
        // Authoring kinds without a counts-as id still need a role for WORK kinds.
        for (PlotBuildingKind kind : effectiveKinds(draft, plugin)) {
            switch (kind) {
                case INN -> roles.add(TownVillagerBinding.KIND_INNKEEPER);
                case TOWN_HALL -> roles.add(TownVillagerBinding.KIND_ELDER);
                case GUILD_HALL -> {
                    roles.add(TownVillagerBinding.KIND_GUILD_MASTER);
                    roles.add(TownVillagerBinding.KIND_BARD);
                }
                case WORK, SHOP, PLAYER_SHOP, HOME, AMENITY, TOURIST_PORTAL, DECORATION, VARIANT -> {}
            }
        }
        return new ArrayList<>(roles);
    }

    public static boolean isSpecialBlockType(@Nonnull String blockTypeId) {
        return AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID.equals(blockTypeId)
            || AetherhavenConstants.BLOCK_PRODUCTION_STORAGE.equals(blockTypeId)
            || AetherhavenConstants.TREASURY_BLOCK_TYPE_ID.equals(blockTypeId)
            || AetherhavenConstants.SHOP_SAFE_BLOCK_TYPE_ID.equals(blockTypeId)
            || AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(blockTypeId)
            || AetherhavenConstants.TOURIST_PORTAL_BLOCK_TYPE_ID.equals(blockTypeId)
            || AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(blockTypeId)
            || "Aetherhaven_Town_Planning_Desk".equals(blockTypeId);
    }

    @Nonnull
    private static List<SubstepRequirement> requirementsForSingleKind(
        @Nonnull PlotBuildingKind kind,
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        return switch (kind) {
            case DECORATION, VARIANT -> List.of();
            case HOME -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.SLEEP_POI, 1)
            );
            case WORK -> workSubsteps(draft, plugin);
            case AMENITY -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.FUN_POI, 1)
            );
            case SHOP -> shopSubsteps(draft, plugin, false);
            case PLAYER_SHOP -> shopSubsteps(draft, plugin, true);
            case TOURIST_PORTAL -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.TOURIST_PORTAL_BLOCK, 1)
            );
            case INN -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.WORK_POI, 1, TownVillagerBinding.KIND_INNKEEPER),
                new SubstepRequirement(PlotCreatorSubstepType.SLEEP_POI, 2),
                new SubstepRequirement(PlotCreatorSubstepType.EAT_POI, 1),
                new SubstepRequirement(PlotCreatorSubstepType.INNKEEPER_SPAWN, 1),
                new SubstepRequirement(PlotCreatorSubstepType.VISITOR_SPAWN, 2),
                new SubstepRequirement(PlotCreatorSubstepType.GUILD_MASTER_SPAWN, 0)
            );
            case TOWN_HALL -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.TREASURY_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.PLANNING_DESK_POI, 1)
            );
            case GUILD_HALL -> List.of(
                new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1),
                new SubstepRequirement(PlotCreatorSubstepType.WORK_POI, 1, TownVillagerBinding.KIND_GUILD_MASTER),
                new SubstepRequirement(PlotCreatorSubstepType.BARD_WORK_POI, 1),
                new SubstepRequirement(PlotCreatorSubstepType.ADVENTURER_SPAWN, 1),
                new SubstepRequirement(PlotCreatorSubstepType.QUEST_BOARD_POI, 1)
            );
        };
    }

    @Nonnull
    private static List<SubstepRequirement> shopSubsteps(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin,
        boolean requireSafe
    ) {
        List<SubstepRequirement> out = new ArrayList<>();
        out.add(new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        if (requireSafe) {
            out.add(new SubstepRequirement(PlotCreatorSubstepType.SHOP_SAFE_BLOCK, 1));
        }
        out.add(new SubstepRequirement(PlotCreatorSubstepType.SHOP_SPOT, 1));
        out.add(new SubstepRequirement(PlotCreatorSubstepType.SHOP_POI, 1));
        out.add(new SubstepRequirement(PlotCreatorSubstepType.TOURIST_VISIT_POI, 1));
        if (requiresEatPoi(draft, plugin)) {
            out.add(new SubstepRequirement(PlotCreatorSubstepType.EAT_POI, 1));
        }
        return out;
    }

    @Nonnull
    private static List<SubstepRequirement> workSubsteps(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<SubstepRequirement> out = new ArrayList<>();
        out.add(new SubstepRequirement(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        if (requiresProductionStorage(draft, plugin)) {
            out.add(new SubstepRequirement(PlotCreatorSubstepType.PRODUCTION_STORAGE, 1));
        }
        String role = null;
        if (plugin != null) {
            for (String id : gameplayIdsForWorkplaceLookup(draft, plugin)) {
                role = ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(id);
                if (role != null) {
                    break;
                }
            }
        }
        out.add(new SubstepRequirement(PlotCreatorSubstepType.WORK_POI, 1, role));
        return out;
    }

    private static boolean requiresProductionStorage(
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (plugin == null) {
            return false;
        }
        for (String id : candidateConstructionIds(draft)) {
            String gameplayId = plugin.getConstructionCatalog().resolveGameplayConstructionId(id.trim());
            if (ProductionCatalog.isProductionWorkplaceConstruction(gameplayId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replace generic WORK_POI rows with one per resolved workplace role when multiple roles apply.
     */
    private static void expandWorkRoles(
        @Nonnull LinkedHashSet<SubstepRequirement> merged,
        @Nonnull PlotCreatorDraft draft,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<String> roles = workplaceRolesForDraft(draft, plugin);
        if (roles.size() <= 1) {
            return;
        }
        List<SubstepRequirement> withoutGenericWork = new ArrayList<>();
        boolean hadWork = false;
        for (SubstepRequirement req : merged) {
            if (req.type() == PlotCreatorSubstepType.WORK_POI
                || req.type() == PlotCreatorSubstepType.BARD_WORK_POI) {
                hadWork = true;
                continue;
            }
            withoutGenericWork.add(req);
        }
        if (!hadWork) {
            return;
        }
        merged.clear();
        merged.addAll(withoutGenericWork);
        for (String role : roles) {
            if (TownVillagerBinding.KIND_BARD.equals(role)) {
                merged.add(new SubstepRequirement(PlotCreatorSubstepType.BARD_WORK_POI, 1));
            } else {
                merged.add(new SubstepRequirement(PlotCreatorSubstepType.WORK_POI, 1, role));
            }
        }
    }

    private static void ensureFirst(
        @Nonnull LinkedHashSet<SubstepRequirement> merged,
        @Nonnull SubstepRequirement management
    ) {
        if (merged.contains(management)) {
            return;
        }
        List<SubstepRequirement> rest = new ArrayList<>(merged);
        merged.clear();
        merged.add(management);
        merged.addAll(rest);
    }

    @Nonnull
    private static List<String> candidateConstructionIds(@Nonnull PlotCreatorDraft draft) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(draft.getCountsAsConstructionIds());
        if (draft.getConstructionId() != null && !draft.getConstructionId().isBlank()) {
            ids.add(draft.getConstructionId().trim());
        }
        return new ArrayList<>(ids);
    }

    @Nonnull
    private static List<String> gameplayIdsForWorkplaceLookup(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull AetherhavenPlugin plugin
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String id : draft.getCountsAsConstructionIds()) {
            out.addAll(plugin.getConstructionCatalog().resolveGameplayConstructionIds(id));
        }
        if (draft.getConstructionId() != null && !draft.getConstructionId().isBlank()) {
            out.addAll(plugin.getConstructionCatalog().resolveGameplayConstructionIds(draft.getConstructionId()));
        }
        return new ArrayList<>(out);
    }

    @Nullable
    static PlotBuildingKind resolveBaseKind(@Nullable String baseId, @Nullable AetherhavenPlugin plugin) {
        if (baseId == null || baseId.isBlank()) {
            return PlotBuildingKind.HOME;
        }
        String id = baseId.trim();
        if (AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(id)) {
            return PlotBuildingKind.INN;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL.equals(id)) {
            return PlotBuildingKind.TOWN_HALL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL.equals(id)) {
            return PlotBuildingKind.GUILD_HALL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_PARK.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR.equals(id)) {
            return PlotBuildingKind.AMENITY;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_BOMB_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP.equals(id)
            || AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT.equals(id)) {
            return PlotBuildingKind.SHOP;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP.equals(id)) {
            return PlotBuildingKind.PLAYER_SHOP;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_TOURIST_PORTAL.equals(id)) {
            return PlotBuildingKind.TOURIST_PORTAL;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(id)) {
            return PlotBuildingKind.HOME;
        }
        if (plugin == null) {
            return PlotBuildingKind.WORK;
        }
        ConstructionDefinition base = plugin.getConstructionCatalog().get(id);
        if (base == null) {
            return PlotBuildingKind.HOME;
        }
        if (AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(base.getGameplayConstructionId())) {
            return PlotBuildingKind.HOME;
        }
        if (base.getShopSafeLocalPos() != null) {
            return PlotBuildingKind.PLAYER_SHOP;
        }
        var buildingTags = base.getBuildingTags();
        if (buildingTags.contains("home") || buildingTags.contains("house")) {
            return PlotBuildingKind.HOME;
        }
        if (buildingTags.contains("inn")) {
            return PlotBuildingKind.INN;
        }
        if (buildingTags.contains("amenity") || buildingTags.contains("park")) {
            return PlotBuildingKind.AMENITY;
        }
        if (buildingTags.contains("player_shop") || buildingTags.contains("playershop")) {
            return PlotBuildingKind.PLAYER_SHOP;
        }
        if (buildingTags.contains("shop")) {
            return PlotBuildingKind.SHOP;
        }
        if (buildingTags.contains("work")) {
            return PlotBuildingKind.WORK;
        }
        if (base.getPois().stream().anyMatch(p -> p.getTags().contains("FUN") || p.getTags().contains("SIT"))) {
            return PlotBuildingKind.AMENITY;
        }
        if (base.getPois().stream().anyMatch(p -> p.getTags().contains("SHOP"))) {
            return PlotBuildingKind.SHOP;
        }
        if (base.getPois().stream().anyMatch(p -> p.getTags().contains("SLEEP"))) {
            return PlotBuildingKind.HOME;
        }
        return PlotBuildingKind.WORK;
    }
}
