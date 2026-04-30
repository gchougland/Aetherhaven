package com.hexvane.aetherhaven.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Data-driven workplace output catalogs (classpath {@code Server/Aetherhaven/Production/production_catalog.json}). */
public final class ProductionCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "Server/Aetherhaven/Production/production_catalog.json";

    public record Entry(@Nonnull List<String> itemIds, int ticksPerUnit) {
        public Entry {
            itemIds = List.copyOf(itemIds);
        }

        public int catalogSize() {
            return itemIds.size();
        }

        @Nullable
        public String itemAtCursor(int cursor) {
            int n = itemIds.size();
            if (n == 0) {
                return null;
            }
            return itemIds.get(Math.floorMod(cursor, n));
        }
    }

    private final Map<String, Entry> byConstructionId;

    private ProductionCatalog(@Nonnull Map<String, Entry> byConstructionId) {
        this.byConstructionId = byConstructionId;
    }

    @Nonnull
    public static ProductionCatalog empty() {
        return new ProductionCatalog(Map.of());
    }

    @Nonnull
    public static ProductionCatalog loadFromClasspath(@Nonnull ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("Missing resource %s", RESOURCE);
                return empty();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("entries");
            Map<String, Entry> map = new LinkedHashMap<>();
            for (String constructionId : entries.keySet()) {
                JsonObject e = entries.getAsJsonObject(constructionId);
                JsonArray arr = e.getAsJsonArray("itemIds");
                List<String> ids = new ArrayList<>();
                for (var el : arr) {
                    if (el == null || !el.isJsonPrimitive()) {
                        continue;
                    }
                    String id = el.getAsString();
                    if (id != null && !id.isBlank()) {
                        ids.add(id.trim());
                    }
                }
                int ticks = 200;
                if (e.has("ticksPerUnit") && !e.get("ticksPerUnit").isJsonNull()) {
                    ticks = e.get("ticksPerUnit").getAsInt();
                }
                if (ticks < 1) {
                    ticks = 1;
                }
                map.put(constructionId, new Entry(ids, ticks));
            }
            return new ProductionCatalog(Collections.unmodifiableMap(map));
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load %s", RESOURCE);
            return empty();
        }
    }

    public boolean hasEntry(@Nonnull String constructionId) {
        Entry e = byConstructionId.get(constructionId);
        return e != null && e.catalogSize() > 0;
    }

    @Nullable
    public Entry get(@Nonnull String constructionId) {
        return byConstructionId.get(constructionId);
    }

    /** Farm, miners hut, and lumbermill use production storage and ticking. */
    public static boolean isProductionWorkplaceConstruction(@Nullable String constructionId) {
        if (constructionId == null || constructionId.isBlank()) {
            return false;
        }
        return AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(constructionId)
            || AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT.equals(constructionId)
            || AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL.equals(constructionId)
            || AetherhavenConstants.CONSTRUCTION_PLOT_BARN.equals(constructionId);
    }
}
