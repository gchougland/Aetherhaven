package com.hexvane.aetherhaven.questboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Scales quest board gold coin payouts by quest type (e.g. hunt jobs pay more than fetch). */
public final class QuestBoardGoldRewardScaling {
    private static final Gson GSON = new Gson();
    private static final Type REWARD_LIST_TYPE = new TypeToken<List<QuestReward>>() {}.getType();

    private QuestBoardGoldRewardScaling() {}

    @Nonnull
    public static List<QuestReward> applyGoldCoinMultiplier(@Nonnull List<QuestReward> rewards, double multiplier) {
        if (multiplier <= 1.0 || rewards.isEmpty()) {
            return new ArrayList<>(rewards);
        }
        JsonArray arr = GSON.toJsonTree(rewards).getAsJsonArray();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            if (!isGoldCoinItemReward(o)) {
                continue;
            }
            int count = o.has("count") && !o.get("count").isJsonNull() ? o.get("count").getAsInt() : 1;
            o.addProperty("count", Math.max(1, (int) Math.round(count * multiplier)));
        }
        List<QuestReward> out = GSON.fromJson(arr, REWARD_LIST_TYPE);
        return out != null ? out : new ArrayList<>(rewards);
    }

    private static boolean isGoldCoinItemReward(@Nonnull JsonObject o) {
        if (!o.has("kind") || o.get("kind").isJsonNull()) {
            return false;
        }
        if (!"item".equalsIgnoreCase(o.get("kind").getAsString().trim())) {
            return false;
        }
        if (!o.has("itemId") || o.get("itemId").isJsonNull()) {
            return false;
        }
        return AetherhavenConstants.ITEM_GOLD_COIN.equals(o.get("itemId").getAsString().trim());
    }
}
