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

    /** Ensures {@link #transitions} is a mutable list. */
    @Nonnull
    private List<VillagerScheduleTransition> mutableTransitions() {
        if (transitions == null) {
            transitions = new ArrayList<>();
        } else if (!(transitions instanceof ArrayList)) {
            transitions = new ArrayList<>(transitions);
        }
        return transitions;
    }

    public void appendTransition(@Nonnull VillagerScheduleTransition transition) {
        mutableTransitions().add(transition);
    }

    public void appendTransitions(@Nonnull List<VillagerScheduleTransition> added) {
        if (added.isEmpty()) {
            return;
        }
        mutableTransitions().addAll(added);
    }

    public boolean removeTransitionById(@Nonnull String transitionId) {
        String id = transitionId.trim();
        if (id.isEmpty()) {
            return false;
        }
        List<VillagerScheduleTransition> list = transitions;
        if (list == null || list.isEmpty()) {
            return false;
        }
        return list.removeIf(t -> t.getId() != null && id.equals(t.getId().trim()));
    }

    public int removeTransitionsByIds(@Nonnull List<String> ids) {
        int removed = 0;
        for (String id : ids) {
            if (id != null && removeTransitionById(id)) {
                removed++;
            }
        }
        return removed;
    }

    public boolean removeTransitionByTime(@Nonnull VillagerScheduleTransition template) {
        List<VillagerScheduleTransition> list = transitions;
        if (list == null || list.isEmpty()) {
            return false;
        }
        return list.removeIf(t -> VillagerScheduleTransitionMatcher.matchesTime(t, template));
    }

    public int removeTransitionsByTime(@Nonnull List<VillagerScheduleTransition> templates) {
        int removed = 0;
        for (VillagerScheduleTransition t : templates) {
            if (t != null && removeTransitionByTime(t)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Replaces an existing transition with the same {@link VillagerScheduleTransition#getId()}, or appends when no id or
     * no match.
     */
    public void addOrReplaceTransition(@Nonnull VillagerScheduleTransition transition) {
        String id = transition.getId();
        if (id != null && !id.isBlank()) {
            String trimmed = id.trim();
            List<VillagerScheduleTransition> list = mutableTransitions();
            for (int i = 0; i < list.size(); i++) {
                VillagerScheduleTransition existing = list.get(i);
                if (existing.getId() != null && trimmed.equals(existing.getId().trim())) {
                    list.set(i, transition);
                    return;
                }
            }
        }
        mutableTransitions().add(transition);
    }
}
