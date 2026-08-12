package com.hexvane.aetherhaven.prop;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A placeable decorative prop: id, display name, and the prefab it pastes. */
public final class PropDefinition {
    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    @SerializedName("prefabPath")
    @Nullable
    private String prefabPath;

    public PropDefinition() {}

    private PropDefinition(@Nonnull String id, @Nullable String displayName, @Nonnull String prefabPath) {
        this.id = id;
        this.displayName = displayName;
        this.prefabPath = prefabPath;
    }

    @Nonnull
    public static PropDefinition create(@Nonnull String id, @Nullable String displayName, @Nonnull String prefabPath) {
        return new PropDefinition(id.trim(), displayName != null ? displayName.trim() : null, prefabPath.trim());
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
}
