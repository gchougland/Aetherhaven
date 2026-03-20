package com.hexvane.aetherhaven.dialogue.data;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueChoiceDefinition {
    @Nullable
    private String text;
    /** Target node id, or null to end without a follow-up node. */
    @Nullable
    private String next;
    @Nullable
    private JsonObject condition;
    /** "hide" (default) or "disabled" when condition fails. */
    @Nullable
    private String whenFalse;
    @Nullable
    private String disabledReason;
    @Nullable
    private List<JsonObject> actions;

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getNext() {
        return next;
    }

    @Nullable
    public JsonObject getCondition() {
        return condition;
    }

    @Nonnull
    public String whenFalseOrDefault() {
        return whenFalse != null ? whenFalse : "hide";
    }

    @Nullable
    public String getDisabledReason() {
        return disabledReason;
    }

    @Nonnull
    public List<JsonObject> getActions() {
        return actions != null ? actions : Collections.emptyList();
    }
}
