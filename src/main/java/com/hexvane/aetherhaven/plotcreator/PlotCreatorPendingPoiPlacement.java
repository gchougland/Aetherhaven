package com.hexvane.aetherhaven.plotcreator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Holds a POI click until the player picks a work animation in {@link com.hexvane.aetherhaven.ui.PlotCreatorPoiActivityPage}. */
public record PlotCreatorPendingPoiPlacement(
    @Nonnull PlotBuildingKindRequirements.SubstepRequirement req,
    @Nonnull int[] poiLocal,
    @Nullable String resolvedBlockTypeId,
    @Nullable Float seatYawRadians,
    @Nonnull Vector3i spotWorldBlock,
    boolean useSeatFacing
) {}
