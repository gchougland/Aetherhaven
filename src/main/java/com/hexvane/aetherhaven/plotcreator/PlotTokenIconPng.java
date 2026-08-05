package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Shared PNG validation and atomic writes for plot-token / community building icons.
 * Empty or corrupt PNGs crash the client when registered as common assets.
 */
public final class PlotTokenIconPng {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Minimum size that can be a real PNG (signature + IHDR + IDAT + IEND). */
    public static final int MIN_PNG_BYTES = 100;

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private PlotTokenIconPng() {}

    public static boolean isValid(@Nonnull byte[] png) {
        if (png.length < MIN_PNG_BYTES) {
            return false;
        }
        return png[0] == (byte) 0x89
            && png[1] == 0x50
            && png[2] == 0x4E
            && png[3] == 0x47;
    }

    /** True when {@code iconFile} exists and contains a valid PNG payload. */
    public static boolean isValidFile(@Nonnull Path iconFile) {
        if (!Files.isRegularFile(iconFile)) {
            return false;
        }
        try {
            return isValid(Files.readAllBytes(iconFile));
        } catch (IOException e) {
            return false;
        }
    }

    @Nonnull
    public static Object lockFor(@Nonnull String constructionId) {
        return LOCKS.computeIfAbsent(constructionId.trim(), id -> new Object());
    }

    /**
     * Writes PNG bytes via a sibling temp file then replaces the destination, so concurrent
     * readers never observe a truncated empty file (Hytale {@code FileCommonAsset} re-reads on send).
     */
    public static void writeAtomically(@Nonnull Path iconFile, @Nonnull byte[] png) throws IOException {
        if (!isValid(png)) {
            throw new IOException("Refusing to write invalid PNG to " + iconFile);
        }
        Path parent = iconFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = iconFile.resolveSibling(iconFile.getFileName().toString() + ".tmp");
        try {
            Files.write(temp, png);
            try {
                Files.move(temp, iconFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, iconFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    /**
     * Deletes an invalid on-disk icon so sync/repair can re-fetch it.
     *
     * @return true when the file was invalid and removed (or was never a valid PNG)
     */
    public static boolean deleteIfInvalid(@Nonnull Path iconFile) {
        if (!Files.isRegularFile(iconFile)) {
            return true;
        }
        if (isValidFile(iconFile)) {
            return false;
        }
        try {
            Files.deleteIfExists(iconFile);
            LOGGER.atWarning().log("Deleted invalid plot-token icon at %s", iconFile);
            return true;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to delete invalid plot-token icon at %s", iconFile);
            return true;
        }
    }
}
