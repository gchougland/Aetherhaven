package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCreatorJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlotCreatorJsonWriter() {}

    public static void writeBuilding(@Nonnull Path outputFile, @Nonnull PlotCreatorDraft draft) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", draft.getConstructionId());
        root.put("displayName", draft.getDisplayName());
        String description = draft.getDescription();
        if (description != null && !description.isBlank()) {
            root.put("description", description.trim());
        }
        root.put("prefabPath", draft.getPrefabPath());
        root.put("plotTokenItemId", AetherhavenConstants.PLOT_TOKEN_UNIFIED);
        root.put("plotAnchorOffset", List.of(draft.getPlotAnchorOffset()[0], draft.getPlotAnchorOffset()[1], draft.getPlotAnchorOffset()[2]));
        root.put("rotationYaw", draft.getRotationYaw() != null ? draft.getRotationYaw() : "None");
        root.put("selfBuildGameDays", draft.getSelfBuildGameDays());
        if (draft.getTreasuryGoldCoinCost() > 0L) {
            root.put("treasuryGoldCoinCost", draft.getTreasuryGoldCoinCost());
        }
        if (PlotBuildingKindRequirements.effectiveKind(draft, null) == PlotBuildingKind.HOME) {
            root.put("maxHomeResidents", draft.getMaxHomeResidents());
        }
        if (draft.getAssemblyPrefabSectionsPerAxis() > 1) {
            root.put("assemblyPrefabSectionsPerAxis", draft.getAssemblyPrefabSectionsPerAxis());
        }
        if (!draft.getMaterials().isEmpty()) {
            root.put("materials", materialMaps(draft.getMaterials()));
        }
        if (!draft.getPois().isEmpty()) {
            root.put("pois", draft.getPois());
        }
        if (draft.getManagementBlockLocalPos() != null && draft.getKind() != PlotBuildingKind.DECORATION) {
            root.put("managementBlockLocalPos", localPosList(draft.getManagementBlockLocalPos()));
        }
        if (draft.getTreasuryLocalPos() != null) {
            root.put("treasuryLocalPos", localPosList(draft.getTreasuryLocalPos()));
        }
        if (PlotBuildingKindRequirements.requiresShopSafe(draft, null)
            && draft.getShopSafeLocalPos() != null) {
            root.put("shopSafeLocalPos", localPosList(draft.getShopSafeLocalPos()));
        }
        if (draft.getProductionStorageLocalPos() != null) {
            root.put("productionStorageLocalPos", localPosList(draft.getProductionStorageLocalPos()));
        }
        if (draft.getInnkeeperSpawnLocal() != null) {
            root.put("innkeeperSpawnLocal", localPosList(draft.getInnkeeperSpawnLocal()));
        }
        if (!draft.getVisitorSpawnLocals().isEmpty()) {
            root.put("visitorSpawnLocals", spawnLocalsList(draft.getVisitorSpawnLocals()));
        }
        if (draft.getGuildMasterSpawnLocal() != null) {
            root.put("guildMasterSpawnLocal", localPosList(draft.getGuildMasterSpawnLocal()));
        }
        if (!draft.getAdventurerSpawns().isEmpty()) {
            List<List<Integer>> locals = new ArrayList<>();
            List<Double> yaws = new ArrayList<>();
            for (PlotCreatorAdventurerSpawnEntry entry : draft.getAdventurerSpawns()) {
                locals.add(localPosList(entry.localArray()));
                yaws.add((double) entry.getYawRadians());
            }
            root.put("adventurerSpawnLocals", locals);
            root.put("adventurerSpawnYaws", yaws);
        }
        if (draft.getCountsAsConstructionId() != null && !draft.getCountsAsConstructionId().isBlank()) {
            root.put("countsAsConstructionId", draft.getCountsAsConstructionId());
        }
        if (draft.getStyleId() != null && !draft.getStyleId().isBlank()) {
            root.put("styleId", draft.getStyleId());
        }
        if (!draft.getBuildingTags().isEmpty()) {
            root.put("tags", new ArrayList<>(draft.getBuildingTags()));
        }
        if (draft.isScheduleSharedUtilityPick()) {
            root.put("scheduleSharedUtilityPick", true);
        }
        if (draft.isTouristDestination()) {
            root.put("touristDestination", true);
        }
        if (draft.isPlotTokenLockedByDefault()) {
            root.put("plotTokenLockedByDefault", true);
            if (draft.isFloatingGiftBlueprint()) {
                root.put("floatingGiftBlueprint", true);
            }
        }
        if (draft.isExcludeFromTownJournal()) {
            root.put("excludeFromTownJournal", true);
        }
        if (draft.getKind() == PlotBuildingKind.DECORATION) {
            root.put("excludeFromTownJournal", true);
            root.put("decorationPlot", true);
        }
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nonnull
    private static List<Integer> localPosList(@Nonnull int[] pos) {
        return List.of(pos[0], pos[1], pos[2]);
    }

    @Nonnull
    private static List<List<Integer>> spawnLocalsList(@Nonnull List<int[]> locals) {
        List<List<Integer>> out = new ArrayList<>();
        for (int[] pos : locals) {
            out.add(localPosList(pos));
        }
        return out;
    }

    @Nonnull
    private static List<Map<String, Object>> materialMaps(@Nonnull List<MaterialRequirement> materials) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MaterialRequirement m : materials) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (m.getItemId() != null && !m.getItemId().isBlank()) {
                row.put("itemId", m.getItemId());
            }
            if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
                row.put("resourceTypeId", m.getResourceTypeId());
            }
            row.put("count", m.getCount());
            out.add(row);
        }
        return out;
    }

    @Nonnull
    public static Rotation parseRotationYaw(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Rotation.None;
        }
        try {
            return Rotation.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return Rotation.None;
        }
    }
}
