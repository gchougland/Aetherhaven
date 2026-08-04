package com.hexvane.aetherhaven.plot;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One selectable entry in the plot placement staff building dropdown. */
public final class PlotTokenPlacementOption {
    public static final String MOVE_VALUE_PREFIX = "move:";

    @Nonnull
    private final String dropdownValue;

    @Nonnull
    private final String constructionId;

    @Nullable
    private final UUID movePlotId;

    @Nullable
    private final UUID moveTownId;

    private PlotTokenPlacementOption(
        @Nonnull String dropdownValue,
        @Nonnull String constructionId,
        @Nullable UUID movePlotId,
        @Nullable UUID moveTownId
    ) {
        this.dropdownValue = dropdownValue;
        this.constructionId = constructionId;
        this.movePlotId = movePlotId;
        this.moveTownId = moveTownId;
    }

    @Nonnull
    public static PlotTokenPlacementOption forNewPlot(@Nonnull String constructionId) {
        return new PlotTokenPlacementOption(constructionId.trim(), constructionId.trim(), null, null);
    }

    @Nonnull
    public static PlotTokenPlacementOption forMovePlot(
        @Nonnull String constructionId,
        @Nonnull UUID plotId,
        @Nullable UUID townId
    ) {
        return new PlotTokenPlacementOption(
            MOVE_VALUE_PREFIX + plotId,
            constructionId.trim(),
            plotId,
            townId
        );
    }

    @Nullable
    public static PlotTokenPlacementOption parseDropdownValue(@Nonnull String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(MOVE_VALUE_PREFIX)) {
            try {
                UUID plotId = UUID.fromString(trimmed.substring(MOVE_VALUE_PREFIX.length()).trim());
                return new PlotTokenPlacementOption(trimmed, "", plotId, null);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return forNewPlot(trimmed);
    }

    @Nonnull
    public String getDropdownValue() {
        return dropdownValue;
    }

    @Nonnull
    public String getConstructionId() {
        return constructionId;
    }

    public boolean isMovePlot() {
        return movePlotId != null;
    }

    @Nullable
    public UUID getMovePlotId() {
        return movePlotId;
    }

    @Nullable
    public UUID getMoveTownId() {
        return moveTownId;
    }
}
