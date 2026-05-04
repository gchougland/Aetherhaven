package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Matches a dead entity against {@link QuestObjective} {@code entity_kills} filters. */
public final class QuestEntityKillMatcher {
    private QuestEntityKillMatcher() {}

    public static boolean matches(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull QuestObjective o
    ) {
        if (!"entity_kills".equalsIgnoreCase(String.valueOf(o.kind()).trim())) {
            return false;
        }
        NPCEntity npc = store.getComponent(victimRef, NPCEntity.getComponentType());
        ModelComponent modelComp = store.getComponent(victimRef, ModelComponent.getComponentType());
        String modelId = null;
        if (modelComp != null) {
            Model model = modelComp.getModel();
            if (model != null) {
                modelId = model.getModelAssetId();
            }
        }
        int roleIndex = npc != null ? npc.getRoleIndex() : Integer.MIN_VALUE;
        String roleName = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        String spawnRole = "";
        if (npc != null && npc.getSpawnRoleIndex() != Integer.MIN_VALUE) {
            String sn = NPCPlugin.get().getName(npc.getSpawnRoleIndex());
            spawnRole = sn != null ? sn.trim() : "";
        }

        if (!passesExcludes(o, modelId, roleName, spawnRole, roleIndex)) {
            return false;
        }
        List<String> ids = o.entityIdsAnyOrEmpty();
        List<String> tagsAny = o.entityTagsAnyOrEmpty();
        List<String> tagsAll = o.entityTagsAllOrEmpty();
        if (!ids.isEmpty() && !passesEntityIdsAny(ids, modelId, roleName, spawnRole)) {
            return false;
        }
        if (!tagsAny.isEmpty() && !passesEntityTagsAny(tagsAny, roleIndex)) {
            return false;
        }
        if (!passesEntityTagsAll(tagsAll, roleIndex)) {
            return false;
        }
        return o.hasEntityKillFilters();
    }

    private static boolean passesExcludes(
        @Nonnull QuestObjective o,
        @Nullable String modelId,
        @Nonnull String roleName,
        @Nonnull String spawnRole,
        int roleIndex
    ) {
        for (String id : o.excludeEntityIdsOrEmpty()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String x = id.trim();
            if (equalsAny(x, modelId, roleName, spawnRole)) {
                return false;
            }
        }
        for (String tag : o.excludeTagsAnyOrEmpty()) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            if (roleIndex != Integer.MIN_VALUE && npcGroupContainsRole(tag.trim(), roleIndex)) {
                return false;
            }
        }
        return true;
    }

    private static boolean passesEntityIdsAny(
        @Nonnull List<String> ids,
        @Nullable String modelId,
        @Nonnull String roleName,
        @Nonnull String spawnRole
    ) {
        if (ids.isEmpty()) {
            return true;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String x = id.trim();
            if (equalsAny(x, modelId, roleName, spawnRole)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passesEntityTagsAny(@Nonnull List<String> tags, int roleIndex) {
        if (tags.isEmpty()) {
            return true;
        }
        if (roleIndex == Integer.MIN_VALUE) {
            return false;
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            if (npcGroupContainsRole(tag.trim(), roleIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passesEntityTagsAll(@Nonnull List<String> tags, int roleIndex) {
        if (tags.isEmpty()) {
            return true;
        }
        if (roleIndex == Integer.MIN_VALUE) {
            return false;
        }
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            if (!npcGroupContainsRole(tag.trim(), roleIndex)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsAny(@Nonnull String want, @Nullable String modelId, @Nonnull String roleName, @Nonnull String spawnRole) {
        if (modelId != null && want.equalsIgnoreCase(modelId.trim())) {
            return true;
        }
        return want.equalsIgnoreCase(roleName) || want.equalsIgnoreCase(spawnRole);
    }

    private static boolean npcGroupContainsRole(@Nonnull String groupId, int roleIndex) {
        try {
            TagSetPlugin plug = TagSetPlugin.get();
            if (plug == null) {
                return false;
            }
            int g = NPCGroup.getAssetMap().getIndex(groupId);
            if (g == Integer.MIN_VALUE) {
                return false;
            }
            return WorldSupport.hasTagInGroup(g, roleIndex);
        } catch (Exception ignored) {
            return false;
        }
    }
}
