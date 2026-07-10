package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shop prices ship in the mod jar at {@value #DEFAULT_RESOURCE}. Other mods may contribute under
 * {@link AetherhavenAssetPaths#SHOP_PRICES}. The data folder may hold a small {@value #SHOP_PRICES_FILE_NAME}
 * with {@code catalogRevision} and per-item overrides only. A legacy full copy of the catalog (from older
 * builds) is ignored in favor of the bundled file.
 *
 * <p>Merge order: bundled → asset packs (later pack wins on same item id) → data-folder overrides.
 */
public final class ShopPriceFiles {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String SHOP_PRICES_FILE_NAME = "shop_prices.json";
    private static final String DEFAULT_RESOURCE = "/defaults/shop_prices.json";
    /** Bump when the bundled defaults/shop_prices.json changes in a breaking way. */
    public static final int BUNDLED_CATALOG_REVISION = 1;
    /** Legacy installs copied the full catalog; small files are treated as intentional overrides. */
    private static final int LEGACY_OVERRIDE_MAX_ENTRIES = 64;
    private static final String OVERRIDE_TEMPLATE =
        """
        {
          "catalogRevision": %d,
          "prices": {}
        }
        """.formatted(BUNDLED_CATALOG_REVISION);

    private ShopPriceFiles() {}

    @Nonnull
    public static Path pricesPath(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory().resolve(SHOP_PRICES_FILE_NAME);
    }

    @Nonnull
    public static String readDefaultJson() throws IOException {
        try (InputStream in = ShopPriceFiles.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return "{\"catalogRevision\":%d,\"defaultGoldPrice\":5,\"prices\":{}}"
                    .formatted(BUNDLED_CATALOG_REVISION);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void ensureDefaultPricesFile(@Nonnull AetherhavenPlugin plugin) {
        Path path = pricesPath(plugin);
        if (Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.createDirectories(plugin.getDataDirectory());
            Files.writeString(path, OVERRIDE_TEMPLATE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write default %s", SHOP_PRICES_FILE_NAME);
        }
    }

    @Nonnull
    public static ShopPriceCatalog loadCatalog(@Nonnull AetherhavenPlugin plugin) {
        try {
            ShopPriceCatalog catalog = ShopPriceCatalog.parseJson(readDefaultJson());
            int packOverrideCount = 0;
            List<PackJsonFile> packFiles =
                AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.SHOP_PRICES);
            for (PackJsonFile f : packFiles) {
                try {
                    String json = Files.readString(f.absolutePath(), StandardCharsets.UTF_8);
                    ShopPriceCatalog packPrices = ShopPriceCatalog.parseJson(json);
                    int before = catalog.getExplicitPriceCount();
                    catalog = catalog.withOverrides(packPrices);
                    int added = catalog.getExplicitPriceCount() - before;
                    packOverrideCount += Math.max(0, packPrices.getExplicitPriceCount());
                    LOGGER
                        .atInfo()
                        .log(
                            "Merged shop prices from pack %s (%s explicit, catalog now %s; ~%s new/replaced keys)",
                            f.packName(),
                            packPrices.getExplicitPriceCount(),
                            catalog.getExplicitPriceCount(),
                            Math.max(added, packPrices.getExplicitPriceCount())
                        );
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to load shop prices from %s", f.absolutePath());
                }
            }
            Path path = pricesPath(plugin);
            if (!Files.isRegularFile(path)) {
                logLoadedCatalog(plugin, catalog, null, 0, packOverrideCount);
                return catalog;
            }
            String dataJson = Files.readString(path, StandardCharsets.UTF_8);
            ShopPriceCatalog data = ShopPriceCatalog.parseJson(dataJson);
            if (data.getCatalogRevision() >= BUNDLED_CATALOG_REVISION) {
                ShopPriceCatalog merged = catalog.withOverrides(data);
                logLoadedCatalog(plugin, merged, path, data.getExplicitPriceCount(), packOverrideCount);
                return merged;
            }
            if (data.getExplicitPriceCount() > 0 && data.getExplicitPriceCount() <= LEGACY_OVERRIDE_MAX_ENTRIES) {
                ShopPriceCatalog merged = catalog.withOverrides(data);
                LOGGER
                    .atInfo()
                    .log(
                        "Loaded shop prices: catalog with %d override(s) from legacy %s (add catalogRevision: %d to that file to silence this).",
                        data.getExplicitPriceCount(),
                        path,
                        BUNDLED_CATALOG_REVISION
                    );
                logLoadedCatalog(plugin, merged, path, data.getExplicitPriceCount(), packOverrideCount);
                return merged;
            }
            backupLegacyCatalog(path);
            logLoadedCatalog(plugin, catalog, path, 0, packOverrideCount);
            return catalog;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load shop price catalog; using empty catalog");
            try {
                return ShopPriceCatalog.parseJson(readDefaultJson());
            } catch (IOException e2) {
                return ShopPriceCatalog.empty();
            }
        }
    }

    private static void backupLegacyCatalog(@Nonnull Path path) {
        Path backup = path.resolveSibling(SHOP_PRICES_FILE_NAME + ".bak");
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER
                .atWarning()
                .log(
                    "Ignored outdated full %s at %s (replaced by bundled catalog). Backed up to %s. Use a small override file with catalogRevision: %d for custom prices.",
                    SHOP_PRICES_FILE_NAME,
                    path,
                    backup,
                    BUNDLED_CATALOG_REVISION
                );
        } catch (IOException e) {
            LOGGER
                .atWarning()
                .withCause(e)
                .log(
                    "Outdated %s at %s is still in use; delete it or set catalogRevision: %d. Using bundled catalog for this session.",
                    SHOP_PRICES_FILE_NAME,
                    path,
                    BUNDLED_CATALOG_REVISION
                );
        }
    }

    private static void logLoadedCatalog(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopPriceCatalog catalog,
        @Nullable Path overridePath,
        int overrideCount,
        int packPriceKeys
    ) {
        if (overridePath == null) {
            LOGGER
                .atInfo()
                .log(
                    "Loaded shop prices: %d entries (bundled + %d pack price key(s); no %s in %s).",
                    catalog.getExplicitPriceCount(),
                    packPriceKeys,
                    SHOP_PRICES_FILE_NAME,
                    plugin.getDataDirectory()
                );
        } else {
            LOGGER
                .atInfo()
                .log(
                    "Loaded shop prices: %d entries (%d pack price key(s), %d data override(s) from %s).",
                    catalog.getExplicitPriceCount(),
                    packPriceKeys,
                    overrideCount,
                    overridePath
                );
        }
    }
}
