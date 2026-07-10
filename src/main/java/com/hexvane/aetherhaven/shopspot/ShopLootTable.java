package com.hexvane.aetherhaven.shopspot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ShopLootTable {
    public static final class Entry {
        private final String itemId;
        private final int weight;
        private final int stockMin;
        private final int stockMax;

        public Entry(@Nonnull String itemId, int weight, int stockMin, int stockMax) {
            this.itemId = itemId;
            this.weight = Math.max(0, weight);
            int min = Math.max(1, stockMin);
            int max = Math.max(1, stockMax);
            if (max < min) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            this.stockMin = min;
            this.stockMax = max;
        }

        @Nonnull
        public String getItemId() {
            return itemId;
        }

        public int getWeight() {
            return weight;
        }

        public int getStockMin() {
            return stockMin;
        }

        public int getStockMax() {
            return stockMax;
        }
    }

    private final List<Entry> entries;
    private final int totalWeight;

    private ShopLootTable(@Nonnull List<Entry> entries) {
        this.entries = entries;
        int sum = 0;
        for (Entry e : entries) {
            sum += e.weight;
        }
        this.totalWeight = sum;
    }

    @Nonnull
    public static ShopLootTable empty() {
        return new ShopLootTable(List.of());
    }

    /** Parsed loot JSON including optional {@code replace} flag for pack merge layers. */
    public record Parsed(@Nonnull ShopLootTable table, boolean replace) {}

    @Nonnull
    public static ShopLootTable parseJson(@Nonnull String json) {
        return parseJsonWithFlags(json).table();
    }

    @Nonnull
    public static Parsed parseJsonWithFlags(@Nonnull String json) {
        List<Entry> list = new ArrayList<>();
        boolean replace = false;
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return new Parsed(new ShopLootTable(list), false);
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("replace") && obj.get("replace").isJsonPrimitive()) {
            replace = obj.get("replace").getAsBoolean();
        }
        JsonArray arr = obj.getAsJsonArray("entries");
        if (arr == null) {
            return new Parsed(new ShopLootTable(list), replace);
        }
        int defaultMin = AetherhavenConstants.SHOP_LOOT_DEFAULT_STOCK_MIN;
        int defaultMax = AetherhavenConstants.SHOP_LOOT_DEFAULT_STOCK_MAX;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject row = el.getAsJsonObject();
            String itemId = row.has("itemId") ? row.get("itemId").getAsString() : "";
            int weight = row.has("weight") ? row.get("weight").getAsInt() : 1;
            int stockMin = row.has("stockMin") ? row.get("stockMin").getAsInt() : defaultMin;
            int stockMax = row.has("stockMax") ? row.get("stockMax").getAsInt() : defaultMax;
            if (itemId != null && !itemId.isBlank() && weight > 0) {
                list.add(new Entry(itemId.trim(), weight, stockMin, stockMax));
            }
        }
        return new Parsed(new ShopLootTable(list), replace);
    }

    /** Append another table's entries (pack merge without replace). */
    @Nonnull
    public ShopLootTable withAppended(@Nonnull ShopLootTable other) {
        if (other.entries.isEmpty()) {
            return this;
        }
        if (entries.isEmpty()) {
            return other;
        }
        List<Entry> merged = new ArrayList<>(entries.size() + other.entries.size());
        merged.addAll(entries);
        merged.addAll(other.entries);
        return new ShopLootTable(merged);
    }

    @Nonnull
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int entryCount() {
        return entries.size();
    }

    @Nonnull
    public static ShopLootTable loadFromFile(@Nonnull Path path, @Nonnull String fallbackJson) throws IOException {
        if (!Files.isRegularFile(path)) {
            return parseJson(fallbackJson);
        }
        return parseJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    @Nullable
    public Entry rollEntry() {
        if (entries.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (Entry e : entries) {
            acc += e.weight;
            if (roll < acc) {
                return isValidItem(e.itemId) ? e : null;
            }
        }
        Entry last = entries.get(entries.size() - 1);
        return isValidItem(last.itemId) ? last : null;
    }

    @Nullable
    public Entry rollEntryWithRetries(int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            Entry entry = rollEntry();
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isValidItem(@Nonnull String itemId) {
        return Item.getAssetMap().getAsset(itemId) != null;
    }
}
