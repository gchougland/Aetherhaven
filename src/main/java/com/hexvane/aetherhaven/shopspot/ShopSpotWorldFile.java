package com.hexvane.aetherhaven.shopspot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ShopSpotWorldFile {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("spots")
    private List<Row> spots = new ArrayList<>();

    /** Player listings waiting to be handed back to their seller; see {@link ShopSpotRegistry#heldReturns()}. */
    @SerializedName("heldReturns")
    private List<Row> heldReturns = new ArrayList<>();

    @Nonnull
    public List<Row> getSpots() {
        if (spots == null) {
            spots = new ArrayList<>();
        }
        return spots;
    }

    @Nonnull
    public List<Row> getHeldReturns() {
        if (heldReturns == null) {
            heldReturns = new ArrayList<>();
        }
        return heldReturns;
    }

    public static final class Row {
        @Nullable
        public String spotId;
        @Nullable
        public String worldName;
        public int blockX;
        public int blockY;
        public int blockZ;
        @Nullable
        public String townId;
        @Nullable
        public String plotId;
        /** Omitted from JSON when unset; runtime uses {@link Float#NaN} on {@link ShopSpotRecord}. */
        @Nullable
        public Float displayYawRadians;
        public boolean playerControlled;
        @Nullable
        public String lootTableId;
        @Nullable
        public String itemId;
        public int stock;
        public long stockEpochDay;
        @Nullable
        public String jewelryMetaJson;
        /** The whole stack for sale as the engine saves one. Left out for NPC stock; absent in older files. */
        @Nullable
        public String stack;
        /** Catalog price the listing went up at, per lot. Left out when none was recorded. */
        @Nullable
        public Long quoteGold;
        @Nullable
        public Integer quoteBatch;
        @Nullable
        public String sellerUuid;
        @Nullable
        public String sellerName;
        @Nullable
        public String displayEntityUuid;
    }

    @Nonnull
    public static ShopSpotWorldFile readOrEmpty(@Nonnull Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new ShopSpotWorldFile();
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ShopSpotWorldFile f = GSON.fromJson(r, ShopSpotWorldFile.class);
            return f != null ? f : new ShopSpotWorldFile();
        }
    }

    public void writeAtomic(@Nonnull Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Nonnull
    public static ShopSpotWorldFile fromRecords(@Nonnull List<ShopSpotRecord> records) {
        return fromRecords(records, List.of());
    }

    @Nonnull
    public static ShopSpotWorldFile fromRegistry(@Nonnull ShopSpotRegistry registry) {
        return fromRecords(registry.allRecords(), registry.heldReturns());
    }

    @Nonnull
    public static ShopSpotWorldFile fromRecords(
        @Nonnull List<ShopSpotRecord> records,
        @Nonnull List<ShopSpotRecord> held
    ) {
        ShopSpotWorldFile f = new ShopSpotWorldFile();
        appendRows(f.getSpots(), records);
        appendRows(f.getHeldReturns(), held);
        return f;
    }

    private static void appendRows(@Nonnull List<Row> rows, @Nonnull List<ShopSpotRecord> records) {
        for (ShopSpotRecord r : records) {
            Row row = new Row();
            row.spotId = r.getSpotId().toString();
            row.worldName = r.getWorldName();
            row.blockX = r.getBlockX();
            row.blockY = r.getBlockY();
            row.blockZ = r.getBlockZ();
            row.townId = r.getTownId().toString();
            row.plotId = r.getPlotId().toString();
            float yaw = r.getDisplayYawRadians();
            row.displayYawRadians = Float.isNaN(yaw) ? null : yaw;
            row.playerControlled = r.isPlayerControlled();
            row.lootTableId = r.getLootTableId();
            row.itemId = r.getItemId();
            row.stock = r.getStock();
            row.stockEpochDay = r.getStockEpochDay();
            row.jewelryMetaJson = r.getJewelryMetaJson();
            row.stack = r.getStackData();
            ShopPriceEntry quote = r.getQuote();
            row.quoteGold = quote != null ? quote.getGoldPerBatch() : null;
            row.quoteBatch = quote != null ? quote.getBatchSize() : null;
            UUID seller = r.getSellerUuid();
            row.sellerUuid = seller != null ? seller.toString() : null;
            row.sellerName = r.getSellerName();
            UUID display = r.getDisplayEntityUuid();
            row.displayEntityUuid = display != null ? display.toString() : null;
            rows.add(row);
        }
    }

    @Nonnull
    public static List<ShopSpotRecord> toRecords(@Nonnull ShopSpotWorldFile file) {
        return toRecords(file.getSpots());
    }

    @Nonnull
    public static List<ShopSpotRecord> toHeldReturns(@Nonnull ShopSpotWorldFile file) {
        return toRecords(file.getHeldReturns());
    }

    @Nonnull
    private static List<ShopSpotRecord> toRecords(@Nonnull List<Row> rows) {
        List<ShopSpotRecord> out = new ArrayList<>();
        for (Row row : rows) {
            ShopSpotRecord r = rowToRecord(row);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    @Nullable
    private static ShopSpotRecord rowToRecord(@Nonnull Row row) {
        if (row.spotId == null || row.spotId.isBlank()) {
            return null;
        }
        try {
            ShopSpotRecord r = new ShopSpotRecord();
            r.setSpotId(UUID.fromString(row.spotId.trim()));
            r.setWorldName(row.worldName != null ? row.worldName : "");
            r.setBlockPosition(new org.joml.Vector3i(row.blockX, row.blockY, row.blockZ));
            if (row.townId != null && !row.townId.isBlank()) {
                r.setTownId(UUID.fromString(row.townId.trim()));
            }
            if (row.plotId != null && !row.plotId.isBlank()) {
                r.setPlotId(UUID.fromString(row.plotId.trim()));
            }
            r.setDisplayYawRadians(
                row.displayYawRadians != null && !Float.isNaN(row.displayYawRadians)
                    ? row.displayYawRadians
                    : Float.NaN
            );
            r.setPlayerControlled(row.playerControlled);
            r.setLootTableId(row.lootTableId != null ? row.lootTableId : "");
            r.setItemId(row.itemId);
            r.setStock(row.stock);
            r.setStockEpochDay(row.stockEpochDay);
            r.setJewelryMetaJson(row.jewelryMetaJson);
            r.setStackData(row.stack);
            if (row.quoteBatch != null && row.quoteBatch > 0 && row.quoteGold != null) {
                r.setQuote(new ShopPriceEntry(row.quoteGold, row.quoteBatch));
            }
            if (row.sellerUuid != null && !row.sellerUuid.isBlank()) {
                r.setSellerUuid(UUID.fromString(row.sellerUuid.trim()));
            }
            r.setSellerName(row.sellerName);
            if (row.displayEntityUuid != null && !row.displayEntityUuid.isBlank()) {
                r.setDisplayEntityUuid(UUID.fromString(row.displayEntityUuid.trim()));
            }
            return r;
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().withCause(e).log("Skipping invalid shop spot row %s", row.spotId);
            return null;
        }
    }
}
