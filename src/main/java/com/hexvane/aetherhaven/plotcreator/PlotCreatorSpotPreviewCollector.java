package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Collects desired villager/tourist important-spot previews from a plot creator draft. */
public final class PlotCreatorSpotPreviewCollector {
    public record DesiredSpotPreview(
        int standX,
        int standY,
        int standZ,
        @Nullable Integer poiBlockX,
        @Nullable Integer poiBlockY,
        @Nullable Integer poiBlockZ,
        @Nonnull PlotCreatorSubstepType type,
        @Nonnull String npcRoleId,
        @Nullable String workResidentKind,
        @Nullable String equipmentProfileId,
        @Nullable String activityId,
        @Nullable Float facingYawWorldRadians,
        @Nonnull String interactionKind,
        @Nullable String blockTypeId,
        @Nonnull List<String> tags,
        boolean mountOnUse,
        boolean adventurerSeat
    ) {
        public long key() {
            long cell = PlotCreatorSpotMarkerCollector.packCell(standX, standY, standZ);
            int role = npcRoleId.toLowerCase(Locale.ROOT).hashCode();
            int equip = equipmentProfileId == null ? 0 : equipmentProfileId.hashCode();
            int activity = activityId == null ? 0 : activityId.hashCode();
            int yawBits = facingYawWorldRadians == null ? 0 : Float.floatToIntBits(facingYawWorldRadians);
            int poi =
                poiBlockX == null
                    ? 0
                    : Objects.hash(poiBlockX, poiBlockY, poiBlockZ);
            return cell * 31L
                + type.ordinal() * 17L
                + (role & 0xffff)
                + (equip & 0xffff)
                + (activity & 0xffff)
                + (yawBits & 0xffffffffL)
                + poi
                + (adventurerSeat ? 1 : 0);
        }
    }

    private PlotCreatorSpotPreviewCollector() {}

    public static long signature(@Nonnull PlotCreatorDraft draft, @Nullable World world) {
        PlotCreatorStep step = draft.getStep();
        long h = 19L;
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
        for (DesiredSpotPreview p : collect(draft, world, filter)) {
            h = 31 * h + p.key();
            h = 31 * h + p.npcRoleId().hashCode();
            if (p.equipmentProfileId() != null) {
                h = 31 * h + p.equipmentProfileId().hashCode();
            }
            if (p.activityId() != null) {
                h = 31 * h + p.activityId().hashCode();
            }
            if (p.facingYawWorldRadians() != null) {
                h = 31 * h + Float.floatToIntBits(p.facingYawWorldRadians());
            }
        }
        return h;
    }

