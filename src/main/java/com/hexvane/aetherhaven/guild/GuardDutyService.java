package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.*;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.npc.*;
import com.hexvane.aetherhaven.patrol.*;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.tourist.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import java.util.*;

/** Changes duty on the same resident. Native role rebuilding preserves the entity UUID. */
public final class GuardDutyService {
    private GuardDutyService() {}
    public static final List<String> CLASSES=List.of("guard_knight","guard_archer","guard_rogue","guard_mage");

    public static String blocked(TownRecord town, UUID id, String character, boolean alive,
            boolean healthy, boolean busy, boolean carrying, boolean assigning, int capacity) {
        if(!TownAdmission.isMember(town,id))return "Invite this Townsfolk to live in the town first.";
        if(!alive)return "Revive this resident before changing their duty.";
        if(!healthy)return "This resident needs to recover before changing duty.";
        if(busy)return "Wait until this resident is out of combat.";
        if(carrying)return "Let this resident finish their delivery first.";
        boolean already=town.getHiredGuardRecords().stream().anyMatch(r->character.equals(r.getCharacterId()));
        if(assigning&&!already&&town.getHiredGuardRecords().size()>=capacity)return "Reach a higher Town Rank to support more guards.";
        return null;
    }

    public static String change(Store<EntityStore> store,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
            UUID player,UUID resident,String character,String profileId) {
        if(!town.hasMemberOrOwner(player)||!town.playerCanManageConstructions(player))return "You cannot manage this town's residents.";
        boolean assigning=profileId!=null;
        if(assigning&&!CLASSES.contains(profileId))return "Choose a guard class.";
        if(ResidentRegistryService.deadResidents(town).stream().anyMatch(r->resident.equals(r.getLastEntityUuid())))
            return "Revive this resident before changing their duty.";
        var ref=store.getExternalData().getRefFromUUID(resident);
        if(ref==null||!ref.isValid())return "This resident must be nearby and awake.";
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(identity==null||binding==null||npc==null||npc.getRole()==null||!identity.getCharacterId().equals(character)
            ||!town.getTownId().equals(binding.getTownId()))return "This resident is no longer available.";
        if(npc.getRole().isRoleChangeRequested())return "This resident is already changing duty.";
        if(!assigning&&!TownVillagerBinding.KIND_GUARD.equals(binding.getKind()))return "This resident already performs profession work.";
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        String state=NpcSupportUtil.stateName(store,ref);
        String error=blocked(town,resident,character,store.getComponent(ref,DeathComponent.getComponentType())==null,
            health!=null&&health.get()>=health.getMax()-.01f,state!=null&&state.startsWith("Combat"),
            town.getWarehouse().batches().stream().anyMatch(b->character.equals(b.courierId())),assigning,
            TownRankCapacity.maxHiredGuards(town,plugin.getQuestBoardCatalog()));
        if(error!=null)return error;
        if(assigning)profileId=com.hexvane.aetherhaven.equipment.GuardKitService.profileFor(
            TownsfolkProgressService.getOrCreate(town,manager,plugin,character),profileId,plugin.getGuardKits());
        var profile=assigning?plugin.getEquipmentProfileCatalog().byId(profileId):null;
        String role=assigning?(profile==null?null:profile.getGuardNpcRole()):AetherhavenConstants.NPC_TOWNSFOLK;
        var npcs=NPCPlugin.get();int index=role==null||npcs==null?-1:npcs.getIndex(role);
        if(index<0||npcs.tryGetCachedValidRole(index)==null)return "This guard class is unavailable.";
        var world=store.getExternalData().getWorld();
        var checkout=TownsfolkExistenceService.checkoutForCharacter(world,plugin,town.getTownId(),character);
        if(checkout==null)return "The resident's town record needs repair before changing duty.";
        var guild=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL);
        if(assigning&&guild==null)return "Build the Guild Hall before assigning guards.";

