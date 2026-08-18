package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Collected data for one plot creator session; preserved when navigating back. */
public final class PlotCreatorDraft {
    @Nonnull
    private PlotCreatorStep step = PlotCreatorStep.WELCOME;
    private int substepIndex;

    @Nullable
    private Vector3i cornerFirst;
    @Nullable
    private Vector3i cornerSecond;

    @Nonnull
    private PlotCreatorBoundsPhase boundsPhase = PlotCreatorBoundsPhase.INITIAL_DRAG;
    @Nullable
    private Vector3i boundsDragStart;
    @Nullable
    private Vector3i boundsDragEnd;
    @Nullable
    private PlotCreatorBoundsFace hoveredBoundsFace;
    @Nullable
    private PlotCreatorBoundsFace activeBoundsFaceDrag;
    private boolean boundsPrimaryHeld;

    @Nullable
    private Vector3i plotAnchor;

    @Nullable
    private String prefabFileName;
    @Nullable
    private String prefabPath;
    @Nonnull
    private int[] plotAnchorOffset = new int[] {0, 0, 0};
    @Nullable
    private Vector3i prefabOriginMin;

    @Nullable
    private PlotBuildingKind kind;
    @Nonnull
    private final List<PlotBuildingKind> kinds = new ArrayList<>();
    @Nullable
    private String constructionId;
    @Nullable
    private String displayName;
    @Nullable
    private String description;
    @Nonnull
    private final List<String> buildingTags = new ArrayList<>();
    /** Raw tags field text; parsed into {@link #buildingTags} when leaving the tags step. */
    @Nullable
    private String buildingTagsInput;
    @Nullable
    private String countsAsConstructionId;
    @Nonnull
    private final List<String> countsAsConstructionIds = new ArrayList<>();
    /** Festival being authored on the FESTIVAL step; null until the player picks one. */
    @Nullable
    private String festivalId;
    /** Festival the session opened for editing; null for a brand new festival or a new look. */
    @Nullable
    private String editingFestivalId;
    /** Base holiday a new look counts as; null for a new holiday or when editing in the building editor. */
    @Nullable
    private String countsAsFestivalId;
    private boolean festivalPicked;
    private boolean festivalSizeLocked;
    @Nullable
    private String festivalSeason;
    private int festivalDayOfSeason = 1;
    private boolean festivalAllDay;
    private int festivalStartHour = 8;
    private int festivalEndHour = 20;
    @Nullable
    private String festivalSeasonInput;
    @Nullable
    private String festivalDayInput;
    @Nullable
    private String festivalStartHourInput;
    @Nullable
    private String festivalEndHourInput;
    /** Registered festival mechanic id, or null for a decoration-only festival. */
    @Nullable
    private String festivalMechanicId;
    /** Typed activity field on the settings step (None, New Life, Pig Racing, or a mechanic id). */
    @Nullable
    private String festivalMechanicInput;
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.NpcRow> festivalNpcs = new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.TouristSpotRow> festivalTouristSpots =
        new ArrayList<>();
    @Nullable
    private int[] festivalCenterpieceLocal;
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceLaneRow> festivalRaceLanes =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.BalloonSpawnRow> festivalBalloonSpawns =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.WhackSpawnRow> festivalWhackSpawns =
        new ArrayList<>();
    @Nullable
    private com.hexvane.aetherhaven.festival.FestivalDefinition.WheelLocalRow festivalWheelLocal;
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> festivalRaceStartSpots =
        new ArrayList<>();
    @Nullable
    private com.hexvane.aetherhaven.festival.FestivalDefinition.RaceFinishLocalRow festivalRaceFinishLocal;
    @Nullable
    private com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow festivalMazeStartLocal;
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> festivalOrbSpawns =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> festivalMarketStands =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> festivalMarketDisplaySlots =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> festivalSnowballPileSpots =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> festivalSnowballTeamASpots =
        new ArrayList<>();
    @Nonnull
    private final List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> festivalSnowballTeamBSpots =
        new ArrayList<>();
    @Nullable
    private com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow festivalSnowballOutLocal;

    /** Wall style pieces in {@link com.hexvane.aetherhaven.wall.WallPieceRole#AUTHORING_ORDER}; empty until WALL is picked. */
    @Nonnull
    private final List<PlotCreatorWallPieceDraft> wallPieces = new ArrayList<>();
    /** Index into {@link #wallPieces} of the piece being authored. */
    private int wallPieceIndex;
    /** 0 marks the build box substep; 1 and up are the connection points of the current piece. */
    private int wallPieceSubstepIndex;
    /** Base id of the wall style opened from the building editor, so saving replaces it instead of clashing. */
    @Nullable
    private String editingWallStyleBaseId;

