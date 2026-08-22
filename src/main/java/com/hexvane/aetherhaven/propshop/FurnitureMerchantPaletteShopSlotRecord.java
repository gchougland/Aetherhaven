package com.hexvane.aetherhaven.propshop;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One daily block palette stock line in Cap'n Clive's shop. */
public final class FurnitureMerchantPaletteShopSlotRecord {
    @SerializedName("paletteId")
    @Nullable
    private String paletteId;

    @SerializedName("stock")
    private int stock;

    public FurnitureMerchantPaletteShopSlotRecord() {}

    public FurnitureMerchantPaletteShopSlotRecord(@Nullable String paletteId, int stock) {
        this.paletteId = paletteId != null && !paletteId.isBlank() ? paletteId.trim() : null;
        this.stock = Math.max(0, stock);
    }

    @Nonnull
    public static FurnitureMerchantPaletteShopSlotRecord empty() {
        return new FurnitureMerchantPaletteShopSlotRecord(null, 0);
    }

    @Nonnull
    public String getPaletteId() {
        return paletteId != null ? paletteId : "";
    }

    public void setPaletteId(@Nullable String paletteId) {
        this.paletteId = paletteId != null && !paletteId.isBlank() ? paletteId.trim() : null;
    }

    public int getStock() {
        return Math.max(0, stock);
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, stock);
    }

    public boolean hasStock() {
        return !getPaletteId().isEmpty() && getStock() > 0;
    }

    public void clear() {
        paletteId = null;
        stock = 0;
    }
}