        // Stop work before requesting the new native role; profession priorities remain saved.
        GuardAttributeCombat.clear(ref,store);
        GuardFollowPlayerSystem.stopFollow(ref,store,false);
        GuardPatrolSystem.clearAssignmentsForGuard(world,plugin,resident);
        GuardHireCleanup.prepareForGuardDuty(ref,store);
        VillagerLifeProps.clear(ref,store);
        NpcStandStill.release(ref,npc,store);
        store.tryRemoveComponent(ref,com.hexvane.aetherhaven.workplace.ApprenticeWorkState.getComponentType());
        store.tryRemoveComponent(ref,com.hexvane.aetherhaven.warehouse.CourierTravelState.getComponentType());
        store.tryRemoveComponent(ref,com.hexvane.aetherhaven.shopspot.ShopClerkState.getComponentType());
        store.tryRemoveComponent(ref,TouristAutonomyState.getComponentType());
        store.tryRemoveComponent(ref,VillagerAutonomyState.getComponentType());
        String assignment=assigning?TownsfolkAssignmentKinds.GUARD:TownsfolkAssignmentKinds.TOURIST;
        var updated=new TownsfolkCharacterBinding(character,identity.getActivePersonalityId(),assignment,identity.getModelAssetId(),identity.getPersonalityIds());
        store.putComponent(ref,TownsfolkCharacterBinding.getComponentType(),updated);
        UUID job=assigning?guild.getPlotId():null;
        String kind=assigning?TownVillagerBinding.KIND_GUARD:TownVillagerBinding.KIND_TOWNSFOLK;
        store.putComponent(ref,TownVillagerBinding.getComponentType(),new TownVillagerBinding(town.getTownId(),kind,binding.getPreferredPlotId(),job));
        recordDuty(town,resident,character,profileId);
        checkout.setAssignmentKind(assignment);
        TownsfolkPoolPersistence.save(world,plugin,TownsfolkPoolPersistence.getOrLoad(world,plugin));
        {
            var armor=store.getComponent(ref,InventoryComponent.Armor.getComponentType());
            if(armor!=null) {
                VillagerEquipmentService.clearArmorInventory(armor);
                store.putComponent(ref,InventoryComponent.Armor.getComponentType(),armor);
            }
            var utility=store.getComponent(ref,InventoryComponent.Utility.getComponentType());
            if(utility!=null) {
                utility.getInventory().clear();
                utility.setActiveSlot((byte)-1,ref,store);
                utility.setOutdatedEquipment(true);
                store.putComponent(ref,InventoryComponent.Utility.getComponentType(),utility);
            }
            VillagerEquipmentService.clearHotbar(ref,store,null);
        }
        if(assigning)VillagerEquipmentService.applyProfile(ref,store,null,profile);
        else store.putComponent(ref,TouristAutonomyState.getComponentType(),TouristAutonomyState.fresh(System.currentTimeMillis()));
        // Update the existing registry entry only. Revival and duplicate handling retain their existing policy.
        ResidentRegistryService.upsert(town,manager,role,kind,job,resident);
        town.getResidentNpcRecords().stream().filter(r->resident.equals(r.getLastEntityUuid())).forEach(r->{r.setTownsfolkIdentity(updated);r.setGuardEquipmentProfile(profile==null?null:profile.getId());});
        manager.updateTown(town);
        RoleChangeSystem.requestRoleChange(ref,npc.getRole(),index,false,"Idle",null,store);
        // Only a fully healthy resident can reach this point. Preserve that across the native max-health change.
        com.hexvane.aetherhaven.villager.TownVillagerHealthTopUp.request(resident);
        return null;
    }

    static void recordDuty(TownRecord town,UUID resident,String character,String profileId) {
        town.getHiredGuardRecords().removeIf(r->character.equals(r.getCharacterId())||resident.equals(r.getEntityUuid()));
        if(profileId!=null)town.getHiredGuardRecords().add(new HiredGuardRecord(character,resident,profileId,
            town.getPlotInstances().stream().anyMatch(p->p.hasHomeResident(resident))));
        // Older guards may have no tourist row or home. Leaving duty must not remove citizenship.
        if(town.getTouristRecords().stream().noneMatch(r->resident.equals(r.getEntityUuid())))
            town.getTouristRecords().add(new TouristRecord(character,resident,new UUID(0,0),true,true));
        else town.getTouristRecords().stream().filter(r->resident.equals(r.getEntityUuid())).forEach(r->{r.setCitizen(true);r.setInvitedToStay(true);});
    }
}
