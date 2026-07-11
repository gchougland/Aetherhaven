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
import java.util.ArrayList;
import java.util.List;
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
        List<CommonAsset> newlyRegistered = new ArrayList<>();
        try (Stream<Path> files = Files.list(iconsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(p -> {
                    CommonAsset asset = registerIconFileLocal(module, packId, p, false, false);
                    if (asset == null) {
                        return;
                    }
                    newlyRegistered.add(asset);
                    String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(p.getFileName().toString());
                    if (constructionId != null) {
                        PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan community icons at %s", iconsDir);
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
     *
     * @return the registered asset when newly added/updated; {@code null} when skipped (unchanged mtime)
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
            LOGGER.atWarning().withCause(e).log("Failed to register community icon %s", iconFile);
            return null;
        }
    }
}
