package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * Shop loot tables: embedded defaults under {@code defaults/shop_loot/}, optional pack contributions under
 * {@link AetherhavenAssetPaths#SHOP_LOOT}, and optional full replacements in the plugin data {@code shop_loot/}
 * folder.
 *
 * <p>Merge order per table id: embedded → pack layers (append, or replace when {@code "replace": true}) →
 * data-folder file (full replace when present).
 */
public final class ShopLootFiles {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String LOOT_DIR = "shop_loot";
    private static final String EMBEDDED_LOOT_PREFIX = "defaults/shop_loot/";
    /** Any bundled table under {@code defaults/shop_loot/}; used to locate the folder in dev and in the jar. */
    private static final String[] EMBEDDED_LOOT_ANCHORS = {
        EMBEDDED_LOOT_PREFIX + "crystal_keeper_shards.json",
        EMBEDDED_LOOT_PREFIX + "florist_common.json",
        EMBEDDED_LOOT_PREFIX + "merchant_food.json",
    };

    private ShopLootFiles() {}

    @Nonnull
    public static Path lootDir(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory().resolve(LOOT_DIR);
    }

    @Nonnull
    public static Path lootTablePath(@Nonnull AetherhavenPlugin plugin, @Nonnull String tableId) {
        String safe = sanitizeTableId(tableId);
        return lootDir(plugin).resolve(safe + ".json");
    }

    @Nonnull
    public static String readEmbeddedLootJson(@Nonnull String tableId) throws IOException {
        String safe = sanitizeTableId(tableId);
        String resource = "/defaults/shop_loot/" + safe + ".json";
        try (InputStream in = ShopLootFiles.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "{\"entries\":[]}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Ensures the data {@code shop_loot} directory exists. Does not seed full table copies so pack merges can
     * extend bundled tables; place a file in the data folder only when you want a full admin override.
     */
    public static void ensureDefaultLootTables(@Nonnull AetherhavenPlugin plugin) {
        try {
            Files.createDirectories(lootDir(plugin));
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to ensure shop loot directory");
        }
    }

    @Nonnull
    public static ShopLootTable loadTable(@Nonnull AetherhavenPlugin plugin, @Nonnull String tableId) {
        String safe = sanitizeTableId(tableId);
        try {
            ShopLootTable merged = ShopLootTable.parseJson(readEmbeddedLootJson(safe));
            merged = applyPackLayers(safe, merged);
            Path path = lootTablePath(plugin, safe);
            if (!Files.isRegularFile(path)) {
                return merged;
            }
            String onDisk = Files.readString(path, StandardCharsets.UTF_8);
            try {
                return ShopLootTable.parseJson(onDisk);
            } catch (RuntimeException parseError) {
                LOGGER
                    .atWarning()
                    .withCause(parseError)
                    .log("Invalid shop loot table %s at %s; using embedded+pack merge", safe, path);
                return merged;
            }
        } catch (IOException e) {
            try {
                return applyPackLayers(safe, ShopLootTable.parseJson(readEmbeddedLootJson(safe)));
            } catch (IOException e2) {
                return ShopLootTable.empty();
            }
        }
    }

    @Nonnull
    static ShopLootTable applyPackLayers(@Nonnull String tableId, @Nonnull ShopLootTable base) {
        ShopLootTable merged = base;
        for (PackJsonFile f : packFilesForTable(tableId)) {
            try {
                String json = Files.readString(f.absolutePath(), StandardCharsets.UTF_8);
                ShopLootTable.Parsed parsed = ShopLootTable.parseJsonWithFlags(json);
                if (parsed.replace()) {
                    merged = parsed.table();
                    LOGGER
                        .atInfo()
                        .log(
                            "Shop loot table %s replaced by pack %s (%s entries)",
                            tableId,
                            f.packName(),
                            merged.entryCount()
                        );
                } else {
                    int before = merged.entryCount();
                    merged = merged.withAppended(parsed.table());
                    LOGGER
                        .atInfo()
                        .log(
                            "Shop loot table %s appended %s entries from pack %s (now %s)",
                            tableId,
                            merged.entryCount() - before,
                            f.packName(),
                            merged.entryCount()
                        );
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to merge shop loot from %s", f.absolutePath());
            }
        }
        return merged;
    }

    @Nonnull
    private static List<PackJsonFile> packFilesForTable(@Nonnull String tableId) {
        String want = tableId + ".json";
        List<PackJsonFile> out = new ArrayList<>();
        for (PackJsonFile f : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.SHOP_LOOT)) {
            if (want.equalsIgnoreCase(f.absolutePath().getFileName().toString())) {
                out.add(f);
            }
        }
        return out;
    }

    @Nonnull
    public static String[] knownTableIds() {
        List<String> ids = listEmbeddedDefaultTableIds();
        return ids.toArray(String[]::new);
    }

    /** All loot table ids from packaged defaults, asset packs, and {@code shop_loot/*.json} in plugin data. */
    @Nonnull
    public static List<String> listLootTableIds(@Nonnull AetherhavenPlugin plugin) {
        Set<String> ids = new LinkedHashSet<>(listEmbeddedDefaultTableIds());
        for (PackJsonFile f : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.SHOP_LOOT)) {
            String name = f.absolutePath().getFileName().toString();
            if (name.endsWith(".json")) {
                String id = name.substring(0, name.length() - 5);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        Path dir = lootDir(plugin);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .filter(id -> !id.isBlank())
                    .forEach(ids::add);
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to list shop loot tables in %s", dir);
            }
        }
        return new ArrayList<>(ids);
    }

    /** Merge helper for tests: apply pack layer JSON strings in order onto a base table. */
    @Nonnull
    public static ShopLootTable mergeLayers(@Nonnull ShopLootTable base, @Nonnull List<String> layerJsons) {
        ShopLootTable merged = base;
        for (String json : layerJsons) {
            ShopLootTable.Parsed parsed = ShopLootTable.parseJsonWithFlags(json);
            merged = parsed.replace() ? parsed.table() : merged.withAppended(parsed.table());
        }
        return merged;
    }

    @Nonnull
    private static String sanitizeTableId(@Nonnull String tableId) {
        String safe = tableId.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return safe.isBlank() ? "default" : safe;
    }

    @Nonnull
    private static List<String> listEmbeddedDefaultTableIds() {
        Set<String> ids = new LinkedHashSet<>();
        ClassLoader classLoader = ShopLootFiles.class.getClassLoader();
        URL anchor = findEmbeddedLootAnchor(classLoader);
        if (anchor == null) {
            LOGGER.atWarning().log(
                "No bundled shop loot tables found under %s; dropdown will only list files in the plugin data folder",
                EMBEDDED_LOOT_PREFIX
            );
            return new ArrayList<>(ids);
        }
        try {
            if ("jar".equalsIgnoreCase(anchor.getProtocol())) {
                scanEmbeddedJar(anchor, ids);
            } else {
                scanEmbeddedDirectory(anchor, ids);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to list embedded shop loot tables");
        }
        return new ArrayList<>(ids);
    }

    @Nonnull
    private static URL findEmbeddedLootAnchor(@Nonnull ClassLoader classLoader) {
        for (String resource : EMBEDDED_LOOT_ANCHORS) {
            URL url = classLoader.getResource(resource);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static void scanEmbeddedJar(@Nonnull URL anchorJarEntry, @Nonnull Set<String> ids) throws IOException {
        JarURLConnection conn = (JarURLConnection) anchorJarEntry.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith(EMBEDDED_LOOT_PREFIX) && name.endsWith(".json")) {
                    addTableIdFromResourcePath(name, ids);
                }
            }
        }
    }

    private static void scanEmbeddedDirectory(@Nonnull URL anchorResource, @Nonnull Set<String> ids) throws Exception {
        Path dir = Path.of(anchorResource.toURI()).getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> EMBEDDED_LOOT_PREFIX + name)
                .forEach(path -> addTableIdFromResourcePath(path, ids));
        }
    }

    private static void addTableIdFromResourcePath(@Nonnull String resourcePath, @Nonnull Set<String> ids) {
        if (!resourcePath.startsWith(EMBEDDED_LOOT_PREFIX) || !resourcePath.endsWith(".json")) {
            return;
        }
        String id = resourcePath.substring(EMBEDDED_LOOT_PREFIX.length(), resourcePath.length() - 5);
        if (!id.isBlank()) {
            ids.add(id);
        }
    }
}
