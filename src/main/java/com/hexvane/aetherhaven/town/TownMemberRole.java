package com.hexvane.aetherhaven.town;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Permissions for non-owner town members. Owner always has full access. */
public enum TownMemberRole {
    /** Place plots and complete construction for the town. */
    BUILD,
    /** Start/complete/abandon town quests (dialogue + journal). */
    QUEST,
    /** Both build and quest permissions (default for invites). */
    BOTH;

    public boolean allowsBuild() {
        return this == BUILD || this == BOTH;
    }

    public boolean allowsQuest() {
        return this == QUEST || this == BOTH;
    }

    @Nonnull
    public static TownMemberRole fromSerialized(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return BOTH;
        }
        try {
            return TownMemberRole.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BOTH;
        }
    }
}
