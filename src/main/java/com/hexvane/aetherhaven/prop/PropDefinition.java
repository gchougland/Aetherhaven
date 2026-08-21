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

    public PropDefinition() {}

    private PropDefinition(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath,
        @Nullable String frontFacing
    ) {
        this.id = id;
        this.displayName = displayName;
        this.prefabPath = prefabPath;
        this.iconPath = iconPath;
        this.frontFacing = com.hexvane.aetherhaven.placement.FrontFacing.normalize(frontFacing);
    }

    @Nonnull
    public static PropDefinition create(@Nonnull String id, @Nullable String displayName, @Nonnull String prefabPath) {
        return create(id, displayName, prefabPath, null, null);
    }

    @Nonnull
    public static PropDefinition create(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath
    ) {
        return create(id, displayName, prefabPath, iconPath, null);
    }

    @Nonnull
    public static PropDefinition create(
        @Nonnull String id,
        @Nullable String displayName,
        @Nonnull String prefabPath,
        @Nullable String iconPath,
        @Nullable String frontFacing
    ) {
        return new PropDefinition(
            id.trim(),
            displayName != null ? displayName.trim() : null,
            prefabPath.trim(),
            iconPath != null && !iconPath.isBlank() ? iconPath.trim() : null,
            frontFacing
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
}
