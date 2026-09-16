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
        this.itemId = itemId != null && !itemId.isBlank() ? itemId.trim() : null;
        if (this.itemId == null) {
            this.jewelryMetaJson = null;
            this.listingDisplaySignature = null;
        }
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
