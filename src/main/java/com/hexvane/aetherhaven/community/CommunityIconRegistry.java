package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenIconSync;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.RuntimeCommonIconBroadcast;
import com.hexvane.aetherhaven.prop.PropIconSync;
import com.hexvane.aetherhaven.prop.PropPaths;
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

/** Registers community building icons from {@code Community/Common/Icons/ItemsGenerated}. */
public final class CommunityIconRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<String, Long> REGISTERED_MTIMES = new ConcurrentHashMap<>();

    private CommunityIconRegistry() {}

    public static void syncFromCommunityDirectory(@Nonnull AetherhavenPlugin plugin) {
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
                    CommonAsset asset = RuntimeCommonIconBroadcast.registerSilently(REGISTERED_MTIMES, packId, p, false);
                    if (asset == null) {
                        return;
                    }
                    newlyRegistered.add(asset);
                    String fileName = p.getFileName().toString();
                    String propId = PropPaths.propIdFromIconFileName(fileName);
                    if (propId != null) {
                        PropIconSync.afterIconRegistered(plugin, propId);
                    } else {
                        String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(fileName);
                        if (constructionId != null) {
                            PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
                        }
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan community icons at %s", iconsDir);
        }
        RuntimeCommonIconBroadcast.broadcast(newlyRegistered);
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
        String fileName = iconFile.getFileName().toString();
        String propId = PropPaths.propIdFromIconFileName(fileName);
        if (propId != null) {
            PropIconSync.afterIconRegistered(plugin, propId);
        } else {
            String constructionId = CustomBuildingsPaths.constructionIdFromIconFileName(fileName);
            if (constructionId != null) {
                PlotTokenIconSync.afterIconRegistered(plugin, constructionId);
            }
        }
    }

    /**
     * Registers an icon into the common-asset registry without broadcasting. Caller should
     * {@link #broadcastAssets(List)} once for a batch so clients rebuild the atlas a single time.
     *
     * @return the registered asset when newly added/updated; {@code null} when skipped (unchanged)
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
