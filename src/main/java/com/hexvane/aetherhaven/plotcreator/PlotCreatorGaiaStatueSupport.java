package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keeps {@link PlotCreatorDraft#getGaiaStatueLocalPos()} and the Gaia statue POI row in sync. */
public final class PlotCreatorGaiaStatueSupport {
    private PlotCreatorGaiaStatueSupport() {}

    public static boolean isGaiaStatueBlockTypeId(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        return AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID.equalsIgnoreCase(blockTypeId.trim());
    }

    /** Reads the statue POI from a loaded building into {@code gaiaStatueLocalPos}. */
    public static void extractLocalPosFromPois(@Nonnull PlotCreatorDraft draft) {
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            if (isGaiaStatueBlockTypeId(poi.getBlockTypeId())) {
                draft.setGaiaStatueLocalPos(new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()});
                return;
            }
        }
    }

    /** Upserts the runtime Gaia statue POI row from {@code gaiaStatueLocalPos}. */
    public static void syncPoiFromLocalPos(@Nonnull PlotCreatorDraft draft) {
        int[] local = draft.getGaiaStatueLocalPos();
        Integer targetX = null;
        Integer targetY = null;
        Integer targetZ = null;
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            if (isGaiaStatueBlockTypeId(poi.getBlockTypeId())) {
                targetX = poi.getInteractionTargetLocalX();
                targetY = poi.getInteractionTargetLocalY();
                targetZ = poi.getInteractionTargetLocalZ();
                break;
            }
        }
        draft.getPois().removeIf(p -> isGaiaStatueBlockTypeId(p.getBlockTypeId()));
        if (local == null || local.length < 3) {
            return;
        }
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(local[0], local[1], local[2]);
        poi.setBlockTypeId(AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID);
        poi.setInteractionKind("WORK_SURFACE");
        poi.getTags().add("WORK");
        poi.setCapacity(1);
        if (targetX != null && targetY != null && targetZ != null) {
            poi.setInteractionTargetLocal(targetX, targetY, targetZ);
        } else {
            poi.setInteractionTargetLocal(local[0], local[1] - 1, local[2] + 1);
        }
        draft.getPois().add(poi);
    }

    public static void clear(@Nonnull PlotCreatorDraft draft) {
        draft.setGaiaStatueLocalPos(null);
        draft.getPois().removeIf(p -> isGaiaStatueBlockTypeId(p.getBlockTypeId()));
    }
}
