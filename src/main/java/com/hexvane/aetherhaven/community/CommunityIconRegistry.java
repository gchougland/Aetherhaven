package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
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

/** Registers community building icons from {@code Community/Common/Icons/ItemsGenerated}. */
public final class CommunityIconRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, Long> REGISTERED_MTIMES = new ConcurrentHashMap<>();

    private CommunityIconRegistry() {}

    @Nonnull
    private static String cacheKey(@Nonnull String packId, @Nonnull String assetName) {
        return packId + "|" + assetName;
    }

    public static void syncFromCommunityDirectory(@Nonnull AetherhavenPlugin plugin) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null) {
            return;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        Path iconsDir = CommunityPaths.iconsDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(iconsDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(iconsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(p -> {
                    CommonAsset asset = registerIconFile(module, packId, p, false, false);
                    if (asset == null) {
                        return;
                    }
                    String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(p.getFileName().toString());
                    if (constructionId != null) {
                        PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan community icons at %s", iconsDir);
        }
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile) {
        registerIconFile(plugin, iconFile, false);
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile, boolean force) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null || !Files.isRegularFile(iconFile)) {
            return;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        CommonAsset asset = registerIconFile(module, packId, iconFile, false, force);
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
            if (Universe.get().getPlayerCount() > 0) {
                module.sendAsset(asset, true);
            }
            return asset;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to register community icon %s", iconFile);
            return null;
        }
    }
}
