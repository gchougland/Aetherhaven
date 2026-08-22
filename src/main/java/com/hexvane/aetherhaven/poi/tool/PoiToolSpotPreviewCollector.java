package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSpotPreviewCollector;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSpotPreviewRoles;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSubstepType;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorWorkActivityTags;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Builds plot-creator-style villager spot previews from registered town POIs for the POI debug staff. */
public final class PoiToolSpotPreviewCollector {
    private PoiToolSpotPreviewCollector() {}

    @Nonnull
    public static List<PlotCreatorSpotPreviewCollector.DesiredSpotPreview> collect(
        @Nonnull World world,
        @Nonnull List<PoiEntry> nearby,
        @Nullable AetherhavenPlugin plugin
    ) {
        List<PlotCreatorSpotPreviewCollector.DesiredSpotPreview> out = new ArrayList<>();
        for (PoiEntry poi : nearby) {
            PlotCreatorSpotPreviewCollector.DesiredSpotPreview preview = toPreview(world, poi, plugin);
            if (preview != null) {
                out.add(preview);
            }
        }
        return out;
    }

    @Nullable
    private static PlotCreatorSpotPreviewCollector.DesiredSpotPreview toPreview(
        @Nonnull World world,
        @Nonnull PoiEntry poi,
        @Nullable AetherhavenPlugin plugin
    ) {
        PlotCreatorSubstepType type = resolveType(poi);
        if (type == null || !PlotCreatorSpotPreviewRoles.usesVillagerPreview(type)) {
            return null;
        }
        Vector3i stand = resolveStandCell(world, poi);
        String workKind = poi.getWorkResidentKind();
        String activityId =
            PlotCreatorWorkActivityTags.resolveActivityId(type, workKind, new ArrayList<>(poi.getTags()));
        String interactionKind = poi.getInteractionKind().name();
        boolean mountOnUse =
            poi.isMountOnUse()
                || poi.getInteractionKind() == PoiInteractionKind.SIT
                || poi.getInteractionKind() == PoiInteractionKind.SLEEP;
        return new PlotCreatorSpotPreviewCollector.DesiredSpotPreview(
            stand.x,
            stand.y,
            stand.z,
            poi.getX(),
            poi.getY(),
            poi.getZ(),
            type,
            PlotCreatorSpotPreviewRoles.resolveNpcRoleId(type, workKind, plugin),
            workKind,
            poi.getEquipmentProfileId(),
            activityId,
            poi.getInteractionTargetYawRadians(),
            interactionKind,
            poi.getBlockTypeId(),
            List.copyOf(poi.getTags()),
            mountOnUse,
            false
        );
    }

    @Nonnull
    private static Vector3i resolveStandCell(@Nonnull World world, @Nonnull PoiEntry poi) {
        int sx = poi.getX();
        int sy = poi.getY();
        int sz = poi.getZ();
        if (poi.hasInteractionTarget()) {
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx != null && ty != null && tz != null) {
                sx = (int) Math.floor(tx);
                sy = (int) Math.floor(ty);
                sz = (int) Math.floor(tz);
            }
        }
        int feetY = VillagerBlockUtil.resolveClearStandFeetY(world, sx, sy, sz);
        if (feetY != Integer.MIN_VALUE) {
            return new Vector3i(sx, feetY, sz);
        }
        return new Vector3i(sx, sy, sz);
    }

    /** True when this registry POI should show as a villager model instead of a spawn marker. */
    public static boolean usesVillagerPreview(@Nonnull PoiEntry poi) {
        PlotCreatorSubstepType type = resolveType(poi);
        return type != null && PlotCreatorSpotPreviewRoles.usesVillagerPreview(type);
    }

    @Nullable
    private static PlotCreatorSubstepType resolveType(@Nonnull PoiEntry poi) {
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
}
