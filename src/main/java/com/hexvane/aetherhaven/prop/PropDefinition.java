package com.hexvane.aetherhaven.prop;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A placeable decorative prop: id, display name, and the prefab it pastes. */
public final class PropDefinition {
    /** Gold price when Cap'n Clive (or others) sell this prop; used when {@code goldPrice} is unset or below 1. */
    public static final long DEFAULT_GOLD_PRICE = 20L;

    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    @SerializedName("prefabPath")
    @Nullable
    private String prefabPath;

    /** Optional common-asset icon path, e.g. {@code Icons/ItemsGenerated/Aetherhaven_Prop_Fish_Barrel.png}. */
    @SerializedName("iconPath")
    @Nullable
    private String iconPath;

    /**
     * Prefab-local cardinal that is the front ({@code North}/{@code East}/{@code South}/{@code West}).
     * Default North ({@code -Z}).
     */
    @SerializedName("frontFacing")
    @Nullable
    private String frontFacing;

    /** Shop gold price for this prop. Missing or below 1 uses {@link #DEFAULT_GOLD_PRICE}. */
    @SerializedName("goldPrice")
    @Nullable
    private Long goldPrice;

    public PropDefinition() {}

    private PropDefinition(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath,
        @Nullable String frontFacing,
        @Nullable Long goldPrice
    ) {
        this.id = id;
        this.displayName = displayName;
        this.prefabPath = prefabPath;
        this.iconPath = iconPath;
        this.frontFacing = com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
        this.goldPrice = goldPrice != null && goldPrice > 0L ? goldPrice : null;
    }

    @Nonnull
    public static PropDefinition create(@Nonnull String id, @Nullable String displayName, @Nonnull String prefabPath) {
        return create(id, displayName, prefabPath, null, null, null);
    }

    @Nonnull
    public static PropDefinition create(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath
    ) {
        return create(id, displayName, prefabPath, iconPath, null, null);
    }

    @Nonnull
    public static PropDefinition create(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath,
        @Nullable String frontFacing
    ) {
        return create(id, displayName, prefabPath, iconPath, frontFacing, null);
    }

    @Nonnull
    public static PropDefinition create(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath,
        @Nullable String frontFacing,
        @Nullable Long goldPrice
    ) {
        return new PropDefinition(
            id.trim(),
            displayName != null ? displayName.trim() : null,
            prefabPath.trim(),
            iconPath != null && !iconPath.isBlank() ? iconPath.trim() : null,
            frontFacing,
            goldPrice
        );
    }

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nonnull
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName.trim() : getId();
    }

    @Nonnull
    public String getPrefabPath() {
        return prefabPath != null ? prefabPath.trim() : "";
    }

    @Nullable
    public String getIconPath() {
        return iconPath != null && !iconPath.isBlank() ? iconPath.trim() : null;
    }

    public void setIconPath(@Nullable String iconPath) {
        this.iconPath = iconPath != null && !iconPath.isBlank() ? iconPath.trim() : null;
    }

    @Nonnull
    public String getFrontFacing() {
        return com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
    }

    public void setFrontFacing(@Nullable String frontFacing) {
        this.frontFacing = com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
    }

    /** Effective shop gold price (never below 1; unset uses {@link #DEFAULT_GOLD_PRICE}). */
    public long getGoldPrice() {
        if (goldPrice == null || goldPrice < 1L) {
            return DEFAULT_GOLD_PRICE;
        }
        return goldPrice;
    }

    public void setGoldPrice(long goldPrice) {
        this.goldPrice = goldPrice > 0L ? goldPrice : null;
    }
}
