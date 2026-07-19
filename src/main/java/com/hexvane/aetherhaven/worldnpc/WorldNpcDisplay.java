package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.ui.NpcPortraitProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves display name and portrait for a world NPC placement. */
public final class WorldNpcDisplay {
    private WorldNpcDisplay() {}

    @Nonnull
    public static String displayName(@Nonnull WorldNpcPlacementRecord placement) {
        String override = placement.displayNameOrEmpty();
        if (!override.isEmpty()) {
            return override;
        }
        String role = placement.npcRoleIdOrEmpty();
        if (!role.isEmpty()) {
            return NpcPortraitProvider.displayLabelForRoleId(role);
        }
        return placement.placementIdOrEmpty();
    }

    @Nonnull
    public static String portraitPath(@Nonnull WorldNpcPlacementRecord placement) {
        String icon = placement.portraitIconOrEmpty();
        if (!icon.isEmpty()) {
            return NpcPortraitProvider.portraitPathForModelAssetId(icon);
        }
        String role = placement.npcRoleIdOrEmpty();
        if (!role.isEmpty()) {
            return NpcPortraitProvider.portraitPathForRoleId(role);
        }
        return NpcPortraitProvider.portraitPathForRoleId("");
    }

    @Nonnull
    public static String truncate(@Nullable String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, Math.max(1, maxChars - 1)) + "…";
    }
}
