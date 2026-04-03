package com.hexvane.aetherhaven.schedule;

import java.util.UUID;
import javax.annotation.Nullable;

/** Result of resolving the current schedule segment to a concrete plot UUID. */
public record VillagerScheduleResolveOutcome(@Nullable UUID plotId, @Nullable UUID jobPlotIdToPersist) {
    public static VillagerScheduleResolveOutcome skip() {
        return new VillagerScheduleResolveOutcome(null, null);
    }
}
