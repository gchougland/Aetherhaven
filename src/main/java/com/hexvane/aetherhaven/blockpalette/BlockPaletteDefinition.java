package com.hexvane.aetherhaven.blockpalette;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One unlockable block palette (e.g. blue walls, softwood roof). */
public final class BlockPaletteDefinition {
    private final String id;
    private final String category;
    private final String displayName;
    private final String familyKey;
    private final String iconBlockId;
    @Nullable
    private final String remapGroupId;

    public BlockPaletteDefinition(
        @Nonnull String id,
        @Nonnull String category,
        @Nonnull String displayName,
        @Nonnull String familyKey,
        @Nonnull String iconBlockId
    ) {
        this(id, category, displayName, familyKey, iconBlockId, null);
    }

    public BlockPaletteDefinition(
        @Nonnull String id,
        @Nonnull String category,
        @Nonnull String displayName,
        @Nonnull String familyKey,
        @Nonnull String iconBlockId,
        @Nullable String remapGroupId
    ) {
        this.id = id.trim();
        this.category = category.trim();
        this.displayName = displayName.trim();
        this.familyKey = familyKey.trim();
        this.iconBlockId = iconBlockId.trim();
        this.remapGroupId =
            remapGroupId != null && !remapGroupId.isBlank() ? remapGroupId.trim() : null;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getCategory() {
        return category;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /** Token used by the remapper (wall color, wood species, rock type, cloth color, roof style key). */
    @Nonnull
    public String getFamilyKey() {
        return familyKey;
    }

    @Nonnull
    public String getIconBlockId() {
        return iconBlockId;
    }

    /** When set, remapping uses {@link BlockPaletteRemapGroup} instead of built-in naming rules. */
    @Nullable
    public String getRemapGroupId() {
        return remapGroupId;
    }
}
