package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.tourist.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;

/** Guild visitors become ordinary citizens; guard assignment is a separate profile choice. */
public final class GuildResidentInvitation {
    private GuildResidentInvitation() {}
    public static Message invite(Store<EntityStore> store,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
            UUID player,Ref<EntityStore> playerRef,Ref<EntityStore> ref) {
        if(!town.hasMemberOrOwner(player)||!town.playerCanManageConstructions(player))return Message.raw("You cannot invite residents to this town.");
        var id=store.getComponent(ref,UUIDComponent.getComponentType());
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        if(id==null||identity==null||binding==null||!town.getTownId().equals(binding.getTownId())
            ||store.getComponent(ref,DeathComponent.getComponentType())!=null
            ||!GuildHallAdventurerPoolService.isGuildHallAdventurer(town,id.getUuid()))return Message.raw("This visitor is no longer available.");
        var blocked=TownAdmission.blocked(plugin,town,id.getUuid(),null);if(blocked!=null)return blocked;
        var world=store.getExternalData().getWorld();
        var checkout=TownsfolkExistenceService.checkoutForCharacter(world,plugin,town.getTownId(),identity.getCharacterId());
        if(checkout==null)return Message.raw("This visitor's town record needs repair.");
        String houseQuest = com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK;
        var quest = plugin.getQuestCatalog().get(houseQuest);
        if (quest == null || !com.hexvane.aetherhaven.quest.QuestAvailability.isEnabled(quest))
            return Message.raw("Housing quests are unavailable right now.");
        if (!town.playerCanAcceptQuests(player)) return Message.raw("You need permission to accept quests to invite this resident.");
        if (town.hasQuestActive(houseQuest)) return Message.raw("Finish the current visitor's house quest before inviting another resident.");
        if (town.hasQuestCompletedForEntity(houseQuest, id.getUuid())) return Message.raw("This resident has already been invited.");
        var requirements=TouristMoveInRequirements.forNpc(plugin,store,ref);
        if(requirements.isEmpty())return Message.raw("This visitor's move in requirements are unavailable.");
        if(!TouristMoveInRequirements.removeAll(playerRef,store,requirements))
            return Message.raw("Bring ").insert(TouristMoveInRequirements.itemsLabelMessage(requirements)).insert(Message.raw(" to invite this resident."));
        settle(store,town,manager,plugin,playerRef,ref,id.getUuid(),identity,checkout,quest);
        return null;
    }
    /**
     * Welcomes a townsfolk a player rescued for the quest board. They skip the move in gift, and still need a house.
     *
     * @return null on success, otherwise why they could not be welcomed
     */
    public static Message welcomeRescued(Store<EntityStore> store,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
            Ref<EntityStore> playerRef,Ref<EntityStore> ref) {
        var id=store.getComponent(ref,UUIDComponent.getComponentType());
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        if(id==null||identity==null||store.getComponent(ref,DeathComponent.getComponentType())!=null)return Message.raw("The rescued townsfolk is no longer available.");
        var checkout=TownsfolkExistenceService.checkoutForCharacter(store.getExternalData().getWorld(),plugin,town.getTownId(),identity.getCharacterId());
        var quest=plugin.getQuestCatalog().get(com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        if(checkout==null||quest==null)return Message.raw("The rescued townsfolk's town record needs repair.");
        settle(store,town,manager,plugin,playerRef,ref,id.getUuid(),identity,checkout,quest);
        return null;
    }
    /** Everything after the move in gift is settled: citizenship, the house quest and tourist behaviour until the house is built. */
    private static void settle(Store<EntityStore> store,TownRecord town,TownManager manager,AetherhavenPlugin plugin,Ref<EntityStore> playerRef,
            Ref<EntityStore> ref,UUID resident,TownsfolkCharacterBinding identity,TownsfolkPoolCheckoutRecord checkout,
            com.hexvane.aetherhaven.quest.data.QuestDefinition quest) {
        var world=store.getExternalData().getWorld();
        String houseQuest=com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK;
        admit(town,resident,identity.getCharacterId());
        startHousing(town, resident);
        com.hexvane.aetherhaven.quest.QuestProgressionService.initialize(plugin, town, houseQuest);
        com.hexvane.aetherhaven.quest.QuestLifecycleEffects.runOnStart(world, plugin, town, manager, quest, resident);
        // The invitation already paid the exact same gift. Never ask for or consume it twice.
        town.completeQuestObjective(houseQuest, TouristMoveInRequirements.MOVE_IN_OBJECTIVE_ID);
        if (com.hexvane.aetherhaven.quest.QuestPlotTokenOnStart.grantIfConfigured(plugin, quest, town, playerRef, store))
            com.hexvane.aetherhaven.quest.QuestProgressionService.markStartGrant(plugin, town, houseQuest,
                com.hexvane.aetherhaven.quest.QuestProgressionService.PLOT_TOKEN_RECEIVED);
        checkout.setAssignmentKind(TownsfolkAssignmentKinds.TOURIST);
        TownsfolkPoolPersistence.save(world,plugin,TownsfolkPoolPersistence.getOrLoad(world,plugin));
        store.putComponent(ref,TownsfolkCharacterBinding.getComponentType(),new TownsfolkCharacterBinding(identity.getCharacterId(),identity.getActivePersonalityId(),
            TownsfolkAssignmentKinds.TOURIST,identity.getModelAssetId(),identity.getPersonalityIds()));
        GuardHireCleanup.prepareForGuardDuty(ref,store);
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc!=null)NpcStandStill.release(ref,npc,store);
        store.putComponent(ref,TouristAutonomyState.getComponentType(),TouristAutonomyState.fresh(System.currentTimeMillis()));
        ResidentRegistryService.upsertFromBinding(town,manager,ref,store);
        manager.updateTown(town);
    }
    static void startHousing(TownRecord town, UUID resident) {
        String quest = com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK;
        town.clearGlobalQuestCompletion(quest);
        town.addActiveQuest(quest);
        town.setQuestTargetEntityUuid(quest, resident);
    }
    static void admit(TownRecord town,UUID id,String character) {
        String key=id.toString();
        Integer slot=town.getGuildHallAdventurerSlotByNpcId().remove(key);
        town.getGuildHallAdventurerNpcIds().removeIf(key::equalsIgnoreCase);
        if(slot!=null)town.getGuildHallAdventurerFilledSlots().remove(slot);
        town.getTouristRecords().removeIf(r->id.equals(r.getEntityUuid()));
        town.getTouristRecords().add(new TouristRecord(character,id,new UUID(0,0),true,true));
    }
}
