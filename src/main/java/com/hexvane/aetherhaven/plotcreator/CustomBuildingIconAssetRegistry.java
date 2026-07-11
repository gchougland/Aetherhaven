package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registers plot-creator token thumbnails from the plugin data folder ({@code Common/Icons/ItemsGenerated}) so
 * clients receive them via {@link CommonAssetModule} (jar-bundled icons alone are not enough).
 */
public final class CustomBuildingIconAssetRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** packId|assetName -> last registered file mtime (skip re-read / sendAsset on journal refresh). */
    private static final ConcurrentHashMap<String, Long> REGISTERED_MTIMES = new ConcurrentHashMap<>();

    private CustomBuildingIconAssetRegistry() {}

    @Nonnull
    private static String cacheKey(@Nonnull String packId, @Nonnull String assetName) {
        return packId + "|" + assetName;
    }

    public static void syncFromDataDirectory(@Nonnull AetherhavenPlugin plugin) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null) {
            return;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        Path iconsDir = CustomBuildingsPaths.iconsDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(iconsDir)) {
            return;
        }
        List<CommonAsset> newlyRegistered = new ArrayList<>();
        try (Stream<Path> files = Files.list(iconsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(p -> {
                    CommonAsset asset = registerIconFileLocal(module, packId, p, false, false);
                    if (asset != null) {
                        newlyRegistered.add(asset);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan custom building icons at %s", iconsDir);
        }
        broadcastAssets(newlyRegistered);
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile) {
        registerIconFile(plugin, iconFile, false);
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile, boolean force) {
        CommonAsset asset = registerIconFileNoSend(plugin, iconFile, force);
        if (asset == null) {
            return;
        }
        broadcastAssets(List.of(asset));
        String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(iconFile.getFileName().toString());
        if (constructionId != null) {
            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
        }
    }

    /**
     * Registers an icon into the common-asset module without broadcasting. Caller should
     * {@link #broadcastAssets(List)} once for a batch so clients rebuild the atlas a single time.
     */
    @Nullable
    public static CommonAsset registerIconFileNoSend(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile, boolean force) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null || !Files.isRegularFile(iconFile)) {
            return null;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        return registerIconFileLocal(module, packId, iconFile, false, force);
    }

    /** Broadcasts assets to all players and triggers one common-assets rebuild. */
    public static void broadcastAssets(@Nonnull List<CommonAsset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null || Universe.get().getPlayerCount() <= 0) {
            return;
        }
        module.sendAssets(assets, true);
    }

    @Nullable
    private static CommonAsset registerIconFileLocal(
        @Nonnull CommonAssetModule module,
        @Nonnull String packId,
        @Nonnull Path iconFile,
        boolean log,
        boolean force
    ) {
        String assetName = "Icons/ItemsGenerated/" + iconFile.getFileName();
        String cacheKey = cacheKey(packId, assetName);
        try {
            long mtime = Files.getLastModifiedTime(iconFile).toMillis();
            Long registered = REGISTERED_MTIMES.get(cacheKey);
            if (!force && registered != null && registered == mtime) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(iconFile);
            FileCommonAsset asset = new FileCommonAsset(iconFile, assetName, bytes);
            module.addCommonAsset(packId, asset, log);
            REGISTERED_MTIMES.put(cacheKey, mtime);
            return asset;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to register custom building icon %s", iconFile);
            return null;
        }
    }
}
