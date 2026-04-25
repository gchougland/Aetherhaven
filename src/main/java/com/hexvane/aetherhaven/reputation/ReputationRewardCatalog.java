package com.hexvane.aetherhaven.reputation;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
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
 * <p>
 * Definitions are loaded from {@code Server/Aetherhaven/Villagers/} on catalog reload, with a legacy Java fallback
 * if no milestones are present in JSON.
 */
public final class ReputationRewardCatalog {
    private static final List<ReputationRewardDefinition> LEGACY = List.of(
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
        new ReputationRewardDefinition(
            "rep_innkeeper_50",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            50,
            "",
            0,
            "rep_reward_50",
            AetherhavenConstants.ITEM_BANQUET_TABLE
        ),
        new ReputationRewardDefinition(
            "rep_innkeeper_75",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            75,
            "",
            0,
            "rep_reward_75",
            null
        ),
        new ReputationRewardDefinition(
            "rep_innkeeper_100",
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            100,
            "",
            0,
            "rep_reward_100",
            null
        ),
        new ReputationRewardDefinition(
            "rep_merchant_50",
            AetherhavenConstants.NPC_MERCHANT,
            50,
            "",
            0,
            "rep_reward_50",
            AetherhavenConstants.ITEM_APPRAISAL_BENCH
        ),
        new ReputationRewardDefinition(
            "rep_merchant_100",
            AetherhavenConstants.NPC_MERCHANT,
            100,
            "",
            0,
            "rep_reward_100",
            AetherhavenConstants.ITEM_JEWELRY_CRAFTING_BENCH
        ),
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

    private static volatile List<ReputationRewardDefinition> activeDefinitions = LEGACY;
    private static final Map<String, ReputationRewardDefinition> BY_ID = new ConcurrentHashMap<>();

    static {
        rebuildById(LEGACY);
    }

    private ReputationRewardCatalog() {}

    /**
     * Rebuilds in-memory rep definitions from the villager catalog. Falls back to the legacy list when the merged list
     * is empty.
     */
    public static void refreshFromVillagerCatalog(@Nullable VillagerDefinitionCatalog villagerCatalog) {
        if (villagerCatalog == null) {
            activeDefinitions = LEGACY;
            rebuildById(LEGACY);
            return;
        }
        List<ReputationRewardDefinition> fromData = villagerCatalog.allReputationMilestones();
        if (fromData.isEmpty()) {
            activeDefinitions = LEGACY;
            rebuildById(LEGACY);
            return;
        }
        activeDefinitions = List.copyOf(fromData);
        rebuildById(fromData);
    }

    private static void rebuildById(@Nonnull List<ReputationRewardDefinition> list) {
        BY_ID.clear();
        for (ReputationRewardDefinition d : list) {
            BY_ID.put(d.rewardId(), d);
        }
    }

    @Nonnull
    public static List<ReputationRewardDefinition> allDefinitions() {
        return activeDefinitions;
    }

    @Nullable
    public static ReputationRewardDefinition byId(@Nonnull String rewardId) {
        return BY_ID.get(rewardId.trim());
    }

    @Nonnull
    public static List<ReputationRewardDefinition> forRoleSorted(@Nonnull String npcRoleName) {
        String r = npcRoleName.trim();
        List<ReputationRewardDefinition> out = new ArrayList<>();
        for (ReputationRewardDefinition d : activeDefinitions) {
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
