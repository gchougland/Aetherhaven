package com.hexvane.aetherhaven.shopspot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ShopPriceCatalog {
    /** Where an item's price comes from. Later layers win: the shipped table, then asset packs, then the server. */
    public enum Source {
        /** The table shipped in the mod, made by {@code scripts/generate_shop_prices.py}. */
        GENERATED,
        /** Another mod's asset pack. */
        PACK,
        /** This server's own price file, see {@link ShopPriceOverrides}. */
        OVERRIDE,
        /** No price anywhere: the catalog's default price applies. */
        DEFAULT
    }

    private final int catalogRevision;
    private final long defaultGoldPrice;
    private final int defaultBatchSize;
    @Nonnull
    private final Map<String, ShopPriceEntry> prices;
    /** Only the ids whose price does not come from the shipped table. */
    @Nonnull
    private final Map<String, Source> sources;
    /** What a server override replaced, so an admin can be shown the price they would go back to. */
    @Nonnull
    private final Map<String, Beneath> beneath;

    /** A price hidden by a server override, and where that price came from. */
    public record Beneath(@Nonnull ShopPriceEntry entry, @Nonnull Source source) {}

    private ShopPriceCatalog(
        int catalogRevision,
        long defaultGoldPrice,
        int defaultBatchSize,
        @Nonnull Map<String, ShopPriceEntry> prices
    ) {
        this(catalogRevision, defaultGoldPrice, defaultBatchSize, prices, Map.of(), Map.of());
    }

    private ShopPriceCatalog(
        int catalogRevision,
        long defaultGoldPrice,
        int defaultBatchSize,
        @Nonnull Map<String, ShopPriceEntry> prices,
        @Nonnull Map<String, Source> sources,
        @Nonnull Map<String, Beneath> beneath
    ) {
        this.catalogRevision = Math.max(0, catalogRevision);
        this.defaultGoldPrice = Math.max(0L, defaultGoldPrice);
        this.defaultBatchSize = Math.max(1, defaultBatchSize);
        this.prices = prices;
        this.sources = sources;
        this.beneath = beneath;
    }

    @Nonnull
    public static ShopPriceCatalog empty() {
        return new ShopPriceCatalog(0, AetherhavenConstants.SHOP_SPOT_DEFAULT_GOLD_PRICE, 1, Map.of());
    }

    @Nonnull
    public static ShopPriceCatalog parseJson(@Nonnull String json) {
        int catalogRevision = 0;
        long defaultPrice = AetherhavenConstants.SHOP_SPOT_DEFAULT_GOLD_PRICE;
        int defaultBatch = 1;
        Map<String, ShopPriceEntry> map = new HashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return new ShopPriceCatalog(catalogRevision, defaultPrice, defaultBatch, map);
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("catalogRevision")) {
            catalogRevision = Math.max(0, obj.get("catalogRevision").getAsInt());
        }
        if (obj.has("defaultGoldPrice")) {
            defaultPrice = Math.max(0L, obj.get("defaultGoldPrice").getAsLong());
        }
        if (obj.has("defaultBatchSize")) {
            defaultBatch = Math.max(1, obj.get("defaultBatchSize").getAsInt());
        }
        if (obj.has("prices") && obj.get("prices").isJsonObject()) {
            JsonObject pricesObj = obj.getAsJsonObject("prices");
            for (var e : pricesObj.entrySet()) {
                ShopPriceEntry entry = parsePriceEntry(e.getValue(), defaultPrice, defaultBatch);
                if (entry != null) {
                    map.put(e.getKey(), entry);
                }
            }
        }
        return new ShopPriceCatalog(catalogRevision, defaultPrice, defaultBatch, map);
    }

    @Nullable
    static ShopPriceEntry parsePriceEntry(
        @Nonnull JsonElement value,
        long defaultGold,
        int defaultBatch
    ) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return new ShopPriceEntry(value.getAsLong(), 1);
        }
        if (!value.isJsonObject()) {
            return null;
        }
        JsonObject o = value.getAsJsonObject();
        long gold = defaultGold;
        if (o.has("gold")) {
            gold = Math.max(0L, o.get("gold").getAsLong());
        } else if (o.has("goldPrice")) {
            gold = Math.max(0L, o.get("goldPrice").getAsLong());
        }
        int batch = defaultBatch;
        if (o.has("batchSize")) {
            batch = Math.max(1, o.get("batchSize").getAsInt());
        } else if (o.has("batch")) {
            batch = Math.max(1, o.get("batch").getAsInt());
        }
        return new ShopPriceEntry(gold, batch);
    }

    @Nonnull
    public static ShopPriceCatalog loadFromFile(@Nonnull Path path, @Nonnull String fallbackJson) throws IOException {
        if (!Files.isRegularFile(path)) {
            return parseJson(fallbackJson);
        }
        return parseJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    @Nonnull
    public ShopPriceEntry getEntry(@Nonnull String itemId) {
        ShopPriceEntry explicit = prices.get(itemId);
        if (explicit != null) {
            return explicit;
        }
        return new ShopPriceEntry(defaultGoldPrice, defaultBatchSize);
    }

    /** Gold per purchase lot (batch of {@link ShopPriceEntry#getBatchSize()} items when batched). */
    public long getGoldPrice(@Nonnull String itemId) {
        return getEntry(itemId).getGoldPerBatch();
    }

    public int getBatchSize(@Nonnull String itemId) {
        return getEntry(itemId).getBatchSize();
    }

    public boolean hasExplicitPrice(@Nonnull String itemId) {
        return prices.containsKey(itemId);
    }

    /** Where {@link #getEntry} takes this item's price from. */
    @Nonnull
    public Source sourceOf(@Nonnull String itemId) {
        if (!prices.containsKey(itemId)) {
            return Source.DEFAULT;
        }
        return sources.getOrDefault(itemId, Source.GENERATED);
    }

    /**
     * The price this item goes back to when its server override is removed, or null when the item has no override
     * or would have no price at all without it.
     */
    @Nullable
    public Beneath beneathOverride(@Nonnull String itemId) {
        return beneath.get(itemId);
    }

    /** Every price set in this server's own file, by item id. */
    @Nonnull
    public Map<String, ShopPriceEntry> overrides() {
        Map<String, ShopPriceEntry> out = new TreeMap<>();
        for (var e : sources.entrySet()) {
            if (e.getValue() == Source.OVERRIDE) {
                out.put(e.getKey(), prices.get(e.getKey()));
            }
        }
        return out;
    }

    public int getCatalogRevision() {
        return catalogRevision;
    }

    public int getExplicitPriceCount() {
        return prices.size();
    }

    public long getDefaultGoldPrice() {
        return defaultGoldPrice;
    }

    public int getDefaultBatchSize() {
        return defaultBatchSize;
    }

    /** Bundled catalog plus per-item overrides from the data folder (override keys win). */
    @Nonnull
    public ShopPriceCatalog withOverrides(@Nonnull ShopPriceCatalog overrides) {
        return withLayer(overrides, Source.OVERRIDE);
    }

    /** This catalog with another laid over it: the layer's prices win and are remembered as coming from it. */
    @Nonnull
    public ShopPriceCatalog withLayer(@Nonnull ShopPriceCatalog layer, @Nonnull Source source) {
        Map<String, ShopPriceEntry> merged = new HashMap<>(this.prices);
        Map<String, Source> mergedSources = new HashMap<>(this.sources);
        Map<String, Beneath> mergedBeneath = new HashMap<>(this.beneath);
        for (var e : layer.prices.entrySet()) {
            Source sourceBefore = sourceOf(e.getKey());
            ShopPriceEntry before = merged.put(e.getKey(), e.getValue());
            mergedSources.put(e.getKey(), source);
            if (source == Source.OVERRIDE && before != null && sourceBefore != Source.OVERRIDE) {
                mergedBeneath.put(e.getKey(), new Beneath(before, sourceBefore));
            }
        }
        return new ShopPriceCatalog(
            Math.max(this.catalogRevision, layer.catalogRevision),
            this.defaultGoldPrice,
            this.defaultBatchSize,
            merged,
            mergedSources,
            mergedBeneath
        );
    }
}
