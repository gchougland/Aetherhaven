package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("questboard")
class QuestBoardGoldRewardScalingTest {
    private static final Gson GSON = new Gson();
    private static final Type REWARD_LIST = new TypeToken<List<QuestReward>>() {}.getType();

    @Test
    void scalesGoldCoinItemRewardsOnly() {
        List<QuestReward> rewards =
            GSON.fromJson(
                """
                [
                  {"kind":"item","itemId":"Aetherhaven_Gold_Coin","count":18,"grantTo":"player"},
                  {"kind":"reputation","amount":5,"grantTo":"quest_giver_npc"},
                  {"kind":"item","itemId":"Rock_Stone","count":4,"grantTo":"player"}
                ]
                """,
                REWARD_LIST
            );

        List<QuestReward> scaled = QuestBoardGoldRewardScaling.applyGoldCoinMultiplier(rewards, 1.5);

        assertEquals(3, scaled.size());
        assertEquals(AetherhavenConstants.ITEM_GOLD_COIN, scaled.get(0).itemId());
        assertEquals(27, scaled.get(0).count());
        assertEquals(5, scaled.get(1).amount());
        assertEquals("Rock_Stone", scaled.get(2).itemId());
        assertEquals(4, scaled.get(2).count());
    }

    @Test
    void multiplierAtMostOneReturnsCopy() {
        List<QuestReward> rewards =
            GSON.fromJson(
                """
                [{"kind":"item","itemId":"Aetherhaven_Gold_Coin","count":10}]
                """,
                REWARD_LIST
            );

        List<QuestReward> unchanged = QuestBoardGoldRewardScaling.applyGoldCoinMultiplier(rewards, 1.0);
        assertEquals(10, unchanged.get(0).count());
        assertTrue(unchanged != rewards);
    }

    @Test
    void roundsScaledGoldUpToAtLeastOne() {
        List<QuestReward> rewards =
            GSON.fromJson(
                """
                [{"kind":"item","itemId":"Aetherhaven_Gold_Coin","count":1}]
                """,
                REWARD_LIST
            );

        List<QuestReward> scaled = QuestBoardGoldRewardScaling.applyGoldCoinMultiplier(rewards, 1.5);
        assertEquals(2, scaled.get(0).count());
    }
}
