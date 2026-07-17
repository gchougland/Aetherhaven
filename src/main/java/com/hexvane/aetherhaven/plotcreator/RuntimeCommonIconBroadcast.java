package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.function.supplier.CachedSupplier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetNotifications;
import com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registers runtime icon PNGs into {@link CommonAssetRegistry} without the per-asset toast that
 * {@link CommonAssetModule#addCommonAsset} emits, then broadcasts a batch with a single rebuild and toast.
 */
public final class RuntimeCommonIconBroadcast {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Field ASSETS_CACHE_FIELD;

    static {
        Field field = null;
        try {
            field = CommonAssetModule.class.getDeclaredField("assets");
            field.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().withCause(e).log("Could not access CommonAssetModule.assets cache field");
        }
        ASSETS_CACHE_FIELD = field;
    }

    private RuntimeCommonIconBroadcast() {}

    /**
     * Registers {@code iconFile} when missing or force/mtime requires it.
     *
     * @return the asset when newly added/updated; {@code null} when unchanged (skip broadcast)
     */
    @Nullable
    public static CommonAsset registerSilently(
        @Nonnull ConcurrentHashMap<String, Long> registeredMtimes,
        @Nonnull String packId,
        @Nonnull Path iconFile,
        boolean force
    ) {
        String assetName = "Icons/ItemsGenerated/" + iconFile.getFileName();
        String cacheKey = packId + "|" + assetName;
        try {
            long mtime = Files.getLastModifiedTime(iconFile).toMillis();
            Long registered = registeredMtimes.get(cacheKey);
            if (!force && registered != null && registered == mtime) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(iconFile);
            FileCommonAsset asset = new FileCommonAsset(iconFile, assetName, bytes);
            CommonAssetRegistry.AddCommonAssetResult result = CommonAssetRegistry.addCommonAsset(packId, asset);
            registeredMtimes.put(cacheKey, mtime);

            CommonAssetRegistry.PackAsset newPack = result.getNewPackAsset();
            CommonAssetRegistry.PackAsset oldPack = result.getPreviousNameAsset();
            if (oldPack != null && oldPack.asset().getHash().equals(newPack.asset().getHash())) {
                return null;
            }
            if (!result.getActiveAsset().equals(newPack)) {
                return null;
            }
            BlockyAnimationCache.invalidate(asset.getName());
            return asset;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to register runtime icon %s", iconFile);
            return null;
        }
    }

    /** Sends assets once, rebuilds the client atlas once, and shows one Common reload toast. */
    public static void broadcast(@Nonnull List<CommonAsset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null) {
            return;
        }
        invalidateRequiredAssetsCache(module);
        if (Universe.get().getPlayerCount() <= 0) {
            return;
        }
        module.sendAssets(assets, true);
        Message primary =
            Message.translation(AssetNotifications.ASSET_RELOADED_MESSAGE_KEY)
                .color(AssetNotifications.ASSET_RELOADED_COLOR)
                .param("class", "Common");
        Message secondary =
            Message.translation(AssetNotifications.ASSET_SECONDARY_GENERIC_MESSAGE_KEY).param("count", assets.size());
        NotificationUtil.sendNotificationToUniverse(
            primary,
            secondary,
            AssetNotifications.ASSET_RELOADED_ICON,
            NotificationStyle.Success
        );
    }

    /** Invalidates the required-assets snapshot without sending packets or rebuilding connected clients. */
    public static void invalidateRequiredAssetsCache() {
        CommonAssetModule module = CommonAssetModule.get();
        if (module != null) {
            invalidateRequiredAssetsCache(module);
        }
    }

    private static void invalidateRequiredAssetsCache(@Nonnull CommonAssetModule module) {
        if (ASSETS_CACHE_FIELD == null) {
            return;
        }
        try {
            Object cached = ASSETS_CACHE_FIELD.get(module);
            if (cached instanceof CachedSupplier<?> supplier) {
                supplier.invalidate();
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.atFine().withCause(e).log("Could not invalidate CommonAssetModule required-assets cache");
        }
    }
}
