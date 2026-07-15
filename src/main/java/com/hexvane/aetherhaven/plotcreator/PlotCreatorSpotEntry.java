package com.hexvane.aetherhaven.plotcreator;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One selected important spot in the plot creator: a substep type, optionally a workplace villager role for work desks.
 */
public final class PlotCreatorSpotEntry {
    @Nonnull
    private final PlotCreatorSubstepType type;
    private final int minCount;
    @Nullable
    private final String workResidentKind;

    public PlotCreatorSpotEntry(@Nonnull PlotCreatorSubstepType type, int minCount, @Nullable String workResidentKind) {
        this.type = Objects.requireNonNull(type, "type");
        this.minCount = Math.max(0, minCount);
        this.workResidentKind =
            workResidentKind != null && !workResidentKind.isBlank() ? workResidentKind.trim() : null;
    }

    @Nonnull
    public static PlotCreatorSpotEntry of(@Nonnull PlotCreatorSubstepType type, int minCount) {
        return new PlotCreatorSpotEntry(type, minCount, null);
    }

    @Nonnull
    public static PlotCreatorSpotEntry work(@Nonnull String residentKind, int minCount) {
        return new PlotCreatorSpotEntry(PlotCreatorSubstepType.WORK_POI, minCount, residentKind);
    }

    @Nonnull
    public static PlotCreatorSpotEntry bard(int minCount) {
        return new PlotCreatorSpotEntry(PlotCreatorSubstepType.BARD_WORK_POI, minCount, null);
    }

    @Nonnull
    public PlotCreatorSubstepType type() {
        return type;
    }

    public int minCount() {
        return minCount;
    }

    @Nullable
    public String workResidentKind() {
        return workResidentKind;
    }

    public boolean isWorkRoleSpot() {
        return type == PlotCreatorSubstepType.WORK_POI && workResidentKind != null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlotCreatorSpotEntry that)) {
            return false;
        }
        return type == that.type && Objects.equals(workResidentKind, that.workResidentKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, workResidentKind);
    }
}
