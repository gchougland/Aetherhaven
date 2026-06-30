package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.WallSegmentRecord;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceGeometry;
import com.hexvane.aetherhaven.wall.WallPlacementChainPlanner;
import com.hexvane.aetherhaven.wall.WallTowerAutoConnector;
import com.hexvane.aetherhaven.wall.WallTowerPrefabResolver;
import com.hypixel.hytale.component.Ref;
import org.joml.Vector3i;
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

public final class WallPlacementSession {
    public enum PieceKind {
        SEGMENT(AetherhavenConstants.CONSTRUCTION_PLOT_WALL_SEGMENT),
        GATE(AetherhavenConstants.CONSTRUCTION_PLOT_WALL_GATE),
        TOWER(null);

        @Nullable
        public final String fixedConstructionId;

        PieceKind(@Nullable String fixedConstructionId) {
            this.fixedConstructionId = fixedConstructionId;
        }
    }

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
            this.plotId = plotId;
            this.constructionId = constructionId;
            this.signAnchor = new Vector3i(signAnchor.x, signAnchor.y, signAnchor.z);
            this.rotationSteps = rotationSteps;
            this.towerConnectionDirs =
                towerConnectionDirs == null || towerConnectionDirs.isEmpty()
                    ? null
                    : EnumSet.copyOf(towerConnectionDirs);
            this.chainExpandDir = chainExpandDir;
            this.ghostOriginX = ghostPrefabOriginWorld.x;
            this.ghostOriginY = ghostPrefabOriginWorld.y;
            this.ghostOriginZ = ghostPrefabOriginWorld.z;
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

    /** Last geometry direction used to place the preview (joint face on segment for towers). */
    @Nullable
    private WallCardinal lastPositionDir;

    /** Last tower preview plan construction id (matches ghost); used at commit so prefab matches preview. */
    @Nullable
    private String previewTowerConstructionId;

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
            + " positionDir="
            + lastPositionDir
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

