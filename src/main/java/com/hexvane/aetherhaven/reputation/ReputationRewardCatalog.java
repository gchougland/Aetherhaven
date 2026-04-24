package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Milestone rewards when reputation crosses thresholds. {@link #rewardId()} is stored in town data;
 * {@link #dialogueNodeId()} must exist in that villager's dialogue JSON.
 */
public final class ReputationRewardCatalog {
    private static final List<ReputationRewardDefinition> ALL = List.of(
        // Elder — charter amendments table recipe; founder monument item
        new ReputationRewardDefinition(
            "rep_elder_50",
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            50,
            "",
            0,
            "rep_reward_50",
            AetherhavenConstants.ITEM_CHARTER_AMENDMENTS_TABLE
        ),
        new ReputationRewardDefinition(
            "rep_elder_100",
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            100,
            AetherhavenConstants.ITEM_FOUNDER_MONUMENT,
            1,
            "rep_reward_100",
            null
        ),
        // Innkeeper
        new ReputationRewardDefinition(
            "rep_innkeeper_25",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            25,
            AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID,
            6,
            "rep_reward_25",
            null
        ),
        new ReputationRewardDefinition(
            "rep_innkeeper_50_table",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            50,
            "",
            0,
            "rep_reward_50_table",
            AetherhavenConstants.ITEM_BANQUET_TABLE
        ),
        new ReputationRewardDefinition(
            "rep_innkeeper_50",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            50,
            AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID,
            12,
            "rep_reward_50",
            null
        ),
        // Merchant — appraisal bench recipe at 50 (crafting knowledge)
        new ReputationRewardDefinition(
            "rep_merchant_50",
            AetherhavenConstants.NPC_MERCHANT,
            50,
            "",
            0,
            "rep_reward_50",
            AetherhavenConstants.ITEM_APPRAISAL_BENCH
        ),
        // Merchant — jewelry crafting bench at 100 (crafting knowledge)
        new ReputationRewardDefinition(
            "rep_merchant_100",
            AetherhavenConstants.NPC_MERCHANT,
            100,
            "",
            0,
            "rep_reward_100",
            AetherhavenConstants.ITEM_JEWELRY_CRAFTING_BENCH
        ),
        // Farmer — sprinkler recipe unlocks (crafting knowledge); no item grant
        new ReputationRewardDefinition(
            "rep_farmer_25",
            AetherhavenConstants.NPC_FARMER,
            25,
            "",
            0,
            "rep_reward_25",
            "Aetherhaven_Sprinkler_Iron"
        ),
        new ReputationRewardDefinition(
            "rep_farmer_50",
            AetherhavenConstants.NPC_FARMER,
            50,
            "",
            0,
            "rep_reward_50",
            "Aetherhaven_Sprinkler_Thorium"
        ),
        new ReputationRewardDefinition(
            "rep_farmer_75",
            AetherhavenConstants.NPC_FARMER,
            75,
            "",
            0,
            "rep_reward_75",
            "Aetherhaven_Sprinkler_Cobalt"
        ),
        new ReputationRewardDefinition(
            "rep_farmer_100",
            AetherhavenConstants.NPC_FARMER,
            100,
            "",
            0,
            "rep_reward_100",
            "Aetherhaven_Sprinkler_Adamantite"
        ),
        // Blacksmith
        new ReputationRewardDefinition(
            "rep_blacksmith_25",
            AetherhavenConstants.NPC_BLACKSMITH,
            25,
            "Rock_Stone",
            32,
            "rep_reward_25",
            null
        ),
        new ReputationRewardDefinition(
            "rep_blacksmith_50",
            AetherhavenConstants.NPC_BLACKSMITH,
            50,
            "",
            0,
            "rep_reward_50",
            AetherhavenConstants.ITEM_GEODE_ANVIL
        )
    );

    private static final Map<String, ReputationRewardDefinition> BY_ID = new ConcurrentHashMap<>();

    static {
        for (ReputationRewardDefinition d : ALL) {
            BY_ID.put(d.rewardId(), d);
        }
    }

    private ReputationRewardCatalog() {}

    @Nonnull
    public static List<ReputationRewardDefinition> allDefinitions() {
        return ALL;
    }

    @Nullable
    public static ReputationRewardDefinition byId(@Nonnull String rewardId) {
        return BY_ID.get(rewardId.trim());
    }

    @Nonnull
    public static List<ReputationRewardDefinition> forRoleSorted(@Nonnull String npcRoleName) {
        String r = npcRoleName.trim();
        List<ReputationRewardDefinition> out = new ArrayList<>();
        for (ReputationRewardDefinition d : ALL) {
            if (r.equals(d.roleId())) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt(ReputationRewardDefinition::minReputation));
        return out;
    }

    /**
     * @param learnRecipeItemId when non-null, grants crafting knowledge for this output item id instead of {@link
     *     #itemId()}/{@link #itemCount()}.
     */
    public record ReputationRewardDefinition(
        @Nonnull String rewardId,
        @Nonnull String roleId,
        int minReputation,
        @Nonnull String itemId,
        int itemCount,
        @Nonnull String dialogueNodeId,
        @Nullable String learnRecipeItemId
    ) {}
}
