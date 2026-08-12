package com.hexvane.aetherhaven.prop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mutable catalog of {@link PropDefinition}s: loads shipped props from asset packs (or the classpath in tests),
 * overlays player/authored props from the plugin data directory, and supports registering + persisting new props
 * created at runtime (prefab browser "Create Prop").
 */
public final class PropCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Nonnull
    private final ConcurrentHashMap<String, PropDefinition> byId = new ConcurrentHashMap<>();

    @Nullable
    private Path dataDirectory;

    private PropCatalog() {}

    @Nonnull
    public static PropCatalog empty() {
        return new PropCatalog();
    }

    @Nonnull
    public static PropCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        return loadFromAssetPacksOrClasspath(classLoader, null);
    }

    @Nonnull
    public static PropCatalog loadFromAssetPacksOrClasspath(
        @Nonnull ClassLoader classLoader,
        @Nullable Path dataDirectory
    ) {
        PropCatalog catalog = new PropCatalog();
        catalog.dataDirectory = dataDirectory;
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PropPaths.PACK_RELATIVE);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    parseInto(in, f.packName() + ":" + f.absolutePath(), catalog.byId);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load prop file %s", f.absolutePath());
                }
            }
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, PropPaths.packPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in == null) {
                        LOGGER.atWarning().log("Prop file not found: %s", path);
                        continue;
                    }
                    parseInto(in, path, catalog.byId);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load prop file %s", path);
                }
            }
        }
        if (dataDirectory != null) {
            catalog.overlayFromDataDirectory(dataDirectory);
        }
        if (!catalog.byId.isEmpty()) {
            LOGGER.atInfo().log("Loaded %s prop(s): %s", catalog.byId.size(), catalog.byId.keySet());
        }
        return catalog;
    }

    private void overlayFromDataDirectory(@Nonnull Path dataDirectory) {
        Path dir = PropPaths.propsDirectory(dataDirectory);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        parseInto(in, p.toString(), byId);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Failed to load custom prop %s", p);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk custom props at %s", dir);
        }
    }

    @Nullable
    private static String parseInto(
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, PropDefinition> map
    ) {
        PropDefinition def;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            def = GSON.fromJson(reader, PropDefinition.class);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse prop %s", label);
            return null;
        }
        if (def == null || def.getId().isEmpty()) {
            LOGGER.atWarning().log("Skipping prop file with missing id: %s", label);
            return null;
        }
        if (def.getPrefabPath().isEmpty()) {
            LOGGER.atWarning().log("Skipping prop %s: missing prefabPath (%s)", def.getId(), label);
            return null;
        }
        if (map.containsKey(def.getId())) {
            LOGGER.atInfo().log("Prop id %s overridden by later asset (%s)", def.getId(), label);
        }
        map.put(def.getId(), def);
        return def.getId();
    }

    @Nullable
    public PropDefinition get(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return byId.get(id.trim());
    }

    public boolean contains(@Nullable String id) {
        return get(id) != null;
    }

    /** Registers (or replaces) a prop definition in memory only; call {@link #persist} to write it to disk. */
    public void register(@Nonnull PropDefinition def) {
        if (!def.getId().isEmpty()) {
            byId.put(def.getId(), def);
        }
    }

    /** Writes a prop definition JSON file to the plugin data directory. Also registers it in memory. */
    public boolean persist(@Nonnull PropDefinition def) {
        register(def);
        if (dataDirectory == null) {
            LOGGER.atWarning().log("PropCatalog has no data directory configured; cannot persist prop %s", def.getId());
            return false;
        }
        Path dir = PropPaths.propsDirectory(dataDirectory);
        Path file = PropPaths.propFile(dir, def.getId());
        try {
            Files.createDirectories(dir);
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(def, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to persist prop %s", def.getId());
            return false;
        }
    }

    @Nonnull
    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    @Nonnull
    public List<PropDefinition> list() {
        return new ArrayList<>(byId.values());
    }

    @Nonnull
    public Map<String, PropDefinition> asMap() {
        return Collections.unmodifiableMap(byId);
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
