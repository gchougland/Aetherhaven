package com.hexvane.aetherhaven.hunter;

import com.hexvane.aetherhaven.ui.AetherhavenLanguage;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
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

/** Run dashboard, creature collection and permanent training. Payments remain server authoritative. */
public final class HuntPage extends AetherhavenInteractiveCustomUIPage<HuntPage.Data> {
    private final UUID townId;private final Ref<EntityStore> hunter;
    private boolean appended,refreshing;
    private static final int CREATURES_PER_PAGE=3;
    private int creaturePage;
    private String tab="Run",hint="Unlock creatures using gold and their drops. Every run begins only when you choose Start.";
    private static final Map<String,String[]> TRAINING=new LinkedHashMap<>();
    static {
        TRAINING.put("vitality",new String[]{"Vitality","60 more maximum health per level.","Potion_Health_Lesser"});
        TRAINING.put("power",new String[]{"Weapon mastery","20% more damage per level.","Weapon_Sword_Iron"});
        TRAINING.put("dressing",new String[]{"Field dressing","Recover 5 health after each wave per level.","Ingredient_Fabric_Scrap_Linen"});
        TRAINING.put("second_wind",new String[]{"Second Wind","Survive one knockout per run with 30% health.","Potion_Health_Greater"});
        TRAINING.put("storage",new String[]{"Spoils racks","Double the room for each kind of spoils.","Furniture_Village_Crate"});
    }
    public HuntPage(PlayerRef player,UUID town,Ref<EntityStore> hunter) {super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Data.CODEC);townId=town;this.hunter=hunter;}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        fill(ref,c,e,store);
        // What the client receives whole. The timer is compared with it and sends only what moved.
        opened(c,e);
        if(!refreshing&&AetherhavenPlugin.get()!=null){refreshing=true;refresh(store.getExternalData().getWorld());}
    }
    /**
     * The whole page, every time. The page is not sent every time: {@link #sendChanges} keeps the cards the client
     * already has and sends only the values that differ, so a courier walking past costs nothing.
     */
    private void fill(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        if(!appended){c.append("Aetherhaven/HuntPage.ui");appended=true;}
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(town==null||!valid(ref,store,town)){
            for(String name:List.of("Run","Creatures","Training","Spoils"))c.set("#"+name+"Panel.Visible",name.equals("Run"));
            c.set("#Wave.Text","HUNTER UNAVAILABLE");c.set("#Status.Text","Speak to the Monster Hunter at the lodge.");
            c.set("#RunInfo.Text","");c.set("#RunDetails.Text","");c.set("#Hint.Text","Return to the Hunter to continue planning.");
            c.set("#Start.Disabled",true);return;
        }
        HuntService.holdForPlanning(townId,hunter,store);
        var state=town.getHunts();
        var inventory=InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING);
        long available=inventory==null?0:GoldCoinPayment.totalAvailable(town,inventory,town.playerCanSpendTreasuryGold(playerRef.getUuid()));
        String preparationIssue=state.run()==null?HuntService.preparationIssue(town,hunter,store):null;
        if(preparationIssue==null&&state.run()==null&&(inventory==null||available<TownHunts.ENTRY_GOLD))preparationIssue="You need "+TownHunts.ENTRY_GOLD+" gold in your inventory or treasury.";
        c.set("#Status.Text",preparationIssue==null?HuntService.status(store,townId):preparationIssue);c.set("#Hint.Text",preparationIssue==null?hint:preparationIssue);
        c.set("#Gold.Text",available+" available gold");
        c.set("#Record.Text","BEST RUN   "+state.best()+(state.best()==1?" wave":" waves"));
        c.set("#Wave.Text",state.run()!=null?"WAVE "+(state.defeated()+1):preparationIssue==null?"READY FOR A RUN":"RUN NOT READY");
        c.set("#RunInfo.Text",state.run()==null?"Last run: "+state.lastResult().replace(" Ready for another run.","")
            :state.defeated()+(state.defeated()==1?" creature defeated. Collected spoils are safe.":" creatures defeated. Collected spoils are safe."));
        var stats=store.getComponent(hunter,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
        var health=stats==null?null:stats.get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
        // Health is shown in twentieths. Every step shown is an update sent, and every update can cost the player a
        // press, so a fight does not send one for each scratch.
        double share=health==null?0:shownShare(health.get(),health.getMax());
        c.set("#Health.Text",health==null?"Health unavailable":Math.round(health.getMax()*share)+" / "+Math.round(health.getMax())+" HEALTH");
        var bar=new com.hypixel.hytale.server.core.ui.Anchor();
        bar.setLeft(com.hypixel.hytale.server.core.ui.Value.of(0));bar.setHeight(com.hypixel.hytale.server.core.ui.Value.of(10));
        bar.setWidth(com.hypixel.hytale.server.core.ui.Value.of((int)Math.round(226*share)));
        c.setObject("#HealthFill.Anchor",bar);
        c.set("#Stats.Text",(int)Math.round(state.damageMultiplier()*100)+"% weapon damage\n"+state.recoveryPerWave()+" health after each wave");
        c.set("#Lineup.Text",lineup(state));
        c.set("#Start.Text",state.run()==null?"Start run   "+TownHunts.ENTRY_GOLD+" gold":"Retire run");
        c.set("#Start.Disabled",preparationIssue!=null);
        {
        for(String name:List.of("Run","Creatures","Training","Spoils")) {
            c.set("#"+name+"Panel.Visible",tab.equals(name));e.addEventBinding(CustomUIEventBindingType.Activating,"#Tab"+name,event("Tab",name),false);
        }
        c.set("#RunDetails.Text","One portal. One monster at a time.\n\nThe creatures ticked on the Creatures tab take turns entering the arena. Each wave gains 15% health and 15% damage.\n\nThe Hunter collects spoils between fights. Defeat closes the portal and immediately restores him at the arena. There is no resurrection fee.\n\nRuns cost "+TownHunts.ENTRY_GOLD+" gold from inventory or treasury to start. No automatic restarts.");
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
            boolean unlocked=state.unlocked(family.id()),in=unlocked&&state.chosen(family.id()),last=in&&state.chosen().size()==1;
            c.set(p+" #LockedShade.Visible",!unlocked);c.set(p+" #Lock.Visible",!unlocked);
            c.set(p+" #Detail.Text",!unlocked?family.unlockGold()+" gold + "+family.unlockItems()+" "+itemName(family.icon())
                :last?"The only creature in the arena. It has to stay in.":in?"Takes its turn in the arena.":"Sitting out. Tick the box to send it in.");
            // Locked: the Unlock button. Unlocked: the box that puts the creature in or out of runs.
            c.set(p+" #Choose.Visible",!unlocked);c.set(p+" #Choose.Text","Unlock");c.set(p+" #Choose.Disabled",unlocked||state.run()!=null);
            c.set(p+" #Choose.TooltipText","Carry the listed drops and pay with gold from your inventory or treasury to unlock this creature.");
            c.set(p+" #Pick.Visible",unlocked);c.set(p+" #Hunt.Value",in);c.set(p+" #Hunt.Disabled",state.run()!=null||last);
            c.set(p+" #PickLabel.Text",in?"In the hunt":"Sitting out");
            e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Unlock",family.id()),false);
            if(unlocked)e.addEventBinding(CustomUIEventBindingType.ValueChanged,p+" #Hunt",event("Pick",family.id()).append("@Checked",p+" #Hunt.Value"),false);
        }
        c.clear("#TrainingCards");index=0;
        for(var entry:TRAINING.entrySet()) {
            c.append("#TrainingCards","Aetherhaven/HuntTraining.ui");String p="#TrainingCards["+index+++"]",id=entry.getKey();var info=entry.getValue();
            c.set(p+" #Icon.ItemId",info[2]);c.set(p+" #Name.Text",info[0]+"   "+state.level(id)+" / "+TownHunts.maxLevel(id));
            c.set(p+" #Description.Text",info[1]);c.set(p+" #Choose.Text",state.level(id)>=TownHunts.maxLevel(id)?"Mastered":state.upgradeCost(id)+" gold");
            // The press names the level it buys, so one offer is never bought twice and never at another price.
            c.set(p+" #Choose.Disabled",state.run()!=null||state.level(id)>=TownHunts.maxLevel(id));e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Upgrade",id).append("Tier",Integer.toString(state.level(id)+1)),false);
        }
        c.clear("#SpoilsCards");index=0;
        for(var entry:state.stock().entrySet()) {
            c.append("#SpoilsCards","Aetherhaven/HuntTraining.ui");String p="#SpoilsCards["+index+++"]";
            long spoilsAvailable=com.hexvane.aetherhaven.warehouse.ProductionStockSources.availableSpoils(town,plugin,entry.getKey());
            long reserved=entry.getValue()-spoilsAvailable;
            c.set(p+" #Icon.ItemId",entry.getKey());c.set(p+" #Name.Text",itemName(entry.getKey()));
            c.set(p+" #Description.Text",entry.getValue()+" / "+state.capacity()+" stored at the lodge"+(reserved>0?"\n"+reserved+" reserved for workplaces":""));
            c.set(p+" #Choose.Text","Collect");c.set(p+" #Choose.Disabled",spoilsAvailable==0||!town.playerCanOpenContainers(playerRef.getUuid()));
            e.addEventBinding(CustomUIEventBindingType.Activating,p+" #Choose",event("Collect",entry.getKey()),false);
        }
        c.set("#SpoilsEmpty.Visible",state.stock().isEmpty());
        // The button says Start or Retire, and the press carries which, so a run that ended a moment ago is never
        // started again, and paid for, by a press meant to retire it.
        e.addEventBinding(CustomUIEventBindingType.Activating,"#Start",event(state.run()==null?"Start":"Retire",""),false);
        }
        int card=0;
        var displayed=HuntFamily.all();
        for(var family:displayed.subList(creaturePage*CREATURES_PER_PAGE,Math.min(displayed.size(),(creaturePage+1)*CREATURES_PER_PAGE))) {
            c.set("#CreatureCards["+card+++ "] #Choose.Disabled",state.unlocked(family.id())||state.run()!=null
                || inventory==null || available<family.unlockGold() || InventoryMaterials.count(inventory,family.icon())<family.unlockItems());
        }
        card=0;
        for(String id:TRAINING.keySet())c.set("#TrainingCards["+card+++ "] #Choose.Disabled",state.run()!=null
            ||state.level(id)>=TownHunts.maxLevel(id)||inventory==null||available<state.upgradeCost(id));
    }

    /** One line for the Run tab saying which unlocked creatures the next run will use. */
    static String lineup(TownHunts state) {
        var unlocked=state.unlocked().stream().filter(id->HuntFamily.get(id)!=null).toList();var chosen=state.chosen();
        if(unlocked.isEmpty())return "In the arena: nothing yet. Unlock a creature on the Creatures tab.";
        if(chosen.size()==unlocked.size())return unlocked.size()==1?"In the arena: "+HuntFamily.get(unlocked.get(0)).name()+".":"In the arena: all "+unlocked.size()+" unlocked creatures.";
        var out=unlocked.stream().filter(id->!chosen.contains(id)).toList();
        if(chosen.size()<=4)return "In the arena: "+names(chosen)+".";
        return "In the arena: "+chosen.size()+" of "+unlocked.size()+" unlocked creatures."+(out.size()<=4?" Sitting out: "+names(out)+".":"");
    }
    private static String names(List<String> ids) {return String.join(", ",ids.stream().map(id->HuntFamily.get(id).name()).toList());}
    private String itemName(String id) {
        var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(id);
        var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String name=item==null||i18n==null?null:AetherhavenLanguage.message(playerRef,item.getTranslationKey());
        return name==null?id:name;
    }
    private EventData event(String action,String key){return new EventData().append("Action",action).append("Key",key);}
    /** The share of health the page shows: the nearest twentieth, but never empty while alive nor full while hurt. */
    static double shownShare(double health,double max) {
        if(!(max>0)||!(health>0))return 0;
        if(health>=max)return 1;
        return Math.clamp(Math.round(20*health/max)/20.0,.05,.95);
    }
    private void refresh(com.hypixel.hytale.server.core.universe.world.World world) {
        AetherhavenPlugin.get().scheduleOnWorld(world,()->{
            if(isDismissed())return;var ref=playerRef.getReference();if(ref==null||!ref.isValid()||ref.getStore().getExternalData().getWorld()!=world)return;
            var store=ref.getStore();var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(player==null||player.getPageManager().getCustomPage()!=this)return;
            // Built every second, which also keeps the Hunter waiting at the lodge while the page is open. Sent only
            // when it differs from what the player already sees.
            var c=new UICommandBuilder();var e=new UIEventBuilder();fill(ref,c,e,store);sendChanges(c,e);refresh(world);
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
            creaturePage=Math.clamp(creaturePage+("CreatureNext".equals(data.action)?1:-1),0,lastPage);
        }
        // A wave won or a courier collecting spoils is no reason to refuse a press. Each one below is judged by
        // what is true now and refuses only what truly cannot be done.
        else if("Collect".equals(data.action)&&!town.playerCanOpenContainers(playerRef.getUuid()))hint="You need permission to collect town supplies.";
        else if("Collect".equals(data.action)) {
            var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            int count=(int)com.hexvane.aetherhaven.warehouse.ProductionStockSources.availableSpoils(town,plugin,data.key);
            if(count==0)hint=state.amount(data.key)==0?"No "+itemName(data.key)+" is left at the lodge.":"Every "+itemName(data.key)+" at the lodge is set aside for a workplace delivery.";var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(data.key);
            if(inv!=null&&item!=null&&count>0) {
                count=Math.min(count,Math.max(1,item.getMaxStack()));
                if(inv.addItemStack(new ItemStack(data.key,count),true,false,true).succeeded()){state.withdraw(data.key,count);manager.updateTown(town);hint="Spoils collected.";}else hint="Your inventory has no room for "+count+" "+itemName(data.key)+". Nothing was taken.";
            }
        } else if("Retire".equals(data.action)) {
            if(state.run()!=null)HuntService.retire(town,store);else hint="That run has already ended. Nothing was spent.";
        } else if("Start".equals(data.action)) {
            if(state.run()!=null)hint="A run is already under way.";
            else {var error=HuntService.start(town,hunter,store,playerRef.getUuid(),InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING));hint=error==null?"Run started. Watch from outside the arena floor.":error;}
        } else if(state.run()!=null)hint="Finish this run before changing creatures or training.";
        else if("Unlock".equals(data.action)) {
            String error=HuntPurchases.unlock(town,playerRef.getUuid(),InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING),data.key);
            if(error==null){manager.updateTown(town);hint="Creature unlocked for future runs.";}else hint=error;
        } else if("Upgrade".equals(data.action)&&TRAINING.containsKey(data.key)) {
            String error=tierIssue(state.level(data.key),data.tier,TRAINING.get(data.key)[0]);
            if(error==null)error=HuntPurchases.upgrade(town,playerRef.getUuid(),InventoryComponent.getCombined(store,ref,InventoryComponent.EVERYTHING),data.key);
            if(error==null){manager.updateTown(town);hint="Training complete. Your next run will use the improvement.";}else hint=error;
        } else if("Pick".equals(data.action)) {
            var family=HuntFamily.get(HuntFamily.activeId(data.key));
            // The box reports its new value. Without one, a click means the opposite of what is saved.
            boolean want=family!=null&&(data.checked!=null?data.checked:!state.chosen(family.id()));
            if(family!=null&&state.choose(family.id(),want)) {
                manager.updateTown(town);hint=family.name()+(want?" will take its turn in the arena.":" will sit out until you tick it again.");
            } else if(family!=null&&state.unlocked(family.id())&&state.chosen(family.id())!=want) {
                // The box on screen no longer matches what is saved. Say why and draw it again.
                hint="At least one creature has to stay in the arena.";
            }
        }

        var c=new UICommandBuilder();var e=new UIEventBuilder();fill(ref,c,e,store);sendUpdate(c,e,false);
    }
    /** Why the training level a press named cannot be bought, or null when it is the next one. */
    public static String tierIssue(int owned,String named,String training) {
        int tier;
        try{tier=Integer.parseInt(named);}catch(NumberFormatException|NullPointerException unreadable){return "Choose the training again.";}
        if(owned>=tier)return training+" level "+tier+" is already trained. Nothing was spent.";
        return owned+1==tier?null:"Train "+training+" level "+(owned+1)+" first.";
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Key",Codec.STRING),(d,v)->d.key=v,d->d.key).add()
            .append(new KeyedCodec<>("Tier",Codec.STRING),(d,v)->d.tier=v,d->d.tier).add()
            .append(new KeyedCodec<>("@Checked",Codec.BOOLEAN),(d,v)->d.checked=v,d->d.checked).add().build();
        String action,key,tier;Boolean checked;
    }
}
