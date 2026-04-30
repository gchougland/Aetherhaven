package com.hexvane.aetherhaven.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Data-driven workplace output catalogs (classpath {@code Server/Aetherhaven/Production/production_catalog.json}). */
public final class ProductionCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RESOURCE = "Server/Aetherhaven/Production/production_catalog.json";

    /** Upper bound for {@code maxStorage} in JSON (per output line). */
    public static final long MAX_STORAGE_PER_OUTPUT = 1_000_000L;

    /**
     * One catalog line: item id and how many {@link com.hexvane.aetherhaven.production.ProductionTickSystem} entity ticks
     * are required to generate one unit in a slot that is set to this line.
     */
    public static final class Entry {
        private final List<String> itemIds;
        private final int[] ticksByIndex;
        private final long[] maxStorageByIndex;

        private Entry(@Nonnull List<String> itemIds, @Nonnull int[] ticksByIndex, @Nonnull long[] maxStorageByIndex) {
            if (ticksByIndex.length != itemIds.size() || maxStorageByIndex.length != itemIds.size()) {
                throw new IllegalArgumentException("array lengths must match itemIds");
            }
            this.itemIds = List.copyOf(itemIds);
            this.ticksByIndex = ticksByIndex.clone();
            this.maxStorageByIndex = maxStorageByIndex.clone();
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

        /** Ticks required for one unit of the item at {@code cursor} modulo catalog size. */
        public int ticksAtCursor(int cursor) {
            int n = itemIds.size();
            if (n == 0) {
                return 1;
            }
            int idx = Math.floorMod(cursor, n);
            return Math.max(1, ticksByIndex[idx]);
        }

        /** Max stored amount for the catalog line at {@code cursor} (modulo size). */
        public long maxStorageAtCursor(int cursor) {
            int n = itemIds.size();
            if (n == 0) {
                return AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
            }
            int idx = Math.floorMod(cursor, n);
            return Math.max(1L, Math.min(MAX_STORAGE_PER_OUTPUT, maxStorageByIndex[idx]));
        }

        /**
         * Max stored amount for {@code itemId} in this workplace catalog: highest {@code maxStorage} among lines with
         * that item (one shared pool per item id).
         */
        public long maxStorageForItem(@Nonnull String itemId) {
            if (itemId.isBlank()) {
                return AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
            }
            long best = 0L;
            for (int i = 0; i < itemIds.size(); i++) {
                if (itemId.equals(itemIds.get(i))) {
                    best = Math.max(best, maxStorageByIndex[i]);
                }
            }
            if (best <= 0L) {
                return AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
            }
            return Math.max(1L, Math.min(MAX_STORAGE_PER_OUTPUT, best));
        }

        @Nonnull
        public static String formatSecondsForTicks(int ticks) {
            double sec = ticks / AetherhavenConstants.PRODUCTION_ENTITY_TICKS_PER_SECOND;
            if (sec >= 100.0) {
                return String.format(Locale.US, "%.0f s", sec);
            }
            if (sec >= 10.0) {
                return String.format(Locale.US, "%.1f s", sec);
            }
            if (sec >= 1.0) {
                return String.format(Locale.US, "%.1f s", sec);
            }
            return String.format(Locale.US, "%.2f s", sec);
        }

        /**
         * Converts summed vanilla crop {@code Farming.Stages} {@code Duration.Min} values (milliseconds in vanilla
         * assets) into workplace production ticks for {@link #formatSecondsForTicks(int)} to match in-world grow
         * pacing at {@link AetherhavenConstants#PRODUCTION_ENTITY_TICKS_PER_SECOND}.
         */
        public static int productionTicksFromCropGrowDurationMs(int totalDurationMs) {
            if (totalDurationMs <= 0) {
                return 1;
            }
            double ticks = (totalDurationMs * AetherhavenConstants.PRODUCTION_ENTITY_TICKS_PER_SECOND) / 1000.0;
            int t = (int) Math.ceil(ticks);
            return Math.max(1, t);
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
                Entry built = parseEntry(e, constructionId);
                if (built != null && built.catalogSize() > 0) {
                    map.put(constructionId, built);
                }
            }
            return new ProductionCatalog(Collections.unmodifiableMap(map));
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load %s", RESOURCE);
            return empty();
        }
    }

    @Nullable
    private static Entry parseEntry(@Nonnull JsonObject e, @Nonnull String constructionId) {
        if (e.has("outputs") && e.get("outputs").isJsonArray()) {
            JsonArray arr = e.getAsJsonArray("outputs");
            List<String> ids = new ArrayList<>();
            List<Integer> ticks = new ArrayList<>();
            List<Long> maxStorage = new ArrayList<>();
            long defaultMax = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String id = readString(o, "itemId");
                if (id == null || id.isBlank()) {
                    id = readString(o, "id");
                }
                if (id == null || id.isBlank()) {
                    continue;
                }
                int t = readPositiveInt(o, "ticks", 200);
                ids.add(id.trim());
                ticks.add(t);
                long maxSt = readPositiveLong(o, "maxStorage", defaultMax);
                maxSt = Math.max(1L, Math.min(MAX_STORAGE_PER_OUTPUT, maxSt));
                maxStorage.add(maxSt);
            }
            if (ids.isEmpty()) {
                LOGGER.atWarning().log("Production entry %s has empty outputs array", constructionId);
                return null;
            }
            return new Entry(ids, toIntArray(ticks), toLongArray(maxStorage));
        }

        JsonArray arr = e.getAsJsonArray("itemIds");
        if (arr == null) {
            LOGGER.atWarning().log("Production entry %s missing itemIds and outputs", constructionId);
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonPrimitive()) {
                continue;
            }
            String id = el.getAsString();
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        int defTicks = 200;
        if (e.has("ticksPerUnit") && !e.get("ticksPerUnit").isJsonNull()) {
            defTicks = e.get("ticksPerUnit").getAsInt();
        }
        defTicks = Math.max(1, defTicks);
        int[] byIdx = new int[ids.size()];
        for (int i = 0; i < byIdx.length; i++) {
            byIdx[i] = defTicks;
        }
        if (e.has("ticksOverrides") && e.get("ticksOverrides").isJsonObject()) {
            JsonObject ov = e.getAsJsonObject("ticksOverrides");
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (ov.has(id) && !ov.get(id).isJsonNull()) {
                    int t = ov.get(id).getAsInt();
                    byIdx[i] = Math.max(1, t);
                }
            }
        }
        long defMax = AetherhavenConstants.PRODUCTION_STORAGE_PER_ITEM_MAX;
        long[] maxByIdx = new long[ids.size()];
        Arrays.fill(maxByIdx, defMax);
        if (e.has("maxStorageOverrides") && e.get("maxStorageOverrides").isJsonObject()) {
            JsonObject msov = e.getAsJsonObject("maxStorageOverrides");
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (msov.has(id) && !msov.get(id).isJsonNull()) {
                    long mx = msov.get(id).getAsLong();
                    maxByIdx[i] = Math.max(1L, Math.min(MAX_STORAGE_PER_OUTPUT, mx));
                }
            }
        }
        return new Entry(ids, byIdx, maxByIdx);
    }

    @Nullable
    private static String readString(@Nonnull JsonObject o, @Nonnull String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    private static int readPositiveInt(@Nonnull JsonObject o, @Nonnull String key, int defaultValue) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return Math.max(1, defaultValue);
        }
        int v = o.get(key).getAsInt();
        return Math.max(1, v);
    }

    private static long readPositiveLong(@Nonnull JsonObject o, @Nonnull String key, long defaultValue) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return Math.max(1L, defaultValue);
        }
        long v = o.get(key).getAsLong();
        return Math.max(1L, v);
    }

    @Nonnull
    private static int[] toIntArray(@Nonnull List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = Math.max(1, list.get(i));
        }
        return a;
    }

    @Nonnull
    private static long[] toLongArray(@Nonnull List<Long> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = Math.max(1L, Math.min(MAX_STORAGE_PER_OUTPUT, list.get(i)));
        }
        return a;
    }

    public boolean hasEntry(@Nonnull String constructionId) {
        Entry e = byConstructionId.get(constructionId);
        return e != null && e.catalogSize() > 0;
    }

    @Nullable
    public Entry get(@Nonnull String constructionId) {
        return byConstructionId.get(constructionId);
    }

    /** Farm, miners hut, lumbermill, and barn use production storage and ticking. */
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
