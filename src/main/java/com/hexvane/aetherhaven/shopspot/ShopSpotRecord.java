package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotRecord {
    private UUID spotId;
    private String worldName = "";
    private int blockX;
    private int blockY;
    private int blockZ;
    private UUID townId;
    private UUID plotId;
    /** Item display yaw (radians); NaN = read from block rotation at sync time. */
    private float displayYawRadians = Float.NaN;
    private boolean playerControlled;
    private String lootTableId = "";
    @Nullable
    private String itemId;
    private int stock;
    private long stockEpochDay = Long.MIN_VALUE;
    /** Serialized {@link com.hexvane.aetherhaven.jewelry.JewelryMetadata#BSON_KEY} for appraised jewelry listings. */
    @Nullable
    private String jewelryMetaJson;
    /**
     * The whole stack that is for sale, exactly as the engine saves one in a container (see {@link ShopListing}).
     * Null for rolled NPC stock and for listings saved before stacks were kept whole.
     */
    @Nullable
    private String stackData;
    /** {@link #stackData} decoded once; never persisted. Hand out copies, see {@link ShopListing#stack}. */
    @Nullable
    private transient Object stackCache;
    /** Catalog price this listing went up at, per lot. Only meaningful while {@link #quoteBatch} is positive. */
    private long quoteGold;
    /** Lot size this listing went up with. Zero means no price was recorded and the live catalog is used. */
    private int quoteBatch;
    /** Last item+metadata signature used for the floating display prop (not persisted). */
    @Nullable
    private transient String listingDisplaySignature;
    @Nullable
    private UUID sellerUuid;
    @Nullable
    private String sellerName;
    @Nullable
    private UUID displayEntityUuid;
    /** Identity of the loaded chunk already checked for orphan displays; never persisted. */
    private transient Object reconciledDisplayChunk;

    boolean needsDisplayReconciliation(Object loadedChunk) {
        return loadedChunk != null && loadedChunk != reconciledDisplayChunk;
    }

    void markDisplayReconciled(Object loadedChunk) {
        reconciledDisplayChunk = loadedChunk;
    }

    public ShopSpotRecord() {}

    @Nonnull
    public UUID getSpotId() {
        return spotId != null ? spotId : new UUID(0L, 0L);
    }

    public void setSpotId(@Nonnull UUID spotId) {
        this.spotId = spotId;
    }

    @Nonnull
    public String getWorldName() {
        return worldName != null ? worldName : "";
    }

    public void setWorldName(@Nonnull String worldName) {
        this.worldName = worldName != null ? worldName : "";
    }

    @Nonnull
    public Vector3i getBlockPosition() {
        return new Vector3i(blockX, blockY, blockZ);
    }

    public void setBlockPosition(@Nonnull Vector3i pos) {
        if (blockX != pos.x || blockY != pos.y || blockZ != pos.z) reconciledDisplayChunk = null;
        this.blockX = pos.x;
        this.blockY = pos.y;
        this.blockZ = pos.z;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    @Nonnull
    public UUID getTownId() {
        return townId != null ? townId : new UUID(0L, 0L);
    }

    public void setTownId(@Nonnull UUID townId) {
        this.townId = townId;
    }

    @Nonnull
    public UUID getPlotId() {
        return plotId != null ? plotId : new UUID(0L, 0L);
    }

    public void setPlotId(@Nonnull UUID plotId) {
        this.plotId = plotId;
    }

    public float getDisplayYawRadians() {
        return displayYawRadians;
    }

    public void setDisplayYawRadians(float displayYawRadians) {
        this.displayYawRadians = displayYawRadians;
    }

    public boolean isPlayerControlled() {
        return playerControlled;
    }

    public void setPlayerControlled(boolean playerControlled) {
        this.playerControlled = playerControlled;
    }

    @Nonnull
    public String getLootTableId() {
        return lootTableId != null ? lootTableId : "";
    }

    public void setLootTableId(@Nonnull String lootTableId) {
        this.lootTableId = lootTableId != null ? lootTableId : "";
    }

    @Nullable
    public String getItemId() {
        return itemId != null && !itemId.isBlank() ? itemId : null;
    }

    public void setItemId(@Nullable String itemId) {
        String next = itemId != null && !itemId.isBlank() ? itemId.trim() : null;
        // A kept stack and a recorded price describe one item. They never outlive it or pass to another.
        if (next == null || !next.equals(this.itemId)) {
            this.stackData = null;
            this.stackCache = null;
            this.quoteGold = 0L;
            this.quoteBatch = 0;
        }
        this.itemId = next;
        if (this.itemId == null) {
            this.jewelryMetaJson = null;
            this.listingDisplaySignature = null;
        }
    }

    /** The stack for sale in the engine's own saved form, or null when only the item id is known. */
    @Nullable
    public String getStackData() {
        return stackData != null && !stackData.isBlank() ? stackData : null;
    }

    public void setStackData(@Nullable String stackData) {
        this.stackData = stackData != null && !stackData.isBlank() ? stackData : null;
        this.stackCache = null;
        this.listingDisplaySignature = null;
    }

    @Nullable
    Object getStackCache() {
        return stackCache;
    }

    void setStackCache(@Nullable Object stackCache) {
        this.stackCache = stackCache;
    }

    /** The catalog price recorded when this was listed, or null to use today's catalog price. */
    @Nullable
    public ShopPriceEntry getQuote() {
        return quoteBatch > 0 && getItemId() != null ? new ShopPriceEntry(quoteGold, quoteBatch) : null;
    }

    public void setQuote(@Nullable ShopPriceEntry quote) {
        this.quoteGold = quote != null ? quote.getGoldPerBatch() : 0L;
        this.quoteBatch = quote != null ? quote.getBatchSize() : 0;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, stock);
    }

    @Nullable
    public String getJewelryMetaJson() {
        return jewelryMetaJson != null && !jewelryMetaJson.isBlank() ? jewelryMetaJson : null;
    }

    public void setJewelryMetaJson(@Nullable String jewelryMetaJson) {
        this.jewelryMetaJson =
            jewelryMetaJson != null && !jewelryMetaJson.isBlank() ? jewelryMetaJson.trim() : null;
        this.listingDisplaySignature = null;
    }

    @Nullable
    String getListingDisplaySignature() {
        return listingDisplaySignature;
    }

    void setListingDisplaySignature(@Nullable String listingDisplaySignature) {
        this.listingDisplaySignature =
            listingDisplaySignature != null && !listingDisplaySignature.isBlank()
                ? listingDisplaySignature
                : null;
    }

    public long getStockEpochDay() {
        return stockEpochDay;
    }

    public void setStockEpochDay(long stockEpochDay) {
        this.stockEpochDay = stockEpochDay;
    }

    @Nullable
    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(@Nullable UUID sellerUuid) {
        this.sellerUuid = sellerUuid;
        if (sellerUuid == null) {
            this.sellerName = null;
        }
    }

    @Nullable
    public String getSellerName() {
        return sellerName != null && !sellerName.isBlank() ? sellerName.trim() : null;
    }

    public void setSellerName(@Nullable String sellerName) {
        this.sellerName = sellerName != null && !sellerName.isBlank() ? sellerName.trim() : null;
    }

    @Nonnull
    public String sellerDisplayName(@Nonnull World world) {
        String stored = getSellerName();
        if (stored != null) {
            return stored;
        }
        UUID seller = getSellerUuid();
        if (seller == null) {
            return "";
        }
        return TownPlayerLookup.displayNameForUuid(world, seller);
    }

    @Nullable
    public UUID getDisplayEntityUuid() {
        return displayEntityUuid;
    }

    public void setDisplayEntityUuid(@Nullable UUID displayEntityUuid) {
        this.displayEntityUuid = displayEntityUuid;
    }

    public boolean hasStock() {
        String id = getItemId();
        return id != null && stock > 0;
    }
}
