package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class FoodSitePage extends AetherhavenInteractiveCustomUIPage<FoodSitePage.Data> {
    private final UUID townId, siteId;
    private final org.joml.Vector3i station;
    private boolean appended, refreshing;
    private View rendered;
    private record View(String name,int points,String state,long remaining,String time,String lastCare,
                        String habitat,String worker,boolean readOnly) {}
    public FoodSitePage(PlayerRef player, UUID town, UUID site) {
        this(player,town,site,null);
    }
    public FoodSitePage(PlayerRef player, UUID town, UUID site,org.joml.Vector3i station) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;
        this.station=station==null?null:new org.joml.Vector3i(station);
    }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        // What the client receives whole. The refresh below is compared with it and sends only what moved.
        if(fill(ref,c,e,store))opened(c,e);
    }
    /** How many game minutes one step of the care clock is. A clock that moved every minute would send an update every minute. */
    static final long CARE_STEP_MINUTES=10;
    /** The care time the page shows: whole steps left, never less than one step while any care is left. */
    static long shownCare(long minutes) {
        if(minutes<=0)return 0;
        return Math.max(CARE_STEP_MINUTES,minutes-minutes%CARE_STEP_MINUTES);
    }
    /** Draws what changed since the last draw. False when the page had to close instead. */
    private boolean fill(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return false;
        var world=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())){close();return false;}
        var site=town.getApprenticeWorkplaces().find(siteId);
        if(site==null||!WorkplaceFood.supports(site.type())){close();return false;}
        if(!appended){
            c.append("Aetherhaven/FoodSitePage.ui");appended=true;
            c.set("#Station.Visible",station!=null);
            // Using this bench opens this page, so its own crafting screen is offered here to those who manage the site.
            String ownScreen=town.playerCanManageConstructions(playerRef.getUuid())?WorkplaceNativeBench.label(world,station):null;
            c.set("#Bench.Visible",ownScreen!=null);
            if(ownScreen!=null)c.set("#Bench.Text",ownScreen);
            for(String action:new String[]{"Back","Refresh","Station","Bench"})e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        var habitat=FoodSiteService.inspect(world,site);var food=site.food();
        int points=habitat.simulated()&&!habitat.ready()?0:food.foodPoints();
        long remaining=shownCare(food.minutesUntilNeglected());
        long workers=TownsfolkWorkRoster.list(town,manager,store,plugin).stream()
            .filter(w->w.progress().canPerform(site.type(),w.guard(),town.getPayroll().overdue(w.characterId()),w.alive())).count();
        var view=new View(site.name(),points,
            food.completedVisits()==0?"Awaiting first visit":points==0?"Food support paused":food.needsTending()?"Ready for tending":"Well tended",
            remaining,food.completedVisits()==0?"Care begins when an Apprentice works here.":"About "+remaining/60+"h "+remaining%60+"m of care remaining",
            food.lastCare(),!habitat.ready()?habitat.issue():food.careIssue()!=null?food.careIssue():
                site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.RANCHING?RanchWorkService.herdStatus(world,site):"The workplace has the equipment and growing area it needs.",
            workers==0?"No available Apprentice has this profession enabled.":workers+" eligible "+(workers==1?"Apprentice":"Apprentices")+" | "+food.completedVisits()+" tending "+(food.completedVisits()==1?"visit":"visits")+" completed",
            !town.playerCanManageConstructions(playerRef.getUuid()));
        if(!view.equals(rendered)) {
            c.set("#FoodTitle.Text",view.name());c.set("#FoodPoints.Text",view.points()+" / 10");
            c.set("#Residents.Text","Supports "+view.points()/TownPopulation.FOOD_PER_RESIDENT+" residents");
            c.set("#CareState.Text",view.state());c.set("#CareReserve.Value",(float)view.remaining()/WorkplaceFood.NEGLECT_MINUTES);
            c.set("#CareTime.Text",view.time());c.set("#LastCare.Text",view.lastCare());
            c.set("#Habitat.Text",view.habitat());c.set("#Worker.Text",view.worker());c.set("#Back.Disabled",view.readOnly());
            rendered=view;
        }
        if(!refreshing){refreshing=true;scheduleRefresh(world);}
        return true;
    }
    private void scheduleRefresh(com.hypixel.hytale.server.core.universe.world.World world) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        plugin.scheduleOnWorld(world,()->{
            if(isDismissed())return;
            var ref=playerRef.getReference();
            if(ref==null||!ref.isValid()||ref.getStore().getExternalData().getWorld()!=world)return;
            var store=ref.getStore();var player=store.getComponent(ref,Player.getComponentType());
            if(player==null||player.getPageManager().getCustomPage()!=this)return;
            // Looked at once a second; only the values that differ from what the player sees are sent.
            var c=new UICommandBuilder();var e=new UIEventBuilder();
            if(fill(ref,c,e,store))sendChanges(c,e);
            if(!isDismissed())scheduleRefresh(world);
        },1000);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())){close();return;}
        var site=town.getApprenticeWorkplaces().find(siteId);if(site==null){close();return;}
        if("Station".equals(data.action)&&station!=null&&town.playerCanManageConstructions(playerRef.getUuid())) {
            store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,
                new WorkplaceStationPage(playerRef,townId,siteId,station));return;
        }
        if("Bench".equals(data.action)&&station!=null&&town.playerCanManageConstructions(playerRef.getUuid())) {
            var opened=WorkplaceNativeBench.open(ref,store,station);
            if(opened==WorkplaceNativeBench.Outcome.OPENED||isDismissed())return;
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(opened.message()));
        }
        if("Back".equals(data.action)&&town.playerCanManageConstructions(playerRef.getUuid())){
            store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplacePage(playerRef,townId,siteId,site.area()));return;
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();if(fill(ref,c,e,store))sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();
        String action;
    }
}
