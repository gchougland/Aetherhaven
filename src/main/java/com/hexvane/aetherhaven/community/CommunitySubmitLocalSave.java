package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsWriter;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorDraft;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorService;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Remaps plot creator saves to the community catalog id before local write and upload. */
public final class CommunitySubmitLocalSave {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private CommunitySubmitLocalSave() {}

    /**
     * Updates {@code draft} and on-disk prefab/icon/materials to the assigned community id.
     *
     * @return {@code null} on success, or a plot creator error key such as {@code id_taken}
     */
    @Nullable
    public static String prepareDraftForCommunitySubmit(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull UUID playerUuid
    ) throws IOException {
        String localId = draft.getConstructionId() != null ? draft.getConstructionId().trim() : "";
        if (localId.isEmpty()) {
            return "incomplete";
        }
        String communityId =
            CommunityBuildingValidator.assignCatalogId(localId, draft.getDisplayName(), playerUuid);

        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        String editingId = draft.getEditingConstructionId();
        if (catalog.get(communityId) != null && !communityId.equals(editingId)) {
            return "id_taken";
        }

        if (communityId.equals(localId)) {
            PlotCreatorService.syncPrefabFileNameFromConstructionId(draft);
            draft.setPrefabPath(CommunityBuildingValidator.prefabPathKeyForCommunityId(communityId));
            return null;
        }

        Path dataDir = plugin.getDataDirectory();
        movePrefabIfPresent(dataDir, draft.getPrefabPath(), communityId);
        moveIconIfPresent(plugin, dataDir, localId, communityId);
        movePrefabMaterialsIfPresent(dataDir, localId, communityId);
        deleteBuildingJsonIfPresent(dataDir, localId);

        draft.setConstructionId(communityId);
        PlotCreatorService.syncPrefabFileNameFromConstructionId(draft);
        draft.setPrefabPath(CommunityBuildingValidator.prefabPathKeyForCommunityId(communityId));
        return null;
    }

    private static void movePrefabIfPresent(
        @Nonnull Path dataDir,
        @Nullable String oldPrefabPathKey,
        @Nonnull String communityId
    ) throws IOException {
        String newKey = CommunityBuildingValidator.prefabPathKeyForCommunityId(communityId);
        Path oldFile = CustomBuildingsPaths.resolvePrefabFile(dataDir, oldPrefabPathKey);
        Path newFile = CustomBuildingsPaths.prefabsDirectory(dataDir).resolve(newKey);
        if (oldFile == null || !Files.isRegularFile(oldFile) || oldFile.equals(newFile)) {
            return;
        }
        Files.createDirectories(newFile.getParent());
        Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void moveIconIfPresent(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Path dataDir,
        @Nonnull String oldConstructionId,
        @Nonnull String communityId
    ) throws IOException {
        Path oldIcon = CustomBuildingsPaths.iconFile(dataDir, oldConstructionId);
        Path newIcon = CustomBuildingsPaths.iconFile(dataDir, communityId);
        if (!Files.isRegularFile(oldIcon) || oldIcon.equals(newIcon)) {
            return;
        }
        Files.createDirectories(newIcon.getParent());
        Files.move(oldIcon, newIcon, StandardCopyOption.REPLACE_EXISTING);
        CustomBuildingIconAssetRegistry.unregisterIconForConstruction(plugin, oldConstructionId);
    }

    private static void movePrefabMaterialsIfPresent(
        @Nonnull Path dataDir,
        @Nonnull String oldConstructionId,
        @Nonnull String communityId
    ) throws IOException {
        Path oldFile = PrefabMaterialsWriter.outputFile(dataDir, oldConstructionId);
        Path newFile = PrefabMaterialsWriter.outputFile(dataDir, communityId);
        if (!Files.isRegularFile(oldFile) || oldFile.equals(newFile)) {
            return;
        }
        Files.createDirectories(newFile.getParent());
        Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteBuildingJsonIfPresent(@Nonnull Path dataDir, @Nonnull String oldConstructionId) {
        Path oldBuilding = CustomBuildingsPaths.buildingFile(dataDir, oldConstructionId);
        try {
            Files.deleteIfExists(oldBuilding);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove old building file %s during community remap", oldBuilding);
        }
    }
}
