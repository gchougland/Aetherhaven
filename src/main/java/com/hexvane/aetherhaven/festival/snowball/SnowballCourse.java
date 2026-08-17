package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Resolves snowball pads, out spot, and pile cells into world space for a festival square. */
public final class SnowballCourse {
    private SnowballCourse() {}

    public static void ensureCourse(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull SnowballSession session
    ) {
        if (!session.teamAPadsView().isEmpty() && !session.teamBPadsView().isEmpty()) {
            return;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        FestivalDefinition festival =
            town.getActiveFestivalId() != null ? plugin.getFestivalCatalog().get(town.getActiveFestivalId()) : null;
        if (square == null || festival == null) {
            return;
        }
        applyFromFestival(plugin, square, festival, session);
    }

    public static void applyFromFestival(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival,
        @Nonnull SnowballSession session
    ) {
        List<SnowballSession.StartPad> teamA = new ArrayList<>();
        for (FestivalDefinition.RaceStartSpotRow spot : festival.getSnowballTeamASpots()) {
            Vector3d pos =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    spot.getLocalX(),
                    spot.getLocalY(),
                    spot.getLocalZ()
                );
            teamA.add(new SnowballSession.StartPad(pos.x, pos.y, pos.z, spot.getYawDegrees()));
        }
        List<SnowballSession.StartPad> teamB = new ArrayList<>();
        for (FestivalDefinition.RaceStartSpotRow spot : festival.getSnowballTeamBSpots()) {
            Vector3d pos =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    spot.getLocalX(),
                    spot.getLocalY(),
                    spot.getLocalZ()
                );
            teamB.add(new SnowballSession.StartPad(pos.x, pos.y, pos.z, spot.getYawDegrees()));
        }
        FestivalDefinition.MazeStartLocalRow out = festival.getSnowballOutLocal();
        Vector3d outPos =
            out != null
                ? FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    out.getLocalX(),
                    out.getLocalY(),
                    out.getLocalZ()
                )
                : FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 6, -13);
        float outYaw = out != null ? out.getYawDegrees() : 0f;
        List<SnowballSession.PileSpot> piles = new ArrayList<>();
        for (FestivalDefinition.OrbSpawnRow spot : festival.getSnowballPileSpots()) {
            Vector3d pos =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin,
                    square,
                    (int) Math.round(spot.getLocalX()),
                    (int) Math.round(spot.getLocalY()),
                    (int) Math.round(spot.getLocalZ())
                );
            piles.add(
                new SnowballSession.PileSpot(
                    (int) Math.floor(pos.x),
                    (int) Math.floor(pos.y),
                    (int) Math.floor(pos.z)
                )
            );
        }
        session.setCourse(
            teamA,
            teamB,
            new SnowballSession.StartPad(outPos.x, outPos.y, outPos.z, outYaw),
            piles
        );
    }
}
