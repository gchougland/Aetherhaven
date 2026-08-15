package com.hexvane.aetherhaven.wall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One installed wall style: a piece per role, all sharing a style id. */
public final class WallStyle {
    /** One piece of a style. */
    public record Piece(
        @Nonnull String constructionId,
        @Nonnull WallPieceRole role,
        @Nonnull WallPieceDefinition definition
    ) {}

    @Nonnull
    private final String styleId;

    @Nonnull
    private final String displayName;

    @Nonnull
    private final EnumMap<WallPieceRole, Piece> byRole;

    @Nonnull
    private final Map<String, Piece> byConstructionId;

    private WallStyle(
        @Nonnull String styleId,
        @Nonnull String displayName,
        @Nonnull EnumMap<WallPieceRole, Piece> byRole,
        @Nonnull Map<String, Piece> byConstructionId
    ) {
        this.styleId = styleId;
        this.displayName = displayName;
        this.byRole = byRole;
        this.byConstructionId = byConstructionId;
    }

    @Nonnull
    public static Builder builder(@Nonnull String styleId) {
        return new Builder(styleId);
    }

    @Nonnull
    public String styleId() {
        return styleId;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nullable
    public Piece piece(@Nonnull WallPieceRole role) {
        return byRole.get(role);
    }

    @Nullable
    public Piece pieceByConstructionId(@Nullable String constructionId) {
        return constructionId == null ? null : byConstructionId.get(constructionId);
    }

    /** Pieces in authoring order, skipping roles this style does not provide. */
    @Nonnull
    public List<Piece> piecesInOrder() {
        List<Piece> out = new ArrayList<>(WallPieceRole.AUTHORING_ORDER.length);
        for (WallPieceRole role : WallPieceRole.AUTHORING_ORDER) {
            Piece piece = byRole.get(role);
            if (piece != null) {
                out.add(piece);
            }
        }
        return out;
    }

    /** True when every role needed to build a wall is present. */
    public boolean isComplete() {
        for (WallPieceRole role : WallPieceRole.AUTHORING_ORDER) {
            if (!byRole.containsKey(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the tower that opens onto exactly {@code worldFaces} and the rotation that gets it there. One face picks
     * the end tower, two opposite faces the straight tower, two faces at right angles the corner tower.
     */
    @Nullable
    public ResolvedPiece resolveTower(@Nonnull EnumSet<WallCardinal> worldFaces) {
        WallPieceRole role = towerRoleFor(worldFaces);
        if (role == null) {
            return null;
        }
        Piece piece = byRole.get(role);
        if (piece == null) {
            return null;
        }
        List<Integer> rotations = piece.definition().rotationsMatchingFaces(worldFaces);
        if (rotations.isEmpty()) {
            return null;
        }
        return new ResolvedPiece(piece, rotations.get(0));
    }

    /**
     * Rotation that puts this role's entry connection on {@code entryWorldFace}, preferring a rotation whose other
     * connection continues toward {@code exitWorldFace} when one is given.
     */
    @Nullable
    public ResolvedPiece resolvePiece(
        @Nonnull WallPieceRole role,
        @Nonnull WallCardinal entryWorldFace,
        @Nullable WallCardinal exitWorldFace
    ) {
        Piece piece = byRole.get(role);
        if (piece == null) {
            return null;
        }
        Integer fallback = null;
        for (int steps = 0; steps < 4; steps++) {
            EnumSet<WallCardinal> faces = piece.definition().worldFaces(steps);
            if (!faces.contains(entryWorldFace)) {
                continue;
            }
            if (exitWorldFace != null && faces.contains(exitWorldFace)) {
                return new ResolvedPiece(piece, steps);
            }
            if (fallback == null) {
                fallback = steps;
            }
        }
        return fallback == null ? null : new ResolvedPiece(piece, fallback);
    }

    @Nullable
    public static WallPieceRole towerRoleFor(@Nonnull EnumSet<WallCardinal> worldFaces) {
        if (worldFaces.size() <= 1) {
            return WallPieceRole.TOWER_END;
        }
        if (worldFaces.size() != 2) {
            return null;
        }
        List<WallCardinal> pair = new ArrayList<>(worldFaces);
        return pair.get(0).opposite() == pair.get(1) ? WallPieceRole.TOWER_STRAIGHT : WallPieceRole.TOWER_CORNER;
    }

    /** A style piece plus the rotation that lines its connections up with the world. */
    public record ResolvedPiece(@Nonnull Piece piece, int rotationSteps) {
        @Nonnull
        public String constructionId() {
            return piece.constructionId();
        }

        @Nonnull
        public WallPieceDefinition definition() {
            return piece.definition();
        }
    }

    /** Collects pieces for one style id. */
    public static final class Builder {
        @Nonnull
        private final String styleId;
        @Nullable
        private String displayName;
        @Nonnull
        private final EnumMap<WallPieceRole, Piece> byRole = new EnumMap<>(WallPieceRole.class);
        @Nonnull
        private final Map<String, Piece> byConstructionId = new LinkedHashMap<>();

        private Builder(@Nonnull String styleId) {
            this.styleId = styleId;
        }

        @Nonnull
        public Builder displayName(@Nullable String displayName) {
            if (displayName != null && !displayName.isBlank() && this.displayName == null) {
                this.displayName = displayName.trim();
            }
            return this;
        }

        /** First piece registered for a role wins, so a style stays stable as more buildings load. */
        @Nonnull
        public Builder add(
            @Nonnull String constructionId, @Nonnull WallPieceRole role, @Nonnull WallPieceDefinition definition
        ) {
            Piece piece = new Piece(constructionId, role, definition);
            byConstructionId.put(constructionId, piece);
            if (definition.isSelectable()) {
                byRole.putIfAbsent(role, piece);
            }
            return this;
        }

        @Nonnull
        public WallStyle build() {
            return new WallStyle(
                styleId,
                displayName != null ? displayName : styleId,
                new EnumMap<>(byRole),
                Collections.unmodifiableMap(new LinkedHashMap<>(byConstructionId))
            );
        }
    }
}
