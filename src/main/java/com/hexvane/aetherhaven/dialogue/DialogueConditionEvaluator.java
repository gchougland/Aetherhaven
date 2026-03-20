package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueConditionEvaluator {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final DialogueWorldView worldView;

    public DialogueConditionEvaluator(@Nonnull DialogueWorldView worldView) {
        this.worldView = worldView;
    }

    public boolean evaluate(
        @Nullable JsonObject condition,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (condition == null) {
            return true;
        }
        return evalObject(condition, playerRef, store, npcRef);
    }

    private boolean evalObject(
        @Nonnull JsonObject o,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String type = getString(o, "type");
        if (type == null) {
            LOGGER.atWarning().log("Dialogue condition missing type: %s", o);
            return false;
        }
        return switch (type) {
            case "literal" -> o.get("value") != null && o.get("value").isJsonPrimitive() && o.get("value").getAsBoolean();
            case "true" -> true;
            case "false" -> false;
            case "not" -> {
                JsonObject inner = o.getAsJsonObject("condition");
                yield inner == null || !evalObject(inner, playerRef, store, npcRef);
            }
            case "and" -> {
                JsonArray arr = o.getAsJsonArray("conditions");
                if (arr == null) {
                    yield false;
                }
                boolean ok = true;
                for (JsonElement el : arr) {
                    if (!el.isJsonObject() || !evalObject(el.getAsJsonObject(), playerRef, store, npcRef)) {
                        ok = false;
                        break;
                    }
                }
                yield ok;
            }
            case "or" -> {
                JsonArray arr = o.getAsJsonArray("conditions");
                if (arr == null) {
                    yield false;
                }
                boolean any = false;
                for (JsonElement el : arr) {
                    if (el.isJsonObject() && evalObject(el.getAsJsonObject(), playerRef, store, npcRef)) {
                        any = true;
                        break;
                    }
                }
                yield any;
            }
            case "achievement" -> worldView.hasAchievement(stringOrEmpty(o, "id"));
            case "flag" -> worldView.getFlag(stringOrEmpty(o, "id"));
            case "item" -> {
                String itemId = stringOrEmpty(o, "itemId");
                if (itemId.isEmpty()) {
                    itemId = stringOrEmpty(o, "item");
                }
                int count = o.has("count") && o.get("count").isJsonPrimitive() ? o.get("count").getAsInt() : 1;
                yield worldView.hasItem(playerRef, store, itemId, Math.max(1, count));
            }
            case "villagerInTown" -> worldView.isVillagerInTown(stringOrEmpty(o, "id"));
            default -> {
                LOGGER.atWarning().log("Unknown dialogue condition type: %s", type);
                yield false;
            }
        };
    }

    @Nullable
    private static String getString(@Nonnull JsonObject o, @Nonnull String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    @Nonnull
    private static String stringOrEmpty(@Nonnull JsonObject o, @Nonnull String key) {
        String s = getString(o, key);
        return s != null ? s : "";
    }
}
