package com.hexvane.aetherhaven.geode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Weighted loot for opening a geode; loaded from {@code geode_loot.json} in the plugin data directory. */
public final class GeodeLootTable {
    public static final class Entry {
        private final String itemId;
        private final int weight;
        private final int count;

        public Entry(@Nonnull String itemId, int weight, int count) {
            this.itemId = itemId;
            this.weight = Math.max(0, weight);
            this.count = Math.max(1, count);
        }

        @Nonnull
        public String getItemId() {
            return itemId;
        }

        public int getWeight() {
            return weight;
        }

        public int getCount() {
            return count;
        }
    }

    private final List<Entry> entries;
    private final int totalWeight;

    private GeodeLootTable(@Nonnull List<Entry> entries) {
        this.entries = entries;
        int sum = 0;
        for (Entry e : entries) {
            sum += e.weight;
        }
        this.totalWeight = sum;
    }

    @Nonnull
    public static GeodeLootTable empty() {
        return new GeodeLootTable(List.of());
    }

    @Nonnull
    public static GeodeLootTable parseJson(@Nonnull String json) {
        List<Entry> list = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return new GeodeLootTable(list);
        }
        JsonObject o = root.getAsJsonObject();
        JsonArray arr = o.getAsJsonArray("entries");
        if (arr == null) {
            return new GeodeLootTable(list);
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject row = el.getAsJsonObject();
            String itemId = row.has("itemId") && row.get("itemId").isJsonPrimitive() ? row.get("itemId").getAsString() : null;
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            int weight = row.has("weight") && row.get("weight").isJsonPrimitive() ? row.get("weight").getAsInt() : 1;
            int count = row.has("count") && row.get("count").isJsonPrimitive() ? row.get("count").getAsInt() : 1;
            list.add(new Entry(itemId.trim(), weight, count));
        }
        return new GeodeLootTable(list);
    }

    @Nonnull
    public static GeodeLootTable loadFromFile(@Nonnull Path path, @Nonnull String fallbackJson) throws IOException {
        if (!Files.isRegularFile(path)) {
            return parseJson(fallbackJson);
        }
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return parseJson(sb.toString());
        }
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    @Nonnull
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Rolls one reward. Returns null if the table is empty or misconfigured.
     */
    @Nullable
    public ItemStack rollStack() {
        if (entries.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (Entry e : entries) {
            acc += e.weight;
            if (roll < acc) {
                return new ItemStack(e.itemId, e.count);
            }
        }
        Entry last = entries.get(entries.size() - 1);
        return new ItemStack(last.itemId, last.count);
    }
}
