package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceDefinition;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import com.hexvane.aetherhaven.wall.WallStyle;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Turns an installed wall style back into editable wizard pieces. Each piece's stored build box and join spots are
 * prefab local, so they are shifted onto the world position the piece was pasted at.
 */
public final class PlotCreatorWallStyleLoader {
    private PlotCreatorWallStyleLoader() {}

    /** Where one piece of the style was pasted so the player can walk around it. */
    public record PastedPiece(@Nonnull WallPieceRole role, @Nonnull String constructionId, @Nonnull Vector3i origin) {}

    /**
     * The id the wizard shows for a style, built from its style id so it round trips through
     * {@link PlotCreatorWallStyleIds#baseId}. Pieces keep their own ids when the style is saved back, so this does not
     * have to match how they are named.
     */
    @Nullable
    public static String baseIdForStyle(@Nonnull WallStyle style) {
        String slug = style.styleId().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        while (slug.startsWith("_")) {
            slug = slug.substring(1);
        }
        if (slug.isEmpty()) {
            return null;
        }
        return slug.startsWith(PlotCreatorWallStyleIds.ID_PREFIX) ? slug : PlotCreatorWallStyleIds.ID_PREFIX + slug;
    }

    /** True when every piece has a building and a prefab on disk, so the style can be pasted and written back. */
    public static boolean isEditable(@Nonnull ConstructionCatalog catalog, @Nonnull WallStyle style) {
        if (!style.isComplete() || baseIdForStyle(style) == null) {
            return false;
        }
        for (WallStyle.Piece piece : style.piecesInOrder()) {
            ConstructionDefinition def = catalog.get(piece.constructionId());
            if (def == null || def.getPrefabPath() == null || def.getPrefabPath().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fills in the identity, materials and per piece shape of a style that has been pasted into the world.
     *
     * @param pasted the world origin each piece was pasted at, in authoring order
     */
    public static void loadIntoDraft(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull WallStyle style,
        @Nonnull String baseId,
        @Nonnull List<PastedPiece> pasted
    ) {
        draft.setKinds(List.of(PlotBuildingKind.WALL));
        draft.setEditingWallStyleBaseId(baseId);
        draft.setConstructionId(baseId);
        draft.setConstructionIdUserEdited(true);
        draft.setStyleId(style.styleId());
        draft.setSubmitToCommunity(false);
        applySegmentIdentity(draft, catalog, style);

        draft.getWallPieces().clear();
        for (PastedPiece entry : pasted) {
            WallStyle.Piece piece = style.pieceByConstructionId(entry.constructionId());
            ConstructionDefinition def = catalog.get(entry.constructionId());
            if (piece == null || def == null) {
                continue;
            }
            draft.getWallPieces().add(toDraftPiece(entry, piece.definition(), def));
        }
        draft.setWallPieceIndex(0);
        draft.setWallPieceSubstepIndex(0);
        PlotCreatorWallPieceAuthoring.enterCurrentPiece(draft);
    }

    private static void applySegmentIdentity(
        @Nonnull PlotCreatorDraft draft, @Nonnull ConstructionCatalog catalog, @Nonnull WallStyle style
    ) {
        WallStyle.Piece segment = style.piece(WallPieceRole.SEGMENT);
        ConstructionDefinition def = segment == null ? null : catalog.get(segment.constructionId());
        if (def == null) {
            return;
        }
        draft.setDisplayName(def.getDisplayName());
        draft.setDescription(def.getDescription());
        draft.getBuildingTags().clear();
        draft.getBuildingTags().addAll(def.getBuildingTags());
    }

    @Nonnull
    private static PlotCreatorWallPieceDraft toDraftPiece(
        @Nonnull PastedPiece entry,
        @Nonnull WallPieceDefinition definition,
        @Nonnull ConstructionDefinition def
    ) {
        PlotCreatorWallPieceDraft out = new PlotCreatorWallPieceDraft(entry.role());
        // The catalog rebases piece locals onto the plot sign, and pieces are pasted unrotated, so the sign cell is
        // simply the paste origin shifted back by the anchor offset.
        Vector3i sign = signCellForPaste(entry.origin(), def.getPlotAnchorOffset());
        Vector3i min = definition.boundsMinLocal();
        Vector3i max = definition.boundsMaxLocal();
        out.setCornerFirst(new Vector3i(sign.x + min.x, sign.y + min.y, sign.z + min.z));
        out.setCornerSecond(new Vector3i(sign.x + max.x, sign.y + max.y, sign.z + max.z));
        out.setAnchor(new Vector3i(sign));
        out.setConstructionId(def.getId());
        out.setPrefabPath(def.getPrefabPath());
        out.setPlotAnchorOffset(def.getPlotAnchorOffset());
        for (WallCardinal face : entry.role().connectionFaces()) {
            Vector3i local = definition.localConnection(face);
            if (local == null) {
                continue;
            }
            out.getConnections().add(
                new PlotCreatorWallPieceDraft.Connection(
                    face, new Vector3i(sign.x + local.x, sign.y + min.y, sign.z + local.z)
                )
            );
        }
        for (MaterialRequirement req : def.getMaterials()) {
            out.getMaterials().add(req);
        }
        return out;
    }

    /**
     * Cell the piece's stored shape is measured from, for a piece pasted with its prefab origin at
     * {@code prefabOrigin}. {@link com.hexvane.aetherhaven.wall.WallStyleCatalog} has already shifted the piece's build
     * box and join spots onto the plot sign column, so the horizontal offset is undone here. Heights stay relative to
     * the prefab origin, and the piece is re-saved measured from this same cell.
     */
    @Nonnull
    static Vector3i signCellForPaste(@Nonnull Vector3i prefabOrigin, @Nullable int[] plotAnchorOffset) {
        int ox = plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset[0] : 0;
        int oz = plotAnchorOffset != null && plotAnchorOffset.length == 3 ? plotAnchorOffset[2] : 0;
        return new Vector3i(prefabOrigin.x - ox, prefabOrigin.y, prefabOrigin.z - oz);
    }
}