    /** Chosen important spots for the SUBSTEP loop; empty until IMPORTANT_SPOTS is committed. */
    @Nonnull
    private final List<PlotCreatorSpotEntry> selectedSpots = new ArrayList<>();
    /** True after the player confirmed the important spots chooser at least once this session. */
    private boolean importantSpotsConfirmed;

    @Nonnull
    private final List<PlotCreatorPoiDraft> pois = new ArrayList<>();
    @Nullable
    private int[] managementBlockLocalPos;
    @Nullable
    private int[] productionStorageLocalPos;
    @Nullable
    private int[] treasuryLocalPos;
    @Nullable
    private int[] shopSafeLocalPos;
    @Nullable
    private int[] innBellLocalPos;
    @Nullable
    private int[] gaiaStatueLocalPos;
    @Nullable
    private int[] innkeeperSpawnLocal;
    @Nonnull
    private final List<int[]> visitorSpawnLocals = new ArrayList<>();
    @Nullable
    private int[] guildMasterSpawnLocal;
    @Nullable
    private String rotationYaw = "None";
    @Nonnull
    private final List<PlotCreatorAdventurerSpawnEntry> adventurerSpawns = new ArrayList<>();

    @Nonnull
    private final List<MaterialRequirement> materials = new ArrayList<>();
    private long treasuryGoldCoinCost;
    private double selfBuildGameDays = 3.0;
    @Nullable
    private String selfBuildDaysInput;
    /** House resident assignment slots (1–8); used when kind is HOME. */
    private int maxHomeResidents = 1;
    @Nullable
    private String maxHomeResidentsInput;
    /** When true, natural air in the footprint is saved as empty cells in the prefab. */
    private boolean saveEmptySpaces;
    /** When true, assembly keeps world water in empty prefab spaces. */
    private boolean preserveWater;
    private boolean scheduleSharedUtilityPick;
    private boolean excludeFromTownJournal;
    /** When true, portal tourists may path here during the day. */
    private boolean touristDestination;
    /** When true, players must unlock this plot at the plot crafting bench before crafting tokens. */
    private boolean plotTokenLockedByDefault;

    /** When true, save also uploads this building to the community marketplace for review. */
    private boolean submitToCommunity;

    /** Crafting bench style filter id (core, jimmy, jszza, hytiny, misc). */
    @Nullable
    private String styleId;

    @Nullable
    private Vector3i stagingChestWorldPos;
    @Nonnull
    private final List<Vector3i> placedSpecialBlocks = new ArrayList<>();

    @Nullable
    private String editingConstructionId;
    /** When true, display name changes no longer auto-update the construction id. */
    private boolean constructionIdUserEdited;
    /** Creative building-editor staff session (edit existing catalog buildings, merge-save). */
    private boolean buildingEditorMode;
    /** Building editor session for a owned marketplace submission (auto upload on save). */
    private boolean communitySubmissionEdit;
    /** True when the edited submission was live on the marketplace when the session started. */
    private boolean communitySubmissionApproved;
    /** Snapshot of the original building JSON for merge-save (keys not managed by the wizard are kept). */
    @Nonnull
    private final java.util.Map<String, Object> originalBuildingJsonSnapshot = new java.util.LinkedHashMap<>();
    /** Locked prefab file name for editor saves (do not rename to construction id). */
    @Nullable
    private String lockedPrefabPathKey;
    /**
     * Prefab file exported during this wizard session. Re-exports to the same file are allowed on the shape step;
     * other on-disk prefabs are not overwritten.
     */
    @Nullable
    private String sessionExportedPrefabPath;
    /** Highest step index reached this session (for step jump menu). */
    private int maxReachedStepIndex;

    public boolean isConstructionIdUserEdited() {
        return constructionIdUserEdited;
    }

    public void setConstructionIdUserEdited(boolean constructionIdUserEdited) {
        this.constructionIdUserEdited = constructionIdUserEdited;
    }

    @Nonnull
    public PlotCreatorStep getStep() {
        return step;
    }

    public void setStep(@Nonnull PlotCreatorStep step) {
        this.step = step;
    }

    public int getSubstepIndex() {
        return substepIndex;
    }

    public void setSubstepIndex(int substepIndex) {
        this.substepIndex = Math.max(0, substepIndex);
    }

    @Nullable
    public Vector3i getCornerFirst() {
        return cornerFirst;
    }

    public void setCornerFirst(@Nullable Vector3i cornerFirst) {
        this.cornerFirst = cornerFirst != null ? new Vector3i(cornerFirst) : null;
    }

    @Nullable
    public Vector3i getCornerSecond() {
        return cornerSecond;
    }

    public void setCornerSecond(@Nullable Vector3i cornerSecond) {
        this.cornerSecond = cornerSecond != null ? new Vector3i(cornerSecond) : null;
    }

