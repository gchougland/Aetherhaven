package com.hexvane.aetherhaven.support;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityHttpClient;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.config.SupportUploadConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Uploads support debug bundles to the community marketplace API. */
public final class SupportUploadService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final String BOUNDARY = "----AetherhavenSupportBundleBoundary";
    private static final long LOCAL_COOLDOWN_MS = 2L * 60L * 1000L;

    private static final ConcurrentHashMap<UUID, Long> LAST_UPLOAD_BY_PLAYER = new ConcurrentHashMap<>();

    private SupportUploadService() {}

    public static boolean beginUploadIfAllowed(@Nonnull UUID playerUuid) {
        long now = System.currentTimeMillis();
        Long last = LAST_UPLOAD_BY_PLAYER.get(playerUuid);
        return last == null || now - last >= LOCAL_COOLDOWN_MS;
    }

    public static void scheduleUpload(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull PlayerRef playerRef,
        @Nullable String note
    ) {
        UUID playerUuid = playerRef.getUuid();
        LAST_UPLOAD_BY_PLAYER.put(playerUuid, System.currentTimeMillis());

        SupportUploadConfig cfg = plugin.getConfig().get().getSupportUpload();
        CommunityMarketplaceConfig marketplace = plugin.getConfig().get().getCommunityMarketplace();
        String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";

        plugin
            .getConstructionScheduler()
            .execute(
                () -> {
                    SupportBundleCollector.Result collected =
                        SupportBundleCollector.collect(
                            plugin,
                            playerUuid,
                            playerName,
                            note,
                            cfg.getMaxBundleBytes()
                        );
                    UploadOutcome outcome = uploadCollected(plugin, playerUuid, playerName, note, marketplace, collected);
                    world.execute(
                        () -> notifyPlayer(playerRef, outcome)
                    );
                }
            );
    }

    @Nonnull
    private static UploadOutcome uploadCollected(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nullable String note,
        @Nonnull CommunityMarketplaceConfig marketplace,
        @Nonnull SupportBundleCollector.Result collected
    ) {
        if (collected.errorKey != null) {
            return new UploadOutcome(collected.errorKey, null);
        }
        if (collected.zipBytes == null) {
            return new UploadOutcome("empty_bundle", null);
        }

        JsonObject meta = new JsonObject();
        meta.addProperty("note", note != null ? note : "");
        meta.addProperty("modVersion", plugin.getManifest().getVersion().toString());
        if (collected.serverUuid != null && !collected.serverUuid.isBlank()) {
            meta.addProperty("serverUuid", collected.serverUuid);
        }
        if (collected.worldNames != null && !collected.worldNames.isEmpty()) {
            meta.add("worldNames", GSON.toJsonTree(collected.worldNames));
        }
        byte[] metaBytes = GSON.toJson(meta).getBytes(StandardCharsets.UTF_8);

        try {
            byte[] body = buildMultipart(collected.zipBytes, metaBytes);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Player-Uuid", playerUuid.toString().trim().toLowerCase(Locale.ROOT));
            headers.put("X-Player-Name", playerName);
            if (note != null && !note.isBlank()) {
                headers.put("X-Support-Note", note.trim());
            }

            String url = marketplace.getApiBaseUrl() + "/api/v1/support-bundles";
            String response = CommunityHttpClient.postMultipart(url, headers, BOUNDARY, body);
            if (response == null) {
                return new UploadOutcome("upload_failed", null);
            }
            String bundleId = parseBundleId(response);
            if (bundleId == null || bundleId.isBlank()) {
                return new UploadOutcome("upload_failed", null);
            }
            LOGGER.atInfo().log("Support bundle uploaded by %s as %s", playerUuid, bundleId);
            return new UploadOutcome(null, bundleId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Support bundle upload failed for %s", playerUuid);
            return new UploadOutcome("upload_failed", null);
        }
    }

    @Nullable
    private static String parseBundleId(@Nonnull String responseJson) {
        try {
            JsonObject root = GSON.fromJson(responseJson, JsonObject.class);
            if (root == null) {
                return null;
            }
            if (root.has("bundleId") && !root.get("bundleId").isJsonNull()) {
                return root.get("bundleId").getAsString();
            }
            if (root.has("id") && !root.get("id").isJsonNull()) {
                return root.get("id").getAsString();
            }
            if (root.has("error") && !root.get("error").isJsonNull()) {
                return null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nonnull
    private static byte[] buildMultipart(@Nonnull byte[] zipBytes, @Nonnull byte[] metaBytes) {
        String crlf = "\r\n";
        StringBuilder zipHead = new StringBuilder();
        zipHead.append("--").append(BOUNDARY).append(crlf);
        zipHead.append("Content-Disposition: form-data; name=\"bundle\"; filename=\"support-bundle.zip\"").append(crlf);
        zipHead.append("Content-Type: application/zip").append(crlf).append(crlf);
        byte[] zipHeadBytes = zipHead.toString().getBytes(StandardCharsets.UTF_8);

        StringBuilder metaHead = new StringBuilder();
        metaHead.append(crlf).append("--").append(BOUNDARY).append(crlf);
        metaHead.append("Content-Disposition: form-data; name=\"meta\"").append(crlf);
        metaHead.append("Content-Type: application/json").append(crlf).append(crlf);
        byte[] metaHeadBytes = metaHead.toString().getBytes(StandardCharsets.UTF_8);

        String end = crlf + "--" + BOUNDARY + "--" + crlf;
        byte[] endBytes = end.getBytes(StandardCharsets.UTF_8);

        byte[] out = new byte[zipHeadBytes.length + zipBytes.length + metaHeadBytes.length + metaBytes.length + endBytes.length];
        int pos = 0;
        pos = copy(zipHeadBytes, out, pos);
        pos = copy(zipBytes, out, pos);
        pos = copy(metaHeadBytes, out, pos);
        pos = copy(metaBytes, out, pos);
        copy(endBytes, out, pos);
        return out;
    }

    private static int copy(@Nonnull byte[] src, @Nonnull byte[] dest, int offset) {
        System.arraycopy(src, 0, dest, offset, src.length);
        return offset + src.length;
    }

    public static void notifyPlayer(@Nonnull PlayerRef playerRef, @Nonnull UploadOutcome outcome) {
        if (outcome.errorKey == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_support.aetherhaven.support.upload.success").param("id", outcome.bundleId != null ? outcome.bundleId : "?")
            );
            return;
        }
        switch (outcome.errorKey) {
            case "disabled" ->
                playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.disabled"));
            case "too_large" ->
                playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.tooLarge"));
            case "rate_limited", "rate_limited_player", "rate_limited_ip" ->
                playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.rateLimited"));
            case "empty_bundle" ->
                playerRef.sendMessage(Message.translation("aetherhaven_support.aetherhaven.support.upload.empty"));
            default ->
                playerRef.sendMessage(
                    Message.translation("aetherhaven_support.aetherhaven.support.upload.failed").param("reason", Message.raw(outcome.errorKey))
                );
        }
    }

    public static final class UploadOutcome {
        @Nullable
        public final String errorKey;
        @Nullable
        public final String bundleId;

        public UploadOutcome(@Nullable String errorKey, @Nullable String bundleId) {
            this.errorKey = errorKey;
            this.bundleId = bundleId;
        }
    }
}
