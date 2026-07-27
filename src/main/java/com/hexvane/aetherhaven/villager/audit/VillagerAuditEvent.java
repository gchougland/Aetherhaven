package com.hexvane.aetherhaven.villager.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One villager audit log line (JSONL). */
public final class VillagerAuditEvent {
    public enum EventType {
        DEATH,
        REMOVED,
        DETECTED_MISSING
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final long epochMs;
    @Nonnull
    private final EventType event;
    @Nonnull
    private final String entityUuid;
    @Nonnull
    private final String displayName;
    @Nonnull
    private final String roleId;
    @Nonnull
    private final String bindingKind;
    @Nonnull
    private final String townId;
    @Nonnull
    private final String townName;
    @Nonnull
    private final String world;
    @Nullable
    private final Double x;
    @Nullable
    private final Double y;
    @Nullable
    private final Double z;
    @Nonnull
    private final String source;
    @Nullable
    private final String deathCause;
    @Nonnull
    private final String notes;

    public VillagerAuditEvent(
        long epochMs,
        @Nonnull EventType event,
        @Nonnull String entityUuid,
        @Nonnull String displayName,
        @Nonnull String roleId,
        @Nonnull String bindingKind,
        @Nonnull String townId,
        @Nonnull String townName,
        @Nonnull String world,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        @Nonnull String source,
        @Nullable String deathCause,
        @Nonnull String notes
    ) {
        this.epochMs = epochMs;
        this.event = event;
        this.entityUuid = entityUuid;
        this.displayName = displayName;
        this.roleId = roleId;
        this.bindingKind = bindingKind;
        this.townId = townId;
        this.townName = townName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.source = source;
        this.deathCause = deathCause;
        this.notes = notes;
    }

    @Nonnull
    public String toJsonLine() {
        return GSON.toJson(this);
    }
}
