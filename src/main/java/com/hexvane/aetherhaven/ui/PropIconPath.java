package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityPaths;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropPaths;
import com.hexvane.aetherhaven.prop.PropShopItemIds;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves prop item / crafting-bench thumbnail paths (greenscreen PNGs, not isometric). */
public final class PropIconPath {
    /** Avoid re-stat / PNG validation on every OpenWindow for the same prop. */
    private static final ConcurrentHashMap<String, Boolean> RUNTIME_ICON_EXISTS = new ConcurrentHashMap<>();

    private PropIconPath() {}

    /** Clears existence cache after icons are written or deleted. */
    public static void invalidateRuntimeIconCache() {
        RUNTIME_ICON_EXISTS.clear();
    }

    public static void invalidateRuntimeIconCache(@Nonnull String propId) {
        RUNTIME_ICON_EXISTS.keySet().removeIf(k -> k.endsWith("|" + propId.trim()));
    }
    @Nonnull
    public static String forPropId(@Nonnull String propId, @Nullable Path dataDirectory) {
        String id = propId.trim();
        if (hasRuntimeIconFile(dataDirectory, id)) {
            return PropPaths.iconAssetPath(id);
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            PropDefinition def = plugin.getPropCatalog().get(id);
            if (def != null && def.getIconPath() != null && !def.getIconPath().isBlank()) {
                return def.getIconPath().trim();
            }
        }
        String shopItemId = PropShopItemIds.forPropId(id);
        Item item = Item.getAssetMap().getAsset(shopItemId);
        if (item != null && item.getIcon() != null && !item.getIcon().isBlank()) {
            return item.getIcon().trim();
        }
        return PropPaths.iconAssetPath(id);
    }

    @Nonnull
    public static String forProp(@Nonnull PropDefinition def, @Nullable Path dataDirectory) {
        if (def.getIconPath() != null && !def.getIconPath().isBlank()) {
            String path = def.getIconPath().trim();
            if (dataDirectory == null || hasFileForAssetPath(dataDirectory, path) || classpathHas(path)) {
                return path;
            }
        }
        return forPropId(def.getId(), dataDirectory);
    }

    public static boolean isIconAvailable(@Nonnull String propId, @Nullable Path dataDirectory) {
        if (hasRuntimeIconFile(dataDirectory, propId)) {
            return true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            PropDefinition def = plugin.getPropCatalog().get(propId.trim());
            if (def != null && def.getIconPath() != null && !def.getIconPath().isBlank()) {
                return true;
            }
        }
        String shopItemId = PropShopItemIds.forPropId(propId);
        Item item = Item.getAssetMap().getAsset(shopItemId);
        return item != null && item.getIcon() != null && !item.getIcon().isBlank();
    }

    public static void registerRuntimeIconIfPresent(@Nonnull AetherhavenPlugin plugin, @Nonnull String propId) {
        Path iconFile = resolveRuntimeIconFile(plugin.getDataDirectory(), propId.trim());
        if (iconFile == null) {
            return;
        }
        Path communityDir = CommunityPaths.iconsDirectory(plugin.getDataDirectory()).normalize();
        if (iconFile.normalize().startsWith(communityDir)) {
            com.hexvane.aetherhaven.community.CommunityIconRegistry.registerIconFile(plugin, iconFile);
        } else {
            CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
        }
    }

    private static boolean hasRuntimeIconFile(@Nullable Path dataDirectory, @Nonnull String propId) {
        return resolveRuntimeIconFile(dataDirectory, propId) != null;
    }

    @Nullable
    private static Path resolveRuntimeIconFile(@Nullable Path dataDirectory, @Nonnull String propId) {
        if (dataDirectory == null || propId.isBlank()) {
            return null;
        }
        String cacheKey = dataDirectory.toAbsolutePath().normalize() + "|" + propId;
        Boolean cached = RUNTIME_ICON_EXISTS.get(cacheKey);
        if (Boolean.FALSE.equals(cached)) {
            return null;
        }
        Path custom = PropPaths.iconFile(dataDirectory, propId);
        if (Files.isRegularFile(custom)) {
            // Existence only on the hot OpenWindow path — full PNG validation happens at icon register time.
            RUNTIME_ICON_EXISTS.put(cacheKey, Boolean.TRUE);
            return custom;
        }
        Path community = CommunityPaths.iconsDirectory(dataDirectory).resolve(PropPaths.iconFileName(propId));
        if (Files.isRegularFile(community)) {
            RUNTIME_ICON_EXISTS.put(cacheKey, Boolean.TRUE);
            return community;
        }
        RUNTIME_ICON_EXISTS.put(cacheKey, Boolean.FALSE);
        return null;
    }

    private static boolean hasFileForAssetPath(@Nonnull Path dataDirectory, @Nonnull String assetPath) {
        String name = assetPath.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        String file = slash >= 0 ? name.substring(slash + 1) : name;
        Path direct = dataDirectory.resolve("Common/Icons/ItemsGenerated").resolve(file);
        if (Files.isRegularFile(direct)) {
            return true;
        }
        Path community = CommunityPaths.iconsDirectory(dataDirectory).resolve(file);
        return Files.isRegularFile(community);
    }

    private static boolean classpathHas(@Nonnull String assetPath) {
        String resource = assetPath.startsWith("Icons/") ? "Common/" + assetPath : assetPath;
        ClassLoader cl = PropIconPath.class.getClassLoader();
        return cl != null && cl.getResource(resource) != null;
    }
}
