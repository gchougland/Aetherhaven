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
        // Elder
        new ReputationRewardDefinition(
            "rep_elder_25",
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            25,
            "Aetherhaven_Gold_Coin",
            8,
            "rep_reward_25"
        ),
        new ReputationRewardDefinition(
            "rep_elder_50",
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            50,
            "Aetherhaven_Gold_Coin",
            16,
            "rep_reward_50"
        ),
        new ReputationRewardDefinition(
            "rep_elder_75",
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            75,
            "Aetherhaven_Town_Planning_Desk",
            1,
            "rep_reward_75"
        ),
        // Innkeeper
        new ReputationRewardDefinition(
            "rep_innkeeper_25",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            25,
            AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID,
            6,
            "rep_reward_25"
        ),
        new ReputationRewardDefinition(
            "rep_innkeeper_50",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            50,
            AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID,
            12,
            "rep_reward_50"
        ),
        // Merchant
        new ReputationRewardDefinition(
            "rep_merchant_25",
            AetherhavenConstants.NPC_MERCHANT,
            25,
            "Aetherhaven_Gold_Coin",
            10,
            "rep_reward_25"
        ),
        new ReputationRewardDefinition(
            "rep_merchant_50",
            AetherhavenConstants.NPC_MERCHANT,
            50,
            "Aetherhaven_Gold_Coin",
            20,
            "rep_reward_50"
        ),
        new ReputationRewardDefinition(
            "rep_merchant_75",
            AetherhavenConstants.NPC_MERCHANT,
            75,
            "Aetherhaven_Gold_Coin",
            35,
            "rep_reward_75"
        ),
        // Farmer
        new ReputationRewardDefinition(
            "rep_farmer_25",
            AetherhavenConstants.NPC_FARMER,
            25,
            "Soil_Dirt",
            24,
            "rep_reward_25"
        ),
        new ReputationRewardDefinition(
            "rep_farmer_50",
            AetherhavenConstants.NPC_FARMER,
            50,
            "Tool_Shovel_Crude",
            1,
            "rep_reward_50"
        ),
        // Blacksmith
        new ReputationRewardDefinition(
            "rep_blacksmith_25",
            AetherhavenConstants.NPC_BLACKSMITH,
            25,
            "Rock_Stone",
            32,
            "rep_reward_25"
        ),
        new ReputationRewardDefinition(
            "rep_blacksmith_50",
            AetherhavenConstants.NPC_BLACKSMITH,
            50,
            "Rock_Stone",
            64,
            "rep_reward_50"
        )
    );

    private static final Map<String, ReputationRewardDefinition> BY_ID = new ConcurrentHashMap<>();

    static {
        for (ReputationRewardDefinition d : ALL) {
            BY_ID.put(d.rewardId(), d);
        }
    }

    private ReputationRewardCatalog() {}

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

    public record ReputationRewardDefinition(
        @Nonnull String rewardId,
        @Nonnull String roleId,
        int minReputation,
        @Nonnull String itemId,
        int itemCount,
        @Nonnull String dialogueNodeId
    ) {}
}
