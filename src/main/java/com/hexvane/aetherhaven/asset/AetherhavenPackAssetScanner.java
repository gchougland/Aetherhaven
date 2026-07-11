package com.hexvane.aetherhaven.asset;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Discovers asset files under a path relative to every registered asset pack (same pattern as engine
 * multi-root resolution).
 */
public final class AetherhavenPackAssetScanner {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public record PackJsonFile(@Nonnull String packName, @Nonnull Path absolutePath) {}

    /** Any pack file (json, markdown, etc.). */
    public record PackAssetFile(@Nonnull String packName, @Nonnull Path absolutePath) {}

    private AetherhavenPackAssetScanner() {}

    /**
     * @param relativeDirectory e.g. {@link AetherhavenAssetPaths#QUESTS} (no trailing slash)
     */
    @Nonnull
    public static List<PackJsonFile> listJsonFilesUnderAllPacks(@Nonnull String relativeDirectory) {
        List<PackAssetFile> files = listFilesUnderAllPacks(relativeDirectory, ".json");
        List<PackJsonFile> out = new ArrayList<>(files.size());
        for (PackAssetFile f : files) {
            out.add(new PackJsonFile(f.packName(), f.absolutePath()));
        }
        return out;
    }

    /**
     * @param relativeDirectory e.g. {@link AetherhavenAssetPaths#GUIDE_TOPICS} (no trailing slash)
     * @param extension including the dot, e.g. {@code .md} (compared case-insensitively)
     */
    @Nonnull
    public static List<PackAssetFile> listFilesUnderAllPacks(
        @Nonnull String relativeDirectory,
        @Nonnull String extension
    ) {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return List.of();
        }
        String ext = extension.startsWith(".") ? extension : "." + extension;
        String extLower = ext.toLowerCase();
        List<PackAssetFile> out = new ArrayList<>();
        for (AssetPack pack : module.getAssetPacks()) {
            Path dir = pack.getRoot().resolve(relativeDirectory);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(extLower))
                    .sorted()
                    .forEach(p -> out.add(new PackAssetFile(pack.getName(), p)));
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to walk %s in pack %s", dir, pack.getName());
            }
        }
        return out;
    }

    /**
     * @return true if at least one pack exposed a non-empty directory for this relative path
     */
    public static boolean anyPackHasDirectory(@Nonnull String relativeDirectory) {
        AssetModule module = AssetModule.get();
        if (module == null) {
            return false;
        }
        for (AssetPack pack : module.getAssetPacks()) {
            Path dir = pack.getRoot().resolve(relativeDirectory);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    if (s.findAny().isPresent()) {
                        return true;
                    }
                } catch (IOException e) {
                    LOGGER.atFine().withCause(e).log("Could not list %s", dir);
                }
            }
        }
        return false;
    }
}
