package com.hexvane.aetherhaven.villager.data;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Crossmod patch that appends gift preference item ids onto an existing villager definition. */
public final class VillagerGiftPatchDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @Nullable
    private Integer schemaVersion;
    @Nullable
    private String targetNpcRoleId;
    @Nullable
    private List<String> addGiftLoves;
    @Nullable
    private List<String> addGiftLikes;
    @Nullable
    private List<String> addGiftDislikes;

    public int schemaVersionOrDefault() {
        return schemaVersion != null ? schemaVersion : SUPPORTED_SCHEMA_VERSION;
    }

    @Nullable
    public String getTargetNpcRoleId() {
        return targetNpcRoleId;
    }

    @Nonnull
    public List<String> addGiftLovesOrEmpty() {
        return addGiftLoves != null ? addGiftLoves : List.of();
    }

    @Nonnull
    public List<String> addGiftLikesOrEmpty() {
        return addGiftLikes != null ? addGiftLikes : List.of();
    }

    @Nonnull
    public List<String> addGiftDislikesOrEmpty() {
        return addGiftDislikes != null ? addGiftDislikes : List.of();
    }
}
