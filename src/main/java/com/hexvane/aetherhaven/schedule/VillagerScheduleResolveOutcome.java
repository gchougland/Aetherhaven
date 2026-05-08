package com.hexvane.aetherhaven.schedule;

import java.util.UUID;
import javax.annotation.Nullable;

/** Result of resolving the current schedule segment to a concrete plot UUID. */
public record VillagerScheduleResolveOutcome(
    @Nullable UUID plotId,
    @Nullable UUID jobPlotIdToPersist,
    @Nullable String utilityPersistGameplayConstructionId,
    @Nullable String utilityPersistScheduleSegment,
    @Nullable UUID utilityPersistPlotId
) {
    /** Basic outcome without schedule utility pick bookkeeping. */
    public VillagerScheduleResolveOutcome(@Nullable UUID plotId, @Nullable UUID jobPlotIdToPersist) {
        this(plotId, jobPlotIdToPersist, null, null, null);
    }

    public static VillagerScheduleResolveOutcome skip() {
        return new VillagerScheduleResolveOutcome(null, null, null, null, null);
    }

    public boolean hasUtilityPickPersist() {
        return utilityPersistPlotId != null
            && utilityPersistGameplayConstructionId != null
            && utilityPersistScheduleSegment != null;
    }
}
