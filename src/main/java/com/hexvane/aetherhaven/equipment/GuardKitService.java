package com.hexvane.aetherhaven.equipment;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.GuardDutyService;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/** Equipment exchanges run on the world thread and never grant a free removable starter kit. */
public final class GuardKitService {
    private GuardKitService() {}
    public static String classFor(String profileId, GuardKitCatalog catalog) {
        if(profileId!=null&&GuardDutyService.CLASSES.contains(profileId))return profileId;
        var kit=catalog.forProfile(profileId);
        return kit==null?null:kit.guardClass();
    }
    public static String profileFor(TownsfolkProgress progress,String guardClass,GuardKitCatalog catalog) {
        var stack=progress.guardKit();var kit=ItemStack.isEmpty(stack)?null:catalog.find(stack.getItemId());
        return kit!=null&&kit.guardClass().equals(guardClass)?kit.equipmentProfile():guardClass;
    }
    /** Swaps the chosen kit with whatever the guard wears at this moment; see {@link ProfessionKitService#equip}. */
    public static String equip(TownsfolkProgress progress, GuardKitCatalog catalog, String guardClass,
            ItemContainer source, short slot, ItemStack expected) {
        short at=ProfessionKitService.locate(source,slot,expected);
        if(at<0)return ProfessionKitService.TAKEN;
        var kit=catalog.find(expected.getItemId());
        if(kit==null)return "This item is not a guard kit.";
        if(!kit.guardClass().equals(guardClass))return "Choose a kit for this guard's class.";
        String encoded=TownsfolkProgress.encodeProfessionKit(expected);
        if(!source.replaceItemStackInSlot(at,expected,progress.guardKit()).succeeded())return ProfessionKitService.TAKEN;
        progress.setGuardKitData(encoded);return null;
    }
    /** Returns the kit worn at this moment, whichever it is. */
    public static String unequip(TownsfolkProgress progress,ItemContainer destination) {
        var worn=progress.guardKit();
        if(ItemStack.isEmpty(worn))return ProfessionKitService.NONE_WORN;
        if(destination==null||!destination.addItemStack(worn,true,false,true).succeeded())return "Make room in your inventory for the kit.";
        progress.setGuardKitData(null);return null;
    }
    /** Preflight before touching either inventory. Wounded guards keep their current health fraction. */
    public static String blocked(Store<EntityStore> store,TownRecord town,java.util.UUID resident,String character,
                                 AetherhavenPlugin plugin,String profile) {
        var ref=store.getExternalData().getRefFromUUID(resident);
        if(ref==null||!ref.isValid())return "This resident must be nearby and awake.";
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(identity==null||binding==null||!character.equals(identity.getCharacterId())||!town.getTownId().equals(binding.getTownId()))return "This resident is no longer available.";
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null)return "Revive this resident before changing equipment.";
        if(npc==null||npc.getRole()==null||npc.getRole().isRoleChangeRequested())return "Wait until this resident finishes changing duty.";
        String state=com.hexvane.aetherhaven.npc.NpcSupportUtil.stateName(store,ref);
        if(state!=null&&state.startsWith("Combat"))return "Wait until this resident is out of combat.";
        var equipment=plugin.getEquipmentProfileCatalog().byId(profile);
        if(equipment==null||!equipment.usesAttributeCombat())return "This equipment is unavailable.";
        var ids=new java.util.ArrayList<>(equipment.getArmorItemIds());
        equipment.getHotbarSlots().forEach(s->ids.add(s.getItemId()));
        if(equipment.getOffhandItemId()!=null)ids.add(equipment.getOffhandItemId());
        if(ids.stream().anyMatch(id->com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(id)==null))return "Some items in this kit are unavailable.";
        return null;
    }
    public static void apply(Store<EntityStore> store,TownRecord town,String character,AetherhavenPlugin plugin,String profile) {
        var guard=town.getHiredGuardRecords().stream().filter(g->character.equals(g.getCharacterId())).findFirst().orElse(null);
        if(guard==null)return; // A stored kit can be returned while its owner performs profession work.
        guard.setEquipmentProfileId(profile);
        town.getResidentNpcRecords().stream().filter(r->guard.getEntityUuid().equals(r.getLastEntityUuid())).forEach(r->r.setGuardEquipmentProfile(profile));
        var ref=store.getExternalData().getRefFromUUID(guard.getEntityUuid());
        if(ref!=null&&ref.isValid()) {
            VillagerEquipmentService.applyProfile(ref,store,null,plugin.getEquipmentProfileCatalog().byId(profile));
            com.hexvane.aetherhaven.guild.GuardAttributeCombat.refresh(ref,store);
        }
        // Update health and movement with the kit, preserving wounds and unrelated modifiers.
    }
}
