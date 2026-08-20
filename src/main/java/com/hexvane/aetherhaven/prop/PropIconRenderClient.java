package com.hexvane.aetherhaven.prop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityHttpClient;
import com.hexvane.aetherhaven.config.CommunityMarketplaceConfig;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingIconAssetRegistry;
import com.hexvane.aetherhaven.plotcreator.PlotTokenIconPng;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Calls the marketplace greenscreen render endpoint and wires the PNG into prop resources for syncAssets. */
public final class PropIconRenderClient {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BOUNDARY = "----AetherhavenPropIconBoundary";

    private PropIconRenderClient() {}

    public enum Result {
        SUCCESS,
        DISABLED,
        UNKNOWN_PROP,
        PREFAB_MISSING,
        RENDER_FAILED,
        IO_ERROR
    }

    @Nonnull
    public static Result generateAndWire(@Nonnull AetherhavenPlugin plugin, @Nonnull String propId) {
        CommunityMarketplaceConfig cfg = plugin.getConfig().get().getCommunityMarketplace();
        // Icon render only needs the website URL; submit-to-community can be off.
        if (cfg.getApiBaseUrl() == null || cfg.getApiBaseUrl().isBlank()) {
            return Result.DISABLED;
        }
        PropDefinition def = plugin.getPropCatalog().get(propId);
        if (def == null) {
            return Result.UNKNOWN_PROP;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null || !Files.isRegularFile(prefabPath)) {
            return Result.PREFAB_MISSING;
        }
        try {
            byte[] prefabBytes = Files.readAllBytes(prefabPath);
            byte[] multipart = buildPrefabMultipart(prefabBytes);
            String url = cfg.getApiBaseUrl().replaceAll("/+$", "") + "/api/v1/render-prop-icon";
            Map<String, String> headers = new LinkedHashMap<>();
            byte[] png = CommunityHttpClient.postMultipartBytes(url, headers, BOUNDARY, multipart);
            if (png == null || !PlotTokenIconPng.isValid(png)) {
                return Result.RENDER_FAILED;
            }

            Path runtimeIcon = PropPaths.iconFile(plugin.getDataDirectory(), propId);
            Files.createDirectories(runtimeIcon.getParent());
            PlotTokenIconPng.writeAtomically(runtimeIcon, png);
            CustomBuildingIconAssetRegistry.registerIconFile(plugin, runtimeIcon);
            if (plugin.getPropIconPacketAdapter() != null) {
                plugin.getPropIconPacketAdapter().onPropIconRegistered(propId);
            }

            String assetPath = PropPaths.iconAssetPath(propId);
            def.setIconPath(assetPath);
            plugin.getPropCatalog().persist(def);

            Path resourcesRoot = resolveResourcesRoot();
            if (resourcesRoot != null) {
                Path shippedIcon = resourcesRoot.resolve("Common/Icons/ItemsGenerated").resolve(PropPaths.iconFileName(propId));
                Files.createDirectories(shippedIcon.getParent());
                Files.write(shippedIcon, png);
                Path propsDir = resourcesRoot.resolve(PropPaths.PACK_RELATIVE.replace('/', java.io.File.separatorChar));
                Path shippedProp = propsDir.resolve(propId.trim() + ".json");
                if (Files.isRegularFile(shippedProp)) {
                    updatePropJsonIconPath(shippedProp, assetPath);
                }
                Path itemJson =
                    resourcesRoot
                        .resolve("Server/Item/Items/Aetherhaven")
                        .resolve(PropShopItemIds.forPropId(propId) + ".json");
                if (Files.isRegularFile(itemJson)) {
                    updateItemJsonIcon(itemJson, assetPath);
                }
            }
            return Result.SUCCESS;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate prop icon for %s", propId);
            return Result.IO_ERROR;
        }
    }

    @Nullable
    private static Path resolveResourcesRoot() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path candidate = cwd.resolve("src/main/resources");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        Path parent = cwd.getParent();
        if (parent != null) {
            Path alt = parent.resolve("src/main/resources");
            if (Files.isDirectory(alt)) {
                return alt;
            }
        }
        // Gradle run may use project root already nested; also try build/resources/main for syncAssets.
        Path buildResources = cwd.resolve("build/resources/main");
        if (Files.isDirectory(buildResources)) {
            return buildResources;
        }
        return null;
    }

    private static void updatePropJsonIconPath(@Nonnull Path propJson, @Nonnull String assetPath) throws Exception {
        JsonObject root = GSON.fromJson(Files.readString(propJson), JsonObject.class);
        if (root == null) {
            root = new JsonObject();
        }
        root.addProperty("iconPath", assetPath);
        Files.writeString(propJson, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void updateItemJsonIcon(@Nonnull Path itemJson, @Nonnull String assetPath) throws Exception {
        JsonObject root = GSON.fromJson(Files.readString(itemJson), JsonObject.class);
        if (root == null) {
            return;
        }
        root.addProperty("Icon", assetPath);
        Files.writeString(itemJson, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nonnull
    private static byte[] buildPrefabMultipart(@Nonnull byte[] prefab) {
        String crlf = "\r\n";
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append(crlf);
        head.append("Content-Disposition: form-data; name=\"prefab\"; filename=\"prefab.prefab.json\"").append(crlf);
        head.append("Content-Type: application/json").append(crlf).append(crlf);
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] endBytes = (crlf + "--" + BOUNDARY + "--" + crlf).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headBytes.length + prefab.length + endBytes.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(prefab, 0, out, headBytes.length, prefab.length);
        System.arraycopy(endBytes, 0, out, headBytes.length + prefab.length, endBytes.length);
        return out;
    }
}
