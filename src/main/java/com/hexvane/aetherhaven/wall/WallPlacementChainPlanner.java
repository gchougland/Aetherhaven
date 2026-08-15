package com.hexvane.aetherhaven.wall;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Wall wand chaining: where the next piece sits, how it is turned, and which direction pads are open. Everything comes
 * from the authored connection points of the pieces in the chosen {@link WallStyle}, so there is only one rule for
 * every direction and every piece pairing.
 */
public final class WallPlacementChainPlanner {
    public enum PieceKind {
        SEGMENT,
        GATE,
        TOWER;

        @Nonnull
        public WallPieceRole roleForFirstPiece() {
            return switch (this) {
                case GATE -> WallPieceRole.GATE;
                case TOWER -> WallPieceRole.TOWER_END;
                default -> WallPieceRole.SEGMENT;
            };
        }
    }

    /**
     * @param placedTowerConnectionDirs doors this tower had when it went down, before the run carried on through it.
     *     A tower that started with one door can still be reshaped while it is the last piece, so this is what decides
     *     which way the run may leave it.
     */
    public record ChainCommittedPiece(
        @Nonnull String constructionId,
        @Nonnull Vector3i signAnchor,
        int rotationSteps,
        @Nullable EnumSet<WallCardinal> towerConnectionDirs,
        @Nullable WallCardinal chainExpandDir,
        @Nullable EnumSet<WallCardinal> placedTowerConnectionDirs
    ) {
        public ChainCommittedPiece(
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps,
            @Nullable EnumSet<WallCardinal> towerConnectionDirs,
            @Nullable WallCardinal chainExpandDir
        ) {
            this(constructionId, signAnchor, rotationSteps, towerConnectionDirs, chainExpandDir, towerConnectionDirs);
        }

        @Nonnull
        public Rotation prefabYaw() {
            return rotationStepsFrom(rotationSteps);
        }

        public boolean isTower() {
            return WallPieceGeometry.isTowerConstructionId(constructionId);
        }

        /** Doors this tower can be reshaped from, which is what it was placed with. */
        @Nullable
        public EnumSet<WallCardinal> reshapeFromDirs() {
            return placedTowerConnectionDirs != null ? placedTowerConnectionDirs : towerConnectionDirs;
        }
    }

    public record ExpandPreviewPlan(
        @Nonnull Vector3i anchor,
        int rotationSteps,
        @Nonnull WallCardinal outgoingExpandDir,
        @Nullable WallCardinal arrivalFromSide,
        @Nullable EnumSet<WallCardinal> towerConnections,
        @Nonnull String resolvedConstructionId,
        @Nonnull EnumSet<WallCardinal> allowedExpandDirections
    ) {}

    private WallPlacementChainPlanner() {}

    /**
     * Plans the piece that follows {@code committed} when the player picks the {@code outgoingExpandDir} pad. With
     * nothing placed yet the piece stays where it is and is only turned so it carries on toward that pad.
     */
    @Nullable
    public static ExpandPreviewPlan planExpandPreview(
        @Nonnull WallStyle style,
        @Nonnull Vector3i currentAnchor,
        @Nonnull PieceKind pieceKind,
        @Nonnull List<ChainCommittedPiece> committed,
        @Nonnull WallCardinal outgoingExpandDir
    ) {
        ChainCommittedPiece last = last(committed);
        if (last == null) {
            WallStyle.ResolvedPiece first =
                style.resolvePiece(pieceKind.roleForFirstPiece(), outgoingExpandDir, null);
            if (first == null) {
                return null;
            }
            return new ExpandPreviewPlan(
                new Vector3i(currentAnchor),
                first.rotationSteps(),
                outgoingExpandDir,
                null,
                pieceKind == PieceKind.TOWER ? EnumSet.of(outgoingExpandDir) : null,
                first.constructionId(),
                EnumSet.allOf(WallCardinal.class)
            );
        }

        WallPieceDefinition fromDef = definitionFor(style, last.constructionId());
        if (fromDef == null) {
            return null;
        }
        WallCardinal entryFace = outgoingExpandDir.opposite();

        EnumSet<WallCardinal> towerConnections = null;
        WallStyle.ResolvedPiece next;
        if (pieceKind == PieceKind.TOWER) {
            towerConnections = EnumSet.of(entryFace);
            next = style.resolveTower(towerConnections);
        } else {
            WallPieceRole role = pieceKind == PieceKind.GATE ? WallPieceRole.GATE : WallPieceRole.SEGMENT;
            next = style.resolvePiece(role, entryFace, outgoingExpandDir);
            next = preferRotation(next, last.rotationSteps(), entryFace, outgoingExpandDir);
        }
        if (next == null) {
            return null;
        }

        Vector3i anchor =
            WallPieceGeometry.joinedSignAnchor(
                fromDef,
                last.signAnchor(),
                last.rotationSteps(),
                outgoingExpandDir,
                next.definition(),
                next.rotationSteps()
            );
        if (anchor == null) {
            return null;
        }
        return new ExpandPreviewPlan(
            anchor,
            next.rotationSteps(),
            outgoingExpandDir,
            entryFace,
            towerConnections,
            next.constructionId(),
            allowedExpandDirections(style, pieceKind, committed, entryFace)
        );
    }

