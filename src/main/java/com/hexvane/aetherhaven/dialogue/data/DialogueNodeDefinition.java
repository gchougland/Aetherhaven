package com.hexvane.aetherhaven.dialogue.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
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
    /** When set with {@code villager_greeting}, shown once on first-ever talk instead of random greetings. */
    @Nullable
    private String introText;
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

    @Nullable
    public String getIntroText() {
        return introText;
    }

    @Nonnull
    public List<JsonObject> getActions() {
        return actions != null ? actions : Collections.emptyList();
    }

    @Nonnull
    public List<DialogueChoiceDefinition> getChoices() {
        return choices != null ? choices : Collections.emptyList();
    }

    /** Appends a choice for dialogue patches; replaces an existing choice with the same {@code id}. */
    public void addOrReplaceChoice(@Nonnull DialogueChoiceDefinition choice) {
        ensureMutableChoices();
        String id = choice.getId();
        if (id != null && !id.isBlank()) {
            String want = id.trim();
            for (int i = 0; i < choices.size(); i++) {
                DialogueChoiceDefinition existing = choices.get(i);
                if (existing != null && want.equals(existing.getId() != null ? existing.getId().trim() : null)) {
                    choices.set(i, choice);
                    return;
                }
            }
        }
        choices.add(choice);
    }

    private void ensureMutableChoices() {
        if (choices == null) {
            choices = new ArrayList<>();
        } else if (!(choices instanceof ArrayList)) {
            choices = new ArrayList<>(choices);
        }
    }
}
