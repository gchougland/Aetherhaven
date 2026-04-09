package com.hexvane.aetherhaven.schedule;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Root Gson type for {@code Server/Aetherhaven/VillagerSchedules/<roleId>.json}. */
public final class VillagerScheduleDefinition {
    private int schemaVersion = 1;
    @Nullable
    private List<VillagerScheduleTransition> transitions;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Nonnull
    public List<VillagerScheduleTransition> getTransitions() {
        return transitions != null ? transitions : List.of();
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public void setTransitions(@Nullable List<VillagerScheduleTransition> transitions) {
        this.transitions = transitions != null ? new ArrayList<>(transitions) : null;
    }
}
