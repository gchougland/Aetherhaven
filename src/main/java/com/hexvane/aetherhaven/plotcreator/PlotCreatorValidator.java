package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorValidator {
    private PlotCreatorValidator() {}

    @Nullable
    public static String validateId(@Nullable String raw, @Nonnull ConstructionCatalog catalog, @Nullable String editingId) {
        if (raw == null) {
            return "id_empty";
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return "id_empty";
        }
        if (!id.startsWith("plot_")) {
            return "id_prefix";
        }
        if (id.startsWith("plot_wall")) {
            return "id_wall";
        }
        if (!id.matches("plot_[a-z0-9_]+")) {
            return "id_chars";
        }
        if (!id.equals(editingId) && catalog.get(id) != null) {
            return "id_taken";
        }
        return null;
    }

    @Nullable
    public static String validateBeforeSave(@Nonnull PlotCreatorDraft draft, @Nonnull AetherhavenPlugin plugin) {
        if (draft.getConstructionId() == null || draft.getDisplayName() == null || draft.getPrefabPath() == null) {
            return "incomplete";
        }
        Vector3i anchor = draft.getPlotAnchor();
        if (anchor == null) {
            return "incomplete";
        }
        // Existing catalog buildings anchor the prefab at plot sign + plotAnchorOffset; do not require the sign
        // cell to sit inside the solid footprint (would force re-centering and shift in-world plots on reconstruct).
        if (!draft.isBuildingEditorMode() && !draft.isInsideBounds(anchor)) {
            return "anchorOutsideBounds";
        }
        String idErr = validateId(draft.getConstructionId(), plugin.getConstructionCatalog(), draft.getEditingConstructionId());
        if (idErr != null) {
            return idErr;
        }
        if (draft.isBuildingEditorMode()) {
            if (!BuildingEditorSavePaths.prefabExistsForEditor(plugin, draft)) {
                return "prefab_missing";
            }
        } else {
            var prefabFile = CustomBuildingsPaths.resolvePrefabFile(plugin.getDataDirectory(), draft.getPrefabPath());
            if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
                return "prefab_missing";
            }
        }
        List<PlotBuildingKindRequirements.SubstepRequirement> steps =
            PlotBuildingKindRequirements.forDraft(draft, plugin);
        for (int i = 0; i < steps.size(); i++) {
            PlotBuildingKindRequirements.SubstepRequirement req = steps.get(i);
            if (countForRequirement(draft, req) < req.minCount()) {
                return "substep_" + req.type().name();
            }
        }
        return null;
    }

    /** Count of placed items matching this important-spot requirement. */
    public static int countForRequirement(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req
    ) {
        PlotCreatorSubstepType type = req.type();
        return switch (type) {
            case MANAGEMENT_BLOCK -> draft.getManagementBlockLocalPos() != null ? 1 : 0;
            case PRODUCTION_STORAGE -> draft.getProductionStorageLocalPos() != null ? 1 : 0;
            case TREASURY_BLOCK -> draft.getTreasuryLocalPos() != null ? 1 : 0;
            case SHOP_SAFE_BLOCK -> draft.getShopSafeLocalPos() != null ? 1 : 0;
            case INN_BELL_BLOCK -> draft.getInnBellLocalPos() != null ? 1 : 0;
            case SHOP_SPOT, TOURIST_PORTAL_BLOCK -> draft.getPlacedSpecialBlocks().size();
            case PLANNING_DESK_POI, WORK_POI, BARD_WORK_POI, SLEEP_POI, EAT_POI, FUN_POI, SHOP_POI, TOURIST_VISIT_POI,
                QUEST_BOARD_POI -> countPoiRequirement(draft, req);
            case INNKEEPER_SPAWN -> draft.getInnkeeperSpawnLocal() != null ? 1 : 0;
            case VISITOR_SPAWN -> draft.getVisitorSpawnLocals().size();
            case GUILD_MASTER_SPAWN -> draft.getGuildMasterSpawnLocal() != null ? 1 : 0;
            case ADVENTURER_SPAWN -> draft.getAdventurerSpawns().size();
        };
    }

    private static int countPoiRequirement(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req
    ) {
        int n = 0;
        for (PlotCreatorPoiDraft p : draft.getPois()) {
            if (matchesPoiRequirement(p, req)) {
                n++;
            }
        }
        return n;
    }

    /** Whether a draft POI row satisfies the given important-spot requirement. */
    public static boolean matchesPoiRequirement(
        @Nonnull PlotCreatorPoiDraft p,
        @Nonnull PlotBuildingKindRequirements.SubstepRequirement req
    ) {
        PlotCreatorSubstepType type = req.type();
        return switch (type) {
            case WORK_POI -> {
                if (!p.getTags().contains("WORK") || p.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                    yield false;
                }
                String want = req.workResidentKind();
                String have = p.getWorkResidentKind();
                if (want == null || want.isBlank()) {
                    yield have == null || have.isBlank();
                }
                // Untyped WORK spots (common in shipped buildings) match any role requirement.
                if (have == null || have.isBlank()) {
                    yield true;
                }
                yield Objects.equals(want, have);
            }
            case BARD_WORK_POI -> p.getTags().contains(AetherhavenConstants.POI_TAG_BARD)
                || com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_BARD.equals(p.getWorkResidentKind());
            case QUEST_BOARD_POI -> p.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)
                || AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(p.getBlockTypeId());
            case SLEEP_POI -> p.getTags().contains("SLEEP") || p.getTags().contains("ENERGY");
            case EAT_POI -> p.getTags().contains("EAT");
            case FUN_POI -> p.getTags().contains("FUN") || p.getTags().contains("SIT");
            case SHOP_POI -> p.getTags().contains("SHOP");
            case TOURIST_VISIT_POI -> p.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
            case PLANNING_DESK_POI -> "Aetherhaven_Town_Planning_Desk".equals(p.getBlockTypeId());
            default -> false;
        };
    }
}
