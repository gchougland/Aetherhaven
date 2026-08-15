package com.hexvane.aetherhaven.community;

import com.hexvane.aetherhaven.plot.PlotCraftingCatalog;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Collapses the pieces of a shared wall style into one crafting bench card. The straight run comes first so it is the
 * thumbnail, and the arrows walk the rest of the pieces in authoring order.
 */
public final class CommunityWallStyleGrouping {
    /** Prefix marking a group key as a whole wall style rather than a single building. */
    public static final String GROUP_PREFIX = "wallstyle:";

    private CommunityWallStyleGrouping() {}

    public static boolean isWallStyleGroupKey(@Nullable String groupKey) {
        return groupKey != null && groupKey.startsWith(GROUP_PREFIX);
    }

    /**
     * Groups the pieces of a style together. Wall styles authored in the plot creator share a style id; entries with no
     * style id fall back to their own id so nothing merges by accident.
     */
    @Nonnull
    public static String groupKeyFor(@Nonnull CommunityManifestEntry entry) {
        String styleId = entry.getStyleId();
        if (styleId == null || styleId.isBlank()) {
            return GROUP_PREFIX + entry.getId().toLowerCase(Locale.ROOT);
        }
        return GROUP_PREFIX + styleId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static List<PlotCraftingCatalog.GroupEntry> toGroups(
        @Nonnull Map<String, List<CommunityManifestEntry>> byStyle
    ) {
        List<PlotCraftingCatalog.GroupEntry> out = new ArrayList<>(byStyle.size());
        for (Map.Entry<String, List<CommunityManifestEntry>> e : byStyle.entrySet()) {
            List<CommunityManifestEntry> pieces = new ArrayList<>(e.getValue());
            pieces.sort(Comparator.comparingInt(CommunityWallStyleGrouping::roleOrder));
            List<PlotCraftingCatalog.VariantEntry> variants = new ArrayList<>(pieces.size());
            for (CommunityManifestEntry piece : pieces) {
                variants.add(
                    new PlotCraftingCatalog.VariantEntry(
                        piece.getId(), piece.getDisplayName(), piece.prefabPathKey()
                    )
                );
            }
            if (variants.isEmpty()) {
                continue;
            }
            out.add(new PlotCraftingCatalog.GroupEntry(e.getKey(), styleDisplayName(pieces), variants));
        }
        return out;
    }

    /** The straight run names the style; if it is missing, the first piece does. */
    @Nonnull
    private static String styleDisplayName(@Nonnull List<CommunityManifestEntry> orderedPieces) {
        for (CommunityManifestEntry piece : orderedPieces) {
            if (piece.getWallPieceRole() == WallPieceRole.SEGMENT) {
                return piece.getDisplayName();
            }
        }
        return orderedPieces.get(0).getDisplayName();
    }

    private static int roleOrder(@Nonnull CommunityManifestEntry entry) {
        WallPieceRole role = entry.getWallPieceRole();
        if (role == null) {
            return WallPieceRole.AUTHORING_ORDER.length;
        }
        for (int i = 0; i < WallPieceRole.AUTHORING_ORDER.length; i++) {
            if (WallPieceRole.AUTHORING_ORDER[i] == role) {
                return i;
            }
        }
        return WallPieceRole.AUTHORING_ORDER.length;
    }
}
