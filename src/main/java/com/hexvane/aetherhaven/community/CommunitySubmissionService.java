package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropPaths;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Uploads plot creator saves to the community marketplace API. */
public final class CommunitySubmissionService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final String BOUNDARY = "----AetherhavenCommunityBoundary";

    private CommunitySubmissionService() {}

    @Nullable
    public static String submitSavedBuilding(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String constructionId
    ) {
        return uploadSavedBuilding(plugin, playerUuid, playerName, constructionId, false);
    }

    @Nullable
    public static String submitSavedProp(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String propId
    ) {
        return uploadSavedProp(plugin, playerUuid, playerName, propId);
    }

    @Nullable
    public static String updateSavedBuilding(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String constructionId,
        @Nonnull UpdateOutcome outcome
    ) {
        String error = uploadSavedBuilding(plugin, playerUuid, playerName, constructionId, true, outcome);
        return error;
    }

    public static final class UpdateOutcome {
        private boolean waitingForReview;

        public boolean isWaitingForReview() {
            return waitingForReview;
        }
    }

    @Nullable
    private static String uploadSavedBuilding(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String constructionId,
        boolean update
    ) {
        return uploadSavedBuilding(plugin, playerUuid, playerName, constructionId, update, null);
    }

    @Nullable
    private static String uploadSavedBuilding(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String constructionId,
        boolean update,
        @Nullable UpdateOutcome outcome
    ) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled()) {
            return "disabled";
        }
        Path dataDir = plugin.getDataDirectory();
        Path buildingFile = resolveBuildingFile(dataDir, constructionId);
        if (buildingFile == null) {
            return "building_missing";
        }
        Path prefabFile = resolvePrefabFile(dataDir, constructionId, buildingFile);
        if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
            return "prefab_missing";
        }
        Path iconFile = CustomBuildingsPaths.iconFile(dataDir, constructionId);
        if (!Files.isRegularFile(iconFile)) {
            iconFile = CommunityPaths.iconFile(dataDir, constructionId);
        }

        try {
            byte[] buildingBytes = Files.readAllBytes(buildingFile);
            byte[] prefabBytes = Files.readAllBytes(prefabFile);
            byte[] iconBytes = Files.isRegularFile(iconFile) ? Files.readAllBytes(iconFile) : null;
            try {
                buildingBytes = CommunityRequiredMods.injectIntoBuildingJson(buildingBytes, prefabBytes);
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Community submission rejected for %s: %s", constructionId, e.getMessage());
                return "unsafe_prefab: " + e.getMessage();
            }

            byte[] body = buildMultipart(buildingBytes, prefabBytes, iconBytes);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(java.util.Locale.ROOT));
            headers.put("X-Player-Name", playerName);

            String url =
                cfg.getApiBaseUrl()
                    + (update ? "/api/v1/submissions/" + constructionId.trim() : "/api/v1/submissions");
            CommunityHttpClient.HttpResult result =
                update
                    ? CommunityHttpClient.putMultipartResult(url, headers, BOUNDARY, body)
                    : CommunityHttpClient.postMultipartResult(url, headers, BOUNDARY, body);
            if (!result.isSuccess()) {
                return mapUploadError(result);
            }
            String response = result.getBody();
            if (response == null) {
                return "upload_failed";
            }
            if (update && outcome != null) {
                outcome.waitingForReview = parseWaitingForReview(response);
            }
            LOGGER.atInfo().log(
                "Community submission %s for %s by %s",
                update ? "updated" : "uploaded",
                constructionId,
                playerUuid
            );
            return null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Community submission failed for %s", constructionId);
            return "io_error";
        }
    }

    @Nullable
    private static String uploadSavedProp(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String propId
    ) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (!cfg.isEnabled()) {
            return "disabled";
        }
        PropCatalog catalog = plugin.getPropCatalog();
        PropDefinition def = catalog.get(propId);
        if (def == null) {
            return "building_missing";
        }
        Path dataDir = plugin.getDataDirectory();
        Path propFile = PropPaths.propFileUnderDataDir(dataDir, propId);
        if (!Files.isRegularFile(propFile)) {
            // Fall back to serializing the in-memory definition.
            try {
                Files.createDirectories(propFile.getParent());
                Files.writeString(propFile, GSON.toJson(def), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "building_missing";
            }
        }
        Path prefabFile = CustomBuildingsPaths.resolvePrefabFile(dataDir, def.getPrefabPath());
        if (prefabFile == null || !Files.isRegularFile(prefabFile)) {
            return "prefab_missing";
        }
        Path iconFile = PropPaths.iconFile(dataDir, propId);
        if (!Files.isRegularFile(iconFile)) {
            iconFile = CommunityPaths.iconFile(dataDir, propId);
        }
        try {
            byte[] propBytes = Files.readAllBytes(propFile);
            byte[] prefabBytes = Files.readAllBytes(prefabFile);
            byte[] iconBytes = Files.isRegularFile(iconFile) ? Files.readAllBytes(iconFile) : null;
            byte[] body = buildPropMultipart(propBytes, prefabBytes, iconBytes);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(java.util.Locale.ROOT));
            headers.put("X-Player-Name", playerName);
            headers.put("X-Content-Type", "prop");
            String url = cfg.getApiBaseUrl() + "/api/v1/submissions";
            CommunityHttpClient.HttpResult result = CommunityHttpClient.postMultipartResult(url, headers, BOUNDARY, body);
            if (!result.isSuccess()) {
                return mapUploadError(result);
            }
            LOGGER.atInfo().log("Community prop submission uploaded for %s by %s", propId, playerUuid);
            return null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Community prop submission failed for %s", propId);
            return "io_error";
        }
    }

    /** Sends chat feedback for a submission result ({@code err == null} is success). */
    public static void notifyPlayer(@Nonnull PlayerRef playerRef, @Nullable String err) {
        notifyPlayer(playerRef, err, false, false);
    }

    /** Sends chat feedback for a building editor community update. */
    public static void notifyUpdatePlayer(
        @Nonnull PlayerRef playerRef,
        @Nullable String err,
        boolean waitingForReview
    ) {
        notifyPlayer(playerRef, err, true, waitingForReview);
    }

    private static void notifyPlayer(
        @Nonnull PlayerRef playerRef,
        @Nullable String err,
        boolean update,
        boolean waitingForReview
    ) {
        if (err == null) {
            if (update) {
                playerRef.sendMessage(
                    Message.translation(
                        waitingForReview
                            ? "aetherhaven_building_editor.aetherhaven.buildingeditor.success.submittedForReview"
                            : "aetherhaven_building_editor.aetherhaven.buildingeditor.success.updatedSubmission"
                    )
                );
            } else {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.submittedCommunity")
                );
            }
            return;
        }
        if ("disabled".equals(err)) {
            playerRef.sendMessage(
                Message.translation(
                    update
                        ? "aetherhaven_building_editor.aetherhaven.buildingeditor.error.communityDisabled"
                        : "aetherhaven_plot_creator.aetherhaven.plotcreator.error.communityDisabled"
                )
            );
            return;
        }
        if (isRateLimitedError(err)) {
            playerRef.sendMessage(
                Message.translation(
                    update
                        ? "aetherhaven_building_editor.aetherhaven.buildingeditor.error.communityRateLimited"
                        : "aetherhaven_plot_creator.aetherhaven.plotcreator.error.communityRateLimited"
                )
            );
            return;
        }
        playerRef.sendMessage(
            Message.translation(
                    update
                        ? "aetherhaven_building_editor.aetherhaven.buildingeditor.error.communityUpdate"
                        : "aetherhaven_plot_creator.aetherhaven.plotcreator.error.communitySubmit"
                )
                .param("reason", Message.raw(err))
        );
    }

    private static boolean isRateLimitedError(@Nonnull String err) {
        return "rate_limited".equals(err) || "rate_limited_player".equals(err) || "rate_limited_ip".equals(err);
    }

    @Nullable
    private static String mapUploadError(@Nonnull CommunityHttpClient.HttpResult result) {
        String apiError = CommunityHttpClient.parseErrorKey(result.getBody());
        if (apiError != null) {
            return apiError;
        }
        if (result.getStatusCode() == 429) {
            return "rate_limited";
        }
        return "upload_failed";
    }

    private static boolean parseWaitingForReview(@Nonnull String responseJson) {
        try {
            JsonObject root = GSON.fromJson(responseJson, JsonObject.class);
            if (root == null || !root.has("action") || root.get("action").isJsonNull()) {
                return false;
            }
            return "created_pending".equalsIgnoreCase(root.get("action").getAsString());
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static Path resolveBuildingFile(@Nonnull Path dataDir, @Nonnull String constructionId) {
        Path community = CommunityPaths.buildingFile(dataDir, constructionId);
        if (Files.isRegularFile(community)) {
            return community;
        }
        Path festival = CustomFestivalPaths.festivalFile(dataDir, constructionId);
        if (Files.isRegularFile(festival)) {
            return festival;
        }
        Path custom = CustomBuildingsPaths.buildingFile(dataDir, constructionId);
        return Files.isRegularFile(custom) ? custom : null;
    }

    @Nullable
    private static Path resolvePrefabFile(
        @Nonnull Path dataDir,
        @Nonnull String constructionId,
        @Nonnull Path buildingFile
    ) {
        Path prefab = CustomBuildingsPaths.resolvePrefabFile(dataDir, constructionId + ".prefab.json");
        if (prefab != null) {
            return prefab;
        }
        prefab = CustomFestivalPaths.resolvePrefabFile(dataDir, CustomFestivalPaths.prefabPathKey(constructionId));
        if (prefab != null && Files.isRegularFile(prefab)) {
            return prefab;
        }
        prefab = CustomFestivalPaths.prefabFile(dataDir, constructionId);
        if (Files.isRegularFile(prefab)) {
            return prefab;
        }
        prefab = CommunityPaths.installedPrefabFile(dataDir, constructionId);
        if (Files.isRegularFile(prefab)) {
            return prefab;
        }
        String prefabPathKey = readPrefabPathFromBuildingJson(buildingFile);
        if (prefabPathKey != null) {
            return CustomBuildingsPaths.resolvePrefabFile(dataDir, prefabPathKey);
        }
        return null;
    }

    @Nullable
    private static String readPrefabPathFromBuildingJson(@Nonnull Path buildingFile) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(buildingFile), JsonObject.class);
            if (root == null || !root.has("prefabPath") || root.get("prefabPath").isJsonNull()) {
                return null;
            }
            String prefabPath = root.get("prefabPath").getAsString();
            return prefabPath != null && !prefabPath.isBlank() ? prefabPath.trim() : null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read prefabPath from %s", buildingFile);
            return null;
        }
    }

    @Nonnull
    private static byte[] buildMultipart(@Nonnull byte[] building, @Nonnull byte[] prefab, @Nullable byte[] icon)
        throws IOException {
        return buildNamedMultipart("building", "building.json", building, prefab, icon);
    }

    @Nonnull
    private static byte[] buildPropMultipart(@Nonnull byte[] prop, @Nonnull byte[] prefab, @Nullable byte[] icon)
        throws IOException {
        return buildNamedMultipart("prop", "prop.json", prop, prefab, icon);
    }

    @Nonnull
    private static byte[] buildNamedMultipart(
        @Nonnull String defFieldName,
        @Nonnull String defFileName,
        @Nonnull byte[] definition,
        @Nonnull byte[] prefab,
        @Nullable byte[] icon
    ) throws IOException {
        String crlf = "\r\n";
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append(crlf);
        head.append("Content-Disposition: form-data; name=\"")
            .append(defFieldName)
            .append("\"; filename=\"")
            .append(defFileName)
            .append("\"")
            .append(crlf);
        head.append("Content-Type: application/json").append(crlf).append(crlf);
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);

        StringBuilder mid = new StringBuilder();
        mid.append(crlf).append("--").append(BOUNDARY).append(crlf);
        mid.append("Content-Disposition: form-data; name=\"prefab\"; filename=\"prefab.prefab.json\"").append(crlf);
        mid.append("Content-Type: application/json").append(crlf).append(crlf);
        byte[] midBytes = mid.toString().getBytes(StandardCharsets.UTF_8);

        StringBuilder tail = new StringBuilder();
        tail.append(crlf).append("--").append(BOUNDARY);
        if (icon != null) {
            tail.append(crlf);
            tail.append("Content-Disposition: form-data; name=\"icon\"; filename=\"icon.png\"").append(crlf);
            tail.append("Content-Type: image/png").append(crlf).append(crlf);
        }
        byte[] iconHead = icon != null ? tail.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];

        String end = (icon != null ? crlf : "") + "--" + BOUNDARY + "--" + crlf;
        byte[] endBytes = end.getBytes(StandardCharsets.UTF_8);

        int total = headBytes.length + definition.length + midBytes.length + prefab.length + iconHead.length;
        if (icon != null) {
            total += icon.length;
        }
        total += endBytes.length;

        byte[] out = new byte[total];
        int pos = 0;
        pos = copy(headBytes, out, pos);
        pos = copy(definition, out, pos);
        pos = copy(midBytes, out, pos);
        pos = copy(prefab, out, pos);
        if (icon != null) {
            pos = copy(iconHead, out, pos);
            pos = copy(icon, out, pos);
        }
        copy(endBytes, out, pos);
        return out;
    }

    private static int copy(@Nonnull byte[] src, @Nonnull byte[] dest, int offset) {
        System.arraycopy(src, 0, dest, offset, src.length);
        return offset + src.length;
    }
}
