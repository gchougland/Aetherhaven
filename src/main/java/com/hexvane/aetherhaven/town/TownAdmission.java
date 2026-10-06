package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;

public final class TownAdmission {
    private TownAdmission() {}

    public static boolean isMember(TownRecord town, UUID id) {
        if (id == null) return false;
        if (id.equals(town.getElderEntityUuid()) || id.equals(town.getInnkeeperEntityUuid())) return true;
        if (town.getHiredGuardRecords().stream().anyMatch(r -> id.equals(r.getEntityUuid()))) return true;
        if (town.getTouristRecords().stream().anyMatch(r -> r.isCitizen() && id.equals(r.getEntityUuid()))) return true;
        if (town.getPlotInstances().stream().anyMatch(p -> p.hasHomeResident(id))) return true;
        return town.getResidentNpcRecords().stream().anyMatch(r -> id.equals(r.getLastEntityUuid())
            && !TownVillagerBinding.isVisitorKind(r.getKind())
            && ((!TownVillagerBinding.KIND_TOWNSFOLK.equals(r.getKind()) && !TownVillagerBinding.KIND_GUARD.equals(r.getKind()))
                || r.isPendingDawnRevival()));
    }

    public static Message blocked(AetherhavenPlugin plugin, TownRecord town, UUID id, String coreRole) {
        if (isMember(town, id)) return null;
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(coreRole) || AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(coreRole)) return null;
        if (coreRole != null && town.getResidentNpcRecords().stream().anyMatch(r -> coreRole.equals(r.getNpcRoleId())
                && !TownVillagerBinding.isVisitorKind(r.getKind()))) return null;
        var population = TownPopulation.snapshot(town, plugin.getConstructionCatalog());
        if (coreRole == null && population.level() < 2) return Message.translation("aetherhaven_population.foodFirst");
        if (coreRole != null && !TownRecruitment.available(plugin, town, coreRole))
            return Message.translation("aetherhaven_population.notAvailable").param("level", TownRecruitment.minimumLevel(plugin, coreRole));
        return population.canAdmit() ? null : Message.translation("aetherhaven_population.shortFood")
            .param("points", population.foodShortfallForNextResident());
    }

    public static boolean isAdmissionQuest(AetherhavenPlugin plugin, String questId) {
        return AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(questId)
            || AetherhavenConstants.QUEST_HOUSE_GUARD.equals(questId)
            || InnVisitorShopPromotion.isInnPoolShopQuest(plugin, questId);
    }

    public static Message blockedForNpc(AetherhavenPlugin plugin, TownRecord town, Ref<EntityStore> npc, Store<EntityStore> store) {
        if (npc == null || !npc.isValid()) return null;
        var uuid = store.getComponent(npc, UUIDComponent.getComponentType());
        var character = store.getComponent(npc, com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType());
        var entity = store.getComponent(npc, NPCEntity.getComponentType());
        return blocked(plugin, town, uuid == null ? null : uuid.getUuid(), character != null || entity == null ? null : entity.getRoleName());
    }

    public static Message blockedChoice(AetherhavenPlugin plugin, TownRecord town, Ref<EntityStore> npc,
            Store<EntityStore> store, DialogueChoiceDefinition choice) {
        if (plugin == null || town == null || choice.getActions() == null) return null;
        for (var action : choice.getActions()) {
            if (action == null || !action.has("type")) continue;
            String type = action.get("type").getAsString();
            boolean admission = "hire_guild_adventurer".equals(type) || "invite_guild_resident".equals(type);
            if ("start_quest".equals(type) && action.has("id")) admission = isAdmissionQuest(plugin, action.get("id").getAsString());
            if (admission) return blockedForNpc(plugin, town, npc, store);
        }
        return null;
    }
}
