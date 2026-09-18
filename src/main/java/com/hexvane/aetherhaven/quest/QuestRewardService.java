package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownSharedRecipeUnlockService;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies quest rewards from quest JSON (items, reputation, recipes, etc.). */
public final class QuestRewardService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String GRANT_TO_PLAYER = "player";
    public static final String GRANT_TO_QUEST_BENEFICIARY_NPC = "quest_beneficiary_npc";
    public static final String GRANT_TO_QUEST_GIVER_NPC = "quest_giver_npc";

    private QuestRewardService() {}

    public record ReputationRewardPreview(int amount, @Nullable String npcRoleId) {}

    @Nullable
    public static ReputationRewardPreview firstReputationReward(
        @Nonnull Iterable<QuestReward> rewards,
        @Nonnull String grantTo
    ) {
        String target = grantTo.trim();
        if (target.isEmpty()) {
            return null;
        }
        for (QuestReward r : rewards) {
            if (r.kind() == null || !"reputation".equalsIgnoreCase(r.kind().trim())) {
                continue;
            }
            String gt = r.grantTo();
            if (gt == null || !target.equalsIgnoreCase(gt.trim())) {
                continue;
            }
            int amount = r.amount();
            if (amount <= 0) {
                continue;
            }
            String role = r.npcRoleId();
            return new ReputationRewardPreview(amount, role != null && !role.isBlank() ? role.trim() : null);
        }
        return null;
    }

    /** First reputation reward for quest-board offers (giver or beneficiary grant target). */
    @Nullable
    public static ReputationRewardPreview firstQuestBoardReputationReward(@Nonnull Iterable<QuestReward> rewards) {
        ReputationRewardPreview giver = firstReputationReward(rewards, GRANT_TO_QUEST_GIVER_NPC);
        if (giver != null) {
            return giver;
        }
        return firstReputationReward(rewards, GRANT_TO_QUEST_BENEFICIARY_NPC);
    }

    /**
     * Grants reputation rewards whose {@code grantTo} is {@link #GRANT_TO_QUEST_GIVER_NPC} or
     * {@link #GRANT_TO_QUEST_BENEFICIARY_NPC} to the given villager. When {@code npcRoleId} is set on a reward,
     * it must match {@code villagerRoleId}.
     */
    public static void grantVillagerReputationRewards(
        @Nonnull Iterable<QuestReward> rewards,
        @Nonnull UUID playerUuid,
        @Nonnull UUID villagerEntityUuid,
        @Nonnull String villagerRoleId,
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm
    ) {
        String role = villagerRoleId.trim();
        if (role.isEmpty()) {
            return;
        }
        for (QuestReward r : rewards) {
            if (r.kind() == null || !"reputation".equalsIgnoreCase(r.kind().trim())) {
                continue;
            }
            String grantTo = r.grantTo();
            if (grantTo == null) {
                continue;
            }
            String gt = grantTo.trim();
            if (!GRANT_TO_QUEST_GIVER_NPC.equalsIgnoreCase(gt) && !GRANT_TO_QUEST_BENEFICIARY_NPC.equalsIgnoreCase(gt)) {
                continue;
            }
            int amount = r.amount();
            if (amount <= 0) {
                continue;
            }
            String rewardRole = r.npcRoleId();
            if (rewardRole != null && !rewardRole.isBlank() && !role.equalsIgnoreCase(rewardRole.trim())) {
                continue;
            }
            VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, playerUuid, villagerEntityUuid);
            VillagerReputationService.addReputationInternal(town, world, playerUuid, villagerEntityUuid, e, amount, tm);
        }
    }

    public static void grantNonReputationRewards(
        @Nonnull QuestDefinition def,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        for (QuestReward r : def.rewardsOrEmpty()) {
            if (r.kind() == null) {
                continue;
            }
            String kind = r.kind().trim();
            if ("reputation".equalsIgnoreCase(kind)) {
                continue;
            }
            if ("item".equalsIgnoreCase(kind)) {
                String itemId = r.itemId();
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                int count = Math.max(1, Math.min(r.count(), 9999));
                String grantTo = r.grantTo();
                if (grantTo != null && !grantTo.isBlank() && !GRANT_TO_PLAYER.equalsIgnoreCase(grantTo.trim())) {
                    LOGGER.atInfo().log("Skipping item reward grantTo=%s for quest %s", grantTo, def.idOrEmpty());
                    continue;
                }
                GoldCoinPayment.giveItemReward(player, playerRef, store, itemId.trim(), count);
            } else if ("learn_recipe".equalsIgnoreCase(kind)) {
                String rid = r.recipeItemId();
                if (rid == null || rid.isBlank()) {
                    continue;
                }
                String grantTo = r.grantTo();
                if (grantTo != null && "town_members".equalsIgnoreCase(grantTo.trim())) {
                    TownSharedRecipeUnlockService.grantTownWideLearnRecipe(town, tm, store, rid.trim());
                } else {
                    if (grantTo != null && !grantTo.isBlank() && !GRANT_TO_PLAYER.equalsIgnoreCase(grantTo.trim())) {
                        LOGGER.atWarning().log(
                            "Unknown learn_recipe grantTo=%s for quest %s; granting to completing player only",
                            grantTo,
                            def.idOrEmpty()
                        );
                    }
                    CraftingPlugin.learnRecipe(playerRef, rid.trim(), store);
                }
            } else if ("currency".equalsIgnoreCase(kind) || "unlock".equalsIgnoreCase(kind)) {
                LOGGER.atInfo().log(
                    "[Quest stub] reward kind %s for quest %s (not implemented)",
                    kind,
                    def.idOrEmpty()
                );
            }
        }
    }
}
