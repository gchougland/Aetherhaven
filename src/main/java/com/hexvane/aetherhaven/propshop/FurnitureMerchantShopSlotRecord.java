package com.hexvane.aetherhaven.propshop;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One daily stock line in Cap'n Clive's prop shop. */
public final class FurnitureMerchantShopSlotRecord {
    @SerializedName("propId")
    @Nullable
    private String propId;

    @SerializedName("stock")
    private int stock;

    public FurnitureMerchantShopSlotRecord() {}

    public FurnitureMerchantShopSlotRecord(@Nullable String propId, int stock) {
        this.propId = propId != null && !propId.isBlank() ? propId.trim() : null;
        this.stock = Math.max(0, stock);
    }

    @Nonnull
    public static FurnitureMerchantShopSlotRecord empty() {
        return new FurnitureMerchantShopSlotRecord(null, 0);
    }

    @Nonnull
    public String getPropId() {
        return propId != null ? propId : "";
    }

    public void setPropId(@Nullable String propId) {
        this.propId = propId != null && !propId.isBlank() ? propId.trim() : null;
    }

    public int getStock() {
        return Math.max(0, stock);
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, stock);
    }

    public boolean hasStock() {
        return !getPropId().isEmpty() && getStock() > 0;
    }

    public void clear() {
        propId = null;
        stock = 0;
    }
}
