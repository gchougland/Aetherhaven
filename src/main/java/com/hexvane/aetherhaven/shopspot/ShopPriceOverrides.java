package com.hexvane.aetherhaven.shopspot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This server's own item prices: the small {@value ShopPriceFiles#SHOP_PRICES_FILE_NAME} in the mod's data folder.
 * It holds only the prices an admin set, laid over the table shipped in the mod, which is never edited.
 *
 * <pre>
 * {
 *   "catalogRevision": 1,
 *   "prices": {
 *     "Furniture_Crude_Chest_Small": 10,
 *     "Wood_Oak_Trunk": { "gold": 2, "batchSize": 10 }
 *   }
 * }
 * </pre>
 *
 * A plain number is the gold for one item. The longer form sells the item in lots: {@code gold} for every
 * {@code batchSize} items. An admin may edit the file by hand and run the price command's reload.
 *
 * <p>Everything here works on a path, so it is the same code under test as on a server.
 */
public final class ShopPriceOverrides {
    /** No single lot in the shipped table costs more than about a thousand gold; a million is past any real price. */
    public static final long MAX_GOLD = 1_000_000L;
    /** The shipped table sells in lots of at most 25. */
    public static final int MAX_LOT = 1_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ShopPriceOverrides() {}

    /** Why a price is refused, or null when it may be set. Each value is a lang key suffix. */
    public enum Refusal {
        TOO_LOW("tooLow"),
        TOO_HIGH("tooHigh"),
        LOT_TOO_SMALL("lotTooSmall"),
        LOT_TOO_LARGE("lotTooLarge");

        private final String key;

        Refusal(String key) {
            this.key = key;
        }

        @Nonnull
        public String key() {
            return key;
        }
    }

    /**
     * @param lotLimit the most of this item that fits in one stack; a lot can never be larger, because a seller
     *                 lists from one stack
     */
    @Nullable
    public static Refusal refusal(long gold, int lot, int lotLimit) {
        if (gold < 1L) {
            return Refusal.TOO_LOW;
        }
        if (gold > MAX_GOLD) {
            return Refusal.TOO_HIGH;
        }
        if (lot < 1) {
            return Refusal.LOT_TOO_SMALL;
        }
        if (lot > Math.min(MAX_LOT, Math.max(1, lotLimit))) {
            return Refusal.LOT_TOO_LARGE;
        }
        return null;
    }

    /** The file could not be understood. Nothing was changed. */
    public static final class Unreadable extends Exception {
        private static final long serialVersionUID = 1L;

        Unreadable(@Nonnull String detail, @Nullable Throwable cause) {
            super(detail, cause);
        }
    }

    /**
     * The prices written in the file, by item id. A missing file has none.
     *
     * @throws Unreadable when the file is there but is not a price file; it is left untouched
     */
    @Nonnull
    public static Map<String, ShopPriceEntry> read(@Nonnull Path path) throws IOException, Unreadable {
        Map<String, ShopPriceEntry> out = new TreeMap<>();
        JsonObject root = readRoot(path);
        JsonElement prices = root.get("prices");
        if (prices == null || prices.isJsonNull()) {
            return out;
        }
        if (!prices.isJsonObject()) {
            throw new Unreadable("\"prices\" must be a list of item ids and prices in curly brackets", null);
        }
        // An entry that leaves its gold or lot size out means what the loader takes it to mean.
        long defaultGold = number(root, "defaultGoldPrice", AetherhavenConstants.SHOP_SPOT_DEFAULT_GOLD_PRICE);
        int defaultLot = (int) Math.max(1L, number(root, "defaultBatchSize", 1L));
        for (var e : prices.getAsJsonObject().entrySet()) {
            ShopPriceEntry entry;
            try {
                entry = ShopPriceCatalog.parsePriceEntry(e.getValue(), defaultGold, defaultLot);
            } catch (RuntimeException notANumber) {
                throw new Unreadable("the price of " + e.getKey() + " is not a number", notANumber);
            }
            if (entry == null) {
                throw new Unreadable("the price of " + e.getKey() + " is not a number", null);
            }
            out.put(e.getKey(), entry);
        }
        return out;
    }

