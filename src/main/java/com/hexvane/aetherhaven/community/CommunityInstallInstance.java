package com.hexvane.aetherhaven.community;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stable id for this save or dedicated server, used so marketplace download counts increment once per
 * instance rather than on every local remove-and-redownload.
 */
public final class CommunityInstallInstance {
    static final String FILE_NAME = "community_install_instance.json";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();

    private CommunityInstallInstance() {}

    @Nonnull
    public static Path instanceFile(@Nonnull Path dataDirectory) {
        return dataDirectory.resolve(FILE_NAME);
    }

    @Nullable
    public static String loadOrCreate(@Nonnull Path dataDirectory) {
        Path file = instanceFile(dataDirectory);
        synchronized (LOCK) {
            String existing = read(file);
            if (existing != null) {
                return existing;
            }
            String id = UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
            try {
                Files.createDirectories(file.getParent());
                StoredId stored = new StoredId();
                stored.id = id;
                Files.writeString(file, GSON.toJson(stored), StandardCharsets.UTF_8);
                return id;
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to write community install instance id");
                return read(file);
            }
        }
    }

    @Nullable
    private static String read(@Nonnull Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            StoredId stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredId.class);
            if (stored == null || stored.id == null || stored.id.isBlank()) {
                return null;
            }
            String id = stored.id.trim().toLowerCase(Locale.ROOT);
            return isUuid(id) ? id : null;
        } catch (IOException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read community install instance id");
            return null;
        }
    }

    private static boolean isUuid(@Nonnull String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final class StoredId {
        @SerializedName("id")
        private String id;
    }
}
