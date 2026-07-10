package com.hexvane.aetherhaven.dialogue.data;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueChoiceDefinition {
    /** Optional stable id for crossmod patches (replace vs append). */
    @Nullable
    private String id;
    @Nullable
    private String text;
    /** Target node id, or null to end without a follow-up node. */
    @Nullable
    private String next;
    @Nullable
    private JsonObject condition;
    /**
     * When set, the row is shown only if this evaluates true; {@link #getCondition()} then controls whether the choice
     * is enabled (all pass) or greyed out (any fail). When not set, visibility follows the legacy {@link #condition} +
     * {@link #whenFalse} rules.
     */
    @Nullable
    private JsonObject visibilityCondition;
    /** "hide" (default) or "disabled" when condition fails. */
    @Nullable
    private String whenFalse;
    @Nullable
    private String disabledReason;
    /**
     * When true, if the choice is otherwise shown (e.g. resident + item in hand) but villager gifting is not
     * allowed (daily/weekly limit), the row is shown disabled with a reason from the dialogue world view.
     */
    @Nullable
    private Boolean giftDisableWhenNotAllowed;
    @Nullable
    private List<JsonObject> actions;

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

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

    @Nullable
    public JsonObject getVisibilityCondition() {
        return visibilityCondition;
    }

    @Nonnull
    public String whenFalseOrDefault() {
        return whenFalse != null ? whenFalse : "hide";
    }

    @Nullable
    public String getDisabledReason() {
        return disabledReason;
    }

    public boolean isGiftDisableWhenNotAllowed() {
        return giftDisableWhenNotAllowed != null && giftDisableWhenNotAllowed;
    }

    @Nonnull
    public List<JsonObject> getActions() {
        return actions != null ? actions : Collections.emptyList();
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public void setNext(@Nullable String next) {
        this.next = next;
    }

    public void setActions(@Nullable List<JsonObject> actions) {
        this.actions = actions;
    }

    /** True when this choice closes the dialogue (goodbye / leave). */
    public boolean closesDialogue() {
        for (JsonObject action : getActions()) {
            if (action == null) {
                continue;
            }
            if (action.has("type") && "close".equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** True when this choice starts a town quest. */
    public boolean startsQuest() {
        for (JsonObject action : getActions()) {
            if (action == null || !action.has("type")) {
                continue;
            }
            if ("start_quest".equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** True when this choice opens quest offer/start dialogue (hub lines and accept/start actions). */
    public boolean isQuestOfferChoice() {
        if (startsQuest()) {
            return true;
        }
        for (JsonObject action : getActions()) {
            if (action == null || !action.has("type")) {
                continue;
            }
            if ("complete_quest".equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        String target = getNext();
        if (target == null || target.isBlank()) {
            return false;
        }
        String next = target.trim();
        if (isQuestProgressNext(next)) {
            return false;
        }
        if (next.endsWith("_offer") || next.endsWith("_finish") || next.endsWith("_turnin")) {
            return true;
        }
        return next.contains("_quest_");
    }

    /** True when this choice checks in on active quest progress (remind / waiting / still building). */
    public boolean isQuestProgressChoice() {
        String target = getNext();
        if (target == null || target.isBlank()) {
            return false;
        }
        return isQuestProgressNext(target.trim());
    }

    private static boolean isQuestProgressNext(@Nonnull String next) {
        return next.endsWith("_remind")
            || next.endsWith("_waiting")
            || next.endsWith("_building")
            || next.endsWith("_active");
    }

    public boolean hasAction(@Nonnull String type) {
        for (JsonObject action : getActions()) {
            if (action == null || !action.has("type")) {
                continue;
            }
            if (type.equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** True when this choice offers or confirms villager gifting. */
    public boolean isGiftChoice() {
        if (isGiftDisableWhenNotAllowed()) {
            return true;
        }
        if ("gift_confirm".equals(getNext())) {
            return true;
        }
        for (JsonObject action : getActions()) {
            if (action == null || !action.has("type")) {
                continue;
            }
            if ("gift_villager".equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when picking this choice ends the conversation (goodbye / leave), excluding choices that close only to
     * open another UI (shop, repair, etc.).
     */
    public boolean endsConversation() {
        if (closesDialogue()) {
            return true;
        }
        String target = getNext();
        if (target != null && !target.isBlank()) {
            return false;
        }
        for (JsonObject action : getActions()) {
            if (actionKeepsDialogueOpen(action)) {
                return false;
            }
        }
        return true;
    }

    private static boolean actionKeepsDialogueOpen(@Nullable JsonObject action) {
        if (action == null || !action.has("type")) {
            return false;
        }
        return switch (action.get("type").getAsString().toLowerCase()) {
            case "open_barter_shop",
                 "open_blacksmith_repair",
                 "open_geode_ui",
                 "open_jewelry_appraisal",
                 "start_quest",
                 "gift_villager",
                 "goto",
                 "hire_guild_adventurer" -> true;
            default -> false;
        };
    }
}