    /** Null when the file is missing or reads cleanly, otherwise what is wrong with it in plain words. */
    @Nullable
    public static String problem(@Nonnull Path path) {
        try {
            for (var e : read(path).entrySet()) {
                // A hand edit is held to the same limits as the command. A lot is checked against one stack when set
                // by command; here only against the overall limit, because a file can name items this server lacks.
                if (refusal(e.getValue().getGoldPerBatch(), e.getValue().getBatchSize(), MAX_LOT) != null) {
                    return "the price of " + e.getKey() + " must be 1 to " + MAX_GOLD + " gold, for a lot of 1 to " + MAX_LOT;
                }
            }
            return null;
        } catch (Unreadable e) {
            return e.getMessage();
        } catch (IOException e) {
            return "the file could not be opened";
        }
    }

    /**
     * Sets or changes one item's price in the file, keeping everything else in it.
     *
     * @return the price the file held for this item before, or null when it had none
     */
    @Nullable
    public static ShopPriceEntry set(@Nonnull Path path, @Nonnull String itemId, @Nonnull ShopPriceEntry price)
        throws IOException, Unreadable {
        Map<String, ShopPriceEntry> prices = read(path);
        ShopPriceEntry before = prices.put(itemId, price);
        write(path, prices);
        return before;
    }

    /**
     * Takes one item's price out of the file, so the item goes back to the shipped price.
     *
     * @return the price that was removed, or null when the file had none for this item (and was not rewritten)
     */
    @Nullable
    public static ShopPriceEntry remove(@Nonnull Path path, @Nonnull String itemId) throws IOException, Unreadable {
        Map<String, ShopPriceEntry> prices = read(path);
        ShopPriceEntry before = prices.remove(itemId);
        if (before != null) {
            write(path, prices);
        }
        return before;
    }

    private static void write(@Nonnull Path path, @Nonnull Map<String, ShopPriceEntry> prices)
        throws IOException, Unreadable {
        // Anything else an admin wrote in the file is kept as it was.
        JsonObject root = readRoot(path);
        int revision = ShopPriceFiles.BUNDLED_CATALOG_REVISION;
        JsonElement saved = root.get("catalogRevision");
        if (saved != null && saved.isJsonPrimitive() && saved.getAsJsonPrimitive().isNumber()) {
            revision = Math.max(revision, saved.getAsInt());
        }
        // The revision tells the loader this is a small override file and not an old full copy of the table.
        root.addProperty("catalogRevision", revision);
        JsonObject out = new JsonObject();
        for (var e : new TreeMap<>(prices).entrySet()) {
            ShopPriceEntry entry = e.getValue();
            if (entry.getBatchSize() <= 1) {
                out.addProperty(e.getKey(), entry.getGoldPerBatch());
            } else {
                JsonObject lot = new JsonObject();
                lot.addProperty("gold", entry.getGoldPerBatch());
                lot.addProperty("batchSize", entry.getBatchSize());
                out.add(e.getKey(), lot);
            }
        }
        root.add("prices", out);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long number(@Nonnull JsonObject root, @Nonnull String key, long fallback) throws Unreadable {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return Math.max(0L, value.getAsLong());
        } catch (RuntimeException notANumber) {
            throw new Unreadable("\"" + key + "\" is not a number", notANumber);
        }
    }

    @Nonnull
    private static JsonObject readRoot(@Nonnull Path path) throws IOException, Unreadable {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return new JsonObject();
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (JsonParseException malformed) {
            throw new Unreadable("it is not valid JSON (" + firstLine(malformed.getMessage()) + ")", malformed);
        }
        if (!root.isJsonObject()) {
            throw new Unreadable("it must start with a curly bracket", null);
        }
        return root.getAsJsonObject();
    }

    @Nonnull
    private static String firstLine(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "unknown mistake";
        }
        String line = message.strip().lines().findFirst().orElse("unknown mistake");
        // Gson prefixes its own class name; the part after it says where the mistake is.
        int cut = line.indexOf(": ");
        return cut >= 0 && cut + 2 < line.length() ? line.substring(cut + 2) : line;
    }
}
