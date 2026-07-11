package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Promotes inn-pool visitors to shop residents when a workplace plot completes, using
 * {@link VillagerDefinition#getWorkConstructionId()} and matching shop quests instead of hardcoded
 * construction/role pairs.
 *
 * <p>Completing the shop quest alone does not promote anyone; the visitor leaves the inn when the
 * matching shop plot finishes (and the quest is active or completed).
 */
public final class InnVisitorShopPromotion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String VISITOR_PREFIX = "visitor_";

    private InnVisitorShopPromotion() {}

    /**
     * For every inn-pool villager whose {@code workConstructionId} matches the completed plot, try
     * {@link InnVisitorShopCompletion#onShopBuilt}.
     */
    public static void promoteForCompletedPlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID shopPlotId,
        @Nonnull TownManager tm,
        @Nonnull String completedConstructionId,
        @Nonnull String completedGameplayConstructionId
    ) {
        ConstructionCatalog constructions = plugin.getConstructionCatalog();
        QuestCatalog quests = plugin.getQuestCatalog();
        for (VillagerDefinition def : plugin.getVillagerDefinitionCatalog().allByNpcRoleId().values()) {
            if (!def.isInnPoolEligible()) {
                continue;
            }
            String work = def.getWorkConstructionId();
            if (work == null) {
                continue;
            }
            if (!constructionMatchesWork(constructions, work, completedConstructionId, completedGameplayConstructionId)) {
                continue;
            }
            String residentKind = resolveResidentKind(def);
            if (residentKind == null || residentKind.isBlank()) {
                LOGGER.atWarning().log(
                    "Skip shop promotion for %s: set dialogueVillagerKind or visitorBindingKind (visitor_*)",
                    def.getNpcRoleId()
                );
                continue;
            }
            String questId = findShopQuestId(quests, constructions, def, work);
            if (questId == null || questId.isBlank()) {
                LOGGER.atWarning().log(
                    "Skip shop promotion for %s: no quest grants workConstructionId %s",
                    def.getNpcRoleId(),
                    work
                );
                continue;
            }
            String label = def.getDisplayName();
            if (label == null || label.isBlank()) {
                label = def.getNpcRoleId();
            }
            InnVisitorShopCompletion.onShopBuilt(
                world,
                plugin,
                town,
                shopPlotId,
                tm,
                new ShopPromotionConfig(questId, def.getNpcRoleId(), residentKind, label)
            );
        }
    }

    /**
     * Resident binding kind: {@code dialogueVillagerKind}, else strip {@code visitor_} from
     * {@code visitorBindingKind}.
     */
    @Nullable
    public static String resolveResidentKind(@Nonnull VillagerDefinition def) {
        String dialogue = def.getDialogueVillagerKind();
        if (!dialogue.isEmpty()) {
            return dialogue;
        }
        String visitor = def.getVisitorBindingKind();
        if (visitor != null && visitor.startsWith(VISITOR_PREFIX) && visitor.length() > VISITOR_PREFIX.length()) {
            return visitor.substring(VISITOR_PREFIX.length());
        }
        return null;
    }

    /**
     * Prefer a quest whose plot token/blueprint grant matches {@code workConstructionId} (and ideally
     * {@code assignNpcRoleId}); otherwise the first quest assigned to this NPC role.
     */
    @Nullable
    public static String findShopQuestId(
        @Nonnull QuestCatalog quests,
        @Nonnull ConstructionCatalog constructions,
        @Nonnull VillagerDefinition def,
        @Nonnull String workConstructionId
    ) {
        String roleId = def.getNpcRoleId();
        String workGameplay = constructions.resolveGameplayConstructionId(workConstructionId);
        String roleMatchedGrant = null;
        String anyGrant = null;
        for (Map.Entry<String, QuestDefinition> e : quests.all().entrySet()) {
            QuestDefinition q = e.getValue();
            if (q == null || !questGrantsConstruction(constructions, q, workConstructionId, workGameplay)) {
                continue;
            }
            String qid = e.getKey();
            if (qid == null || qid.isBlank()) {
                qid = q.idOrEmpty();
            }
            if (qid == null || qid.isBlank()) {
                continue;
            }
            String assign = q.assignNpcRoleId();
            if (assign != null && roleId.equals(assign)) {
                return qid;
            }
            if (roleMatchedGrant == null && assign == null) {
                roleMatchedGrant = qid;
            }
            if (anyGrant == null) {
                anyGrant = qid;
            }
        }
        if (roleMatchedGrant != null) {
            return roleMatchedGrant;
        }
        if (anyGrant != null) {
            return anyGrant;
        }
        return quests.findQuestIdByAssignNpcRole(roleId);
    }

    public static boolean constructionMatchesWork(
        @Nonnull ConstructionCatalog constructions,
        @Nonnull String workConstructionId,
        @Nonnull String completedConstructionId,
        @Nonnull String completedGameplayConstructionId
    ) {
        if (workConstructionId.equals(completedConstructionId) || workConstructionId.equals(completedGameplayConstructionId)) {
            return true;
        }
        String workGameplay = constructions.resolveGameplayConstructionId(workConstructionId);
        String completedGameplay = constructions.resolveGameplayConstructionId(completedGameplayConstructionId);
        if (completedGameplay.isEmpty()) {
            completedGameplay = constructions.resolveGameplayConstructionId(completedConstructionId);
        }
        return !workGameplay.isEmpty() && workGameplay.equals(completedGameplay);
    }

    private static boolean questGrantsConstruction(
        @Nonnull ConstructionCatalog constructions,
        @Nonnull QuestDefinition q,
        @Nonnull String workConstructionId,
        @Nonnull String workGameplay
    ) {
        return grantMatches(constructions, q.grantPlotTokenConstructionId(), workConstructionId, workGameplay)
            || grantMatches(constructions, q.grantPlotBlueprintConstructionId(), workConstructionId, workGameplay);
    }

    private static boolean grantMatches(
        @Nonnull ConstructionCatalog constructions,
        @Nullable String grant,
        @Nonnull String workConstructionId,
        @Nonnull String workGameplay
    ) {
        if (grant == null || grant.isBlank()) {
            return false;
        }
        if (workConstructionId.equals(grant)) {
            return true;
        }
        String grantGameplay = constructions.resolveGameplayConstructionId(grant);
        return !workGameplay.isEmpty() && workGameplay.equals(grantGameplay);
    }
}
