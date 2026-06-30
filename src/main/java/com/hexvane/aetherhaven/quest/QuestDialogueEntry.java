package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestPrerequisites;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Picks a resident dialogue entry node when a role-assigned town quest is offerable. */
public final class QuestDialogueEntry {
    private QuestDialogueEntry() {}

    /**
     * @return first offerable quest's {@link QuestDefinition#dialogueOfferEntryNodeId()} for this NPC role, or null
     */
    @Nullable
    public static String resolveOfferEntryNodeId(
        @Nonnull QuestCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull String npcRoleId
    ) {
        if (!town.playerCanAcceptQuests(playerUuid)) {
            return null;
        }
        String role = npcRoleId.trim();
        if (role.isEmpty()) {
            return null;
        }
        for (String qid : catalog.listQuestIdsAssignedToRole(role)) {
            QuestDefinition def = catalog.get(qid);
            if (def == null || !isOfferable(def, town)) {
                continue;
            }
            String node = def.dialogueOfferEntryNodeId();
            if (node != null && !node.isBlank()) {
                return node.trim();
            }
        }
        return null;
    }

    /**
     * @return first offerable entity-scoped quest's {@link QuestDefinition#dialogueOfferEntryNodeId()} for this NPC, or null
     */
    @Nullable
    public static String resolveOfferEntryNodeIdForEntity(
        @Nonnull QuestCatalog catalog,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull UUID entityUuid
    ) {
        if (!town.playerCanAcceptQuests(playerUuid)) {
            return null;
        }
        for (String qid : catalog.all().keySet()) {
            QuestDefinition def = catalog.get(qid);
            if (def == null || !def.assignByEntity()) {
                continue;
            }
            if (!isOfferableForEntity(def, town, entityUuid)) {
                continue;
            }
            String node = def.dialogueOfferEntryNodeId();
            if (node != null && !node.isBlank()) {
                return node.trim();
            }
        }
        return null;
    }

    static boolean isOfferable(@Nonnull QuestDefinition def, @Nonnull TownRecord town) {
        return isOfferableForEntity(def, town, null);
    }

    static boolean isOfferableForEntity(
        @Nonnull QuestDefinition def, @Nonnull TownRecord town, @Nullable UUID entityUuid
    ) {
        String qid = def.idOrEmpty();
        if (qid.isEmpty()) {
            return false;
        }
        if (!QuestAvailability.isEnabled(def)) {
            return false;
        }
        if (town.hasQuestActive(qid)) {
            return false;
        }
        if (def.repeatOrDefault().isPerEntity()) {
            if (entityUuid != null && town.hasQuestCompletedForEntity(qid, entityUuid)) {
                return false;
            }
            if (AetherhavenConstants.QUEST_HOUSE_GUARD.equals(qid)
                && entityUuid != null
                && !GuardHireService.isUnhousedHiredGuard(town, entityUuid)) {
                return false;
            }
            if (AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(qid)
                && entityUuid != null
                && !TouristPortalTickService.isActivePortalTourist(town, entityUuid)) {
                return false;
            }
        } else {
            String rm = def.repeatOrDefault().mode();
            if (rm == null || rm.isBlank() || "none".equalsIgnoreCase(rm)) {
                if (town.hasQuestCompleted(qid)) {
                    return false;
                }
            }
        }
        QuestPrerequisites pre = def.prerequisitesOrEmpty();
        return pre.satisfiedBy(town);
    }
}
