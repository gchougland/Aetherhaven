package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plot.GaiaStatueAppearance;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keeps {@link PlotCreatorDraft#getGaiaStatueLocalPos()} and the Gaia statue POI row in sync. */
public final class PlotCreatorGaiaStatueSupport {
    private PlotCreatorGaiaStatueSupport() {}

    public static boolean isGaiaStatueBlockTypeId(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        return GaiaStatueAppearance.isGaiaStatue(blockTypeId);
    }

    /** Reads the statue POI from a loaded building into {@code gaiaStatueLocalPos}. */
    public static void extractLocalPosFromPois(@Nonnull PlotCreatorDraft draft) {
        for (PlotCreatorPoiDraft poi : draft.getPois()) {
            if (isGaiaStatueBlockTypeId(poi.getBlockTypeId())) {
                draft.setGaiaStatueLocalPos(new int[] {poi.getLocalX(), poi.getLocalY(), poi.getLocalZ()});
                draft.setGaiaStatueBlockTypeId(poi.getBlockTypeId());
                return;
            }
        }
    }

    /** Upserts the runtime Gaia statue POI row from {@code gaiaStatueLocalPos}. */
    public static void syncPoiFromLocalPos(@Nonnull PlotCreatorDraft draft) {
        int[] local = draft.getGaiaStatueLocalPos();
        draft.getPois().removeIf(p -> isGaiaStatueBlockTypeId(p.getBlockTypeId()));
        if (local == null || local.length < 3) {
            return;
        }
        PlotCreatorPoiDraft poi = new PlotCreatorPoiDraft();
        poi.setLocal(local[0], local[1], local[2]);
        poi.setBlockTypeId(draft.getGaiaStatueBlockTypeId());
        poi.setInteractionKind("NONE");
        poi.setCapacity(1);
        draft.getPois().add(poi);
    }

    /** Refresh metadata from the actual block before exporting; prefab block IDs carry the appearance too. */
    public static void captureAppearance(World world, PlotCreatorDraft draft) {
        int[] local = draft.getGaiaStatueLocalPos();
        if (local == null || local.length < 3) return;
        var pos = PlotCreatorLocalCoords.toWorldBlock(draft, local);
        String id = PlotCreatorLocalCoords.blockTypeAt(world, pos);
        if (isGaiaStatueBlockTypeId(id)) {
            draft.setGaiaStatueBlockTypeId(id);
            syncPoiFromLocalPos(draft);
        }
    }

    public static void clear(@Nonnull PlotCreatorDraft draft) {
        draft.setGaiaStatueLocalPos(null);
        draft.setGaiaStatueBlockTypeId(null);
        draft.getPois().removeIf(p -> isGaiaStatueBlockTypeId(p.getBlockTypeId()));
    }
}
