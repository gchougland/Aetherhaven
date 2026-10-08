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
        var requirements=TouristMoveInRequirements.forNpc(plugin,store,ref);
        if(requirements.isEmpty())return Message.raw("This visitor's move in requirements are unavailable.");
        if(!TouristMoveInRequirements.removeAll(playerRef,store,requirements))
            return Message.raw("Bring ").insert(TouristMoveInRequirements.itemsLabelMessage(requirements)).insert(Message.raw(" to invite this resident."));
        admit(town,id.getUuid(),identity.getCharacterId());
        checkout.setAssignmentKind(TownsfolkAssignmentKinds.TOURIST);
        TownsfolkPoolPersistence.save(world,plugin,TownsfolkPoolPersistence.getOrLoad(world,plugin));
        store.putComponent(ref,TownsfolkCharacterBinding.getComponentType(),new TownsfolkCharacterBinding(identity.getCharacterId(),identity.getActivePersonalityId(),
            TownsfolkAssignmentKinds.TOURIST,identity.getModelAssetId(),identity.getPersonalityIds()));
        GuardHireCleanup.prepareForGuardDuty(ref,store);
        var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc!=null)NpcStandStill.release(ref,npc,store);
        store.putComponent(ref,TouristAutonomyState.getComponentType(),TouristAutonomyState.fresh(System.currentTimeMillis()));
        ResidentRegistryService.upsertFromBinding(town,manager,ref,store);
        manager.updateTown(town);
        return null;
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
