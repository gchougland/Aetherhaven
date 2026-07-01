package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.ArrayList;
import java.util.List;
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
    /** When true, natural air in the footprint is saved as empty cells in the prefab. */
    private boolean saveEmptySpaces;
    private int assemblyPrefabSectionsPerAxis = 1;
    @Nullable
    private String assemblySectionsInput;
    private boolean scheduleSharedUtilityPick;
    private boolean excludeFromTownJournal;
    /** When true, portal tourists may path here during the day. */
    private boolean touristDestination;
    /** When true, players must unlock this plot at the plot crafting bench before crafting tokens. */
    private boolean plotTokenLockedByDefault;
    /** When true, regular balloon gifts may drop a plot blueprint for this building (requires locked token). */
    private boolean floatingGiftBlueprint;

    @Nullable
    private Vector3i stagingChestWorldPos;
    @Nonnull
    private final List<Vector3i> placedSpecialBlocks = new ArrayList<>();

    @Nullable
    private String editingConstructionId;
    /** When true, display name changes no longer auto-update the construction id. */
    private boolean constructionIdUserEdited;

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
        return kind;
    }

    public void setKind(@Nullable PlotBuildingKind kind) {
        this.kind = kind;
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
        return countsAsConstructionId;
    }

    public void setCountsAsConstructionId(@Nullable String countsAsConstructionId) {
        this.countsAsConstructionId = countsAsConstructionId;
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

    public boolean isSaveEmptySpaces() {
        return saveEmptySpaces;
    }

    public void setSaveEmptySpaces(boolean saveEmptySpaces) {
        this.saveEmptySpaces = saveEmptySpaces;
    }

    public int getAssemblyPrefabSectionsPerAxis() {
        return assemblyPrefabSectionsPerAxis;
    }

    public void setAssemblyPrefabSectionsPerAxis(int assemblyPrefabSectionsPerAxis) {
        this.assemblyPrefabSectionsPerAxis = assemblyPrefabSectionsPerAxis;
    }

    @Nullable
    public String getAssemblySectionsInput() {
        return assemblySectionsInput;
    }

    public void setAssemblySectionsInput(@Nullable String assemblySectionsInput) {
        this.assemblySectionsInput = assemblySectionsInput;
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
        if (!plotTokenLockedByDefault) {
            this.floatingGiftBlueprint = false;
        }
    }

    public boolean isFloatingGiftBlueprint() {
        return floatingGiftBlueprint;
    }

    public void setFloatingGiftBlueprint(boolean floatingGiftBlueprint) {
        this.floatingGiftBlueprint = floatingGiftBlueprint;
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
