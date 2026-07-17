package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public record StarterTownLayoutPlan(
    @Nonnull String layout,
    long seed,
    @Nonnull List<Building> buildings
) {
    public StarterTownLayoutPlan {
        buildings = List.copyOf(buildings);
    }

    public record Building(
        @Nonnull String constructionId,
        @Nonnull Vector3i prefabAnchor,
        @Nonnull Rotation yaw,
        @Nonnull PlotFootprintRecord footprint,
        @Nonnull Vector3i roadPoint
    ) {}
}