    public void setPieceKind(@Nonnull PieceKind pieceKind) {
        if (pieceKind == PieceKind.TOWER && !canPlaceTowerNow()) {
            this.pieceKind = PieceKind.SEGMENT;
            towerConnections.clear();
            realignPreviewToLastCommitted();
            return;
        }
        this.pieceKind = pieceKind;
        clearBirdsEyeSnapshot();
        if (pieceKind != PieceKind.TOWER) {
            previewTowerConstructionId = null;
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

    /**
     * Recomputes {@link #currentAnchor} for the selected piece type so wall/gate/tower previews sit at the correct
     * joint distance from the last committed piece.
     */
    public void realignPreviewToLastCommitted() {
        WallCardinal expandDir = resolveChainExpandDir();
        if (expandDir == null) {
            if (pieceKind == PieceKind.TOWER && getLastCommitted() == null) {
                ensureTowerEndCapPreviewDefaults();
            }
            return;
        }
        previewExpandDirection(expandDir);
    }

    /**
     * Shows {@code EndCap_S} in the preview before the player picks a direction pad (rotation 0). Pad clicks replace
     * this via {@link #previewExpandDirection}.
     */
    public void ensureTowerEndCapPreviewDefaults() {
        if (pieceKind != PieceKind.TOWER) {
            return;
        }
        if (towerConnections.isEmpty()) {
            currentRotationSteps = 0;
        } else {
            applyTowerResolvedRotation();
        }
    }

    /** Applies resolved tower yaw before {@link #resolveConstructionId()} is used for preview or place. */
    public void prepareTowerForCommit() {
        ensureTowerEndCapPreviewDefaults();
    }

    /**
     * Picks where the current preview piece will sit relative to the last commit (does not place). Expand pads call
     * this; use the Place button to commit.
     *
     * @return true when a single-connection tower was upgraded to a two-face tower for {@code expandDir}
     */
    public boolean previewExpandDirection(@Nonnull WallCardinal expandDir) {
        boolean towerUpgraded = upgradeLastCommittedTowerIfNeeded(expandDir);
        seededContinueFromEdit = false;
        CommittedStep last = getLastCommitted();
        if (last == null) {
            if (pieceKind == PieceKind.TOWER) {
                applyExpandPreviewPlan(
                    WallPlacementChainPlanner.planExpandPreview(
                        new Vector3i(currentAnchor),
                        currentRotationSteps,
                        toPlannerPieceKind(pieceKind),
                        toPlannerCommitted(),
                        expandDir,
                        null
                    )
                );
            } else {
                lastExpandDir = expandDir;
                placementExpandDir = expandDir;
                currentRotationSteps = expandDir.rotationStepsForLocalNorthAlongAxis();
                arrivalFromSide = null;
            }
            return towerUpgraded;
        }
        applyExpandPreviewPlan(
            WallPlacementChainPlanner.planExpandPreview(
                new Vector3i(last.signAnchor),
                last.rotationSteps,
                toPlannerPieceKind(pieceKind),
                toPlannerCommitted(),
                expandDir,
                null
            )
        );
        return towerUpgraded;
    }

    /**
     * After a straight end-cap tower is committed, the next expand pad adds the outgoing face and swaps the plot to the
     * matching corner or run-through tower (before the following wall/gate is placed).
     */
    public boolean upgradeLastCommittedTowerIfNeeded(@Nonnull WallCardinal outgoingExpandDir) {
        if (pieceKind == PieceKind.TOWER) {
            return false;
        }
        return applyOutgoingDirectionToLastTower(outgoingExpandDir);
    }

    public void reGroundSignYAtCurrentColumn(
        @Nonnull World world,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buf
    ) {
        if (heightManuallyAdjusted) {
            return;
        }
        Vector3i grounded =
            PlotSignGrounding.resolveSignCell(world, currentAnchor, def, getCurrentPrefabYaw(), buf);
        currentAnchor = new Vector3i(grounded.x, grounded.y, grounded.z);
    }

    private void applyExpandPreviewPlan(@Nonnull WallPlacementChainPlanner.ExpandPreviewPlan plan) {
        int previewY = currentAnchor.y;
        currentAnchor = new Vector3i(plan.anchor());
        currentAnchor = new Vector3i(currentAnchor.x, previewY, currentAnchor.z);
        currentRotationSteps = plan.rotationSteps();
        lastExpandDir = plan.outgoingExpandDir();
        placementExpandDir = plan.outgoingExpandDir();
        lastPositionDir = plan.positionDir();
        arrivalFromSide = plan.arrivalFromSide();
        towerConnections.clear();
        if (plan.towerConnections() != null) {
            towerConnections.addAll(plan.towerConnections());
        }
        if (pieceKind == PieceKind.TOWER) {
            applyTowerResolvedRotation();
            previewTowerConstructionId = plan.resolvedConstructionId();
        } else {
            previewTowerConstructionId = null;
        }
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

    /** Towers cannot be placed twice in a row; use wall/gate after a tower until another segment is placed. */
    public boolean canPlaceTowerNow() {
        CommittedStep last = getLastCommitted();
        return last == null || !WallPieceGeometry.isTowerConstructionId(last.constructionId);
    }

    public void afterTowerCommittedSwitchToWall() {
        pieceKind = PieceKind.SEGMENT;
        previewTowerConstructionId = null;
        towerConnections.clear();
        refreshArrivalFromSide();
    }

    @Nonnull
    public EnumSet<WallCardinal> getTowerConnections() {
        return EnumSet.copyOf(towerConnections);
    }

    /** Before placing: if the last commit is a 1-connection tower, add the outgoing face from the next pad press. */
    public boolean applyOutgoingDirectionToLastTower(@Nonnull WallCardinal outgoingExpandDir) {
        CommittedStep last = getLastCommitted();
        if (last == null || last.towerConnectionDirs == null || last.towerConnectionDirs.size() != 1) {
            return false;
        }
        EnumSet<WallCardinal> pair =
            WallTowerAutoConnector.connectionsForCorner(
                EnumSet.copyOf(last.towerConnectionDirs), outgoingExpandDir
            );
        WallTowerPrefabResolver.ResolvedTower resolved = WallTowerAutoConnector.resolve(pair);
        if (resolved == null) {
            return false;
        }
        int idx = committed.size() - 1;
        committed.set(
            idx,
            new CommittedStep(
                last.plotId,
                resolved.constructionId(),
                last.signAnchor,
                resolved.rotationSteps(),
                pair,
                last.chainExpandDir,
                last.ghostPrefabOriginWorld()
            )
        );
        return true;
    }

    /**
     * Sets tower connections for the pad click without moving {@link #currentAnchor}. Incoming face is where the chain
     * arrived; outgoing is the pad direction.
     */
    public void prepareTowerPlacementForClick(@Nonnull WallCardinal outgoingExpandDir) {
        previewExpandDirection(outgoingExpandDir);
    }

    /**
     * Corner towers need two connection faces before commit (e.g. chain north, then pad east). Straight end caps commit
     * on the first pad along the run.
     */
    public boolean isTowerReadyToCommit(@Nonnull WallCardinal outgoingPad) {
        if (pieceKind != PieceKind.TOWER) {
            return true;
        }
        if (towerConnections.size() >= 2) {
            return true;
        }
        CommittedStep last = getLastCommitted();
        if (last == null) {
            return true;
        }
        if (last.chainExpandDir == null) {
            return !towerConnections.isEmpty();
        }
        WallPlacementChainPlanner.ChainCommittedPiece plannerLast = toPlannerPiece(last);
        WallCardinal jointDir = WallPlacementChainPlanner.towerJointExpandDir(plannerLast, outgoingPad);
        if (WallPlacementChainPlanner.isStraightRunTowerDeadEnd(last.chainExpandDir, outgoingPad, jointDir)) {
            return !towerConnections.isEmpty();
        }
        return towerConnections.size() >= 2;
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
        return WallPlacementChainPlanner.allowedExpandDirections(
            toPlannerPieceKind(pieceKind), toPlannerCommitted(), arrivalFromSide
        );
    }

    private void refreshArrivalFromSide() {
        CommittedStep last = getLastCommitted();
        if (last == null) {
            arrivalFromSide = null;
            return;
        }
        if (pieceKind == PieceKind.TOWER && last.chainExpandDir != null) {
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

        if (WallPieceGeometry.isTowerConstructionId(undone.constructionId)) {
            pieceKind = PieceKind.TOWER;
            if (undone.towerConnectionDirs != null) {
                towerConnections.addAll(undone.towerConnectionDirs);
            }
        } else {
            pieceKind = PieceKind.SEGMENT;
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
        if (pieceKind == PieceKind.TOWER) {
            if (previewTowerConstructionId != null) {
                return previewTowerConstructionId;
            }
            WallTowerPrefabResolver.ResolvedTower r = WallTowerPrefabResolver.resolve(towerConnections);
            return r != null ? r.constructionId() : AetherhavenConstants.CONSTRUCTION_PLOT_WALL_TOWER_ENDCAP_S;
        }
        return pieceKind.fixedConstructionId != null ? pieceKind.fixedConstructionId : AetherhavenConstants.CONSTRUCTION_PLOT_WALL_SEGMENT;
    }

    public void applyTowerResolvedRotation() {
        if (pieceKind != PieceKind.TOWER) {
            return;
        }
        WallTowerPrefabResolver.ResolvedTower r = WallTowerPrefabResolver.resolve(towerConnections);
        if (r != null) {
            currentRotationSteps = r.rotationSteps();
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

    public void extendPreview(@Nonnull WallCardinal expandDir) {
        previewExpandDirection(expandDir);
    }

    /**
     * Straight wall/gate runs keep the previous yaw. After a tower, match the wall segment run before the tower.
     * Tower previews use {@link WallPlacementChainPlanner#planExpandPreview} (rotation and anchor stay in sync).
     */
    private int rotationStepsForChainAfter(@Nonnull CommittedStep last, @Nonnull WallCardinal expandDir) {
        CommittedStep previous = getPreviousCommitted();
        return WallPlacementChainPlanner.rotationStepsForChainAfter(
            toPlannerPieceKind(pieceKind),
            toPlannerPiece(last),
            previous == null ? null : toPlannerPiece(previous),
            expandDir
        );
    }

    @Nullable
    public EnumSet<WallCardinal> towerConnectionsForCommit() {
        if (pieceKind != PieceKind.TOWER) {
            return null;
        }
        if (towerConnections.isEmpty()) {
            return EnumSet.of(WallCardinal.SOUTH);
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
        if (pieceKind == PieceKind.TOWER) {
            return WallCardinal.SOUTH;
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
     * After "Continue" on an existing wall/tower: seed that piece as the chain base and wait for an expand pad before
     * showing the next preview (avoids stacking a ghost on the selected sign).
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
        pieceKind = PieceKind.SEGMENT;
        towerConnections.clear();
        currentAnchor = new Vector3i(seed.signAnchor);
        currentRotationSteps = seed.rotationSteps;
        lastExpandDir = null;
        placementExpandDir = null;
        lastPositionDir = null;
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
     * Straight wall/gate runs: probe along the run axis in town data.
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
        boolean alongZ = (seed.rotationSteps % 2) == 0;
        WallCardinal[] runDirs = alongZ ? new WallCardinal[] {WallCardinal.NORTH, WallCardinal.SOUTH}
            : new WallCardinal[] {WallCardinal.EAST, WallCardinal.WEST};
        int span = WallPieceGeometry.segmentChainSpan();
        WallCardinal blocked = null;
        for (WallCardinal dir : runDirs) {
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
            if (!WallPieceGeometry.isTowerConstructionId(plot.getConstructionId())
                && !AetherhavenConstants.CONSTRUCTION_PLOT_WALL_GATE.equals(plot.getConstructionId())
                && !AetherhavenConstants.CONSTRUCTION_PLOT_WALL_SEGMENT.equals(plot.getConstructionId())) {
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
    private CommittedStep buildCommittedSeedFromEditTarget(@Nonnull TownRecord town) {
        return buildCommittedSeedFromEditTarget(town, editTargetPlotId, editTargetSegmentId);
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
                    ? WallTowerPrefabResolver.connectionsForPlacedTower(plot.getConstructionId(), rot)
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
                    ? WallTowerPrefabResolver.connectionsForPlacedTower(seg.getConstructionId(), rot)
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
    private Vector3i computeChainedSignAnchor(
        @Nonnull CommittedStep last,
        int newRotationSteps,
        @Nonnull Rotation newYaw,
        @Nonnull WallCardinal positionDir,
        @Nonnull WallCardinal outgoingExpandDir,
        boolean newPieceIsTower
    ) {
        return WallPlacementChainPlanner.computeChainedSignAnchor(
            toPlannerPiece(last),
            newRotationSteps,
            newYaw,
            positionDir,
            outgoingExpandDir,
            newPieceIsTower,
            toPlannerPieceKind(pieceKind),
            null,
            null
        );
    }

    @Nonnull
    private List<WallPlacementChainPlanner.ChainCommittedPiece> toPlannerCommitted() {
        return committed.stream().map(this::toPlannerPiece).toList();
    }

    @Nonnull
    private WallPlacementChainPlanner.ChainCommittedPiece toPlannerPiece(@Nonnull CommittedStep step) {
        return new WallPlacementChainPlanner.ChainCommittedPiece(
            step.constructionId,
            new Vector3i(step.signAnchor),
            step.rotationSteps,
            step.towerConnectionDirs,
            step.chainExpandDir
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
