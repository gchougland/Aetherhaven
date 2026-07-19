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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional subplugin assets ship inside the parent JAR under {@code subplugin-packs/<Sub>/} (not under root
 * {@code Server/}), so the core classpath pack does not load them. During parent {@code setup()}, enabled subs
 * register as separate asset packs before {@code LoadAssetEvent}.
 *
 * <p>Packs are registered in-place (exploded dir, classpath {@code file:} URL, or a directory path inside the
 * parent mod archive). Nothing is copied or deleted on disk — avoids self-extract patterns that trip mod-store
 * scanners.
 */
public final class AetherhavenEmbeddedSubpluginPacks {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PACKS_ROOT = "subplugin-packs";
    /**
     * Real pack metadata. Pack roots also ship a stub {@code manifest.json} (not a mod manifest) so Hytale's
     * {@code AssetModule.validatePackExistsOnDisk} does not unregister packs during {@code /prefabedit load}.
     */
    private static final String PACK_MANIFEST_FILE = "asset-pack.json";

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
            new EmbeddedPack(AetherhavenPluginIds.GUILD, "Guild"),
            new EmbeddedPack(AetherhavenPluginIds.WORLD_NPCS, "WorldNpcs")
        );

    /** Kept open for the process lifetime so in-archive pack paths remain readable. */
    @Nullable
    private static volatile FileSystem modArchiveFileSystem;

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
                Path packRoot = resolvePackRoot(modFile, pack.folderName());
                if (packRoot == null) {
                    LOGGER.atWarning().log("Embedded subplugin pack '%s' not found under %s", pack.folderName(), PACKS_ROOT);
                    continue;
                }
                PluginManifest manifest = loadPackManifest(packRoot);
                if (manifest == null) {
                    LOGGER.atWarning().log("Embedded subplugin pack '%s' has no %s", pack.folderName(), PACK_MANIFEST_FILE);
                    continue;
                }
                String packId = pack.subplugin().toString();
                if (!assetModule.registerPack(packId, packRoot, manifest, AssetPack.PackSource.RUNTIME)) {
                    LOGGER.atWarning().log("Failed to register embedded asset pack %s from %s", packId, packRoot);
                    continue;
                }
                markImmutableIfNotDefaultFilesystem(assetModule, packId, packRoot);
                LOGGER.atInfo().log("Registered embedded asset pack %s from %s", packId, packRoot);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to prepare embedded asset pack %s", pack.folderName());
            }
        }
    }

    /**
     * Packs registered as directories inside the parent JAR are ZipPaths. {@link AssetModule#registerPack} only
     * marks {@code .jar}/{@code .zip} file paths immutable, so AssetMonitor would try to watch ZipPaths with the
     * default filesystem WatchService and throw {@link java.nio.file.ProviderMismatchException}. Mark those packs
     * immutable (same as real jar packs) so monitoring is skipped. Exploded/dev packs on the default FS stay mutable.
     */
    private static void markImmutableIfNotDefaultFilesystem(
        @Nonnull AssetModule assetModule,
        @Nonnull String packId,
        @Nonnull Path packRoot
    ) {
        if (packRoot.getFileSystem().equals(FileSystems.getDefault())) {
            return;
        }
        AssetPack registered = assetModule.getAssetPack(packId);
        if (registered == null || registered.isImmutable()) {
            return;
        }
        AssetPack immutable = new AssetPack(
            registered.getPackLocation(),
            registered.getName(),
            registered.getRoot(),
            registered.getFileSystem(),
            true,
            registered.getManifest(),
            registered.getSource()
        );
        List<AssetPack> packs = assetModule.getAssetPacks();
        int index = packs.indexOf(registered);
        if (index >= 0) {
            packs.set(index, immutable);
            LOGGER.atInfo().log("Marked embedded asset pack %s immutable (non-default filesystem root)", packId);
        }
    }

    @Nullable
    private static Path resolvePackRoot(@Nonnull Path modFile, @Nonnull String folderName) throws IOException {
        String relative = PACKS_ROOT + "/" + folderName;
        Path direct = modFile.resolve(relative);
        if (isPackRoot(direct)) {
            return direct.normalize();
        }
        Path fromClasspath = resolveFromClasspathResource(relative);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        if (!isArchive(modFile)) {
            return null;
        }
        return resolveFromModArchive(modFile, relative);
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
            Path packRoot = serverDir.getParent();
            return packRoot != null && isPackRoot(packRoot) ? packRoot : null;
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Could not resolve embedded pack from classpath resource %s", marker);
            return null;
        }
    }

    /**
     * Resolves a pack directory inside the parent mod archive without copying it to disk. The archive
     * {@link FileSystem} is retained so asset reads keep working.
     */
    @Nullable
    private static Path resolveFromModArchive(@Nonnull Path archivePath, @Nonnull String relativePackPath)
        throws IOException {
        FileSystem fileSystem = modArchiveFileSystem();
        if (fileSystem == null) {
            synchronized (AetherhavenEmbeddedSubpluginPacks.class) {
                fileSystem = modArchiveFileSystem();
                if (fileSystem == null) {
                    fileSystem = openModArchive(archivePath);
                    modArchiveFileSystem = fileSystem;
                }
            }
        }
        Path packInArchive = fileSystem.getPath(relativePackPath);
        if (!isPackRoot(packInArchive)) {
            // Some zip providers require a leading slash.
            packInArchive = fileSystem.getPath("/" + relativePackPath);
        }
        return isPackRoot(packInArchive) ? packInArchive : null;
    }

    @Nonnull
    private static FileSystem openModArchive(@Nonnull Path archivePath) throws IOException {
        try {
            return FileSystems.newFileSystem(archivePath);
        } catch (FileSystemAlreadyExistsException ignored) {
            URI uri = URI.create("jar:" + archivePath.toAbsolutePath().normalize().toUri());
            return FileSystems.getFileSystem(uri);
        }
    }

    @Nullable
    private static FileSystem modArchiveFileSystem() {
        FileSystem fs = modArchiveFileSystem;
        return fs != null && fs.isOpen() ? fs : null;
    }

    private static boolean isPackRoot(@Nonnull Path packRoot) {
        return Files.isDirectory(packRoot) && Files.isDirectory(packRoot.resolve("Server"));
    }

    @Nullable
    private static PluginManifest loadPackManifest(@Nonnull Path packRoot) throws IOException {
        Path manifestPath = packRoot.resolve(PACK_MANIFEST_FILE);
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

    private static boolean isArchive(@Nonnull Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private record EmbeddedPack(@Nonnull PluginIdentifier subplugin, @Nonnull String folderName) {}
}
