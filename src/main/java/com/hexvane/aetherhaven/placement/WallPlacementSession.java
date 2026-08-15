package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.WallSegmentRecord;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceGeometry;
import com.hexvane.aetherhaven.wall.WallPieceRole;
import com.hexvane.aetherhaven.wall.WallPlacementChainPlanner;
import com.hexvane.aetherhaven.wall.WallStyle;
import com.hexvane.aetherhaven.wall.WallStyleCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class WallPlacementSession {
    public enum PieceKind {
        SEGMENT,
        GATE,
        TOWER
    }

    /** Result of turning a one door tower into a straight or corner tower once the next piece is chosen. */
    public record TowerUpgrade(@Nonnull Vector3i previousSignAnchor, boolean moved) {}

    public static final class CommittedStep {
        @Nonnull
        public final UUID plotId;
        @Nonnull
        public final String constructionId;
        @Nonnull
        public final Vector3i signAnchor;
        public final int rotationSteps;

        @Nullable
        public final EnumSet<WallCardinal> towerConnectionDirs;

        /**
         * Doors this tower had when it was first placed, before the next piece turned it into a straight or corner
         * tower. Undoing that next piece puts the tower back to this.
         */
        @Nullable
        public final EnumSet<WallCardinal> initialTowerConnectionDirs;

        /** World direction from the previous committed sign to this one (pad direction on place). */
        @Nullable
        public final WallCardinal chainExpandDir;

        /**
         * Prefab buffer origin frozen when this step was committed (from the live preview anchor). Stored as ints so
         * refresh never shares mutable {@link Vector3i} state with the active placement anchor.
         */
        public final int ghostOriginX;
        public final int ghostOriginY;
        public final int ghostOriginZ;

        public CommittedStep(
            @Nonnull UUID plotId,
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps
        ) {
            this(plotId, constructionId, signAnchor, rotationSteps, null, null, signAnchor);
        }

        public CommittedStep(
            @Nonnull UUID plotId,
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps,
            @Nullable EnumSet<WallCardinal> towerConnectionDirs
        ) {
            this(plotId, constructionId, signAnchor, rotationSteps, towerConnectionDirs, null, signAnchor);
        }

        public CommittedStep(
            @Nonnull UUID plotId,
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps,
            @Nullable EnumSet<WallCardinal> towerConnectionDirs,
            @Nullable WallCardinal chainExpandDir
        ) {
            this(plotId, constructionId, signAnchor, rotationSteps, towerConnectionDirs, chainExpandDir, signAnchor);
        }

        public CommittedStep(
            @Nonnull UUID plotId,
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps,
            @Nullable EnumSet<WallCardinal> towerConnectionDirs,
            @Nullable WallCardinal chainExpandDir,
            @Nonnull Vector3i ghostPrefabOriginWorld
        ) {
            this(
                plotId,
                constructionId,
                signAnchor,
                rotationSteps,
                towerConnectionDirs,
                towerConnectionDirs,
                chainExpandDir,
                ghostPrefabOriginWorld
            );
        }

        public CommittedStep(
            @Nonnull UUID plotId,
            @Nonnull String constructionId,
            @Nonnull Vector3i signAnchor,
            int rotationSteps,
            @Nullable EnumSet<WallCardinal> towerConnectionDirs,
            @Nullable EnumSet<WallCardinal> initialTowerConnectionDirs,
            @Nullable WallCardinal chainExpandDir,
            @Nonnull Vector3i ghostPrefabOriginWorld
        ) {
            this.plotId = plotId;
            this.constructionId = constructionId;
            this.signAnchor = new Vector3i(signAnchor.x, signAnchor.y, signAnchor.z);
            this.rotationSteps = rotationSteps;
            this.towerConnectionDirs = copyOrNull(towerConnectionDirs);
            this.initialTowerConnectionDirs = copyOrNull(initialTowerConnectionDirs);
            this.chainExpandDir = chainExpandDir;
            this.ghostOriginX = ghostPrefabOriginWorld.x;
            this.ghostOriginY = ghostPrefabOriginWorld.y;
            this.ghostOriginZ = ghostPrefabOriginWorld.z;
        }

        @Nullable
        private static EnumSet<WallCardinal> copyOrNull(@Nullable EnumSet<WallCardinal> dirs) {
            return dirs == null || dirs.isEmpty() ? null : EnumSet.copyOf(dirs);
        }

        @Nonnull
        public Vector3i ghostPrefabOriginWorld() {
            return new Vector3i(ghostOriginX, ghostOriginY, ghostOriginZ);
        }

        @Nonnull
        public Rotation getPrefabYaw() {
            return rotationStepsFrom(rotationSteps);
        }
    }

    @Nonnull
    private final World world;

    @Nonnull
    private Vector3i currentAnchor;

    private int currentRotationSteps;

    @Nonnull
    private PieceKind pieceKind = PieceKind.SEGMENT;

    /** Wall style the wand is building with. Null means the default style. */
    @Nullable
    private String wallStyleId;

    @Nonnull
    private final EnumSet<WallCardinal> towerConnections = EnumSet.noneOf(WallCardinal.class);

    @Nonnull
    private final List<CommittedStep> committed = new ArrayList<>();

    @Nonnull
    private final List<Ref<EntityStore>> previewEntityRefs = new ObjectArrayList<>();

    private boolean birdsEyeSnapshotValid;
    private double birdsEyeSnapshotX;
    private double birdsEyeSnapshotY;
    private double birdsEyeSnapshotZ;
    @Nonnull
    private WallCardinal cameraViewFromSide = WallCardinal.SOUTH;

    @Nullable
    private UUID editTargetPlotId;

    @Nullable
    private UUID editTargetSegmentId;

    private boolean removeConfirmOpen;

    /** Last world direction used when chaining with the placement pad (for tower auto-connect). */
    @Nullable
    private WallCardinal lastExpandDir;

    /** World side the previous committed piece lies on relative to {@link #currentAnchor}. */
    @Nullable
    private WallCardinal arrivalFromSide;

    /** Pad direction for the piece currently being placed (set before {@code tryPlace}). */
    @Nullable
    private WallCardinal placementExpandDir;

    /** Construction id of the current preview piece, resolved from the style. */
    @Nullable
    private String previewConstructionId;

    /**
     * True after Continue on an existing piece: blocks auto-preview until the player picks an expand pad, and marks
     * the in-world seed so we do not stack a ghost prefab on it.
     */
    private boolean seededContinueFromEdit;

    private boolean editContinueInFlight;

    /** Set when the player uses Y nudge; keeps preview Y across expand-pad preview until the next place. */
    private boolean heightManuallyAdjusted;

    private static boolean defaultDebugLogging = false;

    private boolean debugLogging = defaultDebugLogging;

    public WallPlacementSession(@Nonnull World world, @Nonnull Vector3i startAnchor) {
        this.world = world;
        this.currentAnchor = new Vector3i(startAnchor);
        this.currentRotationSteps = 0;
    }

    public static boolean isDefaultDebugLogging() {
        return defaultDebugLogging;
    }

    public static void setDefaultDebugLogging(boolean defaultDebugLogging) {
        WallPlacementSession.defaultDebugLogging = defaultDebugLogging;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    @Nonnull
    public String describeState() {
        CommittedStep last = getLastCommitted();
        String lastDesc =
            last == null
                ? "none"
                : last.constructionId
                    + "@"
                    + last.signAnchor
                    + " rot="
                    + last.rotationSteps
                    + " chain="
                    + last.chainExpandDir
                    + " towerFaces="
                    + WallPlacementDebug.formatDirs(last.towerConnectionDirs);
        return "piece="
            + pieceKind
            + " style="
            + resolveStyleId()
            + " anchor="
            + currentAnchor
            + " rot="
            + currentRotationSteps
            + " arrival="
            + arrivalFromSide
            + " lastExpand="
            + lastExpandDir
            + " placeExpand="
            + placementExpandDir
            + " towerConn="
            + WallPlacementDebug.formatDirs(towerConnections)
            + " allowed="
            + WallPlacementDebug.formatAllowed(allowedExpandDirections())
            + " commits="
            + committed.size()
            + " last="
            + lastDesc
            + " resolved="
            + resolveConstructionId();
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public Vector3i getCurrentAnchor() {
        return new Vector3i(currentAnchor);
    }

    public void setCurrentAnchor(@Nonnull Vector3i anchor) {
        this.currentAnchor = new Vector3i(anchor);
    }

    public int getCurrentRotationSteps() {
        return currentRotationSteps;
    }

    public void setCurrentRotationSteps(int steps) {
        this.currentRotationSteps = (steps % 4 + 4) % 4;
    }

    @Nonnull
    public Rotation getCurrentPrefabYaw() {
        return rotationStepsFrom(currentRotationSteps);
    }

    @Nonnull
    public PieceKind getPieceKind() {
        return pieceKind;
    }

    @Nullable
    public String getWallStyleId() {
        return wallStyleId;
    }

    @Nonnull
    public String resolveStyleId() {
        WallStyle style = resolveStyle();
        return style != null ? style.styleId() : WallStyleCatalog.DEFAULT_STYLE_ID;
    }

    /** Style in use, falling back to the default style when the pick is missing or incomplete. */
    @Nullable
    public WallStyle resolveStyle() {
        WallStyleCatalog catalog = WallStyleCatalog.get();
        WallStyle picked = catalog.style(wallStyleId);
        if (picked != null) {
            return picked;
        }
        return catalog.defaultStyle();
    }

    /** Switches style and rebuilds the preview so the ghost shows the new pieces. */
    public boolean setWallStyleId(@Nullable String styleId) {
        String next = styleId == null ? null : WallStyleCatalog.normalizeStyleId(styleId);
        if (java.util.Objects.equals(next, wallStyleId)) {
            return false;
        }
        wallStyleId = next;
        previewConstructionId = null;
        clearBirdsEyeSnapshot();
        realignPreviewToLastCommitted();
        return true;
    }

    /** Moves to the next or previous installed style; false when only one style is installed. */
    public boolean cycleWallStyle(int delta) {
        List<String> ids = WallStyleCatalog.get().completeStyleIds();
        if (ids.size() <= 1) {
            return false;
        }
        int index = Math.max(0, ids.indexOf(resolveStyleId()));
        int next = ((index + delta) % ids.size() + ids.size()) % ids.size();
        return setWallStyleId(ids.get(next));
    }

    public void setPieceKind(@Nonnull PieceKind pieceKind) {
        if (pieceKind == PieceKind.TOWER && !canPlaceTowerNow()) {
            this.pieceKind = PieceKind.SEGMENT;
            towerConnections.clear();
            realignPreviewToLastCommitted();
            return;
        }
        this.pieceKind = pieceKind;
        clearBirdsEyeSnapshot();
        previewConstructionId = null;
        if (pieceKind != PieceKind.TOWER) {
            towerConnections.clear();
        } else {
            CommittedStep last = getLastCommitted();
            if (lastExpandDir == null && last != null && last.chainExpandDir != null) {
                lastExpandDir = last.chainExpandDir;
            }
            refreshArrivalFromSide();
        }
        realignPreviewToLastCommitted();
    }

    /** Adopts the style and piece type of an existing wall piece when editing or continuing it. */
    public void adoptPieceForEdit(@Nullable String constructionId) {
        WallStyleCatalog catalog = WallStyleCatalog.get();
        String styleId = catalog.styleIdForConstruction(constructionId);
        if (styleId != null) {
            wallStyleId = styleId;
        }
        WallPieceRole role = catalog.roleFor(constructionId);
        if (role == null) {
            return;
        }
        if (role.isTower()) {
            pieceKind = PieceKind.TOWER;
        } else if (role == WallPieceRole.GATE) {
            pieceKind = PieceKind.GATE;
        } else {
            pieceKind = PieceKind.SEGMENT;
        }
    }

    /**
     * Recomputes {@link #currentAnchor} for the selected piece type so wall, gate and tower previews sit at the
     * correct joint distance from the last committed piece.
     */
    public void realignPreviewToLastCommitted() {
        WallCardinal expandDir = resolveChainExpandDir();
        if (expandDir == null) {
            if (getLastCommitted() == null) {
                applyStandalonePreviewDefaults();
            }
            return;
        }
        // Only reseats the preview. Doors on the tower behind it are left alone until the player commits a direction,
        // so swapping between wall, gate, and tower never changes a piece that is already down.
        planPreviewFromLastCommitted(expandDir);
    }

    /** Resolves the piece for the very first preview, before the player picks a direction. */
    public void applyStandalonePreviewDefaults() {
        WallStyle style = resolveStyle();
        if (style == null) {
            return;
        }
        WallPieceRole role =
            pieceKind == PieceKind.TOWER
                ? WallPieceRole.TOWER_END
                : pieceKind == PieceKind.GATE ? WallPieceRole.GATE : WallPieceRole.SEGMENT;
        WallCardinal facing = lastExpandDir != null ? lastExpandDir : WallCardinal.SOUTH;
        WallStyle.ResolvedPiece resolved = style.resolvePiece(role, facing, null);
        if (resolved == null) {
            return;
        }
        previewConstructionId = resolved.constructionId();
        currentRotationSteps = resolved.rotationSteps();
        towerConnections.clear();
        if (pieceKind == PieceKind.TOWER) {
            towerConnections.add(facing);
        }
    }

    /** Applies resolved yaw before {@link #resolveConstructionId()} is used for preview or place. */
    public void prepareTowerForCommit() {
        if (pieceKind != PieceKind.TOWER) {
            return;
        }
        if (towerConnections.isEmpty()) {
            applyStandalonePreviewDefaults();
            return;
        }
        applyTowerResolvedRotation();
    }

    /**
     * Picks where the current preview piece will sit relative to the last commit (does not place). Expand pads call
     * this; use the Place button to commit.
     *
     * @return the upgrade applied to a one door tower behind the new piece, or null when nothing changed
     */
    @Nullable
    public TowerUpgrade previewExpandDirection(@Nonnull WallCardinal expandDir) {
        TowerUpgrade upgrade = upgradeLastCommittedTowerIfNeeded(expandDir);
        seededContinueFromEdit = false;
        planPreviewFromLastCommitted(expandDir);
        return upgrade;
    }

    /** Reseats the preview against the last committed piece without touching that piece's doors. */
    public void planPreviewFromLastCommitted(@Nonnull WallCardinal expandDir) {
        WallStyle style = resolveStyle();
        if (style == null) {
            return;
        }
        WallPlacementChainPlanner.ExpandPreviewPlan plan =
            WallPlacementChainPlanner.planExpandPreview(
                style,
                new Vector3i(currentAnchor),
                toPlannerPieceKind(pieceKind),
                toPlannerCommitted(),
                expandDir
            );
        if (plan != null) {
            applyExpandPreviewPlan(plan);
        }
    }

    /**
     * After a one door tower is committed, the next pad adds the outgoing face and swaps the plot to the matching
     * corner or run through tower (before the following wall or gate is placed).
     */
    @Nullable
    public TowerUpgrade upgradeLastCommittedTowerIfNeeded(@Nonnull WallCardinal outgoingExpandDir) {
        if (pieceKind == PieceKind.TOWER) {
            return null;
        }
        return applyOutgoingDirectionToLastTower(outgoingExpandDir);
    }

    /**
     * Snaps the preview to the ground under the current column. Only used while the player is still positioning the
     * very first piece: once a run has started the wall keeps the height the player chose, so a long wall stays level
     * instead of following the terrain.
     */
    public void reGroundSignYAtCurrentColumn(
        @Nonnull World world,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buf
    ) {
        if (heightManuallyAdjusted || !isAdjustingFirstPiece()) {
            return;
        }
        int signY =
            PlotSignGrounding.resolveSignYAtColumn(
                world,
                currentAnchor.x,
                currentAnchor.z,
                currentAnchor.y,
                currentAnchor.y
            );
        currentAnchor = new Vector3i(currentAnchor.x, signY, currentAnchor.z);
    }

    private void applyExpandPreviewPlan(@Nonnull WallPlacementChainPlanner.ExpandPreviewPlan plan) {
        int previewY = currentAnchor.y;
        currentAnchor = new Vector3i(plan.anchor().x, previewY, plan.anchor().z);
        currentRotationSteps = plan.rotationSteps();
        lastExpandDir = plan.outgoingExpandDir();
        placementExpandDir = plan.outgoingExpandDir();
        arrivalFromSide = plan.arrivalFromSide();
        towerConnections.clear();
        if (plan.towerConnections() != null) {
            towerConnections.addAll(plan.towerConnections());
        }
        previewConstructionId = plan.resolvedConstructionId();
    }

    @Nullable
    private WallCardinal resolveChainExpandDir() {
        if (lastExpandDir != null) {
            return lastExpandDir;
        }
        if (seededContinueFromEdit) {
            return null;
        }
        CommittedStep last = getLastCommitted();
        if (last != null && last.chainExpandDir != null) {
            return last.chainExpandDir;
        }
        if (arrivalFromSide != null) {
            return arrivalFromSide.opposite();
        }
        return null;
    }

    public boolean isSeededContinueFromEdit() {
        return seededContinueFromEdit;
    }

    /** Towers cannot be placed twice in a row; use wall or gate after a tower until another segment is placed. */
    public boolean canPlaceTowerNow() {
        CommittedStep last = getLastCommitted();
        return last == null || !WallPieceGeometry.isTowerConstructionId(last.constructionId);
    }

    public void afterTowerCommittedSwitchToWall() {
        pieceKind = PieceKind.SEGMENT;
        previewConstructionId = null;
        towerConnections.clear();
        refreshArrivalFromSide();
    }

    @Nonnull
    public EnumSet<WallCardinal> getTowerConnections() {
        return EnumSet.copyOf(towerConnections);
    }

    /**
     * Before placing: opens the outgoing face on the tower behind the new piece and reseats it so it still meets the
     * wall behind it. The tower is always reshaped from the doors it was placed with, so changing your mind and
     * picking another direction turns a straight tower into a corner one (or back again) instead of piling doors on.
     */
    @Nullable
    public TowerUpgrade applyOutgoingDirectionToLastTower(@Nonnull WallCardinal outgoingExpandDir) {
        CommittedStep last = getLastCommitted();
        if (last == null || last.towerConnectionDirs == null) {
            return null;
        }
        EnumSet<WallCardinal> placedDoors =
            last.initialTowerConnectionDirs != null ? last.initialTowerConnectionDirs : last.towerConnectionDirs;
        if (placedDoors.size() != 1) {
            return null;
        }
        EnumSet<WallCardinal> pair = EnumSet.copyOf(placedDoors);
        pair.add(outgoingExpandDir);
        if (pair.equals(last.towerConnectionDirs)) {
            return null;
        }
        WallStyle style = resolveStyle();
        if (style == null) {
            return null;
        }
        WallStyle.ResolvedPiece resolved = style.resolveTower(pair);
        if (resolved == null) {
            return null;
        }
        WallCardinal incoming = placedDoors.iterator().next();
        Vector3i anchor = new Vector3i(last.signAnchor);
        CommittedStep previous = getPreviousCommitted();
        if (previous != null) {
            Vector3i reseated =
                WallPlacementChainPlanner.reseatUpgradedTower(
                    style, toPlannerPiece(previous), incoming, resolved
                );
            if (reseated != null) {
                anchor = new Vector3i(reseated.x, last.signAnchor.y, reseated.z);
            }
        }
        boolean moved = anchor.x != last.signAnchor.x || anchor.z != last.signAnchor.z;
        Vector3i ghostOrigin = last.ghostPrefabOriginWorld();
        ConstructionDefinition def = resolveConstructionDef(resolved.constructionId());
        if (def != null) {
            ghostOrigin = def.resolvePrefabAnchorWorld(anchor, rotationStepsFrom(resolved.rotationSteps()));
        }
        Vector3i previousAnchor = new Vector3i(last.signAnchor);
        committed.set(
            committed.size() - 1,
            new CommittedStep(
                last.plotId,
                resolved.constructionId(),
                anchor,
                resolved.rotationSteps(),
                pair,
                placedDoors,
                last.chainExpandDir,
                ghostOrigin
            )
        );
        return new TowerUpgrade(previousAnchor, moved);
    }

    /**
     * Puts the last committed tower back to the doors it had when it was placed. Called after the piece that followed
     * it is undone, so a run-through or corner tower becomes an end cap again.
     *
     * @return the change that was applied, or null when the last piece is not an upgraded tower
     */
    @Nullable
    public TowerUpgrade revertLastCommittedTowerUpgrade() {
        CommittedStep last = getLastCommitted();
        if (last == null
            || last.initialTowerConnectionDirs == null
            || last.towerConnectionDirs == null
            || last.towerConnectionDirs.equals(last.initialTowerConnectionDirs)) {
            return null;
        }
        WallStyle style = resolveStyle();
        if (style == null) {
            return null;
        }
        WallStyle.ResolvedPiece resolved = style.resolveTower(EnumSet.copyOf(last.initialTowerConnectionDirs));
        if (resolved == null) {
            return null;
        }
        Vector3i anchor = new Vector3i(last.signAnchor);
        CommittedStep previous = getPreviousCommitted();
        if (previous != null && last.initialTowerConnectionDirs.size() == 1) {
            WallCardinal incoming = last.initialTowerConnectionDirs.iterator().next();
            Vector3i reseated =
                WallPlacementChainPlanner.reseatUpgradedTower(style, toPlannerPiece(previous), incoming, resolved);
            if (reseated != null) {
                anchor = new Vector3i(reseated.x, last.signAnchor.y, reseated.z);
            }
        }
        boolean moved = anchor.x != last.signAnchor.x || anchor.z != last.signAnchor.z;
        Vector3i ghostOrigin = last.ghostPrefabOriginWorld();
        ConstructionDefinition def = resolveConstructionDef(resolved.constructionId());
        if (def != null) {
            ghostOrigin = def.resolvePrefabAnchorWorld(anchor, rotationStepsFrom(resolved.rotationSteps()));
        }
        Vector3i previousSign = new Vector3i(last.signAnchor);
        committed.set(
            committed.size() - 1,
            new CommittedStep(
                last.plotId,
                resolved.constructionId(),
                anchor,
                resolved.rotationSteps(),
                last.initialTowerConnectionDirs,
                last.initialTowerConnectionDirs,
                last.chainExpandDir,
                ghostOrigin
            )
        );
        return new TowerUpgrade(previousSign, moved);
    }

    public void setPlacementExpandDir(@Nullable WallCardinal placementExpandDir) {
        this.placementExpandDir = placementExpandDir;
    }

    @Nullable
    public WallCardinal getPlacementExpandDir() {
        return placementExpandDir;
    }

    @Nullable
    public WallCardinal getLastExpandDir() {
        return lastExpandDir;
    }

    @Nonnull
    public EnumSet<WallCardinal> allowedExpandDirections() {
        WallStyle style = resolveStyle();
        if (style == null) {
            return EnumSet.allOf(WallCardinal.class);
        }
        WallCardinal attach = towerAttachDir();
        if (attach != null) {
            return WallPlacementChainPlanner.allowedExpandDirectionsForNewTower(style, attach);
        }
        return WallPlacementChainPlanner.allowedExpandDirections(
            style, toPlannerPieceKind(pieceKind), toPlannerCommitted(), arrivalFromSide
        );
    }

    /**
     * Direction from the last placed piece to the tower being previewed, which is the only way that tower can attach.
     * Null when no tower is being previewed or the wand does not know the slot yet (the first piece of a run, or
     * straight after Continue), where the pad the player picks is the direction the tower attaches in.
     */
    @Nullable
    public WallCardinal towerAttachDir() {
        if (pieceKind != PieceKind.TOWER || getLastCommitted() == null) {
            return null;
        }
        return resolveChainExpandDir();
    }

    private void refreshArrivalFromSide() {
        CommittedStep last = getLastCommitted();
        if (last == null) {
            arrivalFromSide = null;
            return;
        }
        if (last.chainExpandDir != null) {
            arrivalFromSide = last.chainExpandDir.opposite();
            return;
        }
        arrivalFromSide = WallCardinal.fromVector(currentAnchor, last.signAnchor);
    }

    /**
     * Restores preview to the piece that was just removed so the player can adjust or re-place it. Does not snap to the
     * first commit or recompute a forward chain slot.
     */
    public void restoreStateAfterUndo(@Nullable CommittedStep undone) {
        seededContinueFromEdit = false;
        placementExpandDir = null;
        previewConstructionId = null;
        clearBirdsEyeSnapshot();
        towerConnections.clear();

        if (undone == null) {
            pieceKind = PieceKind.SEGMENT;
            lastExpandDir = null;
            arrivalFromSide = null;
            return;
        }

        currentAnchor = new Vector3i(undone.signAnchor);
        currentRotationSteps = undone.rotationSteps;
        previewConstructionId = undone.constructionId;
        adoptPieceForEdit(undone.constructionId);
        if (pieceKind == PieceKind.TOWER && undone.towerConnectionDirs != null) {
            towerConnections.addAll(undone.towerConnectionDirs);
        }

        if (committed.isEmpty()) {
            lastExpandDir = null;
            arrivalFromSide = null;
            return;
        }

        lastExpandDir = undone.chainExpandDir;
        if (lastExpandDir != null) {
            arrivalFromSide = lastExpandDir.opposite();
        } else {
            CommittedStep last = getLastCommitted();
            arrivalFromSide = last != null ? WallCardinal.fromVector(undone.signAnchor, last.signAnchor) : null;
        }
    }

    @Nonnull
    public String resolveConstructionId() {
        if (previewConstructionId != null) {
            return previewConstructionId;
        }
        WallStyle style = resolveStyle();
        if (style != null) {
            if (pieceKind == PieceKind.TOWER) {
                WallStyle.ResolvedPiece tower =
                    towerConnections.isEmpty() ? null : style.resolveTower(EnumSet.copyOf(towerConnections));
                if (tower != null) {
                    return tower.constructionId();
                }
                WallStyle.Piece end = style.piece(WallPieceRole.TOWER_END);
                if (end != null) {
                    return end.constructionId();
                }
            }
            WallStyle.Piece piece =
                style.piece(pieceKind == PieceKind.GATE ? WallPieceRole.GATE : WallPieceRole.SEGMENT);
            if (piece != null) {
                return piece.constructionId();
            }
        }
        return switch (pieceKind) {
            case GATE -> AetherhavenConstants.CONSTRUCTION_PLOT_WALL_GATE;
            case TOWER -> AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_ENDCAP_S;
            default -> AetherhavenConstants.CONSTRUCTION_PLOT_WALL_SEGMENT;
        };
    }

    public void applyTowerResolvedRotation() {
        if (pieceKind != PieceKind.TOWER || towerConnections.isEmpty()) {
            return;
        }
        WallStyle style = resolveStyle();
        if (style == null) {
            return;
        }
        WallStyle.ResolvedPiece resolved = style.resolveTower(EnumSet.copyOf(towerConnections));
        if (resolved != null) {
            currentRotationSteps = resolved.rotationSteps();
            previewConstructionId = resolved.constructionId();
        }
    }

    @Nonnull
    public List<CommittedStep> getCommitted() {
        return List.copyOf(committed);
    }

    @Nullable
    public CommittedStep getLastCommitted() {
        return committed.isEmpty() ? null : committed.get(committed.size() - 1);
    }

    @Nullable
    public CommittedStep getPreviousCommitted() {
        int n = committed.size();
        return n >= 2 ? committed.get(n - 2) : null;
    }

    public void addCommitted(@Nonnull CommittedStep step) {
        committed.add(step);
    }

    @Nullable
    public CommittedStep undoLastCommitted() {
        if (committed.isEmpty()) {
            return null;
        }
        return committed.remove(committed.size() - 1);
    }

    @Nullable
    public EnumSet<WallCardinal> towerConnectionsForCommit() {
        if (pieceKind != PieceKind.TOWER) {
            return null;
        }
        if (towerConnections.isEmpty()) {
            WallCardinal facing = placementExpandDir != null ? placementExpandDir : WallCardinal.SOUTH;
            return EnumSet.of(facing);
        }
        return EnumSet.copyOf(towerConnections);
    }

    @Nullable
    public WallCardinal chainExpandDirForCommit() {
        if (placementExpandDir != null) {
            return placementExpandDir;
        }
        if (lastExpandDir != null) {
            return lastExpandDir;
        }
        return null;
    }

    public void nudgeY(int dy) {
        currentAnchor = new Vector3i(currentAnchor.x, currentAnchor.y + dy, currentAnchor.z);
        heightManuallyAdjusted = true;
    }

    /** One-block shift on the XZ plane (bird's-eye world axes). */
    public void nudgeHorizontal(int dx, int dz) {
        currentAnchor = new Vector3i(currentAnchor.x + dx, currentAnchor.y, currentAnchor.z + dz);
        clearBirdsEyeSnapshot();
    }

    public void rotateClockwise90() {
        setCurrentRotationSteps(getCurrentRotationSteps() + 1);
        lastExpandDir = null;
        placementExpandDir = null;
        clearBirdsEyeSnapshot();
    }

    /**
     * True for a new wall wand session before the first piece is placed (not edit prompt or continue-from-edit).
     */
    public boolean isAdjustingFirstPiece() {
        return !hasEditTarget() && !seededContinueFromEdit && committed.isEmpty();
    }

    public void clearHeightManualAdjust() {
        heightManuallyAdjusted = false;
    }

    public boolean isHeightManuallyAdjusted() {
        return heightManuallyAdjusted;
    }

    @Nonnull
    public List<Ref<EntityStore>> getPreviewEntityRefs() {
        return previewEntityRefs;
    }

    @Nullable
    public UUID getEditTargetPlotId() {
        return editTargetPlotId;
    }

    public void setEditTargetPlotId(@Nullable UUID editTargetPlotId) {
        this.editTargetPlotId = editTargetPlotId;
        this.editTargetSegmentId = null;
    }

    @Nullable
    public UUID getEditTargetSegmentId() {
        return editTargetSegmentId;
    }

    public void setEditTargetSegmentId(@Nullable UUID editTargetSegmentId) {
        this.editTargetSegmentId = editTargetSegmentId;
        this.editTargetPlotId = null;
    }

    public boolean hasEditTarget() {
        return editTargetPlotId != null || editTargetSegmentId != null;
    }

    /**
     * After "Continue" on an existing wall or tower: seed that piece as the chain base and wait for an expand pad
     * before showing the next preview (avoids stacking a ghost on the selected sign).
     */
    public boolean continueFromEditTarget(@Nonnull TownRecord town) {
        return continueFromEditTarget(town, editTargetPlotId, editTargetSegmentId);
    }

    public boolean continueFromEditTarget(
        @Nonnull TownRecord town, @Nullable UUID plotId, @Nullable UUID segmentId
    ) {
        CommittedStep seed = buildCommittedSeedFromEditTarget(town, plotId, segmentId);
        if (seed == null) {
            return false;
        }
        committed.add(seed);
        setEditTargetPlotId(null);
        setEditTargetSegmentId(null);
        adoptPieceForEdit(seed.constructionId);
        pieceKind = PieceKind.SEGMENT;
        towerConnections.clear();
        previewConstructionId = null;
        currentAnchor = new Vector3i(seed.signAnchor);
        currentRotationSteps = seed.rotationSteps;
        lastExpandDir = null;
        placementExpandDir = null;
        arrivalFromSide = inferArrivalForSeededContinue(town, seed, segmentId);
        seededContinueFromEdit = true;
        return true;
    }

    public boolean tryBeginEditContinue() {
        if (editContinueInFlight) {
            return false;
        }
        editContinueInFlight = true;
        return true;
    }

    public void endEditContinue() {
        editContinueInFlight = false;
    }

    /**
     * World side where an existing neighbor attaches (expand into that side is blocked). Towers: opening faces.
     * Straight wall and gate runs: probe along the run axis in town data.
     */
    @Nullable
    private static WallCardinal inferArrivalForSeededContinue(
        @Nonnull TownRecord town, @Nonnull CommittedStep seed, @Nullable UUID editSegmentId
    ) {
        if (WallPieceGeometry.isTowerConstructionId(seed.constructionId)) {
            return arrivalFromSideForSeededTower(seed);
        }
        return inferArrivalAlongWallRun(town, seed, editSegmentId);
    }

    @Nullable
    private static WallCardinal arrivalFromSideForSeededTower(@Nonnull CommittedStep seed) {
        if (seed.towerConnectionDirs == null || seed.towerConnectionDirs.isEmpty()) {
            return null;
        }
        if (seed.towerConnectionDirs.size() == 1) {
            return seed.towerConnectionDirs.iterator().next();
        }
        return null;
    }

    @Nullable
    private static WallCardinal inferArrivalAlongWallRun(
        @Nonnull TownRecord town, @Nonnull CommittedStep seed, @Nullable UUID editSegmentId
    ) {
        EnumSet<WallCardinal> faces =
            WallStyleCatalog.get().connectionsForPlacedPiece(seed.constructionId, seed.rotationSteps);
        if (faces == null || faces.isEmpty()) {
            boolean alongZ = (seed.rotationSteps % 2) == 0;
            faces =
                alongZ
                    ? EnumSet.of(WallCardinal.NORTH, WallCardinal.SOUTH)
                    : EnumSet.of(WallCardinal.EAST, WallCardinal.WEST);
        }
        int span =
            WallPieceGeometry.segmentChainSpan(
                WallStyleCatalog.get().style(WallStyleCatalog.get().styleIdForConstruction(seed.constructionId))
            );
        WallCardinal blocked = null;
        for (WallCardinal dir : faces) {
            int px = seed.signAnchor.x + dir.dx * span;
            int pz = seed.signAnchor.z + dir.dz * span;
            if (hasWallAlongRunAt(town, seed, editSegmentId, px, pz)) {
                if (blocked != null) {
                    return null;
                }
                blocked = dir;
            }
        }
        return blocked;
    }

    private static boolean hasWallAlongRunAt(
        @Nonnull TownRecord town,
        @Nonnull CommittedStep seed,
        @Nullable UUID editSegmentId,
        int x,
        int z
    ) {
        WallSegmentRecord seg = town.findWallSegmentAtBlock(x, seed.signAnchor.y, z);
        if (seg != null) {
            if (editSegmentId != null && seg.getSegmentId().equals(editSegmentId)) {
                return false;
            }
            return true;
        }
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getPlotId().equals(seed.plotId)) {
                continue;
            }
            if (WallStyleCatalog.get().roleFor(plot.getConstructionId()) == null) {
                continue;
            }
            int dx = Math.abs(plot.getSignX() - x);
            int dz = Math.abs(plot.getSignZ() - z);
            if (dx <= 2 && dz <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the floating next-piece preview should render. False right after continue-from-edit until the player
     * picks an expand direction.
     */
    public boolean shouldShowNextPiecePreview() {
        if (hasEditTarget() || isRemoveConfirmOpen()) {
            return false;
        }
        return getLastCommitted() == null || resolveChainExpandDir() != null;
    }

    @Nullable
    private CommittedStep buildCommittedSeedFromEditTarget(
        @Nonnull TownRecord town, @Nullable UUID plotId, @Nullable UUID segmentId
    ) {
        if (plotId != null) {
            PlotInstance plot = town.findPlotById(plotId);
            if (plot == null) {
                return null;
            }
            int rot = PlotPlacementSession.rotationStepsFromPrefabYaw(plot.resolvePrefabYaw());
            EnumSet<WallCardinal> towerDirs =
                WallPieceGeometry.isTowerConstructionId(plot.getConstructionId())
                    ? WallStyleCatalog.get().connectionsForPlacedPiece(plot.getConstructionId(), rot)
                    : null;
            Vector3i sign = new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
            ConstructionDefinition def = resolveConstructionDef(plot.getConstructionId());
            Vector3i origin = def != null ? plot.resolvePrefabAnchorWorld(def) : sign;
            return new CommittedStep(plotId, plot.getConstructionId(), sign, rot, towerDirs, null, origin);
        }
        if (segmentId != null) {
            WallSegmentRecord seg = town.findWallSegmentById(segmentId);
            if (seg == null) {
                return null;
            }
            int rot = PlotPlacementSession.rotationStepsFromPrefabYaw(seg.resolvePrefabYaw());
            Vector3i sign =
                new Vector3i(
                    seg.getPrefabAnchorX(),
                    seg.getPrefabAnchorY() + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
                    seg.getPrefabAnchorZ()
                );
            EnumSet<WallCardinal> towerDirs =
                WallPieceGeometry.isTowerConstructionId(seg.getConstructionId())
                    ? WallStyleCatalog.get().connectionsForPlacedPiece(seg.getConstructionId(), rot)
                    : null;
            Vector3i origin =
                new Vector3i(seg.getPrefabAnchorX(), seg.getPrefabAnchorY(), seg.getPrefabAnchorZ());
            return new CommittedStep(segmentId, seg.getConstructionId(), sign, rot, towerDirs, null, origin);
        }
        return null;
    }

    @Nullable
    private static ConstructionDefinition resolveConstructionDef(@Nonnull String constructionId) {
        com.hexvane.aetherhaven.AetherhavenPlugin plugin = com.hexvane.aetherhaven.AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        return plugin.getConstructionCatalog().get(constructionId);
    }

    public boolean isRemoveConfirmOpen() {
        return removeConfirmOpen;
    }

    public void setRemoveConfirmOpen(boolean removeConfirmOpen) {
        this.removeConfirmOpen = removeConfirmOpen;
    }

    public void clearBirdsEyeSnapshot() {
        birdsEyeSnapshotValid = false;
    }

    public void setBirdsEyeSnapshot(double x, double y, double z) {
        birdsEyeSnapshotX = x;
        birdsEyeSnapshotY = y;
        birdsEyeSnapshotZ = z;
        birdsEyeSnapshotValid = true;
    }

    public boolean hasBirdsEyeSnapshot() {
        return birdsEyeSnapshotValid;
    }

    public double getBirdsEyeSnapshotX() {
        return birdsEyeSnapshotX;
    }

    public double getBirdsEyeSnapshotY() {
        return birdsEyeSnapshotY;
    }

    public double getBirdsEyeSnapshotZ() {
        return birdsEyeSnapshotZ;
    }

    @Nonnull
    public WallCardinal getCameraViewFromSide() {
        return cameraViewFromSide;
    }

    public void setCameraViewFromSide(@Nonnull WallCardinal side) {
        this.cameraViewFromSide = side;
    }

    @Nonnull
    private List<WallPlacementChainPlanner.ChainCommittedPiece> toPlannerCommitted() {
        return committed.stream().map(WallPlacementSession::toPlannerPiece).toList();
    }

    @Nonnull
    private static WallPlacementChainPlanner.ChainCommittedPiece toPlannerPiece(@Nonnull CommittedStep step) {
        return new WallPlacementChainPlanner.ChainCommittedPiece(
            step.constructionId,
            new Vector3i(step.signAnchor),
            step.rotationSteps,
            step.towerConnectionDirs,
            step.chainExpandDir,
            step.initialTowerConnectionDirs
        );
    }

    @Nonnull
    public static Vector3i prefabOriginForSign(
        @Nonnull ConstructionDefinition def, @Nonnull Vector3i signAnchor, int rotationSteps
    ) {
        return def.resolvePrefabAnchorWorld(signAnchor, rotationStepsFrom(rotationSteps));
    }

    @Nonnull
    private static WallPlacementChainPlanner.PieceKind toPlannerPieceKind(@Nonnull PieceKind kind) {
        return switch (kind) {
            case GATE -> WallPlacementChainPlanner.PieceKind.GATE;
            case TOWER -> WallPlacementChainPlanner.PieceKind.TOWER;
            default -> WallPlacementChainPlanner.PieceKind.SEGMENT;
        };
    }

    @Nonnull
    public static Rotation rotationStepsFrom(int steps) {
        return WallPlacementChainPlanner.rotationStepsFrom(steps);
    }
}
