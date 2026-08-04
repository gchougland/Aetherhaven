package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityIconDownload;
import com.hexvane.aetherhaven.community.CommunityIconRegistry;
import com.hexvane.aetherhaven.community.CommunityPaths;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves isometric thumbnail paths for plot token crafting UI (not the sign item model). */
public final class ConstructionTokenIconPath {
    private ConstructionTokenIconPath() {}

    @Nonnull
    public static String forConstruction(@Nonnull ConstructionDefinition def, @Nullable Path dataDirectory) {
        String id = def.getId().trim();
        if (hasRuntimeIconFile(dataDirectory, id)) {
            return CustomBuildingsPaths.iconAssetPath(id);
        }
        String tokenItemId = def.getPlotTokenItemId();
        if (tokenItemId != null && AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(tokenItemId.trim())) {
            return CustomBuildingsPaths.iconAssetPath(id);
        }
        if (tokenItemId != null
            && !tokenItemId.isBlank()
            && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(tokenItemId.trim())) {
            Item item = Item.getAssetMap().getAsset(tokenItemId.trim());
            return ItemAssetImagePath.forItem(item, tokenItemId.trim());
        }
        return CustomBuildingsPaths.iconAssetPath(id);
    }

    @Nonnull
    public static String forConstruction(@Nonnull ConstructionDefinition def) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Path dataDir = plugin != null ? plugin.getDataDirectory() : null;
        return forConstruction(def, dataDir);
    }

    /** Resolves icon path from construction id alone (catalog lookup when available). */
    @Nonnull
    public static String forConstructionId(@Nonnull String constructionId, @Nullable Path dataDirectory) {
        String id = constructionId.trim();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(id);
            if (def != null) {
                return forConstruction(def, dataDirectory);
            }
        }
        if (hasRuntimeIconFile(dataDirectory, id)) {
            return CustomBuildingsPaths.iconAssetPath(id);
        }
        return CustomBuildingsPaths.iconAssetPath(id);
    }

    /**
     * Registers a runtime plot-creator or community PNG into the common-asset registry when present on disk.
     * Safe to call before journal ItemGrid slots render.
     */
    public static void registerRuntimeIconIfPresent(@Nonnull AetherhavenPlugin plugin, @Nonnull String constructionId) {
        String id = constructionId.trim();
        if (id.isEmpty()) {
            return;
        }
        Path iconFile = resolveRuntimeIconFile(plugin.getDataDirectory(), id);
        if (iconFile == null) {
            return;
        }
        Path communityDir = CommunityPaths.iconsDirectory(plugin.getDataDirectory()).normalize();
        if (iconFile.normalize().startsWith(communityDir)) {
            CommunityIconRegistry.registerIconFile(plugin, iconFile);
        } else {
            CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
        }
    }

    private static boolean hasRuntimeIconFile(@Nullable Path dataDirectory, @Nonnull String constructionId) {
        return resolveRuntimeIconFile(dataDirectory, constructionId) != null;
    }

    @Nullable
    static Path resolveRuntimeIconFile(@Nullable Path dataDirectory, @Nonnull String constructionId) {
        if (dataDirectory == null) {
            return null;
        }
        String id = constructionId.trim();
        Path customIcon = CustomBuildingsPaths.iconFile(dataDirectory, id);
        if (Files.isRegularFile(customIcon)) {
            return customIcon;
        }
        Path communityIcon = CommunityPaths.iconFile(dataDirectory, id);
        if (Files.isRegularFile(communityIcon)) {
            return communityIcon;
        }
        return null;
    }

    /**
     * Whether a plot-token icon is safe to show in custom UI item grids (avoids client NRE when the PNG is missing).
     */
    public static boolean isIconAvailable(@Nonnull String constructionId, @Nullable Path dataDirectory) {
        String id = constructionId.trim();
        if (id.isEmpty()) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(id);
            if (def != null) {
                return isIconAvailable(def, dataDirectory);
            }
        }
        return isRuntimeIconClientSafe(id, dataDirectory, plugin) || hasBundledTokenIcon(id);
    }

    public static boolean isIconAvailable(@Nonnull ConstructionDefinition def, @Nullable Path dataDirectory) {
        String id = def.getId().trim();
        if (isRuntimeIconClientSafe(id, dataDirectory, AetherhavenPlugin.get())) {
            return true;
        }
        String tokenItemId = def.getPlotTokenItemId();
        if (tokenItemId != null
            && !tokenItemId.isBlank()
            && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(tokenItemId.trim())) {
            return Item.getAssetMap().getAsset(tokenItemId.trim()) != null;
        }
        return hasBundledTokenIcon(id);
    }

    private static boolean isRuntimeIconClientSafe(
        @Nonnull String constructionId,
        @Nullable Path dataDirectory,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (!hasRuntimeIconFile(dataDirectory, constructionId)) {
            return false;
        }
        if (plugin != null) {
            registerRuntimeIconIfPresent(plugin, constructionId);
        }
        return CommunityIconDownload.isRegistered(CustomBuildingsPaths.iconAssetPath(constructionId));
    }

    /** Unified plot token default icon from {@link AetherhavenConstants#PLOT_TOKEN_UNIFIED}. */
    @Nonnull
    public static String unifiedPlotTokenFallbackIconPath() {
        Item unified = Item.getAssetMap().getAsset(AetherhavenConstants.PLOT_TOKEN_UNIFIED);
        return ItemAssetImagePath.forItem(unified, AetherhavenConstants.PLOT_TOKEN_UNIFIED);
    }

    private static boolean hasBundledTokenIcon(@Nonnull String constructionId) {
        String resourcePath =
            CustomBuildingsPaths.ICONS_RELATIVE + "/" + CustomBuildingsPaths.iconFileName(constructionId);
        ClassLoader cl =
            AetherhavenPlugin.get() != null
                ? AetherhavenPlugin.get().getClassLoader()
                : ConstructionTokenIconPath.class.getClassLoader();
        return cl.getResource(resourcePath) != null;
    }
}
