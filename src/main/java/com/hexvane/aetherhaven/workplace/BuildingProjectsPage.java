package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3i;

/** Project cards refer to existing paid assembly jobs. Opening this board never starts a construction. */
public final class BuildingProjectsPage extends AetherhavenInteractiveCustomUIPage<BuildingProjectsPage.Data> {
    private final UUID townId,siteId;private final Vector3i station;
    private boolean appended;private WorkplaceArea openedArea;private long revision;private String hint;
    private final Map<String,Runnable> actions=new HashMap<>();
    private record Context(TownManager manager,TownRecord town,ApprenticeWorkplace site) {}
    public BuildingProjectsPage(PlayerRef player,UUID town,UUID site,Vector3i station){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;this.station=station==null?null:new Vector3i(station);}
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var world=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);var town=manager.getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid()))return null;
        var site=town.getApprenticeWorkplaces().find(siteId);var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(site==null||site.type()!=TownsfolkWorkType.BUILDING||transform==null)return null;
        var p=transform.getPosition();if(!TownTerritoryClaims.contains(town,com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        if(openedArea==null)openedArea=site.area();if(!openedArea.equals(site.area()))return null;
        if(BuildingProjectCleanup.reconcile(town))manager.updateTown(town);
        return new Context(manager,town,site);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        if(!appended){b.append("Aetherhaven/BuildingProjectsPage.ui");appended=true;for(String action:List.of("Pause","Refresh","Station"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);}
        var world=store.getExternalData().getWorld();var plan=c.site().building();revision=plan.revision();actions.clear();
        b.set("#TitleText.Text",c.site().name()+" | PROJECT BOARD");b.set("#Count.Text",plan.list().size()+" / 32 projects");
        b.set("#Contribution.Text",plan.contributions()+" pieces placed by this yard");
        b.set("#Pause.Text",plan.paused()?"Resume yard":"Pause yard");b.set("#Station.Visible",station!=null);
        var equipment=WorkplaceEquipment.scan(world,c.site().area());
        b.set("#Status.Text",hint!=null?hint:plan.paused()?"This yard is paused. Ordinary construction continues.":Objects.requireNonNullElse(equipment.requirement(WorkplaceEquipment.BUILDERS_BENCH,1),"Builder's Workbench ready. Enable Building for an Apprentice in the journal. Keep paths and scaffolding accessible."));
        b.clear("#Queue");b.clear("#Available");int queued=0,available=0;
        for(var order:plan.list()) {
            var plot=c.town().findPlotById(order.plot());String path="#Queue["+(queued++)+"]";b.append("#Queue","Aetherhaven/BuildingProjectCard.ui");
            draw(b,path,plot,queued,store);String issue=NativeBuildingWork.issue(world,c.town(),order);
            b.set(path+" #Status.Text",Objects.requireNonNullElse(issue,"Ready for an Apprentice"));b.set(path+" #Add.Visible",false);
            bind(e,path+" #Earlier","Earlier",order.id(),()->plan.move(revision,order.id(),-1));
            bind(e,path+" #Later","Later",order.id(),()->plan.move(revision,order.id(),1));
            bind(e,path+" #Remove","Remove",order.id(),()->plan.remove(revision,order.id()));
            b.set(path+" #Earlier.Disabled",queued==1);b.set(path+" #Later.Disabled",queued==plan.list().size());
        }
        for(var plot:c.town().getPlotInstances()) {
            if(plot.getState()!=PlotInstanceState.ASSEMBLING||plan.list().stream().anyMatch(o->o.plot().equals(plot.getPlotId())))continue;
            String path="#Available["+(available++)+"]";b.append("#Available","Aetherhaven/BuildingProjectCard.ui");draw(b,path,plot,0,store);
            b.set(path+" #Status.Text","Construction already started");b.set(path+" #Ordering.Visible",false);b.set(path+" #Add.Disabled",plan.list().size()>=32);
            bind(e,path+" #Add","Add",plot.getPlotId(),()->{
                if(plot.getState()!=PlotInstanceState.ASSEMBLING||c.town().findPlotById(plot.getPlotId())!=plot)hint="This construction project changed. Review it again.";
                else hint=plan.add(revision,plot.getPlotId(),playerRef.getUuid());
            });
        }
        b.set("#QueueEmpty.Visible",queued==0);b.set("#AvailableEmpty.Visible",available==0);
    }
    private void draw(UICommandBuilder b,String path,PlotInstance plot,int position,Store<EntityStore> store) {
        var definition=plot==null?null:AetherhavenPlugin.get().getConstructionCatalog().get(plot.getConstructionId());
        String name=definition==null?"Unavailable project":definition.getDisplayName();
        b.set(path+" #Name.Text",(position==0?"":position+". ")+Objects.requireNonNullElse(name,"Construction project"));
        String item=definition==null?null:definition.getPlotTokenItemId();b.set(path+" #Icon.Visible",item!=null);if(item!=null)b.set(path+" #Icon.ItemId",item);
        var job=plot==null?null:AssemblyWorldRegistry.get(store.getExternalData().getWorld(),plot.getPlotId());
        int count=plot==null?0:plot.getAssemblyPlacedBlockCount(),total=job==null?0:job.pendingBlocks().size();
        double fraction=plot!=null&&plot.getState()==PlotInstanceState.COMPLETE?1:total==0?0:Math.clamp((double)count/total,0,1);
        b.set(path+" #Fill.FlexWeight",Math.max(1,(int)(fraction*1000)));b.set(path+" #Remainder.FlexWeight",Math.max(1,(int)((1-fraction)*1000)));
        b.set(path+" #Fill.Visible",fraction>0);b.set(path+" #Remainder.Visible",fraction<1);
        b.set(path+" #Progress.Text",plot==null?"Project removed":plot.getState()==PlotInstanceState.COMPLETE?"Complete":total==0?"Visit the building site":count+" / "+total+" pieces");
    }
    private void bind(UIEventBuilder e,String selector,String action,UUID id,Runnable change) {
        String token=UUID.randomUUID().toString();actions.put(token,change);e.addEventBinding(CustomUIEventBindingType.Activating,selector,EventData.of("Action",token),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}var plan=c.site().building();hint=null;
        if("Station".equals(data.action)&&station!=null){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplaceStationPage(playerRef,townId,siteId,station));return;}
        if("Refresh".equals(data.action)){}
        else if(revision!=plan.revision())hint="The project queue changed. Review it again.";
        else if("Pause".equals(data.action)){plan.pause(revision,!plan.paused());c.manager().updateTown(c.town());}
        else {var action=actions.get(data.action);if(action==null)return;action.run();c.manager().updateTown(c.town());}
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    public static final class Data {public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new).append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();String action;}
}