    @Nonnull
    public static List<DesiredSpotPreview> collect(
        @Nonnull PlotCreatorDraft draft,
        @Nullable World world,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter
    ) {
        List<DesiredSpotPreview> out = new ArrayList<>();
        if (draft.getPlotAnchor() == null && draft.getPrefabOriginMin() == null) {
            return out;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        addStand(
            out,
            draft,
            draft.getInnkeeperSpawnLocal(),
            PlotCreatorSubstepType.INNKEEPER_SPAWN,
            null,
            null,
            filter,
            plugin,
            false
        );
        addStand(
            out,
            draft,
            draft.getGuildMasterSpawnLocal(),
            PlotCreatorSubstepType.GUILD_MASTER_SPAWN,
            null,
            null,
            filter,
            plugin,
            false
        );
        for (int[] local : draft.getVisitorSpawnLocals()) {
            addStand(out, draft, local, PlotCreatorSubstepType.VISITOR_SPAWN, null, null, filter, plugin, false);
        }
        for (PlotCreatorAdventurerSpawnEntry entry : draft.getAdventurerSpawns()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.ADVENTURER_SPAWN, null)) {
                continue;
            }
            Vector3i stand = PlotCreatorLocalCoords.toWorldBlock(draft, entry.localArray());
            Float worldYaw = worldYawFromPrefabRadians(draft, entry.getYawRadians());
            out.add(
                preview(
                    stand.x,
                    stand.y,
                    stand.z,
                    null,
                    null,
                    null,
                    PlotCreatorSubstepType.ADVENTURER_SPAWN,
                    PlotCreatorSpotPreviewRoles.resolveNpcRoleId(
                        PlotCreatorSubstepType.ADVENTURER_SPAWN,
                        null,
                        plugin
                    ),
                    null,
                    null,
                    null,
                    worldYaw,
                    "NONE",
                    null,
                    List.of(),
                    false,
                    true
                )
            );
        }
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            addPoi(out, draft, world, poi, filter, plugin);
        }
        addFestivalPreviews(out, draft, filter, plugin);
        return out;
    }

    private static void addFestivalPreviews(
        @Nonnull List<DesiredSpotPreview> out,
        @Nonnull PlotCreatorDraft draft,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter,
        @Nullable AetherhavenPlugin plugin
    ) {
        for (var npc : draft.getFestivalNpcs()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_NPC, npc.getNpcRoleId())) {
                continue;
            }
            Vector3i stand =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {npc.getLocalX(), npc.getLocalY(), npc.getLocalZ()}
                );
            Float worldYaw = worldYawFromDegrees(draft, npc.getYawDegrees());
            out.add(
                preview(
                    stand.x,
                    stand.y,
                    stand.z,
                    null,
                    null,
                    null,
                    PlotCreatorSubstepType.FESTIVAL_NPC,
                    PlotCreatorSpotPreviewRoles.resolveNpcRoleId(
                        PlotCreatorSubstepType.FESTIVAL_NPC,
                        npc.getNpcRoleId(),
                        plugin
                    ),
                    npc.getNpcRoleId(),
                    null,
                    null,
                    worldYaw,
                    "NONE",
                    null,
                    List.of(),
                    false,
                    false
                )
            );
        }
        for (var spot : draft.getFestivalTouristSpots()) {
            if (!passesTypeFilter(filter, PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT, null)) {
                continue;
            }
            Vector3i stand =
                PlotCreatorLocalCoords.toWorldBlock(
                    draft,
                    new int[] {spot.getLocalX(), spot.getLocalY(), spot.getLocalZ()}
                );
            Float worldYaw = worldYawFromDegrees(draft, spot.getYawDegrees());
            out.add(
                preview(
                    stand.x,
                    stand.y,
                    stand.z,
                    null,
                    null,
                    null,
                    PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT,
                    AetherhavenConstants.NPC_TOWNSFOLK,
                    null,
                    null,
                    null,
                    worldYaw,
                    "NONE",
                    null,
                    List.of(),
                    false,
                    false
                )
            );
        }
    }

    private static void addStand(
        @Nonnull List<DesiredSpotPreview> out,
        @Nonnull PlotCreatorDraft draft,
        @Nullable int[] local,
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nullable Float worldYaw,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter,
        @Nullable AetherhavenPlugin plugin,
        boolean adventurerSeat
    ) {
        if (local == null || local.length < 3) {
            return;
        }
        if (!passesTypeFilter(filter, type, workResidentKind)) {
            return;
        }
        Vector3i stand = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        out.add(
            preview(
                stand.x,
                stand.y,
                stand.z,
                null,
                null,
                null,
                type,
                PlotCreatorSpotPreviewRoles.resolveNpcRoleId(type, workResidentKind, plugin),
                workResidentKind,
                null,
                null,
                worldYaw,
                "NONE",
                null,
                List.of(),
                false,
                adventurerSeat
            )
        );
    }

    private static void addPoi(
        @Nonnull List<DesiredSpotPreview> out,
        @Nonnull PlotCreatorDraft draft,
        @Nullable World world,
        @Nonnull PlotCreatorPoiDraft poi,
        @Nullable PlotBuildingKindRequirements.SubstepRequirement filter,
        @Nullable AetherhavenPlugin plugin
    ) {
        PlotCreatorSubstepType type = PlotCreatorSpotMarkerCollector.resolvePoiTypePublic(poi);
        if (type == null || !PlotCreatorSpotPreviewRoles.usesVillagerPreview(type)) {
            return;
        }
        String workKind = poi.getWorkResidentKind();
        if (filter != null && !PlotCreatorValidator.matchesPoiRequirement(poi, filter)) {
            return;
        }
        Vector3i poiBlock =
            PlotCreatorLocalCoords.toWorldBlock(
                draft,
                new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()}
            );
        Vector3i stand = resolveStandCell(world, poiBlock);
        Float worldYaw = worldYawForPoi(draft, poi);
        String activityId =
            PlotCreatorWorkActivityTags.resolveActivityId(type, poi.getWorkResidentKind(), poi.getTags());
        String interactionKind = poi.getInteractionKind() != null ? poi.getInteractionKind() : "NONE";
        PoiInteractionKind kind = PoiInteractionKind.fromJson(interactionKind);
        boolean mountOnUse = kind == PoiInteractionKind.SIT || kind == PoiInteractionKind.SLEEP;
        out.add(
            preview(
                stand.x,
                stand.y,
                stand.z,
                poiBlock.x,
                poiBlock.y,
                poiBlock.z,
                type,
                PlotCreatorSpotPreviewRoles.resolveNpcRoleId(type, workKind, plugin),
                workKind,
                poi.getEquipmentProfileId(),
                activityId,
                worldYaw,
                interactionKind,
                poi.getBlockTypeId(),
                List.copyOf(poi.getTags()),
                mountOnUse,
                false
            )
        );
    }

    @Nonnull
    private static Vector3i resolveStandCell(@Nullable World world, @Nonnull Vector3i poiBlock) {
        if (world != null) {
            PlotCreatorSpotPlacement.ResolvedSpot stand =
                PlotCreatorSpotPlacement.resolveStandSpawn(world, poiBlock);
            return stand.worldBlock();
        }
        return poiBlock;
    }

    @Nullable
    private static Float worldYawForPoi(@Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorPoiDraft poi) {
        Float deg = poi.getInteractionTargetYawDegrees();
        if (deg == null) {
            return null;
        }
        return worldYawFromDegrees(draft, deg);
    }

    @Nullable
    private static Float worldYawFromDegrees(@Nonnull PlotCreatorDraft draft, float yawDegrees) {
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        return PrefabYaw.worldFromPrefabLocal(placement, (float) Math.toRadians(yawDegrees));
    }

    @Nullable
    private static Float worldYawFromPrefabRadians(@Nonnull PlotCreatorDraft draft, float prefabYawRadians) {
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        return PrefabYaw.worldFromPrefabLocal(placement, prefabYawRadians);
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
    private static DesiredSpotPreview preview(
        int standX,
        int standY,
        int standZ,
        @Nullable Integer poiBlockX,
        @Nullable Integer poiBlockY,
        @Nullable Integer poiBlockZ,
        @Nonnull PlotCreatorSubstepType type,
        @Nonnull String npcRoleId,
        @Nullable String workResidentKind,
        @Nullable String equipmentProfileId,
        @Nullable String activityId,
        @Nullable Float facingYawWorldRadians,
        @Nonnull String interactionKind,
        @Nullable String blockTypeId,
        @Nonnull List<String> tags,
        boolean mountOnUse,
        boolean adventurerSeat
    ) {
        return new DesiredSpotPreview(
            standX,
            standY,
            standZ,
            poiBlockX,
            poiBlockY,
            poiBlockZ,
            type,
            npcRoleId,
            workResidentKind,
            equipmentProfileId,
            activityId,
            facingYawWorldRadians,
            interactionKind,
            blockTypeId,
            tags,
            mountOnUse,
            adventurerSeat
        );
    }
}
