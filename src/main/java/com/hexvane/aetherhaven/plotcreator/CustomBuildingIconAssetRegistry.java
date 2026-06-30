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
        try (Stream<Path> files = Files.list(iconsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(p -> registerIconFile(module, packId, p, false));
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan custom building icons at %s", iconsDir);
        }
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null || !Files.isRegularFile(iconFile)) {
            return;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        CommonAsset asset = registerIconFile(module, packId, iconFile, false);
        if (asset == null) {
            return;
        }
        String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(iconFile.getFileName().toString());
        if (constructionId != null) {
            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
        }
    }

    @Nullable
    private static CommonAsset registerIconFile(
        @Nonnull CommonAssetModule module,
        @Nonnull String packId,
        @Nonnull Path iconFile,
        boolean log
    ) {
        String assetName = "Icons/ItemsGenerated/" + iconFile.getFileName();
        String cacheKey = cacheKey(packId, assetName);
        try {
            long mtime = Files.getLastModifiedTime(iconFile).toMillis();
            Long registered = REGISTERED_MTIMES.get(cacheKey);
            if (registered != null && registered == mtime) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(iconFile);
            FileCommonAsset asset = new FileCommonAsset(iconFile, assetName, bytes);
            module.addCommonAsset(packId, asset, log);
            REGISTERED_MTIMES.put(cacheKey, mtime);
            if (Universe.get().getPlayerCount() > 0) {
                // Force item-icon atlas rebuild so inventory slots pick up runtime PNGs (once per icon revision).
                module.sendAsset(asset, true);
            }
            return asset;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to register custom building icon %s", iconFile);
            return null;
        }
    }
}
