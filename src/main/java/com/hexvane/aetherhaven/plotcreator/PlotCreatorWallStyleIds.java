package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.wall.WallPieceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Ids for the five buildings a wall style saves. The wizard's own construction id is the style base, for example
 * {@code plot_wall_mossy}, and each piece hangs off it as {@code plot_wall_mossy_segment}.
 */
public final class PlotCreatorWallStyleIds {
    public static final String ID_PREFIX = "plot_wall_";

    private PlotCreatorWallStyleIds() {}

    /** The style id shared by every piece, taken from the base construction id. */
    @Nullable
    public static String styleId(@Nonnull PlotCreatorDraft draft) {
        String base = baseId(draft);
        if (base == null) {
            return null;
        }
        String slug = base.substring(ID_PREFIX.length());
        return slug.isEmpty() ? null : slug;
    }

    @Nullable
    public static String baseId(@Nonnull PlotCreatorDraft draft) {
        String raw = draft.getConstructionId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.startsWith(ID_PREFIX) && id.length() > ID_PREFIX.length() ? id : null;
    }

    @Nullable
    public static String pieceConstructionId(@Nonnull PlotCreatorDraft draft, @Nonnull WallPieceRole role) {
        String base = baseId(draft);
        return base == null ? null : base + "_" + role.serialized();
    }

    @Nonnull
    public static List<String> pieceConstructionIds(@Nonnull PlotCreatorDraft draft) {
        String base = baseId(draft);
        return base == null ? List.of() : pieceConstructionIds(draft, base);
    }

    /** Piece ids under an explicit base, used when the community catalog assigns a different base id. */
    @Nonnull
    public static List<String> pieceConstructionIds(@Nonnull PlotCreatorDraft draft, @Nonnull String baseId) {
        List<String> out = new ArrayList<>(draft.getWallPieces().size());
        for (PlotCreatorWallPieceDraft piece : draft.getWallPieces()) {
            out.add(pieceConstructionId(draft, piece, baseId));
        }
        if (out.isEmpty()) {
            for (WallPieceRole role : WallPieceRole.AUTHORING_ORDER) {
                out.add(baseId + "_" + role.serialized());
            }
        }
        return out;
    }

    /**
     * True when the wizard is saving back the same style it loaded in the building editor. Renaming the style turns the
     * save into a fresh copy instead, so the pieces it loaded are left alone.
     */
    public static boolean isSavingLoadedStyle(@Nonnull PlotCreatorDraft draft, @Nonnull String baseId) {
        String editing = draft.getEditingWallStyleBaseId();
        return editing != null && editing.equals(baseId);
    }

    /**
     * Id a piece is written under. A style loaded for editing keeps each piece's own id, which is what lets the shipped
     * walls (whose ids never followed the {@code <base>_<role>} pattern) be edited in place.
     */
    @Nonnull
    public static String pieceConstructionId(
        @Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorWallPieceDraft piece, @Nonnull String baseId
    ) {
        String own = piece.getConstructionId();
        if (isSavingLoadedStyle(draft, baseId) && own != null && !own.isBlank()) {
            return own.trim().toLowerCase(Locale.ROOT);
        }
        return baseId + "_" + piece.getRole().serialized();
    }

    /** Prefab file key a piece is written to, keeping the loaded file name when a style is saved back in place. */
    @Nonnull
    public static String piecePrefabPathKey(
        @Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorWallPieceDraft piece, @Nonnull String baseId
    ) {
        String own = piece.getPrefabPath();
        if (isSavingLoadedStyle(draft, baseId) && own != null && !own.isBlank()) {
            return own.trim();
        }
        return pieceConstructionId(draft, piece, baseId) + ".prefab.json";
    }

    /** The {@code styleId} written into every piece's building file, so the catalog groups them as one wall. */
    @Nonnull
    public static String styleIdForBase(@Nonnull String baseId) {
        String id = baseId.trim().toLowerCase(Locale.ROOT);
        return id.startsWith(ID_PREFIX) && id.length() > ID_PREFIX.length() ? id.substring(ID_PREFIX.length()) : id;
    }
}
