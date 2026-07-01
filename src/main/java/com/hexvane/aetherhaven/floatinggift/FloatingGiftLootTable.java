package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.plot.PlotTokenUnlockPageMetadata;
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

public final class FloatingGiftLootTable {
    public static final class Entry {
        private final String itemId;
        @Nullable
        private final String constructionId;
        private final int weight;

        public Entry(@Nonnull String itemId, int weight) {
            this(itemId, null, weight);
        }

        public Entry(@Nonnull String itemId, @Nullable String constructionId, int weight) {
            this.itemId = itemId;
            this.constructionId = constructionId != null && !constructionId.isBlank() ? constructionId.trim() : null;
            this.weight = Math.max(0, weight);
        }

        @Nonnull
        public String itemId() {
            return itemId;
        }

        @Nullable
        public String constructionId() {
            return constructionId;
        }

        public int weight() {
            return weight;
        }
    }

    private final List<Entry> entries;
    private final int totalWeight;

    private FloatingGiftLootTable(@Nonnull List<Entry> entries) {
        this.entries = entries;
        int sum = 0;
        for (Entry e : entries) {
            sum += e.weight;
        }
        this.totalWeight = sum;
    }

    @Nonnull
    public static FloatingGiftLootTable empty() {
        return new FloatingGiftLootTable(List.of());
    }

    @Nonnull
    public static FloatingGiftLootTable of(@Nonnull List<Entry> entries) {
        return new FloatingGiftLootTable(new ArrayList<>(entries));
    }

    @Nonnull
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Nonnull
    public static FloatingGiftLootTable parseJson(@Nonnull String json) {
        List<Entry> list = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return new FloatingGiftLootTable(list);
        }
        JsonArray entries = root.getAsJsonObject().getAsJsonArray("entries");
        if (entries == null) {
            return new FloatingGiftLootTable(list);
        }
        for (JsonElement el : entries) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject row = el.getAsJsonObject();
            String itemId = row.has("itemId") ? row.get("itemId").getAsString() : "";
            String constructionId = row.has("constructionId") ? row.get("constructionId").getAsString() : null;
            int weight = row.has("weight") ? row.get("weight").getAsInt() : 1;
            if (itemId != null && !itemId.isBlank() && weight > 0) {
                list.add(new Entry(itemId.trim(), constructionId, weight));
            }
        }
        return new FloatingGiftLootTable(list);
    }

    @Nonnull
    public static FloatingGiftLootTable loadFromFile(@Nonnull Path path, @Nonnull String fallbackJson) throws IOException {
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

    @Nullable
    public ItemStack rollStack() {
        return rollStack(ThreadLocalRandom.current());
    }

    @Nullable
    public ItemStack rollStack(@Nonnull ThreadLocalRandom rnd) {
        if (entries.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = rnd.nextInt(totalWeight);
        int acc = 0;
        for (Entry e : entries) {
            acc += e.weight;
            if (roll < acc) {
                return toStack(e);
            }
        }
        return toStack(entries.get(entries.size() - 1));
    }

    /** Rolls up to {@code count} unique stacks from this table (no duplicate item ids). */
    @Nonnull
    public List<ItemStack> rollUniqueStacks(int count, @Nonnull ThreadLocalRandom rnd) {
        if (count <= 0 || entries.isEmpty() || totalWeight <= 0) {
            return List.of();
        }
        List<ItemStack> picked = new ArrayList<>(count);
        List<String> seen = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = Math.max(count * 20, 40);
        while (picked.size() < count && attempts < maxAttempts) {
            attempts++;
            ItemStack stack = rollStack(rnd);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            String itemId = stack.getItemId();
            if (itemId != null && !seen.contains(itemId)) {
                seen.add(itemId);
                picked.add(stack);
            }
        }
        return picked;
    }

    @Nonnull
    private static ItemStack toStack(@Nonnull Entry e) {
        if (e.constructionId != null) {
            return PlotTokenUnlockPageMetadata.createStack(e.constructionId);
        }
        return new ItemStack(e.itemId, 1);
    }
}
