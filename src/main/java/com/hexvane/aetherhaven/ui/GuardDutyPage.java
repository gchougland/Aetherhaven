package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.guild.GuardDutyService;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Four illustrated class cards alongside the resident's portrait and current duty. */
public final class GuardDutyPage extends AetherhavenInteractiveCustomUIPage<GuardDutyPage.Data> {
    private final UUID townId,residentId;
    private final String characterId;
    private final boolean returnToWork;
    private boolean appended,selectionInitialized;
    private String selected="guard_knight",hint="Guard duty pauses all profession work. Saved priorities resume when the resident returns to civilian life.";
    public GuardDutyPage(PlayerRef player,UUID town,UUID resident,String character) {
        this(player,town,resident,character,false);
    }
    public GuardDutyPage(PlayerRef player,UUID town,UUID resident,String character,boolean returnToWork) {
        super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Data.CODEC);
        townId=town;residentId=resident;characterId=character;this.returnToWork=returnToWork;
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended){c.append("Aetherhaven/GuardDutyPage.ui");appended=true;}
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(town==null)return;
        var def=plugin.getTownsfolkCharacterCatalog().byId(characterId);
        c.set("#Name.Text",def==null?characterId:def.getDisplayName());
        c.set("#Portrait.AssetPath",NpcPortraitProvider.portraitPathForTownsfolk(def));
        var duty=town.getHiredGuardRecords().stream().filter(r->characterId.equals(r.getCharacterId())).findFirst().orElse(null);
        if(!selectionInitialized){if(duty!=null){var current=com.hexvane.aetherhaven.equipment.GuardKitService.classFor(duty.getEquipmentProfileId(),plugin.getGuardKits());if(current!=null)selected=current;}selectionInitialized=true;}
        var profile=duty==null?null:plugin.getEquipmentProfileCatalog().byId(duty.getEquipmentProfileId());
        c.set("#Duty.Text",duty==null?"Profession work":profile==null?"On guard duty":profile.getDisplayName()+" guard");
        c.set("#Capacity.Text",town.getHiredGuardRecords().size()+" / "+TownRankCapacity.maxHiredGuards(town,plugin.getQuestBoardCatalog())+" guard places");
        c.set("#Hint.Text",hint);
        c.set("#Back.Text",returnToWork?"Work":"Profile");
        var progress=TownsfolkProgressService.getOrCreate(town,manager,plugin,characterId);
        var selectedEquipment=plugin.getEquipmentProfileCatalog().byId(com.hexvane.aetherhaven.equipment.GuardKitService.profileFor(progress,selected,plugin.getGuardKits()));
        if(selectedEquipment!=null&&selectedEquipment.usesAttributeCombat()) {
            var combat=com.hexvane.aetherhaven.guild.GuardCombatStats.derive(progress,selectedEquipment);
            c.set("#CombatStats.Text",String.format(java.util.Locale.ROOT,"Class preview: %.0f health   |   %.0f%% attack power   |   %.0f%% protection   |   %.0f%% movement",combat.health(),100*combat.damageMultiplier(),100*combat.damageReduction(),100*com.hexvane.aetherhaven.guild.GuardCombatStats.movementMultiplier(combat.speed())));
        } else c.set("#CombatStats.Text","");
        c.set("#Return.Disabled",duty==null);
        c.set("#Assign.Disabled",duty!=null&&selected.equals(com.hexvane.aetherhaven.equipment.GuardKitService.classFor(duty.getEquipmentProfileId(),plugin.getGuardKits())));
        for(int i=0;i<GuardDutyService.CLASSES.size();i++) {
            String id=GuardDutyService.CLASSES.get(i);
            c.set("#Class"+i+" #Selected.Text",selected.equals(id)?"SELECTED":"");
            e.addEventBinding(CustomUIEventBindingType.Activating,"#Class"+i,new EventData().append("Action",id),false);
        }
        for(String id:new String[]{"Assign","Return","Back"})e.addEventBinding(CustomUIEventBindingType.Activating,"#"+id,new EventData().append("Action",id),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid())){close();return;}
        if("Back".equals(data.action)) {
            if(returnToWork){QuestJournalPage.openWork(ref,store,playerRef,townId);return;}
            store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                .openCustomPage(ref,store,new TownsfolkProfilePage(playerRef,townId,characterId,residentId));return;
        }
        if(GuardDutyService.CLASSES.contains(data.action))selected=data.action;
        else if("Assign".equals(data.action)||"Return".equals(data.action)) {
            var error=GuardDutyService.change(store,town,manager,plugin,playerRef.getUuid(),residentId,characterId,"Assign".equals(data.action)?selected:null);
            if(error==null&&returnToWork){QuestJournalPage.openWork(ref,store,playerRef,townId);return;}
            hint=error==null?("Assign".equals(data.action)?"Guard duty assigned. Profession work is paused.":"Returned to civilian life. Their saved work priorities are available again."):error;
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();
        String action;
    }
}
