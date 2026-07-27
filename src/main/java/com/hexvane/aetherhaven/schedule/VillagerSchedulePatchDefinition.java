package com.hexvane.aetherhaven.schedule;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Crossmod patch that adds, replaces, or removes transitions on an existing weekly schedule. */
public final class VillagerSchedulePatchDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @Nullable
    private Integer schemaVersion;

    @Nullable
    private String targetScheduleRoleId;

    @Nullable
    private List<String> removeTransitionIds;

    @Nullable
    private List<VillagerScheduleTransition> removeTransitions;

    @Nullable
    private List<VillagerScheduleTransition> addTransitions;

    public int schemaVersionOrDefault() {
        return schemaVersion != null ? schemaVersion : SUPPORTED_SCHEMA_VERSION;
    }

    @Nullable
    public String getTargetScheduleRoleId() {
        return targetScheduleRoleId;
    }

    @Nonnull
    public List<String> removeTransitionIdsOrEmpty() {
        return removeTransitionIds != null ? removeTransitionIds : List.of();
    }

    @Nonnull
    public List<VillagerScheduleTransition> removeTransitionsOrEmpty() {
        return removeTransitions != null ? removeTransitions : List.of();
    }

    @Nonnull
    public List<VillagerScheduleTransition> addTransitionsOrEmpty() {
        return addTransitions != null ? addTransitions : List.of();
    }
}
