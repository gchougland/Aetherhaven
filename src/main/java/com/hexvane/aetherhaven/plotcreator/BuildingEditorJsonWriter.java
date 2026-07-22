package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Writes building JSON for the building editor by merging wizard fields onto a snapshot of the original file so
 * identity tokens and extra authoring fields are preserved.
 */
public final class BuildingEditorJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();

    private BuildingEditorJsonWriter() {}

    @Nonnull
    public static Map<String, Object> loadSnapshot(@Nullable Path existingFile) {
        if (existingFile == null || !Files.isRegularFile(existingFile)) {
            return new LinkedHashMap<>();
        }
        try {
            String raw = Files.readString(existingFile, StandardCharsets.UTF_8);
            Map<String, Object> parsed = GSON.fromJson(raw, MAP_TYPE);
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static void writeMerged(
        @Nonnull Path outputFile,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Map<String, Object> originalSnapshot
    ) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>(originalSnapshot);
        root.put("id", draft.getConstructionId());
        root.put("displayName", draft.getDisplayName());
        String description = draft.getDescription();
        if (description != null && !description.isBlank()) {
            root.put("description", description.trim());
        } else {
            root.remove("description");
        }
        root.put("prefabPath", draft.getPrefabPath());
        root.put(
            "plotAnchorOffset",
            List.of(draft.getPlotAnchorOffset()[0], draft.getPlotAnchorOffset()[1], draft.getPlotAnchorOffset()[2])
        );
        root.put("rotationYaw", draft.getRotationYaw() != null ? draft.getRotationYaw() : "None");
        root.put("selfBuildGameDays", draft.getSelfBuildGameDays());
        if (draft.getTreasuryGoldCoinCost() > 0L) {
            root.put("treasuryGoldCoinCost", draft.getTreasuryGoldCoinCost());
        } else {
            root.remove("treasuryGoldCoinCost");
        }
        if (PlotBuildingKindRequirements.effectiveKinds(draft, null).contains(PlotBuildingKind.HOME)) {
            root.put("maxHomeResidents", draft.getMaxHomeResidents());
        }
        if (!draft.getMaterials().isEmpty()) {
            root.put("materials", materialMaps(draft.getMaterials()));
        } else {
            root.remove("materials");
        }
        if (!draft.getPois().isEmpty()) {
            root.put("pois", draft.getPois());
        } else {
            root.remove("pois");
        }
        putLocalOrRemove(root, "managementBlockLocalPos", draft.getManagementBlockLocalPos());
        putLocalOrRemove(root, "treasuryLocalPos", draft.getTreasuryLocalPos());
        putLocalOrRemove(root, "shopSafeLocalPos", draft.getShopSafeLocalPos());
        putLocalOrRemove(root, "productionStorageLocalPos", draft.getProductionStorageLocalPos());
        putLocalOrRemove(root, "innBellLocalPos", draft.getInnBellLocalPos());
        putLocalOrRemove(root, "innkeeperSpawnLocal", draft.getInnkeeperSpawnLocal());
        putLocalOrRemove(root, "guildMasterSpawnLocal", draft.getGuildMasterSpawnLocal());
        if (!draft.getVisitorSpawnLocals().isEmpty()) {
            root.put("visitorSpawnLocals", spawnLocalsList(draft.getVisitorSpawnLocals()));
        } else {
            root.remove("visitorSpawnLocals");
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
        } else {
            root.remove("adventurerSpawnLocals");
            root.remove("adventurerSpawnYaws");
        }
        if (draft.getCountsAsConstructionIds().isEmpty()) {
            root.remove("countsAsConstructionId");
        } else if (draft.getCountsAsConstructionIds().size() == 1) {
            root.put("countsAsConstructionId", draft.getCountsAsConstructionIds().get(0));
        } else {
            root.put("countsAsConstructionId", new ArrayList<>(draft.getCountsAsConstructionIds()));
        }
        if (draft.getStyleId() != null && !draft.getStyleId().isBlank()) {
            root.put("styleId", draft.getStyleId());
        }
        if (!draft.getBuildingTags().isEmpty()) {
            root.put("tags", new ArrayList<>(draft.getBuildingTags()));
        } else {
            root.remove("tags");
        }
        putFlag(root, "scheduleSharedUtilityPick", draft.isScheduleSharedUtilityPick());
        putFlag(root, "touristDestination", draft.isTouristDestination());
        putFlag(root, "plotTokenLockedByDefault", draft.isPlotTokenLockedByDefault());
        putFlag(root, "floatingGiftBlueprint", draft.isFloatingGiftBlueprint());
        putFlag(root, "excludeFromTownJournal", draft.isExcludeFromTownJournal());
        putFlag(root, "decorationPlot", draft.isDecorationOnly());
        if (draft.isDecorationOnly()) {
            root.put("excludeFromTownJournal", true);
        }
        root.remove("assemblyPrefabSectionsPerAxis");
        // Never rewrite the original token id if the snapshot had one.
        if (!originalSnapshot.containsKey("plotTokenItemId")) {
            // leave unset; shipped defs always have one when present in snapshot
        }
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void putFlag(@Nonnull Map<String, Object> root, @Nonnull String key, boolean value) {
        if (value) {
            root.put(key, true);
        } else {
            root.remove(key);
        }
    }

    private static void putLocalOrRemove(
        @Nonnull Map<String, Object> root,
        @Nonnull String key,
        @Nullable int[] pos
    ) {
        if (pos != null && pos.length >= 3) {
            root.put(key, localPosList(pos));
        } else {
            root.remove(key);
        }
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
}
