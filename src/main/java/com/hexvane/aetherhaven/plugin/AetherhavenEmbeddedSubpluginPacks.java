package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.assetstore.AssetPack;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional subplugin assets ship inside the parent JAR under {@code subplugin-packs/<Sub>/} (not under root
 * {@code Server/}), so the core classpath pack does not load them. During parent {@code setup()}, enabled subs
 * register as separate asset packs before {@code LoadAssetEvent}.
 */
public final class AetherhavenEmbeddedSubpluginPacks {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PACKS_ROOT = "subplugin-packs";
    /** Bump when embedded pack files change so extracted cache under plugin data is refreshed. */
    private static final String PACK_EXTRACT_REVISION = "3";

    private static final List<EmbeddedPack> PACKS =
        List.of(
            new EmbeddedPack(AetherhavenPluginIds.REPUTATION_UNLOCKS, "ReputationUnlocks"),
            new EmbeddedPack(AetherhavenPluginIds.JEWELRY, "Jewelry"),
            new EmbeddedPack(AetherhavenPluginIds.FLOATING_GIFTS, "FloatingGifts"),
            new EmbeddedPack(AetherhavenPluginIds.PATH_DESIGNER, "PathDesigner"),
            new EmbeddedPack(AetherhavenPluginIds.BARD, "Bard"),
            new EmbeddedPack(AetherhavenPluginIds.ADMIN_TOOLS, "AdminTools"),
            new EmbeddedPack(AetherhavenPluginIds.RTS, "Rts"),
            new EmbeddedPack(AetherhavenPluginIds.PATROL_ROUTES, "PatrolRoutes"),
            new EmbeddedPack(AetherhavenPluginIds.PLOT_CREATOR, "PlotCreator"),
            new EmbeddedPack(AetherhavenPluginIds.QUESTS, "Quests"),
            new EmbeddedPack(AetherhavenPluginIds.ECONOMY, "Economy"),
            new EmbeddedPack(AetherhavenPluginIds.COMMERCE, "Commerce"),
            new EmbeddedPack(AetherhavenPluginIds.GUILD, "Guild")
        );

    private AetherhavenEmbeddedSubpluginPacks() {}

    public static void registerEnabled(@Nonnull AetherhavenPlugin plugin) {
        AssetModule assetModule = AssetModule.get();
        if (assetModule == null) {
            return;
        }
        Path modFile = plugin.getFile();
        if (modFile == null) {
            LOGGER.atWarning().log("Cannot register embedded subplugin packs: parent mod path is null");
            return;
        }
        for (EmbeddedPack pack : PACKS) {
            if (!AetherhavenFeatures.isEnabledInServerConfig(pack.subplugin())) {
                continue;
            }
            try {
                Path packRoot = resolvePackRoot(plugin, modFile, pack.folderName());
                if (packRoot == null) {
                    continue;
                }
                PluginManifest manifest = loadPackManifest(packRoot);
                if (manifest == null) {
                    LOGGER.atWarning().log("Embedded subplugin pack '%s' has no manifest.json", pack.folderName());
                    continue;
                }
                String packId = pack.subplugin().toString();
                if (!assetModule.registerPack(packId, packRoot, manifest, AssetPack.PackSource.RUNTIME)) {
                    LOGGER.atWarning().log("Failed to register embedded asset pack %s from %s", packId, packRoot);
                    continue;
                }
                LOGGER.atInfo().log("Registered embedded asset pack %s from %s", packId, packRoot);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to prepare embedded asset pack %s", pack.folderName());
            }
        }
    }

    @Nullable
    private static Path resolvePackRoot(@Nonnull AetherhavenPlugin plugin, @Nonnull Path modFile, @Nonnull String folderName)
        throws IOException {
        String relative = PACKS_ROOT + "/" + folderName;
        Path direct = modFile.resolve(relative);
        if (Files.isDirectory(direct.resolve("Server"))) {
            return direct.normalize();
        }
        Path fromClasspath = resolveFromClasspathResource(relative);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        if (!isArchive(modFile)) {
            return null;
        }
        return extractPackFromArchive(plugin, modFile, relative, folderName);
    }

    @Nullable
    private static Path resolveFromClasspathResource(@Nonnull String relativePackPath) {
        String marker = relativePackPath + "/Server";
        var url = AetherhavenEmbeddedSubpluginPacks.class.getClassLoader().getResource(marker);
        if (url == null || !"file".equals(url.getProtocol())) {
            return null;
        }
        try {
            Path serverDir = Path.of(url.toURI());
            return serverDir.getParent();
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Could not resolve embedded pack from classpath resource %s", marker);
            return null;
        }
    }

    @Nonnull
    private static Path extractPackFromArchive(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Path archivePath,
        @Nonnull String relativePackPath,
        @Nonnull String folderName
    ) throws IOException {
        Path cacheRoot = plugin.getDataDirectory().resolve("embedded-packs").resolve(folderName);
        Path stamp = cacheRoot.resolve(".extract-version");
        String version = plugin.getManifest().getVersion().toString() + "|" + PACK_EXTRACT_REVISION;
        if (Files.isDirectory(cacheRoot.resolve("Server")) && Files.exists(stamp) && version.contentEquals(Files.readString(stamp))) {
            return cacheRoot;
        }
        if (Files.exists(cacheRoot)) {
            deleteRecursive(cacheRoot);
        }
        Files.createDirectories(cacheRoot);
        try (FileSystem fileSystem = FileSystems.newFileSystem(archivePath)) {
            Path packInArchive = fileSystem.getPath(relativePackPath);
            if (!Files.isDirectory(packInArchive)) {
                throw new IOException("Missing embedded pack path in mod archive: " + relativePackPath);
            }
            copyTree(packInArchive, cacheRoot);
        }
        Files.writeString(stamp, version);
        return cacheRoot;
    }

    @Nullable
    private static PluginManifest loadPackManifest(@Nonnull Path packRoot) throws IOException {
        Path manifestPath = packRoot.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        try (
            InputStreamReader reader = new InputStreamReader(Files.newInputStream(manifestPath), StandardCharsets.UTF_8)
        ) {
            char[] buffer = RawJsonReader.READ_BUFFER.get();
            RawJsonReader rawJsonReader = new RawJsonReader(reader, buffer);
            ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
            PluginManifest manifest = PluginManifest.CODEC.decodeJson(rawJsonReader, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);
            return manifest;
        }
    }

    private static void copyTree(@Nonnull Path source, @Nonnull Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path relative = source.relativize(src);
                // Jar/zip Path instances cannot be resolved on the host filesystem (ProviderMismatchException).
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(src, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void deleteRecursive(@Nonnull Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static boolean isArchive(@Nonnull Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private record EmbeddedPack(@Nonnull PluginIdentifier subplugin, @Nonnull String folderName) {}
}
