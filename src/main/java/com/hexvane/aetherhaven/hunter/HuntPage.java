package com.hexvane.aetherhaven.hunter;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Run dashboard, creature collection and permanent training. Treasury transactions remain server authoritative. */
public final class HuntPage extends AetherhavenInteractiveCustomUIPage<HuntPage.Data> {
    private final UUID townId;private final Ref<EntityStore> hunter;
    private boolean appended,refreshing,contentBuilt;private long seenRevision,seenWarehouseRevision;
    private String renderedTab;
    private static final int CREATURES_PER_PAGE=3;
    private int creaturePage;
    private String tab="Run",hint="Unlock creatures using gold and their drops. Every run begins only when you choose Start.";
    private static final Map<String,String[]> TRAINING=new LinkedHashMap<>();
    static {
        TRAINING.put("vitality",new String[]{"Vitality","60 more maximum health per level.","Potion_Health_Lesser"});
        TRAINING.put("power",new String[]{"Weapon mastery","20% more damage per level.","Weapon_Sword_Iron"});
        TRAINING.put("dressing",new String[]{"Field dressing","Recover 4 health after each wave per level.","Ingredient_Fabric_Scrap_Linen"});
        TRAINING.put("second_wind",new String[]{"Second Wind","Survive one knockout per run with 30% health.","Potion_Health_Greater"});
        TRAINING.put("storage",new String[]{"Spoils racks","Double the room for each kind of spoils.","Furniture_Village_Crate"});
    }
    public HuntPage(PlayerRef player,UUID town,Ref<EntityStore> hunter) {super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Data.CODEC);townId=town;this.hunter=hunter;}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended){c.append("Aetherhaven/HuntPage.ui");appended=true;}
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(!refreshing){refreshing=true;refresh(store.getExternalData().getWorld());}
        if(town==null||!valid(ref,store,town)){
            for(String name:List.of("Run","Creatures","Training","Spoils"))c.set("#"+name+"Panel.Visible",name.equals("Run"));
            c.set("#Wave.Text","HUNTER UNAVAILABLE");c.set("#Status.Text","Speak to the Monster Hunter at the lodge.");
            c.set("#RunInfo.Text","");c.set("#RunDetails.Text","");c.set("#Hint.Text","Return to the Hunter to continue planning.");
            c.set("#Start.Disabled",true);contentBuilt=false;return;
        }
        HuntService.holdForPlanning(townId,hunter,store);
        var state=town.getHunts();boolean rebuild=!contentBuilt||seenRevision!=state.revision()||seenWarehouseRevision!=town.getWarehouse().revision()||!tab.equals(renderedTab);seenRevision=state.revision();seenWarehouseRevision=town.getWarehouse().revision();
        String preparationIssue=state.run()==null?HuntService.preparationIssue(town,hunter,store):null;
        c.set("#Status.Text",preparationIssue==null?HuntService.status(store,townId):preparationIssue);c.set("#Hint.Text",preparationIssue==null?hint:preparationIssue);
        c.set("#Gold.Text",town.getTreasuryGoldCoinCount()+" treasury gold");
        c.set("#Record.Text","BEST RUN   "+state.best()+(state.best()==1?" wave":" waves"));
        c.set("#Wave.Text",state.run()!=null?"WAVE "+(state.defeated()+1):preparationIssue==null?"READY FOR A RUN":"RUN NOT READY");
        c.set("#RunInfo.Text",state.run()==null?"Last run: "+state.lastResult().replace(" Ready for another run.","")
            :state.defeated()+(state.defeated()==1?" creature defeated. Collected spoils are safe.":" creatures defeated. Collected spoils are safe."));
        var stats=store.getComponent(hunter,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
        c.set("#Health.Text",health==null?"Health unavailable":Math.round(health.get())+" / "+Math.round(health.getMax())+" HEALTH");
        var bar=new com.hypixel.hytale.server.core.ui.Anchor();
        bar.setLeft(com.hypixel.hytale.server.core.ui.Value.of(0));bar.setHeight(com.hypixel.hytale.server.core.ui.Value.of(10));
        bar.setWidth(com.hypixel.hytale.server.core.ui.Value.of(health==null?0:Math.round(226*Math.clamp(health.get()/health.getMax(),0,1))));
        c.setObject("#HealthFill.Anchor",bar);
        c.set("#Stats.Text",(int)Math.round(state.damageMultiplier()*100)+"% weapon damage\n"+state.recoveryPerWave()+" health after each wave");
        c.set("#Start.Text",state.run()==null?"Start run   "+TownHunts.ENTRY_GOLD+" gold":"Retire run");
        c.set("#Start.Disabled",preparationIssue!=null);
        if(rebuild) {
        for(String name:List.of("Run","Creatures","Training","Spoils")) {
            c.set("#"+name+"Panel.Visible",tab.equals(name));e.addEventBinding(CustomUIEventBindingType.Activating,"#Tab"+name,event("Tab",name),false);
        }
        c.set("#RunDetails.Text","One portal. One monster at a time.\n\nUnlocked creatures take turns entering the arena. Each wave gains 15% health and 12% damage.\n\nThe Hunter collects spoils between fights. Defeat closes the portal and immediately restores him at the arena. There is no resurrection fee.\n\nRuns cost "+TownHunts.ENTRY_GOLD+" treasury gold to start. No automatic restarts.");
        c.clear("#CreatureCards");
        var families=HuntFamily.all();
        int pageCount=Math.max(1,(families.size()+CREATURES_PER_PAGE-1)/CREATURES_PER_PAGE);
        creaturePage=Math.clamp(creaturePage,0,pageCount-1);
        c.set("#CreaturePage.Text","Page "+(creaturePage+1)+" of "+pageCount);
        c.set("#CreaturePrevious.Disabled",creaturePage==0);c.set("#CreatureNext.Disabled",creaturePage==pageCount-1);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#CreaturePrevious",event("CreaturePrevious",""),false);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#CreatureNext",event("CreatureNext",""),false);
        int index=0;
        for(var family:families.subList(creaturePage*CREATURES_PER_PAGE,Math.min(families.size(),(creaturePage+1)*CREATURES_PER_PAGE))) {
            c.append("#CreatureCards","Aetherhaven/HuntCard.ui");String p="#CreatureCards["+index+++"]";
            c.set(p+" #Icon.ItemId",family.icon());c.set(p+" #Name.Text",family.name());c.set(p+" #Description.Text",family.description());
            c.set(p+" #LockedShade.Visible",!state.unlocked(family.id()));c.set(p+" #Lock.Visible",!state.unlocked(family.id()));
            c.set(p+" #Detail.Text",state.unlocked(family.id())?"Unlocked for future runs":family.unlockGold()+" gold + "+family.unlockItems()+" "+itemName(family.icon()));
            c.set(p+" #Choose.Text",state.unlocked(family.id())?"Unlocked":"Unlock");c.set(p+" #Choose.Disabled",state.unlocked(family.id())||state.run()!=null);
            c.set(p+" #Choose.TooltipText",state.unlocked(family.id())?"This creature appears during runs.":"Carry the listed drops and pay from the town treasury to unlock this creature.");
            e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Unlock",family.id()),false);
        }
        c.clear("#TrainingCards");index=0;
        for(var entry:TRAINING.entrySet()) {
            c.append("#TrainingCards","Aetherhaven/HuntTraining.ui");String p="#TrainingCards["+index+++"]",id=entry.getKey();var info=entry.getValue();
            c.set(p+" #Icon.ItemId",info[2]);c.set(p+" #Name.Text",info[0]+"   "+state.level(id)+" / "+TownHunts.maxLevel(id));
            c.set(p+" #Description.Text",info[1]);c.set(p+" #Choose.Text",state.level(id)>=TownHunts.maxLevel(id)?"Mastered":state.upgradeCost(id)+" gold");
            c.set(p+" #Choose.Disabled",state.run()!=null||state.level(id)>=TownHunts.maxLevel(id));e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Upgrade",id),false);
        }
        c.clear("#SpoilsCards");index=0;
        for(var entry:state.stock().entrySet()) {
            c.append("#SpoilsCards","Aetherhaven/HuntTraining.ui");String p="#SpoilsCards["+index+++"]";
            long available=com.hexvane.aetherhaven.warehouse.ProductionStockSources.availableSpoils(town,plugin,entry.getKey());
            long reserved=entry.getValue()-available;
            c.set(p+" #Icon.ItemId",entry.getKey());c.set(p+" #Name.Text",itemName(entry.getKey()));
            c.set(p+" #Description.Text",entry.getValue()+" / "+state.capacity()+" stored at the lodge"+(reserved>0?"\n"+reserved+" reserved for workplaces":""));
            c.set(p+" #Choose.Text","Collect");c.set(p+" #Choose.Disabled",available==0||!town.playerCanOpenContainers(playerRef.getUuid()));
            e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Collect",entry.getKey()),false);
        }
        c.set("#SpoilsEmpty.Visible",state.stock().isEmpty());
        e.addEventBinding(CustomUIEventBindingType.Activating,"#Start",event("Start",""),false);
        contentBuilt=true;renderedTab=tab;
        }
    }
    private String itemName(String id) {
        var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(id);
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String name=item==null||i18n==null?null:i18n.getMessage(playerRef.getLanguage(),item.getTranslationKey());
        return name==null?id:name;
    }
    private EventData event(String action,String key){return new EventData().append("Action",action).append("Key",key).append("Revision",Long.toString(seenRevision));}
    private void refresh(com.hypixel.hytale.server.core.universe.world.World world) {
        AetherhavenPlugin.get().scheduleOnWorld(world,()->{
            if(isDismissed())return;var ref=playerRef.getReference();if(ref==null||!ref.isValid()||ref.getStore().getExternalData().getWorld()!=world)return;
            var store=ref.getStore();var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(player==null||player.getPageManager().getCustomPage()!=this)return;
            var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);refresh(world);
        },1000);
    }
    private boolean valid(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town) {
        if(!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid())||hunter==null||!hunter.isValid()||store.getComponent(hunter,DeathComponent.getComponentType())!=null)return false;
        var b=store.getComponent(hunter,TownVillagerBinding.getComponentType());var a=store.getComponent(ref,TransformComponent.getComponentType());var h=store.getComponent(hunter,TransformComponent.getComponentType());
        return b!=null&&HuntService.KIND.equals(b.getKind())&&townId.equals(b.getTownId())&&a!=null&&h!=null&&a.getPosition().distanceSquared(h.getPosition())<=1600;
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);var town=manager.getTown(townId);
        if(town==null||!valid(ref,store,town)){close();return;}var state=town.getHunts();
        if("Tab".equals(data.action)&&List.of("Run","Creatures","Training","Spoils").contains(data.key))tab=data.key;
        else if("CreaturePrevious".equals(data.action)||"CreatureNext".equals(data.action)) {
            int lastPage=Math.max(0,(HuntFamily.all().size()-1)/CREATURES_PER_PAGE);
            creaturePage=Math.clamp(creaturePage+("CreatureNext".equals(data.action)?1:-1),0,lastPage);contentBuilt=false;
        }
        else if(!Long.toString(state.revision()).equals(data.revision))hint="The run changed. Choose again.";
        else if("Collect".equals(data.action)&&!town.playerCanOpenContainers(playerRef.getUuid()))hint="You need permission to collect town supplies.";
        else if("Collect".equals(data.action)) {
            var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            int count=(int)com.hexvane.aetherhaven.warehouse.ProductionStockSources.availableSpoils(town,plugin,data.key);
            if(count==0)hint="Those supplies are reserved for a workplace.";var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(data.key);
            if(inv!=null&&item!=null&&count>0) {
                count=Math.min(count,Math.max(1,item.getMaxStack()));
                if(inv.addItemStack(new ItemStack(data.key,count),true,false,true).succeeded()){state.withdraw(data.key,count);manager.updateTown(town);hint="Spoils collected.";}else hint="Make room in your inventory.";
            }
        } else if(!town.playerCanSpendTreasuryGold(playerRef.getUuid()))hint="You need permission to spend treasury gold.";
        else if("Start".equals(data.action)) {
            if(state.run()!=null)HuntService.retire(town,store);else {var error=HuntService.start(town,hunter,store);hint=error==null?"Run started. Watch from outside the arena floor.":error;}
        } else if(state.run()!=null)hint="Finish this run before changing creatures or training.";
        else if("Unlock".equals(data.action)) {
            var family=HuntFamily.get(data.key);var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
            if(family!=null&&!state.unlocked(data.key)) {
                if(town.getTreasuryGoldCoinCount()<family.unlockGold())hint="The treasury needs more gold.";
                else if(inv==null||!inv.removeItemStack(new ItemStack(family.icon(),family.unlockItems()),true,true).succeeded())hint="Carry "+family.unlockItems()+" of this creature's drops.";
                else if(state.unlock(family.id())) {town.addTreasuryGoldCoins(-family.unlockGold());manager.updateTown(town);hint="Creature unlocked for future runs.";}
            }
        } else if("Upgrade".equals(data.action)&&TRAINING.containsKey(data.key)) {
            int cost=state.upgradeCost(data.key);
            if(town.getTreasuryGoldCoinCount()<cost)hint="The treasury needs more gold.";
            else if(state.upgrade(data.key)){town.addTreasuryGoldCoins(-cost);manager.updateTown(town);hint="Training complete. Your next run will use the improvement.";}
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Key",Codec.STRING),(d,v)->d.key=v,d->d.key).add()
            .append(new KeyedCodec<>("Revision",Codec.STRING),(d,v)->d.revision=v,d->d.revision).add().build();
        String action,key,revision;
    }
}
