package com.hexvane.aetherhaven.construction;

import com.google.gson.annotations.SerializedName;

public final class MaterialRequirement {
    @SerializedName("itemId")
    private String itemId;

    @SerializedName("count")
    private int count;

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }
}
