package com.hexvane.aetherhaven.guide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Loads and merges {@link AetherhavenAssetPaths#GUIDE_PATCHES} into hub → extra sub-topic ids. */
public final class GuidePatchApplier {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GuidePatchApplier() {}

    /**
     * @return map of target topic id → ordered extra sub-topic ids (later packs append; duplicates skipped)
     */
    @Nonnull
    public static Map<String, List<String>> loadMergedSubTopicExtras() {
        Gson gson = new GsonBuilder().create();
        Map<String, List<String>> extras = new LinkedHashMap<>();
        List<PackJsonFile> files =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.GUIDE_PATCHES);
        int applied = 0;
        for (PackJsonFile f : files) {
            try (InputStream in = Files.newInputStream(f.absolutePath())) {
                GuidePatchDefinition patch =
                    gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), GuidePatchDefinition.class);
                if (applyPatch(extras, patch, f.packName() + ":" + f.absolutePath())) {
                    applied++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load guide patch %s", f.absolutePath());
            }
        }
        if (applied > 0) {
            LOGGER.atInfo().log("Applied %s guide patch file(s) from asset packs", applied);
        }
        return extras;
    }

    /** Visible for tests. */
    public static boolean applyPatch(
        @Nonnull Map<String, List<String>> extrasByHub,
        @Nonnull GuidePatchDefinition patch,
        @Nonnull String label
    ) {
        if (patch.getTargetTopicId() == null || patch.getTargetTopicId().isBlank()) {
            LOGGER.atWarning().log("Skipping guide patch with missing targetTopicId: %s", label);
            return false;
        }
        if (patch.schemaVersionOrDefault() != GuidePatchDefinition.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atWarning().log(
                "Guide patch %s schemaVersion %s (expected %s)",
                label,
                patch.schemaVersionOrDefault(),
                GuidePatchDefinition.SUPPORTED_SCHEMA_VERSION
            );
        }
        String hub = patch.getTargetTopicId().trim();
        List<String> add = patch.addSubTopicsOrEmpty();
        if (add.isEmpty()) {
            LOGGER.atWarning().log("Guide patch %s has no addSubTopics", label);
            return false;
        }
        List<String> list = extrasByHub.computeIfAbsent(hub, k -> new ArrayList<>());
        int added = 0;
        for (String id : add) {
            if (!list.contains(id)) {
                list.add(id);
                added++;
            }
        }
        LOGGER.atInfo().log("Applied guide patch %s to hub %s (+%s sub-topics)", label, hub, added);
        return true;
    }

    @Nonnull
    public static List<String> mergeSubTopics(
        @Nonnull List<String> baseSubTopics,
        @Nonnull Map<String, List<String>> extrasByHub,
        @Nonnull String topicId
    ) {
        List<String> extras = extrasByHub.get(topicId.trim());
        if (extras == null || extras.isEmpty()) {
            return baseSubTopics;
        }
        List<String> out = new ArrayList<>(baseSubTopics);
        for (String id : extras) {
            if (id != null && !id.isBlank() && !out.contains(id.trim())) {
                out.add(id.trim());
            }
        }
        return List.copyOf(out);
    }
}
