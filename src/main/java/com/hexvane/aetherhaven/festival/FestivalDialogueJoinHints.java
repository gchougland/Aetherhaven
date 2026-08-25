package com.hexvane.aetherhaven.festival;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.festival.snowball.SnowballDialogueHandlers;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbDialogueHandlers;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Extra text for the join and start options on festival activity dialogue: how many players are already waiting,
 * and why the option cannot be picked right now. Without this the options simply vanish and players are left
 * guessing.
 */
public final class FestivalDialogueJoinHints {
    private static final String LANG_ROOT = "aetherhaven_festivals.aetherhaven.festival.join";
    private static final String LANG_WAITING = LANG_ROOT + ".waiting";

    private static final String ACTION_TREE_CLIMB_JOIN = "tree_climb_join";
    private static final String ACTION_TREE_CLIMB_START = "tree_climb_start";
    private static final String ACTION_SNOWBALL_JOIN = "snowball_join";
    private static final String ACTION_SNOWBALL_START = "snowball_start";

    private FestivalDialogueJoinHints() {}

    /** Returns the waiting count tail to append to the choice label, or null when it does not apply. */
    @Nullable
    public static Message waitingSuffix(
        @Nonnull DialogueChoiceDefinition choice,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        int joined = joinedCountFor(choice, playerRef, store, npcRef);
        if (joined <= 0) {
            return null;
        }
        return Message.raw(" ").insert(
            Message.translation(LANG_WAITING).param("count", String.valueOf(joined))
        );
    }

    /** Returns why a greyed out join or start option cannot be picked, or null when it does not apply. */
    @Nullable
    public static Message blockedReason(
        @Nonnull DialogueChoiceDefinition choice,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String suffix = blockedReasonSuffix(choice, playerRef, store, npcRef);
        return suffix == null ? null : Message.translation(LANG_ROOT + ".blocked." + suffix);
    }

    @Nullable
    private static String blockedReasonSuffix(
        @Nonnull DialogueChoiceDefinition choice,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        for (JsonObject action : choice.getActions()) {
            String type = actionType(action);
            if (type == null) {
                continue;
            }
            switch (type) {
                case ACTION_TREE_CLIMB_JOIN -> {
                    return TreeClimbDialogueHandlers.joinBlockedReason(playerRef, store, npcRef);
                }
                case ACTION_TREE_CLIMB_START -> {
                    return TreeClimbDialogueHandlers.startBlockedReason(playerRef, store, npcRef);
                }
                case ACTION_SNOWBALL_JOIN -> {
                    return SnowballDialogueHandlers.joinBlockedReason(playerRef, store, npcRef);
                }
                case ACTION_SNOWBALL_START -> {
                    return SnowballDialogueHandlers.startBlockedReason(playerRef, store, npcRef);
                }
                default -> {
                    // Not a festival activity choice.
                }
            }
        }
        return null;
    }

    private static int joinedCountFor(
        @Nonnull DialogueChoiceDefinition choice,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        for (JsonObject action : choice.getActions()) {
            String type = actionType(action);
            if (ACTION_TREE_CLIMB_JOIN.equals(type)) {
                return TreeClimbDialogueHandlers.joinedCount(playerRef, store, npcRef);
            }
            if (ACTION_SNOWBALL_JOIN.equals(type)) {
                return SnowballDialogueHandlers.joinedCount(playerRef, store, npcRef);
            }
        }
        return -1;
    }

    @Nullable
    private static String actionType(@Nullable JsonObject action) {
        if (action == null) {
            return null;
        }
        JsonElement type = action.get("type");
        return type != null && type.isJsonPrimitive() ? type.getAsString() : null;
    }
}
