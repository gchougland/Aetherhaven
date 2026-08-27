package com.hexvane.aetherhaven.festival;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Festival definitions from {@link AetherhavenAssetPaths#FESTIVALS} under each asset pack (plus classpath fallback and
 * the plugin data directory for festivals authored in the plot creator). Later sources replace the same id, so another
 * mod can either add festivals or override a shipped one.
 */
public final class FestivalCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, FestivalDefinition> byId;
    private final Set<String> customFestivalIds;
    private final FestivalGreetingLangIndex greetingLangIndex;

    private FestivalCatalog(
        @Nonnull Map<String, FestivalDefinition> byId,
        @Nonnull Set<String> customFestivalIds,
        @Nonnull FestivalGreetingLangIndex greetingLangIndex
    ) {
        this.byId = byId;
        this.customFestivalIds = customFestivalIds;
        this.greetingLangIndex = greetingLangIndex;
    }

    @Nonnull
    public static FestivalCatalog empty() {
        return new FestivalCatalog(Collections.emptyMap(), Set.of(), FestivalGreetingLangIndex.empty());
    }

    @Nonnull
    public static FestivalCatalog forTests(@Nonnull List<FestivalDefinition> defs) {
        return forTests(defs, FestivalGreetingLangIndex.empty());
    }

    @Nonnull
    public static FestivalCatalog forTests(
        @Nonnull List<FestivalDefinition> defs,
        @Nonnull FestivalGreetingLangIndex greetingLangIndex
    ) {
        Map<String, FestivalDefinition> map = new LinkedHashMap<>();
        for (FestivalDefinition def : defs) {
            if (def != null && !def.getId().isEmpty()) {
                map.put(def.getId(), def);
            }
        }
        return new FestivalCatalog(Collections.unmodifiableMap(map), Set.of(), greetingLangIndex);
    }

    @Nonnull
    public static FestivalCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        return loadFromAssetPacksOrClasspath(classLoader, null);
    }

    @Nonnull
    public static FestivalCatalog loadFromAssetPacksOrClasspath(
        @Nonnull ClassLoader classLoader,
        @Nullable Path dataDirectory
    ) {
        Gson gson = new GsonBuilder().create();
        Map<String, FestivalDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.FESTIVALS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    parseInto(gson, in, f.packName() + ":" + f.absolutePath(), map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load festival file %s", f.absolutePath());
                }
            }
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.festivalsPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in == null) {
                        LOGGER.atWarning().log("Festival file not found: %s", path);
                        continue;
                    }
                    parseInto(gson, in, path, map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load festival file %s", path);
                }
            }
        }
        Set<String> customIds = Set.of();
        if (dataDirectory != null) {
            customIds = overlayFromDataDirectory(gson, dataDirectory, map);
        }
        warnOnDuplicateDays(map);
        if (!map.isEmpty()) {
            LOGGER.atInfo().log("Loaded %s festival(s): %s", map.size(), map.keySet());
        }
        FestivalGreetingLangIndex greetingLangIndex = FestivalGreetingLangIndex.load(classLoader);
        return new FestivalCatalog(Collections.unmodifiableMap(map), customIds, greetingLangIndex);
    }

    @Nonnull
    private static Set<String> overlayFromDataDirectory(
        @Nonnull Gson gson,
        @Nonnull Path dataDirectory,
        @Nonnull Map<String, FestivalDefinition> map
    ) {
        Path dir = CustomFestivalPaths.festivalsDirectory(dataDirectory);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        String id = parseInto(gson, in, p.toString(), map);
                        if (id != null) {
                            ids.add(id);
                        }
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Failed to load custom festival %s", p);
                    }
                });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to walk custom festivals at %s", dir);
        }
        if (!ids.isEmpty()) {
            LOGGER.atInfo().log("Loaded %s custom festival(s) from %s", ids.size(), dir);
        }
        return Set.copyOf(ids);
    }

    @Nullable
    private static String parseInto(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, FestivalDefinition> map
    ) {
        FestivalDefinition def;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            def = gson.fromJson(reader, FestivalDefinition.class);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse festival %s", label);
            return null;
        }
        if (def == null || def.getId().isEmpty()) {
            LOGGER.atWarning().log("Skipping festival file with missing id: %s", label);
            return null;
        }
        if (def.getPrefabPath().isEmpty()) {
            LOGGER.atWarning().log("Skipping festival %s: missing prefabPath (%s)", def.getId(), label);
            return null;
        }
        String sizeProblem = FestivalPrefabMetrics.validateFestivalSize(def.getPrefabPath());
        if (sizeProblem != null) {
            LOGGER.atWarning().log(
                "Festival %s prefab %s is the wrong size (%s); it may not line up with the festival square",
                def.getId(),
                def.getPrefabPath(),
                sizeProblem
            );
        }
        if (map.containsKey(def.getId())) {
            LOGGER.atInfo().log("Festival id %s overridden by later asset (%s)", def.getId(), label);
        }
        map.put(def.getId(), def);
        return def.getId();
    }

    private static void warnOnDuplicateDays(@Nonnull Map<String, FestivalDefinition> map) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (FestivalDefinition def : map.values()) {
            if (def.isLook()) {
                continue;
            }
            String key = def.getSeason().name() + ":" + def.getDayOfSeason();
            String previous = seen.put(key, def.getId());
            if (previous != null) {
                LOGGER.atWarning().log(
                    "Festivals %s and %s share %s %s; only one can run that day",
                    previous,
                    def.getId(),
                    def.getSeason().displayName(),
                    def.getDayOfSeason()
                );
            }
        }
    }

    @Nonnull
    public FestivalGreetingLangIndex greetingLangIndex() {
        return greetingLangIndex;
    }

    public boolean isCustomFestival(@Nullable String id) {
        return id != null && customFestivalIds.contains(id.trim());
    }

    @Nonnull
    public Set<String> customFestivalIds() {
        return customFestivalIds;
    }

    @Nullable
    public FestivalDefinition get(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return byId.get(id.trim());
    }

    @Nonnull
    public List<FestivalDefinition> list() {
        return new ArrayList<>(byId.values());
    }

    @Nonnull
    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** The festival scheduled on this calendar day, or null when the day has none. Looks never occupy a day. */
    @Nullable
    public FestivalDefinition festivalOn(@Nonnull AetherhavenCalendar.Season season, int dayOfSeason) {
        for (FestivalDefinition def : byId.values()) {
            if (def.isLook()) {
                continue;
            }
            if (def.getSeason() == season && def.getDayOfSeason() == dayOfSeason) {
                return def;
            }
        }
        return null;
    }

    /** Holidays only: catalog entries that are not looks. */
    @Nonnull
    public List<FestivalDefinition> listBases() {
        List<FestivalDefinition> out = new ArrayList<>();
        for (FestivalDefinition def : byId.values()) {
            if (!def.isLook()) {
                out.add(def);
            }
        }
        return out;
    }
}
