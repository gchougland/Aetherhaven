package com.hexvane.aetherhaven.dialogue.data;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueNodeDefinition {
    @Nullable
    private String speaker;
    @Nullable
    private String text;
    /** When {@code villager_greeting}, {@link com.hexvane.aetherhaven.ui.DialoguePage} builds body from villager data. */
    @Nullable
    private String bodyMode;
    @Nullable
    private List<JsonObject> actions;
    @Nullable
    private List<DialogueChoiceDefinition> choices;

    @Nullable
    public String getSpeaker() {
        return speaker;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getBodyMode() {
        return bodyMode;
    }

    @Nonnull
    public List<JsonObject> getActions() {
        return actions != null ? actions : Collections.emptyList();
    }

    @Nonnull
    public List<DialogueChoiceDefinition> getChoices() {
        return choices != null ? choices : Collections.emptyList();
    }
}
