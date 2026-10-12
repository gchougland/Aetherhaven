package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Server-owned selection snapshots prevent stale UI events from swapping the wrong item. */
public final class GuardKitPage extends AetherhavenInteractiveCustomUIPage<GuardKitPage.Data> {
    private final UUID townId, residentId;
    private final String characterId;
    private final Map<String, Choice> choices = new HashMap<>();
    private record Choice(TownWarehouse.Page page, short slot, ItemStack stack) {}
    private ItemStack equipped;
    private String guardClass;
    private long frame;
    private boolean appended;
    private String hint = "Select a kit to equip it. Your previous kit returns to the same inventory slot.";
    public GuardKitPage(PlayerRef player, UUID townId, String characterId, UUID residentId) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.townId=townId; this.characterId=characterId; this.residentId=residentId;
    }
    private boolean allowed(TownRecord town) {
        return town != null && town.hasMemberOrOwner(playerRef.getUuid()) && town.playerCanManageConstructions(playerRef.getUuid())
            && TownAdmission.isMember(town, residentId);
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get(); if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId); if(!allowed(town)){close();return;}
        if(!appended){c.append("Aetherhaven/GuardKitPage.ui");appended=true;}
        var progress=TownsfolkProgressService.getOrCreate(town,manager,plugin,characterId);
        var definition=plugin.getTownsfolkCharacterCatalog().byId(characterId);
        c.set("#Portrait.AssetPath",NpcPortraitProvider.portraitPathForTownsfolk(definition));
        c.set("#VillagerName.Text",definition!=null&&definition.getDisplayName()!=null?definition.getDisplayName():characterId);
        var duty=town.getHiredGuardRecords().stream().filter(g->characterId.equals(g.getCharacterId())).findFirst().orElse(null);
        guardClass=duty==null?null:GuardKitService.classFor(duty.getEquipmentProfileId(),plugin.getGuardKits());
        equipped=progress.guardKit();
        var current=ItemStack.isEmpty(equipped)?null:plugin.getGuardKits().find(equipped.getItemId());
        c.set("#CurrentIcon.Visible",!ItemStack.isEmpty(equipped));
        if(!ItemStack.isEmpty(equipped))c.set("#CurrentIcon.ItemId",equipped.getItemId());
        c.set("#CurrentName.Text",ItemStack.isEmpty(equipped)?"Standard equipment":current==null?"Unavailable kit":current.name());
        c.set("#CurrentEffect.Text",current==null?guardClass==null?"Assign guard duty before equipping a kit.":"Starter gear is included with guard duty.":current.description()+(current.guardClass().equals(guardClass)?"":" Saved until this class is on duty."));
        c.set("#Hint.Text",hint);
        c.clear("#Kits"); choices.clear(); frame++;
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if(inventory!=null)for(short slot=0;slot<inventory.getCapacity();slot++)add(c,e,progress,plugin,new Choice(null,slot,inventory.getItemStack(slot)),"Your inventory");
        if(WarehouseAccess.canBrowse(town,playerRef.getUuid()))for(var page:TownWarehouse.Page.values()) {
            for(short slot=0;slot<town.getWarehouse().slots(page);slot++) {
                if(town.getWarehouse().reserved(page,slot)==0)add(c,e,progress,plugin,new Choice(page,slot,town.getWarehouse().stack(page,slot)),page==TownWarehouse.Page.GENERAL?"General storage":"Crafted goods");
            }
        }
        c.set("#Empty.Visible",choices.isEmpty());
        for(String action:List.of("Back","Refresh","Unequip"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,new EventData().append("Action",action).append("Frame",Long.toString(frame)),false);
        c.set("#Unequip.Disabled",ItemStack.isEmpty(equipped));
    }
    private void add(UICommandBuilder c,UIEventBuilder e,TownsfolkProgress progress,AetherhavenPlugin plugin,Choice choice,String source) {
        if(ItemStack.isEmpty(choice.stack())||choice.stack().getQuantity()!=1)return;
        var kit=plugin.getGuardKits().find(choice.stack().getItemId()); if(kit==null)return;
        int index=choices.size();String key=Integer.toString(index);choices.put(key,choice);
        if(index%2==0)c.append("#Kits","Aetherhaven/ProfessionKitRow.ui");
        String host="#Kits["+index/2+"]";c.append(host,"Aetherhaven/ProfessionKitCard.ui");String card=host+"["+index%2+"]";
        c.set(card+" #Icon.ItemId",kit.itemId());c.set(card+" #Name.Text",kit.name());
        c.set(card+" #Source.Text",source);c.set(card+" #Effect.Text",kit.description());
        boolean capable=kit.guardClass().equals(guardClass);
        c.set(card+" #Equip.Text",capable?"Equip":guardClass==null?"Assign guard duty":"Needs "+kit.guardClass().substring(6));c.set(card+" #Equip.Disabled",!capable);
        c.set(card+" #Icon.TooltipText",kit.name()+"\n"+kit.description());
        e.addEventBinding(CustomUIEventBindingType.Activating,card+" #Equip",new EventData().append("Action","Equip").append("Choice",key).append("Frame",Long.toString(frame)),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(!allowed(town)){close();return;}
        if("Back".equals(data.action)){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new TownsfolkProfilePage(playerRef,townId,characterId,residentId));return;}
        var progress=TownsfolkProgressService.getOrCreate(town,manager,plugin,characterId);
        // The frame only moves when this page itself was drawn again, never because the town was busy elsewhere.
        if(!Long.toString(frame).equals(data.frame))hint="This list was drawn again while you chose. Choose a kit once more.";
        else if("Equip".equals(data.action)) {
            var choice=choices.get(data.choice);String error="Choose an available kit.";
            var duty=town.getHiredGuardRecords().stream().filter(g->characterId.equals(g.getCharacterId())).findFirst().orElse(null);
            guardClass=duty==null?null:GuardKitService.classFor(duty.getEquipmentProfileId(),plugin.getGuardKits());
            var chosen=choice==null?null:plugin.getGuardKits().find(choice.stack().getItemId());
            if(chosen!=null)error=GuardKitService.blocked(store,town,residentId,characterId,plugin,chosen.equipmentProfile());
            if(chosen!=null&&error==null&&!chosen.guardClass().equals(guardClass))error="Choose a kit for this guard's class.";
            if(choice!=null&&chosen!=null&&error==null) {
                error="That inventory is unavailable.";
                if(choice.page()==null) {
                    var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
                    if(inventory!=null)error=GuardKitService.equip(progress,plugin.getGuardKits(),guardClass,inventory,choice.slot(),choice.stack());
                } else if(WarehouseAccess.canBrowse(town,playerRef.getUuid())) error=town.getWarehouse().equipGuardKit(choice.page(),choice.slot(),choice.stack(),progress,plugin.getGuardKits(),guardClass);
            }
            hint=error==null?"Guard kit equipped. Attributes and equipment updated.":error;
            if(error==null){GuardKitService.apply(store,town,characterId,plugin,chosen.equipmentProfile());manager.updateTown(town);}
        } else if("Unequip".equals(data.action)) {
            var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            var duty=town.getHiredGuardRecords().stream().filter(g->characterId.equals(g.getCharacterId())).findFirst().orElse(null);
            // The kit worn at this moment, which is the one that comes off.
            var worn=progress.guardKit();
            var saved=ItemStack.isEmpty(worn)?null:plugin.getGuardKits().find(worn.getItemId());
            String fallback=duty==null?(saved==null?"guard_knight":saved.guardClass()):GuardKitService.classFor(duty.getEquipmentProfileId(),plugin.getGuardKits());
            String error=ItemStack.isEmpty(worn)?com.hexvane.aetherhaven.equipment.ProfessionKitService.NONE_WORN:GuardKitService.blocked(store,town,residentId,characterId,plugin,fallback);
            if(error==null)error=GuardKitService.unequip(progress,inventory);
            hint=error==null?"Kit returned to your inventory.":error;
            if(error==null){GuardKitService.apply(store,town,characterId,plugin,fallback);manager.updateTown(town);}
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Choice",Codec.STRING),(d,v)->d.choice=v,d->d.choice).add()
            .append(new KeyedCodec<>("Frame",Codec.STRING),(d,v)->d.frame=v,d->d.frame).add().build();
        String action,choice,frame;
    }
}
