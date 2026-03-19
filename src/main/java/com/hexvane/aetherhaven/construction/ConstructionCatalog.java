package com.hexvane.aetherhaven.construction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ConstructionCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, ConstructionDefinition> byId;

    private ConstructionCatalog(Map<String, ConstructionDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static ConstructionCatalog loadFromClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, ConstructionDefinition> map = new LinkedHashMap<>();
        try (InputStream in = classLoader.getResourceAsStream("Server/Constructions/constructions.json")) {
            if (in == null) {
                LOGGER.atWarning().log("No Server/Constructions/constructions.json on classpath; construction catalog empty");
                return new ConstructionCatalog(Collections.emptyMap());
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                ConstructionsFile file = gson.fromJson(reader, ConstructionsFile.class);
                for (ConstructionDefinition def : file.getConstructions()) {
                    if (def.getId() == null || def.getId().isBlank()) {
                        LOGGER.atWarning().log("Skipping construction with missing id");
                        continue;
                    }
                    if (def.getPrefabPath() == null || def.getPrefabPath().isBlank()) {
                        LOGGER.atWarning().log("Skipping construction %s: missing prefabPath", def.getId());
                        continue;
                    }
                    Path prefabResolved = PrefabStore.get().findAssetPrefabPath(def.getPrefabPath());
                    if (prefabResolved == null) {
                        LOGGER.atWarning().log(
                            "Construction %s prefabPath '%s' not found in asset packs (may load later)",
                            def.getId(),
                            def.getPrefabPath()
                        );
                    }
                    map.put(def.getId(), def);
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load construction catalog");
            return new ConstructionCatalog(Collections.emptyMap());
        }
        return new ConstructionCatalog(Collections.unmodifiableMap(map));
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
}