    @Nonnull
    public PlotCreatorBoundsPhase getBoundsPhase() {
        return boundsPhase;
    }

    public void setBoundsPhase(@Nonnull PlotCreatorBoundsPhase boundsPhase) {
        this.boundsPhase = boundsPhase;
    }

    @Nullable
    public Vector3i getBoundsDragStart() {
        return boundsDragStart;
    }

    public void setBoundsDragStart(@Nullable Vector3i boundsDragStart) {
        this.boundsDragStart = boundsDragStart != null ? new Vector3i(boundsDragStart) : null;
    }

    @Nullable
    public Vector3i getBoundsDragEnd() {
        return boundsDragEnd;
    }

    public void setBoundsDragEnd(@Nullable Vector3i boundsDragEnd) {
        this.boundsDragEnd = boundsDragEnd != null ? new Vector3i(boundsDragEnd) : null;
    }

    @Nullable
    public PlotCreatorBoundsFace getHoveredBoundsFace() {
        return hoveredBoundsFace;
    }

    public void setHoveredBoundsFace(@Nullable PlotCreatorBoundsFace hoveredBoundsFace) {
        this.hoveredBoundsFace = hoveredBoundsFace;
    }

    @Nullable
    public PlotCreatorBoundsFace getActiveBoundsFaceDrag() {
        return activeBoundsFaceDrag;
    }

    public void setActiveBoundsFaceDrag(@Nullable PlotCreatorBoundsFace activeBoundsFaceDrag) {
        this.activeBoundsFaceDrag = activeBoundsFaceDrag;
    }

    public boolean isBoundsPrimaryHeld() {
        return boundsPrimaryHeld;
    }

    public void setBoundsPrimaryHeld(boolean boundsPrimaryHeld) {
        this.boundsPrimaryHeld = boundsPrimaryHeld;
    }

    /** True while the drag and face adjust tools own a build box: the bounds step, or a wall piece's box. */
    public boolean isEditingBounds() {
        return step == PlotCreatorStep.BOUNDS
            || (step == PlotCreatorStep.WALL_PIECES && wallPieceSubstepIndex == 0);
    }

    /** Clears committed corners and returns to the initial drag sub-phase. */
    public void resetBoundsEditing() {
        cornerFirst = null;
        cornerSecond = null;
        boundsPhase = PlotCreatorBoundsPhase.INITIAL_DRAG;
        boundsDragStart = null;
        boundsDragEnd = null;
        hoveredBoundsFace = null;
        activeBoundsFaceDrag = null;
        boundsPrimaryHeld = false;
        plotAnchor = null;
    }

    @Nullable
    public Vector3i getPlotAnchor() {
        return plotAnchor;
    }

    public void setPlotAnchor(@Nullable Vector3i plotAnchor) {
        this.plotAnchor = plotAnchor != null ? new Vector3i(plotAnchor) : null;
    }

    @Nullable
    public String getPrefabFileName() {
        return prefabFileName;
    }

    public void setPrefabFileName(@Nullable String prefabFileName) {
        this.prefabFileName = prefabFileName;
    }

    @Nullable
    public String getPrefabPath() {
        return prefabPath;
    }

    public void setPrefabPath(@Nullable String prefabPath) {
        this.prefabPath = prefabPath;
    }

    @Nonnull
    public int[] getPlotAnchorOffset() {
        return plotAnchorOffset;
    }

    public void setPlotAnchorOffset(@Nonnull int[] plotAnchorOffset) {
        this.plotAnchorOffset = plotAnchorOffset;
    }

    @Nullable
    public Vector3i getPrefabOriginMin() {
        return prefabOriginMin;
    }

    public void setPrefabOriginMin(@Nullable Vector3i prefabOriginMin) {
        this.prefabOriginMin = prefabOriginMin != null ? new Vector3i(prefabOriginMin) : null;
    }

    @Nullable
    public PlotBuildingKind getKind() {
        if (!kinds.isEmpty()) {
            return kinds.get(0);
        }
        return kind;
    }

    public void setKind(@Nullable PlotBuildingKind kind) {
        this.kind = kind;
        kinds.clear();
        if (kind != null) {
            kinds.add(kind);
        }
    }

    @Nonnull
    public List<PlotBuildingKind> getKinds() {
        if (!kinds.isEmpty()) {
            return kinds;
        }
        if (kind != null) {
            return List.of(kind);
        }
        return List.of();
    }

    public void setKinds(@Nonnull List<PlotBuildingKind> next) {
        kinds.clear();
        for (PlotBuildingKind k : next) {
            if (k != null && !kinds.contains(k)) {
                kinds.add(k);
            }
        }
        kind = kinds.isEmpty() ? null : kinds.get(0);
    }

    public boolean hasKind(@Nonnull PlotBuildingKind want) {
        return getKinds().contains(want);
    }

