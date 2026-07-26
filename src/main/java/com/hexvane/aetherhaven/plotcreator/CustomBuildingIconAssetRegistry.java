package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
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
 * clients receive them via common assets (jar-bundled icons alone are not enough).
 */
public final class CustomBuildingIconAssetRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** packId|assetName -> last registered file mtime (skip re-read / send on journal refresh). */
    private static final ConcurrentHashMap<String, Long> REGISTERED_MTIMES = new ConcurrentHashMap<>();

    private CustomBuildingIconAssetRegistry() {}

    public static void syncFromDataDirectory(@Nonnull AetherhavenPlugin plugin) {
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        Path iconsDir = CustomBuildingsPaths.iconsDirectory(plugin.getDataDirectory());
        if (!Files.isDirectory(iconsDir)) {
            pruneStaleRegisteredIcons(packId, iconsDir);
            return;
        }
        pruneStaleRegisteredIcons(packId, iconsDir);
        List<CommonAsset> newlyRegistered = new ArrayList<>();
        try (Stream<Path> files = Files.list(iconsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(p -> {
                    CommonAsset asset = RuntimeCommonIconBroadcast.registerSilently(REGISTERED_MTIMES, packId, p, false);
                    if (asset != null) {
                        newlyRegistered.add(asset);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan custom building icons at %s", iconsDir);
        }
        RuntimeCommonIconBroadcast.broadcast(newlyRegistered);
    }

    /** Removes registry entries for plot token icons whose PNG no longer exists on disk. */
    private static void pruneStaleRegisteredIcons(@Nonnull String packId, @Nonnull Path iconsDir) {
        String prefix = packId + "|Icons/ItemsGenerated/Aetherhaven_Token_";
        List<String> stale = new ArrayList<>();
        for (String cacheKey : REGISTERED_MTIMES.keySet()) {
            if (!cacheKey.startsWith(prefix)) {
                continue;
            }
            String assetName = cacheKey.substring(packId.length() + 1);
            String fileName = assetName.substring("Icons/ItemsGenerated/".length());
            if (!Files.isRegularFile(iconsDir.resolve(fileName))) {
                stale.add(assetName);
            }
        }
        for (String assetName : stale) {
            RuntimeCommonIconBroadcast.unregisterSilently(packId, assetName, REGISTERED_MTIMES);
        }
    }

    public static void unregisterIconForConstruction(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        String assetName = RuntimeCommonIconBroadcast.assetNameForIconFileName(CustomBuildingsPaths.iconFileName(constructionId));
        RuntimeCommonIconBroadcast.unregisterSilently(packId, assetName, REGISTERED_MTIMES);
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile) {
        registerIconFile(plugin, iconFile, false);
    }

    public static void registerIconFile(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile, boolean force) {
        CommonAsset asset = registerIconFileNoSend(plugin, iconFile, force);
        if (asset == null) {
            return;
        }
        RuntimeCommonIconBroadcast.broadcast(List.of(asset));
        String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(iconFile.getFileName().toString());
        if (constructionId != null) {
            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
        }
    }

    /**
     * Registers an icon into the common-asset registry without broadcasting. Caller should
     * {@link #broadcastAssets(List)} once for a batch so clients rebuild the atlas a single time.
     */
    @Nullable
    public static CommonAsset registerIconFileNoSend(@Nonnull AetherhavenPlugin plugin, @Nonnull Path iconFile, boolean force) {
        if (!Files.isRegularFile(iconFile)) {
            return null;
        }
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        return RuntimeCommonIconBroadcast.registerSilently(REGISTERED_MTIMES, packId, iconFile, force);
    }

    /** Broadcasts assets to all players and triggers one common-assets rebuild + one toast. */
    public static void broadcastAssets(@Nonnull List<CommonAsset> assets) {
        RuntimeCommonIconBroadcast.broadcast(assets);
    }
}
