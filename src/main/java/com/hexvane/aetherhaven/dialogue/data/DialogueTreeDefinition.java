package com.hexvane.aetherhaven.dialogue.data;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueTreeDefinition {
    @Nullable
    private String id;
    @Nullable
    private String entry;
    @Nullable
    private Map<String, DialogueNodeDefinition> nodes;

    @Nullable
    public String getId() {
        return id;
    }

    @Nonnull
    public String entryOrDefault() {
        return entry != null && !entry.isBlank() ? entry : "root";
    }

    @Nonnull
    public Map<String, DialogueNodeDefinition> getNodes() {
        return nodes != null ? nodes : Collections.emptyMap();
    }

    @Nullable
    public DialogueNodeDefinition getNode(@Nonnull String nodeId) {
        return getNodes().get(nodeId);
    }
}