    public boolean isDecorationOnly() {
        List<PlotBuildingKind> ks = getKinds();
        return ks.size() == 1 && ks.get(0) == PlotBuildingKind.DECORATION;
    }

    /** True while the wizard is building a festival rather than a normal plot. */
    public boolean isFestivalMode() {
        List<PlotBuildingKind> ks = getKinds();
        return ks.size() == 1 && ks.get(0) == PlotBuildingKind.FESTIVAL;
    }

    /** True while the wizard is building a wall style rather than a single plot. */
    public boolean isWallMode() {
        List<PlotBuildingKind> ks = getKinds();
        return ks.size() == 1 && ks.get(0) == PlotBuildingKind.WALL;
    }

    @Nonnull
    public List<PlotCreatorWallPieceDraft> getWallPieces() {
        return wallPieces;
    }

    /** Creates one empty piece per role the first time the wall step is entered. */
    public void ensureWallPieces() {
        if (!wallPieces.isEmpty()) {
            return;
        }
        for (com.hexvane.aetherhaven.wall.WallPieceRole role
            : com.hexvane.aetherhaven.wall.WallPieceRole.AUTHORING_ORDER) {
            wallPieces.add(new PlotCreatorWallPieceDraft(role));
        }
    }

    public int getWallPieceIndex() {
        return wallPieceIndex;
    }

    public void setWallPieceIndex(int wallPieceIndex) {
        this.wallPieceIndex = Math.max(0, wallPieceIndex);
    }

    public int getWallPieceSubstepIndex() {
        return wallPieceSubstepIndex;
    }

    public void setWallPieceSubstepIndex(int wallPieceSubstepIndex) {
        this.wallPieceSubstepIndex = Math.max(0, wallPieceSubstepIndex);
    }

    @Nullable
    public PlotCreatorWallPieceDraft currentWallPiece() {
        if (wallPieceIndex < 0 || wallPieceIndex >= wallPieces.size()) {
            return null;
        }
        return wallPieces.get(wallPieceIndex);
    }

    /** Drops every authored wall piece, for when the player switches the build type. */
    public void clearWallSelection() {
        wallPieces.clear();
        wallPieceIndex = 0;
        wallPieceSubstepIndex = 0;
        editingWallStyleBaseId = null;
    }

    @Nullable
    public String getEditingWallStyleBaseId() {
        return editingWallStyleBaseId;
    }

