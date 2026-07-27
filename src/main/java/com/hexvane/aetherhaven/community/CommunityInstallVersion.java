package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tracks which marketplace version of a community building is installed locally. */
public final class CommunityInstallVersion {
    private static final Gson GSON = new Gson();

    private CommunityInstallVersion() {}

    public static int parseVersionNumber(@Nullable String version) {
        if (version == null || version.isBlank()) {
            return 0;
        }
        try {
            int n = Integer.parseInt(version.trim());
            return n >= 0 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nonnull
    public static String readInstalledVersion(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        Path metaFile = metaFile(dataDirectory, constructionId);
        if (!Files.isRegularFile(metaFile)) {
            return "1";
        }
        try {
            Meta meta = GSON.fromJson(Files.readString(metaFile, StandardCharsets.UTF_8), Meta.class);
            if (meta == null || meta.version == null || meta.version.isBlank()) {
                return "1";
            }
            return meta.version.trim();
        } catch (IOException | RuntimeException e) {
            return "1";
        }
    }

    public static void writeInstalledVersion(
        @Nonnull Path dataDirectory,
        @Nonnull String constructionId,
        @Nonnull String version
    ) throws IOException {
        Path metaFile = metaFile(dataDirectory, constructionId);
        Files.createDirectories(metaFile.getParent());
        Meta meta = new Meta();
        meta.version = version != null && !version.isBlank() ? version.trim() : "1";
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    public static void deleteInstalledVersion(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        try {
            Files.deleteIfExists(metaFile(dataDirectory, constructionId));
        } catch (IOException ignored) {
        }
    }

    public static boolean hasUpdate(@Nonnull Path dataDirectory, @Nonnull CommunityManifestEntry entry) {
        if (!CommunityPaths.isInstalled(dataDirectory, entry.getId())) {
            return false;
        }
        int installed = parseVersionNumber(readInstalledVersion(dataDirectory, entry.getId()));
        int remote = parseVersionNumber(entry.getVersion());
        return remote > installed;
    }

    @Nonnull
    private static Path metaFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return CommunityPaths.communityRoot(dataDirectory)
            .resolve(".install-meta")
            .resolve(constructionId.trim() + ".json");
    }

    private static final class Meta {
        @SerializedName("version")
        private String version;
    }
}
