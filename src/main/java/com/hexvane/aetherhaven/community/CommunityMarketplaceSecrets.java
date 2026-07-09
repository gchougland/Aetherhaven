package com.hexvane.aetherhaven.community;

import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/**
 * Resolves the community marketplace API key from the server process environment — never from {@code config.json}.
 *
 * <p>Set on your Hytale dedicated server (not in the public git repo):
 * <ul>
 *   <li>{@code AETHERHAVEN_COMMUNITY_API_KEY} — the shared secret (must match Railway {@code API_KEY})</li>
 *   <li>{@code AETHERHAVEN_COMMUNITY_API_KEY_FILE} — optional path to a file containing the key (Docker/Railway secret file pattern)</li>
 * </ul>
 */
public final class CommunityMarketplaceSecrets {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Environment variable holding the marketplace API key for mod → backend uploads. */
    public static final String API_KEY_ENV = "AETHERHAVEN_COMMUNITY_API_KEY";

    /** Optional file path env var; file contents are trimmed and used if {@link #API_KEY_ENV} is unset. */
    public static final String API_KEY_FILE_ENV = "AETHERHAVEN_COMMUNITY_API_KEY_FILE";

    private CommunityMarketplaceSecrets() {}

    @Nonnull
    public static String resolveApiKey() {
        String fromEnv = System.getenv(API_KEY_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String filePath = System.getenv(API_KEY_FILE_ENV);
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            String contents = Files.readString(Path.of(filePath.trim()), StandardCharsets.UTF_8).trim();
            if (!contents.isBlank()) {
                return contents;
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read %s from %s", API_KEY_ENV, filePath);
        }
        return "";
    }

    public static boolean hasApiKey() {
        return !resolveApiKey().isBlank();
    }
}
