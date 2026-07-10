package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
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

            byte[] body = buildMultipart(buildingBytes, prefabBytes, iconBytes);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Player-Uuid", playerUuid.toString());
            headers.put("X-Player-Name", playerName);

            String url = cfg.getApiBaseUrl() + "/api/v1/submissions";
            String response = CommunityHttpClient.postMultipart(url, headers, BOUNDARY, body);
            if (response == null) {
                return "upload_failed";
            }
            LOGGER.atInfo().log("Community submission uploaded for %s by %s", constructionId, playerUuid);
            return null;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Community submission failed for %s", constructionId);
            return "io_error";
        }
    }

    /** Sends chat feedback for a submission result ({@code err == null} is success). */
    public static void notifyPlayer(@Nonnull PlayerRef playerRef, @Nullable String err) {
        if (err == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.submittedCommunity"));
            return;
        }
        if ("disabled".equals(err)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.communityDisabled"));
            return;
        }
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.communitySubmit")
                .param("reason", Message.raw(err))
        );
    }

    @Nullable
    private static Path resolveBuildingFile(@Nonnull Path dataDir, @Nonnull String constructionId) {
        Path community = CommunityPaths.buildingFile(dataDir, constructionId);
        if (Files.isRegularFile(community)) {
            return community;
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
        String crlf = "\r\n";
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append(crlf);
        head.append("Content-Disposition: form-data; name=\"building\"; filename=\"building.json\"").append(crlf);
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

        int total = headBytes.length + building.length + midBytes.length + prefab.length + iconHead.length;
        if (icon != null) {
            total += icon.length;
        }
        total += endBytes.length;

        byte[] out = new byte[total];
        int pos = 0;
        pos = copy(headBytes, out, pos);
        pos = copy(building, out, pos);
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
