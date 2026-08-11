package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Collects desired important-spot preview markers from a plot creator draft. */
public final class PlotCreatorSpotMarkerCollector {
    public record DesiredSpotMarker(
        int x,
        int y,
        int z,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nonnull String nameplateText,
        @Nonnull String texturePath,
        /** World-space body yaw for facing; null when the spot has no authored facing. */
        @Nullable Float facingYawWorldRadians
    ) {
        public long key() {
            long cell = packCell(x, y, z);
            int role = workResidentKind == null ? 0 : workResidentKind.toLowerCase(Locale.ROOT).hashCode();
            int yawBits = facingYawWorldRadians == null ? 0 : Float.floatToIntBits(facingYawWorldRadians);
            return cell * 31L + type.ordinal() * 17L + (role & 0xffff) + (yawBits & 0xffffffffL);
        }
    }

    private PlotCreatorSpotMarkerCollector() {}

    public static long packCell(int x, int y, int z) {
        return ((long) (x & 0x1fffff) << 42) | ((long) (y & 0xfffff) << 21) | (long) (z & 0x1fffff);
    }

    /**
     * Signature of which markers should be visible for the current wizard step.
     * Changes when step, substep, or any placement data changes.
     */
    public static long signature(@Nonnull PlotCreatorDraft draft, @Nullable World world) {
        PlotCreatorStep step = draft.getStep();
        long h = 17L;
        h = 31 * h + step.ordinal();
        h = 31 * h + draft.getSubstepIndex();
        h = 31 * h + (draft.isBuildingEditorMode() ? 1 : 0);
        boolean showAllInEditorChooser =
            step == PlotCreatorStep.IMPORTANT_SPOTS && draft.isBuildingEditorMode();
        if (step != PlotCreatorStep.SUBSTEP && step != PlotCreatorStep.REVIEW && !showAllInEditorChooser) {
            return h;
        }
        @Nullable
        PlotBuildingKindRequirements.SubstepRequirement filter =
            step == PlotCreatorStep.SUBSTEP ? PlotCreatorService.currentSubstep(draft) : null;
        for (DesiredSpotMarker m : collect(draft, world, filter)) {
            h = 31 * h + m.key();
            h = 31 * h + m.nameplateText().hashCode();
            h = 31 * h + m.texturePath().hashCode();
            if (m.facingYawWorldRadians() != null) {
                h = 31 * h + Float.floatToIntBits(m.facingYawWorldRadians());
            }
        }
        h = 31 * h + PlotCreatorSpotPreviewCollector.signature(draft, world);
        return h;
    }

    /**
     * @param world optional; used to distinguish shop stall vs tourist portal special blocks
     * @param filter when non-null (SUBSTEP), only markers matching this requirement are returned
     */
    @Nonnull
    public static List<DesiredSpotMarker> collect(
        @Nonnull PlotCreatorDraft draft,
        @Nullable World world,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        List<DesiredSpotMarker> out = new ArrayList<>();
        if (draft.getPlotAnchor() == null && draft.getPrefabOriginMin() == null) {
            return out;
        }
        addLocal(out, draft, draft.getManagementBlockLocalPos(), PlotCreatorSubstepType.MANAGEMENT_BLOCK, null, filter);
        addLocal(out, draft, draft.getProductionStorageLocalPos(), PlotCreatorSubstepType.PRODUCTION_STORAGE, null, filter);
        addLocal(out, draft, draft.getTreasuryLocalPos(), PlotCreatorSubstepType.TREASURY_BLOCK, null, filter);
        addLocal(out, draft, draft.getShopSafeLocalPos(), PlotCreatorSubstepType.SHOP_SAFE_BLOCK, null, filter);
        addLocal(out, draft, draft.getInnBellLocalPos(), PlotCreatorSubstepType.INN_BELL_BLOCK, null, filter);
        addLocal(out, draft, draft.getGaiaStatueLocalPos(), PlotCreatorSubstepType.GAIA_STATUE_BLOCK, null, filter);

        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            addPoi(out, draft, poi, filter);
        }