    public void setEditingWallStyleBaseId(@Nullable String editingWallStyleBaseId) {
        this.editingWallStyleBaseId =
            editingWallStyleBaseId == null || editingWallStyleBaseId.isBlank()
                ? null
                : editingWallStyleBaseId.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public String getFestivalId() {
        return festivalId;
    }

    public void setFestivalId(@Nullable String festivalId) {
        this.festivalId = festivalId != null && !festivalId.isBlank() ? festivalId.trim() : null;
    }

    /** Festival id the session started from, or null when the player picked "new festival". */
    @Nullable
    public String getEditingFestivalId() {
        return editingFestivalId;
    }

    public void setEditingFestivalId(@Nullable String editingFestivalId) {
        this.editingFestivalId = editingFestivalId != null && !editingFestivalId.isBlank()
            ? editingFestivalId.trim()
            : null;
    }

    @Nullable
    public String getCountsAsFestivalId() {
        return countsAsFestivalId;
    }

    public void setCountsAsFestivalId(@Nullable String countsAsFestivalId) {
        this.countsAsFestivalId =
            countsAsFestivalId != null && !countsAsFestivalId.isBlank() ? countsAsFestivalId.trim() : null;
    }

    /** True when this session is authoring a look for an existing holiday rather than a new calendar festival. */
    public boolean isFestivalLookMode() {
        return isFestivalMode() && countsAsFestivalId != null && !countsAsFestivalId.isBlank();
    }

    public boolean isFestivalPicked() {
        return festivalPicked;
    }

    public void setFestivalPicked(boolean festivalPicked) {
        this.festivalPicked = festivalPicked;
    }

    /** Drops the festival pick and unlocks the build box, for when the player switches the build type. */
    public void clearFestivalSelection() {
        festivalPicked = false;
        festivalSizeLocked = false;
        festivalId = null;
        editingFestivalId = null;
        countsAsFestivalId = null;
        festivalMechanicId = null;
        festivalMechanicInput = null;
        festivalNpcs.clear();
        festivalTouristSpots.clear();
        festivalCenterpieceLocal = null;
        festivalRaceLanes.clear();
        festivalBalloonSpawns.clear();
        festivalWhackSpawns.clear();
        festivalWheelLocal = null;
        festivalRaceStartSpots.clear();
        festivalRaceFinishLocal = null;
        festivalMazeStartLocal = null;
        festivalOrbSpawns.clear();
        festivalMarketStands.clear();
        festivalMarketDisplaySlots.clear();
        festivalSnowballPileSpots.clear();
        festivalSnowballTeamASpots.clear();
        festivalSnowballTeamBSpots.clear();
        festivalSnowballOutLocal = null;
    }

    @Nullable
    public String getFestivalMechanicId() {
        return festivalMechanicId;
    }

    public void setFestivalMechanicId(@Nullable String festivalMechanicId) {
        this.festivalMechanicId =
            festivalMechanicId != null && !festivalMechanicId.isBlank() ? festivalMechanicId.trim() : null;
    }

    @Nullable
    public String getFestivalMechanicInput() {
        return festivalMechanicInput;
    }

    public void setFestivalMechanicInput(@Nullable String festivalMechanicInput) {
        this.festivalMechanicInput = festivalMechanicInput;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.NpcRow> getFestivalNpcs() {
        return festivalNpcs;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.TouristSpotRow> getFestivalTouristSpots() {
        return festivalTouristSpots;
    }

    @Nullable
    public int[] getFestivalCenterpieceLocal() {
        return festivalCenterpieceLocal;
    }

    public void setFestivalCenterpieceLocal(@Nullable int[] festivalCenterpieceLocal) {
        if (festivalCenterpieceLocal == null || festivalCenterpieceLocal.length < 3) {
            this.festivalCenterpieceLocal = null;
            return;
        }
        this.festivalCenterpieceLocal =
            new int[] {festivalCenterpieceLocal[0], festivalCenterpieceLocal[1], festivalCenterpieceLocal[2]};
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceLaneRow> getFestivalRaceLanes() {
        return festivalRaceLanes;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.BalloonSpawnRow> getFestivalBalloonSpawns() {
        return festivalBalloonSpawns;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.WhackSpawnRow> getFestivalWhackSpawns() {
        return festivalWhackSpawns;
    }

    @Nullable
    public com.hexvane.aetherhaven.festival.FestivalDefinition.WheelLocalRow getFestivalWheelLocal() {
        return festivalWheelLocal;
    }

    public void setFestivalWheelLocal(
        @Nullable com.hexvane.aetherhaven.festival.FestivalDefinition.WheelLocalRow festivalWheelLocal
    ) {
        this.festivalWheelLocal = festivalWheelLocal;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> getFestivalRaceStartSpots() {
        return festivalRaceStartSpots;
    }

    @Nullable
    public com.hexvane.aetherhaven.festival.FestivalDefinition.RaceFinishLocalRow getFestivalRaceFinishLocal() {
        return festivalRaceFinishLocal;
    }

    public void setFestivalRaceFinishLocal(
        @Nullable com.hexvane.aetherhaven.festival.FestivalDefinition.RaceFinishLocalRow festivalRaceFinishLocal
    ) {
        this.festivalRaceFinishLocal = festivalRaceFinishLocal;
    }

    @Nullable
    public com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow getFestivalMazeStartLocal() {
        return festivalMazeStartLocal;
    }

    public void setFestivalMazeStartLocal(
        @Nullable com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow festivalMazeStartLocal
    ) {
        this.festivalMazeStartLocal = festivalMazeStartLocal;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> getFestivalOrbSpawns() {
        return festivalOrbSpawns;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> getFestivalMarketStands() {
        return festivalMarketStands;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> getFestivalMarketDisplaySlots() {
        return festivalMarketDisplaySlots;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.OrbSpawnRow> getFestivalSnowballPileSpots() {
        return festivalSnowballPileSpots;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> getFestivalSnowballTeamASpots() {
        return festivalSnowballTeamASpots;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.festival.FestivalDefinition.RaceStartSpotRow> getFestivalSnowballTeamBSpots() {
        return festivalSnowballTeamBSpots;
    }

    @Nullable
    public com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow getFestivalSnowballOutLocal() {
        return festivalSnowballOutLocal;
    }

    public void setFestivalSnowballOutLocal(
        @Nullable com.hexvane.aetherhaven.festival.FestivalDefinition.MazeStartLocalRow festivalSnowballOutLocal
    ) {
        this.festivalSnowballOutLocal = festivalSnowballOutLocal;
    }

    /** Festival prefabs are a fixed size, so the drag box stops moving once a festival is picked. */
    public boolean isFestivalSizeLocked() {
        return festivalSizeLocked;
    }

    public void setFestivalSizeLocked(boolean festivalSizeLocked) {
        this.festivalSizeLocked = festivalSizeLocked;
    }

    @Nonnull
    public String getFestivalSeason() {
        return festivalSeason != null ? festivalSeason : "Spring";
    }

    public void setFestivalSeason(@Nullable String festivalSeason) {
        this.festivalSeason = festivalSeason != null && !festivalSeason.isBlank() ? festivalSeason.trim() : null;
    }

    public int getFestivalDayOfSeason() {
        return festivalDayOfSeason;
    }

    public void setFestivalDayOfSeason(int festivalDayOfSeason) {
        this.festivalDayOfSeason = festivalDayOfSeason;
    }

    public boolean isFestivalAllDay() {
        return festivalAllDay;
    }

    public void setFestivalAllDay(boolean festivalAllDay) {
        this.festivalAllDay = festivalAllDay;
    }

    public int getFestivalStartHour() {
        return festivalStartHour;
    }

    public void setFestivalStartHour(int festivalStartHour) {
        this.festivalStartHour = festivalStartHour;
    }

    public int getFestivalEndHour() {
        return festivalEndHour;
    }

    public void setFestivalEndHour(int festivalEndHour) {
        this.festivalEndHour = festivalEndHour;
    }

    @Nullable
    public String getFestivalSeasonInput() {
        return festivalSeasonInput;
    }

    public void setFestivalSeasonInput(@Nullable String festivalSeasonInput) {
        this.festivalSeasonInput = festivalSeasonInput;
    }

    @Nullable
    public String getFestivalDayInput() {
        return festivalDayInput;
    }

    public void setFestivalDayInput(@Nullable String festivalDayInput) {
        this.festivalDayInput = festivalDayInput;
    }

    @Nullable
    public String getFestivalStartHourInput() {
        return festivalStartHourInput;
    }

    public void setFestivalStartHourInput(@Nullable String festivalStartHourInput) {
        this.festivalStartHourInput = festivalStartHourInput;
    }

    @Nullable
    public String getFestivalEndHourInput() {
        return festivalEndHourInput;
    }

    public void setFestivalEndHourInput(@Nullable String festivalEndHourInput) {
        this.festivalEndHourInput = festivalEndHourInput;
    }

    @Nullable
    public String getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(@Nullable String constructionId) {
        this.constructionId = constructionId;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nonnull
    public List<String> getBuildingTags() {
        return buildingTags;
    }

    @Nullable
    public String getBuildingTagsInput() {
        return buildingTagsInput;
    }

    public void setBuildingTagsInput(@Nullable String buildingTagsInput) {
        this.buildingTagsInput = buildingTagsInput;
    }

    @Nullable
    public String getCountsAsConstructionId() {
        if (!countsAsConstructionIds.isEmpty()) {
            return countsAsConstructionIds.get(0);
        }
        return countsAsConstructionId;
    }

    public void setCountsAsConstructionId(@Nullable String countsAsConstructionId) {
        this.countsAsConstructionId = countsAsConstructionId;
        countsAsConstructionIds.clear();
        if (countsAsConstructionId != null && !countsAsConstructionId.isBlank()) {
            countsAsConstructionIds.add(countsAsConstructionId.trim());
        }
    }

    @Nonnull
    public List<String> getCountsAsConstructionIds() {
        if (!countsAsConstructionIds.isEmpty()) {
            return countsAsConstructionIds;
        }
        if (countsAsConstructionId != null && !countsAsConstructionId.isBlank()) {
            return List.of(countsAsConstructionId.trim());
        }
        return List.of();
    }

    public void setCountsAsConstructionIds(@Nonnull List<String> ids) {
        countsAsConstructionIds.clear();
        for (String id : ids) {
            if (id != null && !id.isBlank() && !countsAsConstructionIds.contains(id.trim())) {
                countsAsConstructionIds.add(id.trim());
            }
        }
        countsAsConstructionId = countsAsConstructionIds.isEmpty() ? null : countsAsConstructionIds.get(0);
    }

    public boolean countsAsFestivalSquare() {
        if (com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE.equals(getConstructionId())) {
            return true;
        }
        for (String id : getCountsAsConstructionIds()) {
            if (com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public List<PlotCreatorSpotEntry> getSelectedSpots() {
        return selectedSpots;
    }

    public boolean isImportantSpotsConfirmed() {
        return importantSpotsConfirmed;
    }

    public void setImportantSpotsConfirmed(boolean importantSpotsConfirmed) {
        this.importantSpotsConfirmed = importantSpotsConfirmed;
    }

    @Nonnull
    public List<PlotCreatorPoiDraft> getPois() {
        return pois;
    }

    @Nullable
    public int[] getManagementBlockLocalPos() {
        return managementBlockLocalPos;
    }

    public void setManagementBlockLocalPos(@Nullable int[] managementBlockLocalPos) {
        this.managementBlockLocalPos = managementBlockLocalPos;
    }

    @Nullable
    public int[] getProductionStorageLocalPos() {
        return productionStorageLocalPos;
    }

    public void setProductionStorageLocalPos(@Nullable int[] productionStorageLocalPos) {
        this.productionStorageLocalPos = productionStorageLocalPos;
    }

    @Nullable
    public int[] getTreasuryLocalPos() {
        return treasuryLocalPos;
    }

    public void setTreasuryLocalPos(@Nullable int[] treasuryLocalPos) {
        this.treasuryLocalPos = treasuryLocalPos;
    }

    @Nullable
    public int[] getShopSafeLocalPos() {
        return shopSafeLocalPos;
    }

    public void setShopSafeLocalPos(@Nullable int[] shopSafeLocalPos) {
        this.shopSafeLocalPos = shopSafeLocalPos;
    }

    @Nullable
    public int[] getInnBellLocalPos() {
        return innBellLocalPos;
    }

    public void setInnBellLocalPos(@Nullable int[] innBellLocalPos) {
        this.innBellLocalPos = innBellLocalPos;
    }

    @Nullable
    public int[] getGaiaStatueLocalPos() {
        return gaiaStatueLocalPos;
    }

    public void setGaiaStatueLocalPos(@Nullable int[] gaiaStatueLocalPos) {
        this.gaiaStatueLocalPos = gaiaStatueLocalPos;
    }

    @Nullable
    public int[] getInnkeeperSpawnLocal() {
        return innkeeperSpawnLocal;
    }

    public void setInnkeeperSpawnLocal(@Nullable int[] innkeeperSpawnLocal) {
        this.innkeeperSpawnLocal = innkeeperSpawnLocal;
    }

    @Nonnull
    public List<int[]> getVisitorSpawnLocals() {
        return visitorSpawnLocals;
    }

    @Nullable
    public int[] getGuildMasterSpawnLocal() {
        return guildMasterSpawnLocal;
    }

    public void setGuildMasterSpawnLocal(@Nullable int[] guildMasterSpawnLocal) {
        this.guildMasterSpawnLocal = guildMasterSpawnLocal;
    }

    @Nullable
    public String getRotationYaw() {
        return rotationYaw;
    }

    public void setRotationYaw(@Nullable String rotationYaw) {
        this.rotationYaw = rotationYaw != null && !rotationYaw.isBlank() ? rotationYaw.trim() : "None";
    }

    @Nonnull
    public List<PlotCreatorAdventurerSpawnEntry> getAdventurerSpawns() {
        return adventurerSpawns;
    }

    @Nonnull
    public List<MaterialRequirement> getMaterials() {
        return materials;
    }

    public long getTreasuryGoldCoinCost() {
        return treasuryGoldCoinCost;
    }

    public void setTreasuryGoldCoinCost(long treasuryGoldCoinCost) {
        this.treasuryGoldCoinCost = Math.max(0L, treasuryGoldCoinCost);
    }

    public double getSelfBuildGameDays() {
        return selfBuildGameDays;
    }

    public void setSelfBuildGameDays(double selfBuildGameDays) {
        this.selfBuildGameDays = selfBuildGameDays > 0.0 ? selfBuildGameDays : 3.0;
    }

    @Nullable
    public String getSelfBuildDaysInput() {
        return selfBuildDaysInput;
    }

    public void setSelfBuildDaysInput(@Nullable String selfBuildDaysInput) {
        this.selfBuildDaysInput = selfBuildDaysInput;
    }

    public int getMaxHomeResidents() {
        if (maxHomeResidents <= 0) {
            return 1;
        }
        return Math.min(8, maxHomeResidents);
    }

    public void setMaxHomeResidents(int maxHomeResidents) {
        if (maxHomeResidents <= 0) {
            this.maxHomeResidents = 1;
        } else {
            this.maxHomeResidents = Math.min(8, maxHomeResidents);
        }
    }

    @Nullable
    public String getMaxHomeResidentsInput() {
        return maxHomeResidentsInput;
    }

    public void setMaxHomeResidentsInput(@Nullable String maxHomeResidentsInput) {
        this.maxHomeResidentsInput = maxHomeResidentsInput;
    }

    public boolean isSaveEmptySpaces() {
        return saveEmptySpaces;
    }

    public void setSaveEmptySpaces(boolean saveEmptySpaces) {
        this.saveEmptySpaces = saveEmptySpaces;
    }

    public boolean isPreserveWater() {
        return preserveWater;
    }

    public void setPreserveWater(boolean preserveWater) {
        this.preserveWater = preserveWater;
    }

    public boolean isScheduleSharedUtilityPick() {
        return scheduleSharedUtilityPick;
    }

    public void setScheduleSharedUtilityPick(boolean scheduleSharedUtilityPick) {
        this.scheduleSharedUtilityPick = scheduleSharedUtilityPick;
    }

    public boolean isExcludeFromTownJournal() {
        return excludeFromTownJournal;
    }

    public void setExcludeFromTownJournal(boolean excludeFromTownJournal) {
        this.excludeFromTownJournal = excludeFromTownJournal;
    }

    public boolean isTouristDestination() {
        return touristDestination;
    }

    public void setTouristDestination(boolean touristDestination) {
        this.touristDestination = touristDestination;
    }

    public boolean isPlotTokenLockedByDefault() {
        return plotTokenLockedByDefault;
    }

    public void setPlotTokenLockedByDefault(boolean plotTokenLockedByDefault) {
        this.plotTokenLockedByDefault = plotTokenLockedByDefault;
    }

    public boolean isSubmitToCommunity() {
        return submitToCommunity;
    }

    public void setSubmitToCommunity(boolean submitToCommunity) {
        this.submitToCommunity = submitToCommunity;
    }

    @Nullable
    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(@Nullable String styleId) {
        if (styleId == null || styleId.isBlank()) {
            this.styleId = null;
        } else {
            this.styleId = styleId.trim().toLowerCase(Locale.ROOT);
        }
    }

    @Nullable
    public Vector3i getStagingChestWorldPos() {
        return stagingChestWorldPos;
    }

    public void setStagingChestWorldPos(@Nullable Vector3i stagingChestWorldPos) {
        this.stagingChestWorldPos = stagingChestWorldPos != null ? new Vector3i(stagingChestWorldPos) : null;
    }

    @Nonnull
    public List<Vector3i> getPlacedSpecialBlocks() {
        return placedSpecialBlocks;
    }

    @Nullable
    public String getEditingConstructionId() {
        return editingConstructionId;
    }

    public void setEditingConstructionId(@Nullable String editingConstructionId) {
        this.editingConstructionId = editingConstructionId;
    }

    @Nullable
    public String getSessionExportedPrefabPath() {
        return sessionExportedPrefabPath;
    }

    public void setSessionExportedPrefabPath(@Nullable String sessionExportedPrefabPath) {
        this.sessionExportedPrefabPath = sessionExportedPrefabPath;
    }

    public int getMaxReachedStepIndex() {
        return maxReachedStepIndex;
    }

    public void setMaxReachedStepIndex(int maxReachedStepIndex) {
        this.maxReachedStepIndex = Math.max(0, maxReachedStepIndex);
    }

    public boolean isBuildingEditorMode() {
        return buildingEditorMode;
    }

    public void setBuildingEditorMode(boolean buildingEditorMode) {
        this.buildingEditorMode = buildingEditorMode;
    }

    public boolean isCommunitySubmissionEdit() {
        return communitySubmissionEdit;
    }

    public void setCommunitySubmissionEdit(boolean communitySubmissionEdit) {
        this.communitySubmissionEdit = communitySubmissionEdit;
    }

    public boolean isCommunitySubmissionApproved() {
        return communitySubmissionApproved;
    }

    public void setCommunitySubmissionApproved(boolean communitySubmissionApproved) {
        this.communitySubmissionApproved = communitySubmissionApproved;
    }

    @Nonnull
    public java.util.Map<String, Object> getOriginalBuildingJsonSnapshot() {
        return originalBuildingJsonSnapshot;
    }

    public void setOriginalBuildingJsonSnapshot(@Nonnull java.util.Map<String, Object> snapshot) {
        originalBuildingJsonSnapshot.clear();
        originalBuildingJsonSnapshot.putAll(snapshot);
        originalBuildingJsonSnapshot.remove("assemblyPrefabSectionsPerAxis");
    }

    @Nullable
    public String getLockedPrefabPathKey() {
        return lockedPrefabPathKey;
    }

    public void setLockedPrefabPathKey(@Nullable String lockedPrefabPathKey) {
        this.lockedPrefabPathKey = lockedPrefabPathKey;
    }

    @Nonnull
    public Vector3i boundsMin() {
        if (cornerFirst == null || cornerSecond == null) {
            return new Vector3i();
        }
        return new Vector3i(
            Math.min(cornerFirst.x, cornerSecond.x),
            Math.min(cornerFirst.y, cornerSecond.y),
            Math.min(cornerFirst.z, cornerSecond.z)
        );
    }

    @Nonnull
    public Vector3i boundsMax() {
        if (cornerFirst == null || cornerSecond == null) {
            return new Vector3i();
        }
        return new Vector3i(
            Math.max(cornerFirst.x, cornerSecond.x),
            Math.max(cornerFirst.y, cornerSecond.y),
            Math.max(cornerFirst.z, cornerSecond.z)
        );
    }

    public boolean isInsideBounds(@Nonnull Vector3i worldPos) {
        Vector3i min = boundsMin();
        Vector3i max = boundsMax();
        return worldPos.x >= min.x
            && worldPos.x <= max.x
            && worldPos.y >= min.y
            && worldPos.y <= max.y
            && worldPos.z >= min.z
            && worldPos.z <= max.z;
    }
}