    /**
     * Sign anchor for a tower whose connections changed after it was committed, so its join with the wall behind it
     * stays flush.
     */
    @Nullable
    public static Vector3i reseatUpgradedTower(
        @Nonnull WallStyle style,
        @Nonnull ChainCommittedPiece previous,
        @Nonnull WallCardinal incomingFace,
        @Nonnull WallStyle.ResolvedPiece upgraded
    ) {
        WallPieceDefinition fromDef = definitionFor(style, previous.constructionId());
        if (fromDef == null) {
            return null;
        }
        return WallPieceGeometry.joinedSignAnchor(
            fromDef,
            previous.signAnchor(),
            previous.rotationSteps(),
            incomingFace.opposite(),
            upgraded.definition(),
            upgraded.rotationSteps()
        );
    }

    /**
     * Direction pads the player may use next. A piece can only be left through a face that carries a connection, and
     * never back into the piece it came from, so a straight wall only ever carries on the way it is already pointing. A
     * tower waiting on a direction is the exception: the run may leave it any way, because the tower is reshaped into
     * the straight or corner tower that suits.
     */
    @Nonnull
    public static EnumSet<WallCardinal> allowedExpandDirections(
        @Nonnull WallStyle style,
        @Nonnull PieceKind pieceKind,
        @Nonnull List<ChainCommittedPiece> committed,
        @Nullable WallCardinal arrivalFromSide
    ) {
        ChainCommittedPiece last = last(committed);
        if (last == null) {
            return EnumSet.allOf(WallCardinal.class);
        }
        WallPieceDefinition def = definitionFor(style, last.constructionId());
        if (def == null) {
            return EnumSet.allOf(WallCardinal.class);
        }
        WallCardinal back = incomingFace(last, arrivalFromSide);
        EnumSet<WallCardinal> faces = def.worldFaces(last.rotationSteps());
        EnumSet<WallCardinal> allowed = canStillTurnAtTower(style, last) ? EnumSet.allOf(WallCardinal.class) : EnumSet.copyOf(faces);
        if (back != null && allowed.size() > 1) {
            allowed.remove(back);
        }
        return allowed.isEmpty() ? EnumSet.copyOf(faces) : allowed;
    }

    /**
     * Direction pads open while a tower is being lined up against the piece behind it. The tower can only sit on that
     * piece's open face, so the pads pick where the run leaves the tower: straight on, or a turn either way. Styles
     * without a corner tower can only carry straight on.
     *
     * @param attachDir direction from the piece behind to the tower
     */
    @Nonnull
    public static EnumSet<WallCardinal> allowedExpandDirectionsForNewTower(
        @Nonnull WallStyle style, @Nonnull WallCardinal attachDir
    ) {
        if (style.piece(WallPieceRole.TOWER_CORNER) == null) {
            return EnumSet.of(attachDir);
        }
        EnumSet<WallCardinal> out = EnumSet.allOf(WallCardinal.class);
        out.remove(attachDir.opposite());
        return out;
    }

    /**
     * True when {@code piece} is a tower still sitting on a single door, so the run has not been sent anywhere from it
     * yet and the wand can swap it for the straight or corner tower that suits whichever way the player picks. Once a
     * direction is taken the tower has that door and the run carries on through it like any other piece.
     */
    public static boolean canStillTurnAtTower(@Nonnull WallStyle style, @Nonnull ChainCommittedPiece piece) {
        if (!piece.isTower() || style.piece(WallPieceRole.TOWER_CORNER) == null) {
            return false;
        }
        EnumSet<WallCardinal> doors = piece.towerConnectionDirs();
        return doors != null && doors.size() == 1;
    }

    /** Face of {@code piece} that the chain arrived through, when known. */
    @Nullable
    public static WallCardinal incomingFace(
        @Nonnull ChainCommittedPiece piece, @Nullable WallCardinal arrivalFromSide
    ) {
        if (piece.chainExpandDir() != null) {
            return piece.chainExpandDir().opposite();
        }
        return arrivalFromSide;
    }

    /** Keeps a straight run visually consistent by reusing the previous rotation when it also fits. */
    @Nullable
    private static WallStyle.ResolvedPiece preferRotation(
        @Nullable WallStyle.ResolvedPiece resolved,
        int preferredSteps,
        @Nonnull WallCardinal entryFace,
        @Nonnull WallCardinal exitFace
    ) {
        if (resolved == null) {
            return null;
        }
        EnumSet<WallCardinal> faces = resolved.definition().worldFaces(preferredSteps);
        if (faces.contains(entryFace) && faces.contains(exitFace)) {
            return new WallStyle.ResolvedPiece(resolved.piece(), preferredSteps);
        }
        return resolved;
    }

    @Nullable
    private static WallPieceDefinition definitionFor(@Nonnull WallStyle style, @Nonnull String constructionId) {
        WallStyle.Piece piece = style.pieceByConstructionId(constructionId);
        if (piece != null) {
            return piece.definition();
        }
        return WallStyleCatalog.get().definitionFor(constructionId);
    }

    @Nullable
    private static ChainCommittedPiece last(@Nonnull List<ChainCommittedPiece> committed) {
        return committed.isEmpty() ? null : committed.get(committed.size() - 1);
    }

    @Nonnull
    public static Rotation rotationStepsFrom(int steps) {
        return WallCardinal.prefabYawFromSteps(steps);
    }
}
