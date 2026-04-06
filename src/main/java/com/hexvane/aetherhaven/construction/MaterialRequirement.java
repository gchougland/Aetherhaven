package com.hexvane.aetherhaven.construction;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public final class MaterialRequirement {
    /**
     * Specific item id (exact match). Ignored for counting when {@link #resourceTypeId} is set.
     */
    @SerializedName("itemId")
    private String itemId;

    /**
     * If set, any item whose {@link com.hypixel.hytale.server.core.asset.type.item.config.Item} lists this resource
     * type id counts toward the requirement (e.g. {@code Rock} for stone blocks, {@code Wood_Trunk} for logs).
     */
    @SerializedName("resourceTypeId")
    private String resourceTypeId;

    @SerializedName("count")
    private int count;

    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getResourceTypeId() {
        return resourceTypeId;
    }

    public int getCount() {
        return count;
    }
}
