package com.hexvane.aetherhaven.festival.market;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Points and categories for Market Festival stall items. */
public final class MarketItemCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final String RESOURCE = "/Server/Aetherhaven/Market/market_items.json";
    private static final AtomicReference<MarketItemCatalog> LOADED = new AtomicReference<>();

    public record Entry(@Nonnull String category, int points) {}

    private final int categoryBonus;
    @Nonnull
    private final Map<String, Entry> items;

    private MarketItemCatalog(int categoryBonus, @Nonnull Map<String, Entry> items) {
        this.categoryBonus = Math.max(0, categoryBonus);
        this.items = Map.copyOf(items);
    }

    @Nonnull
    public static MarketItemCatalog get() {
        MarketItemCatalog cached = LOADED.get();
        if (cached != null) {
            return cached;
        }
        synchronized (LOADED) {
            cached = LOADED.get();
            if (cached != null) {
                return cached;
            }
            cached = load();
            LOADED.set(cached);
            return cached;
        }
    }

    static void replaceForTests(@Nonnull MarketItemCatalog catalog) {
        LOADED.set(catalog);
    }

    @Nonnull
    static MarketItemCatalog loadFromJson(@Nonnull String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        return fromRoot(root);
    }

    public int categoryBonus() {
        return categoryBonus;
    }

    @Nullable
    public Entry entry(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return items.get(itemId.trim());
    }

    public int size() {
        return items.size();
    }

    @Nonnull
    private static MarketItemCatalog load() {
        JsonObject merged = new JsonObject();
        try (InputStream in = MarketItemCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject shipped = GSON.fromJson(reader, JsonObject.class);
                    if (shipped != null) {
                        merged = shipped;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to load shipped Market Festival item catalog");
        }
        try {
            List<PackJsonFile> packFiles =
                AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.MARKET);
            for (PackJsonFile file : packFiles) {
                try (InputStream in = Files.newInputStream(file.absolutePath());
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject extra = GSON.fromJson(reader, JsonObject.class);
                    merge(merged, extra);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to merge Market Festival catalog %s", file.absolutePath());
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("No pack overlays for Market Festival item catalog");
        }
        return fromRoot(merged);
    }

    @Nonnull
    private static MarketItemCatalog fromRoot(@Nullable JsonObject root) {
        Map<String, Entry> items = new LinkedHashMap<>();
        int bonus = MarketIds.CATEGORY_BONUS;
        if (root == null) {
            return new MarketItemCatalog(bonus, items);
        }
        if (root.has("categoryBonus") && root.get("categoryBonus").isJsonPrimitive()) {
            try {
                bonus = Math.max(0, root.get("categoryBonus").getAsInt());
            } catch (RuntimeException ignored) {
                bonus = MarketIds.CATEGORY_BONUS;
            }
        }
        JsonObject rawItems = root.has("items") && root.get("items").isJsonObject()
            ? root.getAsJsonObject("items")
            : root;
        for (Map.Entry<String, JsonElement> e : rawItems.entrySet()) {
            String id = e.getKey() != null ? e.getKey().trim() : "";
            if (id.isEmpty() || "categoryBonus".equals(id) || "items".equals(id) || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject row = e.getValue().getAsJsonObject();
            String category = row.has("category") ? row.get("category").getAsString() : MarketIds.UNLISTED_CATEGORY;
            int points = row.has("points") ? row.get("points").getAsInt() : MarketIds.UNLISTED_POINTS;
            items.put(
                id,
                new Entry(
                    category != null && !category.isBlank()
                        ? category.trim().toLowerCase(Locale.ROOT)
                        : MarketIds.UNLISTED_CATEGORY,
                    Math.max(0, points)
                )
            );
        }
        return new MarketItemCatalog(bonus, items);
    }

    private static void merge(@Nonnull JsonObject into, @Nullable JsonObject extra) {
        if (extra == null) {
            return;
        }
        if (extra.has("categoryBonus")) {
            into.add("categoryBonus", extra.get("categoryBonus"));
        }
        JsonObject intoItems =
            into.has("items") && into.get("items").isJsonObject() ? into.getAsJsonObject("items") : into;
        JsonObject extraItems =
            extra.has("items") && extra.get("items").isJsonObject() ? extra.getAsJsonObject("items") : extra;
        for (Map.Entry<String, JsonElement> e : extraItems.entrySet()) {
            if (e.getKey() == null || "categoryBonus".equals(e.getKey()) || "items".equals(e.getKey())) {
                continue;
            }
            intoItems.add(e.getKey(), e.getValue());
        }
        if (!into.has("items") && extra.has("items")) {
            into.add("items", extra.get("items"));
        }
    }
}
