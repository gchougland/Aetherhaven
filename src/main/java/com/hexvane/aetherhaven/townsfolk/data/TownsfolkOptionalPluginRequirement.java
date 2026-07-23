package com.hexvane.aetherhaven.townsfolk.data;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** When set, the character is only eligible for new pool draws while this plugin is loaded and enabled. */
public final class TownsfolkOptionalPluginRequirement {
    @SerializedName("group")
    @Nullable
    private String group;

    @SerializedName("name")
    @Nullable
    private String name;

    @Nullable
    public String getGroup() {
        return group != null && !group.isBlank() ? group.trim() : null;
    }

    @Nullable
    public String getName() {
        return name != null && !name.isBlank() ? name.trim() : null;
    }

    public boolean isComplete() {
        return getGroup() != null && getName() != null;
    }

    @Nonnull
    public String displayId() {
        String g = getGroup();
        String n = getName();
        if (g == null || n == null) {
            return "";
        }
        return g + ":" + n;
    }
}
