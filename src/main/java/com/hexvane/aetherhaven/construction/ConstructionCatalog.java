package com.hexvane.aetherhaven.construction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Construction definitions from {@code Server/Aetherhaven/Buildings/} under each asset pack (plus classpath fallback).
 */
public final class ConstructionCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, ConstructionDefinition> byId;

    private ConstructionCatalog(Map<String, ConstructionDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static ConstructionCatalog empty() {
        return new ConstructionCatalog(Collections.emptyMap());
    }

    @Nonnull
    public static ConstructionCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, ConstructionDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.BUILDINGS);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadFromStream(gson, in, f.packName() + ":" + f.absolutePath(), map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load construction file %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log("Loaded %s construction(s) from asset packs under %s", map.size(), AetherhavenAssetPaths.BUILDINGS);
        } else {
            List<String> paths = ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.buildingsPrefix());
            for (String path : paths) {
                loadFromClasspath(classLoader, gson, path, map);
            }
            LOGGER.atInfo().log("Loaded %s construction(s) from classpath %s", map.size(), AetherhavenAssetPaths.buildingsPrefix());
        }
        validateCountsAsAliases(map);
        return new ConstructionCatalog(Collections.unmodifiableMap(map));
    }

    private static void validateCountsAsAliases(@Nonnull Map<String, ConstructionDefinition> map) {
        for (ConstructionDefinition def : map.values()) {
            String raw = def.getCountsAsConstructionIdRaw();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String target = raw.trim();
            if (target.equals(def.getId())) {
                LOGGER.atWarning().log("Construction %s countsAsConstructionId points to itself", def.getId());
                continue;
            }
            if (!map.containsKey(target)) {
                LOGGER.atWarning().log("Construction %s countsAsConstructionId '%s' missing from catalog", def.getId(), target);
                continue;
            }
            Set<String> chain = new HashSet<>();
            String walk = def.getId();
            int guard = 0;
            while (walk != null && guard++ < 64) {
                if (!chain.add(walk)) {
                    LOGGER.atWarning().log("Construction countsAsConstructionId cycle involving %s", def.getId());
                    break;
                }
                ConstructionDefinition step = map.get(walk);
                if (step == null) {
                    break;
                }
                String next = step.getCountsAsConstructionIdRaw();
                if (next == null || next.isBlank()) {
                    break;
                }
                walk = next.trim();
            }
        }
    }

    private static void loadFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, ConstructionDefinition> map
    ) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            ConstructionDefinition def = gson.fromJson(reader, ConstructionDefinition.class);
            putDef(def, label, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse construction %s", label);
        }
    }

    private static void loadFromClasspath(
        @Nonnull ClassLoader classLoader,
        @Nonnull Gson gson,
        @Nonnull String path,
        @Nonnull Map<String, ConstructionDefinition> map
    ) {
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.atWarning().log("Construction file not found: %s", path);
                return;
            }
            loadFromStream(gson, in, path, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load construction file %s", path);
        }
    }

    private static void putDef(
        @Nullable ConstructionDefinition def,
        @Nonnull String label,
        @Nonnull Map<String, ConstructionDefinition> map
    ) {
        if (def == null || def.getId() == null || def.getId().isBlank()) {
            LOGGER.atWarning().log("Skipping construction file with missing id: %s", label);
            return;
        }
        if (def.getPrefabPath() == null || def.getPrefabPath().isBlank()) {
            LOGGER.atWarning().log("Skipping construction %s: missing prefabPath (%s)", def.getId(), label);
            return;
        }
        String id = def.getId();
        Path prefabResolved = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabResolved == null) {
            LOGGER.atWarning().log(
                "Construction %s prefabPath '%s' not found in asset packs (may load later) (%s)",
                id,
                def.getPrefabPath(),
                label
            );
        }
        if (map.containsKey(id)) {
            LOGGER.atInfo().log("Construction id %s overridden by later asset (%s)", id, label);
        }
        map.put(id, def);
    }

    @Nullable
    public ConstructionDefinition get(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return byId.get(id);
    }

    @Nonnull
    public List<ConstructionDefinition> list() {
        return new ArrayList<>(byId.values());
    }

    @Nonnull
    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    /**
     * Resolves {@code plotStoredConstructionId} (variant or canonical) to the gameplay construction id used for
     * production catalogs, quests, and workplace matching.
     */
    @Nonnull
    public String resolveGameplayConstructionId(@Nullable String plotStoredConstructionId) {
        if (plotStoredConstructionId == null || plotStoredConstructionId.isBlank()) {
            return "";
        }
        Set<String> visited = new HashSet<>();
        String current = plotStoredConstructionId.trim();
        for (int i = 0; i < 64; i++) {
            if (!visited.add(current)) {
                return current;
            }
            ConstructionDefinition def = byId.get(current);
            if (def == null) {
                return current;
            }
            String alias = def.getCountsAsConstructionIdRaw();
            if (alias == null || alias.isBlank()) {
                return def.getId() != null ? def.getId() : current;
            }
            current = alias.trim();
        }
        return plotStoredConstructionId.trim();
    }

    /** True when the canonical definition for {@code gameplayConstructionId} opts into random multi-plot schedule picks. */
    public boolean isScheduleSharedUtilityGameplay(@Nonnull String gameplayConstructionId) {
        String g = gameplayConstructionId.trim();
        if (g.isEmpty()) {
            return false;
        }
        ConstructionDefinition canon = byId.get(g);
        return canon != null && canon.isScheduleSharedUtilityPick();
    }
}
