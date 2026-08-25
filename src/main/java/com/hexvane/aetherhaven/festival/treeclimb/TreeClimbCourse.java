package com.hexvane.aetherhaven.festival.treeclimb;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalLookSelection;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Resolves tree climb start pads and finish crystal into world space for a festival square. */
public final class TreeClimbCourse {
    private TreeClimbCourse() {}

    public static void ensureCourse(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TreeClimbSession session
    ) {
        if (!session.startPadsView().isEmpty()) {
            return;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        FestivalDefinition festival = FestivalLookSelection.activeLayout(plugin, town);
        if (square == null || festival == null) {
            return;
        }
        applyFromFestival(plugin, square, festival, session);
    }

    public static void applyFromFestival(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival,
        @Nonnull TreeClimbSession session
    ) {
        List<TreeClimbSession.StartPad> pads = new ArrayList<>();
        for (FestivalDefinition.RaceStartSpotRow spot : festival.getRaceStartSpots()) {
            Vector3d pos =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    spot.getLocalX(),
                    spot.getLocalY(),
                    spot.getLocalZ()
                );
            pads.add(new TreeClimbSession.StartPad(pos.x, pos.y, pos.z, spot.getYawDegrees()));
        }
        FestivalDefinition.RaceFinishLocalRow finish = festival.getRaceFinishLocal();
        Vector3d finishPos =
            finish != null
                ? FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    finish.getLocalX(),
                    finish.getLocalY(),
                    finish.getLocalZ()
                )
                : FestivalPrefabSwapService.spotWorldPosition(plugin, square, -4, 44, 3);
        session.setCourse(
            pads,
            finishPos.x,
            finishPos.y,
            finishPos.z,
            TreeClimbIds.maxRacers(festival.getMaxRacers())
        );
    }

    @Nullable
    public static Vector3d finishCenter(@Nonnull TreeClimbSession session) {
        if (session.startPadsView().isEmpty()) {
            return null;
        }
        return new Vector3d(session.getFinishWorldX(), session.getFinishWorldY(), session.getFinishWorldZ());
    }
}