        for (Vector3i worldPos : draft.getPlacedSpecialBlocks()) {
            addSpecialBlock(out, world, worldPos, filter);
        }
        addFestivalMarkers(out, draft, filter);
        return out;
    }

    private static void addFestivalMarkers(
        @Nonnull List<DesiredSpotMarker> out,
        @Nonnull PlotCreatorDraft draft,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        int[] centerpiece = draft.getFestivalCenterpieceLocal();
        if (centerpiece != null && passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_CENTERPIECE, null)) {
            Vector3i block = PlotCreatorLocalCoords.toWorldBlock(draft, centerpiece);
            out.add(
                desired(
                    block.x,
                    block.y,
                    block.z,
                    PlotCreatorSubstepType.FESTIVAL_CENTERPIECE,
                    null,
                    null,
                    null
                )
            );
        }
        for (var lane : draft.getFestivalRaceLanes()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_RACE_LANE, null)) {
                continue;
            }
            Vector3i start =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {lane.getStartLocalX(), lane.getStartLocalY(), lane.getStartLocalZ()}
                );
            Vector3i finish =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {lane.getFinishLocalX(), lane.getFinishLocalY(), lane.getFinishLocalZ()}
                );
            out.add(
                desired(
                    start.x,
                    start.y,
                    start.z,
                    PlotCreatorSubstepType.FESTIVAL_RACE_LANE,
                    lane.getNpcRoleId(),
                    null,
                    null
                )
            );
            out.add(
                desired(
                    finish.x,
                    finish.y,
                    finish.z,
                    PlotCreatorSubstepType.FESTIVAL_RACE_LANE,
                    lane.getNpcRoleId(),
                    null,
                    null
                )
            );
        }
        for (var balloon : draft.getFestivalBalloonSpawns()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN, null)) {
                continue;
            }
            Vector3i block =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {balloon.getLocalX(), balloon.getLocalY(), balloon.getLocalZ()}
                );
            out.add(
                desired(
                    block.x,
                    block.y,
                    block.z,
                    PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN,
                    null,
                    null,
                    null
                )
            );
        }
        for (var whack : draft.getFestivalWhackSpawns()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN, null)) {
                continue;
            }
            Vector3i block =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {whack.getLocalX(), whack.getLocalY(), whack.getLocalZ()}
                );
            out.add(
                desired(
                    block.x,
                    block.y,
                    block.z,
                    PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN,
                    null,
                    null,
                    null
                )
            );
        }
        var wheel = draft.getFestivalWheelLocal();
        if (wheel != null && passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_WHEEL, null)) {
            Vector3i block =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {wheel.getLocalX(), wheel.getLocalY(), wheel.getLocalZ()}
                );
            out.add(
                desired(block.x, block.y, block.z, PlotCreatorSubstepType.FESTIVAL_WHEEL, null, null, null)
            );
        }
    }

    private static void addLocal(
        @Nonnull List<DesiredSpotMarker> out,
        @Nonnull PlotCreatorDraft draft,
        @Nullable int[] local,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        if (local == null || local.length < 3) {
            return;
        }
        if (PlotCreatorSpotPreviewRoles.usesVillagerPreview(type)) {
            return;
        }
        if (!passesTypeFilter(filter, type, workResidentKind)) {
            return;
        }
        Vector3i block = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        out.add(desired(block.x, block.y, block.z, type, workResidentKind, null, null));
    }

    private static void addPoi(
        @Nonnull List<DesiredSpotMarker> out,
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotCreatorPoiDraft poi,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        PlotCreatorSubstepType type = resolvePoiType(poi);
        if (type == null || PlotCreatorSpotPreviewRoles.usesVillagerPreview(type)) {
            return;
        }
        String workKind = type == PlotCreatorSubstepType.WORK_POI ? poi.getWorkResidentKind() : null;
        if (filter != null) {
            if (!PlotCreatorValidator.matchesPoiRequirement(poi, filter)) {
                return;
            }
        }
        int[] local = new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()};
        Vector3i block = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        Float worldYaw = worldYawForPoi(draft, poi);
        String activityId =
            PlotCreatorWorkActivityTags.resolveActivityId(type, poi.getWorkResidentKind(), poi.getTags());
        out.add(desired(block.x, block.y, block.z, type, workKind, activityId, worldYaw));
    }

    @Nullable
    private static Float worldYawForPoi(@Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorPoiDraft poi) {
        Float deg = poi.getInteractionTargetYawDegrees();
        if (deg == null) {
            return null;
        }
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        return PrefabYaw.worldFromPrefabLocal(placement, (float) Math.toRadians(deg));
    }

    private static void addSpecialBlock(
        @Nonnull List<DesiredSpotMarker> out,
        @Nullable World world,
        @Nonnull Vector3i worldPos,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        PlotCreatorSubstepType type = resolveSpecialBlockType(world, worldPos);
        if (!passesTypeFilter(filter, type, null)) {
            return;
        }
        out.add(desired(worldPos.x, worldPos.y, worldPos.z, type, null, null, null));
    }

    @Nonnull
    private static PlotCreatorSubstepType resolveSpecialBlockType(@Nullable World world, @Nonnull Vector3i worldPos) {
        if (world != null) {
            String blockId = PlotCreatorLocalCoords.blockTypeAt(world, worldPos);
            if (TownPortalTravelColor.isTouristPortalBlockTypeId(blockId)) {
                return PlotCreatorSubstepType.TOURIST_PORTAL_BLOCK;
            }
            if (AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(blockId)) {
                return PlotCreatorSubstepType.SHOP_SPOT;
            }
        }
        return PlotCreatorSubstepType.SHOP_SPOT;
    }

    @Nullable
    public static PlotCreatorSubstepType resolvePoiTypePublic(@Nonnull PlotCreatorPoiDraft poi) {
        return resolvePoiType(poi);
    }

    @Nullable
    private static PlotCreatorSubstepType resolvePoiType(@Nonnull PlotCreatorPoiDraft poi) {
        if (PlotCreatorGaiaStatueSupport.isGaiaStatueBlockTypeId(poi.getBlockTypeId())) {
            return null;
        }
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_BARD)
            || "bard".equalsIgnoreCase(poi.getWorkResidentKind())) {
            return PlotCreatorSubstepType.BARD_WORK_POI;
        }
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)
            || AetherhavenConstants.QUEST_BOARD_ITEM_ID.equals(poi.getBlockTypeId())) {
            return PlotCreatorSubstepType.QUEST_BOARD_POI;
        }
        if ("Aetherhaven_Town_Planning_Desk".equals(poi.getBlockTypeId())) {
            return PlotCreatorSubstepType.PLANNING_DESK_POI;
        }
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
            return PlotCreatorSubstepType.TOURIST_VISIT_POI;
        }
        if (poi.getTags().contains("SHOP") && !poi.getTags().contains("WORK")) {
            return PlotCreatorSubstepType.SHOP_POI;
        }
        if (poi.getTags().contains("SLEEP") || poi.getTags().contains("ENERGY")) {
            return PlotCreatorSubstepType.SLEEP_POI;
        }
        if (poi.getTags().contains("EAT")) {
            return PlotCreatorSubstepType.EAT_POI;
        }
        if (poi.getTags().contains("FUN") || poi.getTags().contains("SIT")) {
            return PlotCreatorSubstepType.FUN_POI;
        }
        if (poi.getTags().contains("WORK")) {
            return PlotCreatorSubstepType.WORK_POI;
        }
        return null;
    }

    private static boolean passesTypeFilter(
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind
    ) {
        if (filter == null) {
            return true;
        }
        if (filter.type() != type) {
            return false;
        }
        if (type != PlotCreatorSubstepType.WORK_POI && type != PlotCreatorSubstepType.FESTIVAL_NPC) {
            return true;
        }
        String want = filter.workResidentKind();
        if (want == null || want.isBlank()) {
            return workResidentKind == null || type == PlotCreatorSubstepType.FESTIVAL_NPC;
        }
        return Objects.equals(want, workResidentKind);
    }

    @Nonnull
    private static DesiredSpotMarker desired(
        int x,
        int y,
        int z,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nullable String activityId,
        @Nullable Float facingYawWorldRadians
    ) {
        return new DesiredSpotMarker(
            x,
            y,
            z,
            type,
            workResidentKind,
            PlotCreatorSpotMarkerVisuals.nameplateText(type, workResidentKind, activityId),
            PlotCreatorSpotMarkerVisuals.textureFor(type),
            facingYawWorldRadians
        );
    }
}
