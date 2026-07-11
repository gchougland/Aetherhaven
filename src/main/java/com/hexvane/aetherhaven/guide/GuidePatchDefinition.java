package com.hexvane.aetherhaven.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Crossmod patch that appends guide {@code sub-topics} onto an existing hub topic. */
public final class GuidePatchDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @Nullable
    private Integer schemaVersion;
    @Nullable
    private String targetTopicId;
    @Nullable
    private List<String> addSubTopics;

    public int schemaVersionOrDefault() {
        return schemaVersion != null ? schemaVersion : SUPPORTED_SCHEMA_VERSION;
    }

    @Nullable
    public String getTargetTopicId() {
        return targetTopicId;
    }

    @Nonnull
    public List<String> addSubTopicsOrEmpty() {
        if (addSubTopics == null || addSubTopics.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : addSubTopics) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }
}
