package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.quest.QuestAvailability;
import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestPrerequisites;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Offer entry nodes for world-scoped quests on world NPCs. */
public final class WorldQuestDialogueEntry {
    private WorldQuestDialogueEntry() {}

    @Nullable
    public static String resolveOfferEntryNodeId(
        @Nonnull QuestCatalog catalog,
        @Nonnull WorldNpcPlayerProgress progress,
        @Nonnull String npcRoleId
    ) {
        String role = npcRoleId.trim();
        if (role.isEmpty()) {
            return null;
        }
        for (String qid : catalog.listQuestIdsAssignedToRole(role)) {
            QuestDefinition def = catalog.get(qid);
            if (def == null || !WorldQuestProgressionService.isWorldQuest(def) || !isOfferable(def, progress)) {
                continue;
            }
            String node = def.dialogueOfferEntryNodeId();
            if (node != null && !node.isBlank()) {
                return node.trim();
            }
        }
        return null;
    }

    static boolean isOfferable(@Nonnull QuestDefinition def, @Nonnull WorldNpcPlayerProgress progress) {
        String qid = def.idOrEmpty();
        if (qid.isEmpty()) {
            return false;
        }
        if (!QuestAvailability.isEnabled(def)) {
            return false;
        }
        if (progress.hasQuestActive(qid) || progress.hasQuestCompleted(qid)) {
            return false;
        }
        QuestPrerequisites pre = def.prerequisitesOrEmpty();
        for (String id : pre.completedQuestIdsOrEmpty()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!progress.hasQuestCompleted(id.trim())) {
                return false;
            }
        }
        List<String> any = pre.anyCompletedQuestIdsOrEmpty();
        if (!any.isEmpty()) {
            boolean ok = false;
            for (String id : any) {
                if (id != null && !id.isBlank() && progress.hasQuestCompleted(id.trim())) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
