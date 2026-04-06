package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Quest completion reputation: amount and which NPC role should receive it (must match beneficiary entity). */
public final class QuestReputationConfig {
    private static final Map<String, QuestRep> BY_QUEST_ID = Map.ofEntries(
        Map.entry(AetherhavenConstants.QUEST_BUILD_INN, new QuestRep(18, AetherhavenConstants.ELDER_NPC_ROLE_ID)),
        Map.entry(AetherhavenConstants.QUEST_BUILD_TOWN_HALL, new QuestRep(20, AetherhavenConstants.ELDER_NPC_ROLE_ID)),
        Map.entry(AetherhavenConstants.QUEST_MERCHANT_STALL, new QuestRep(14, AetherhavenConstants.NPC_MERCHANT)),
        Map.entry(AetherhavenConstants.QUEST_FARM_PLOT, new QuestRep(14, AetherhavenConstants.NPC_FARMER)),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_ELDER, new QuestRep(10, AetherhavenConstants.ELDER_NPC_ROLE_ID)),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_INNKEEPER, new QuestRep(10, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID)),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_MERCHANT, new QuestRep(10, AetherhavenConstants.NPC_MERCHANT)),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_FARMER, new QuestRep(10, AetherhavenConstants.NPC_FARMER)),
        Map.entry(AetherhavenConstants.QUEST_HOUSE_BLACKSMITH, new QuestRep(10, AetherhavenConstants.NPC_BLACKSMITH)),
        Map.entry("aetherhaven_dialogue_test_quest", new QuestRep(3, "Aetherhaven_Test_Villager"))
    );

    private QuestReputationConfig() {}

    @Nullable
    public static QuestRep forQuest(@Nonnull String questId) {
        return BY_QUEST_ID.get(questId.trim());
    }

    public record QuestRep(int amount, @Nonnull String beneficiaryRoleId) {}
}
