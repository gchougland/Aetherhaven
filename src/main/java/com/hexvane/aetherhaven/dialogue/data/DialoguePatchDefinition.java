package com.hexvane.aetherhaven.dialogue.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Crossmod patch that injects nodes/choices into an existing dialogue tree. */
public final class DialoguePatchDefinition {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    @Nullable
    private Integer schemaVersion;
    @Nullable
    private String targetTreeId;
    @Nullable
    private Map<String, DialogueNodeDefinition> addNodes;
    @Nullable
    private List<DialogueNodePatchDefinition> nodePatches;

    public int schemaVersionOrDefault() {
        return schemaVersion != null ? schemaVersion : SUPPORTED_SCHEMA_VERSION;
    }

    @Nullable
    public String getTargetTreeId() {
        return targetTreeId;
    }

    @Nonnull
    public Map<String, DialogueNodeDefinition> addNodesOrEmpty() {
        return addNodes != null ? addNodes : Collections.emptyMap();
    }

    @Nonnull
    public List<DialogueNodePatchDefinition> nodePatchesOrEmpty() {
        return nodePatches != null ? nodePatches : List.of();
    }

    public static final class DialogueNodePatchDefinition {
        @Nullable
        private String nodeId;
        @Nullable
        private List<DialogueChoiceDefinition> addChoices;

        @Nullable
        public String getNodeId() {
            return nodeId;
        }

        @Nonnull
        public List<DialogueChoiceDefinition> addChoicesOrEmpty() {
            return addChoices != null ? addChoices : List.of();
        }
    }
}
