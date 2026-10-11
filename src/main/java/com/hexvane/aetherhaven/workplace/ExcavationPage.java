package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3i;

/** Concrete terrain preview with explicit excavation consent. Reopening never starts or replaces an order. */
public final class ExcavationPage extends AetherhavenInteractiveCustomUIPage<ExcavationPage.Data> {
    private final UUID townId,siteId;private final Vector3i station;
    private boolean appended;private WorkplaceArea openedArea;private ExcavationPlan.Entrance entrance;
    private ExcavationPlan.Entrance openedEntrance;private ExcavationPlan.Layout openedLayout;
    private ExcavationPlan.Layout layout;
    private ExcavationFittings.Mode fittings;
    private NativeExcavation.Review reviewed;private long revision;private String hint;
    private record Context(TownManager manager,TownRecord town,ApprenticeWorkplace site) {}
    public ExcavationPage(PlayerRef player,UUID town,UUID site,Vector3i station){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;this.station=station==null?null:new Vector3i(station);}
    public ExcavationPage(PlayerRef player,UUID town,UUID site,Vector3i station,ExcavationPlan.Entrance entrance,ExcavationPlan.Layout layout){this(player,town,site,station);this.entrance=entrance;this.layout=layout;}
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);var town=manager.getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid())||!town.playerCanBreakBlocks(playerRef.getUuid()))return null;
        var site=town.getApprenticeWorkplaces().find(siteId);var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(site==null||site.type()!=TownsfolkWorkType.MINING||transform==null)return null;
        var p=transform.getPosition();if(!TownTerritoryClaims.contains(town,com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        if(openedArea==null){openedArea=site.area();openedEntrance=site.mineshaftEntrance();openedLayout=site.mineshaftLayout();}
        if(!openedArea.equals(site.area())||openedEntrance!=site.mineshaftEntrance()||openedLayout!=site.mineshaftLayout())return null;
        if(entrance==null)entrance=site.mineshaftEntrance();if(layout==null)layout=site.mineshaftLayout();if(fittings==null)fittings=site.excavation().fittingsMode();return new Context(manager,town,site);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        if(!appended){b.append("Aetherhaven/ExcavationPage.ui");appended=true;for(String action:List.of("Entrance","Layout","Fittings","Start","Pause","Refresh","Station","SetStart"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);}
        var world=store.getExternalData().getWorld();var plan=c.site().excavation();revision=plan.revision();reviewed=NativeExcavation.review(world,c.town(),c.site(),entrance,layout);
        var mine=NativeExcavation.mine(c.town(),AetherhavenPlugin.get());
        b.set("#SiteName.Text",c.site().name());b.set("#Entrance.Text","Direction and length");
        b.set("#Layout.Text",layout.label);b.set("#LayoutDescription.Text","Digging "+MineshaftSelection.direction(entrance).name().toLowerCase(Locale.ROOT)+". "+layout.description+" Keep the approach clear. This tunnel is the Mining workplace. No bench is needed.");
        b.set("#Blocks.Text",Integer.toString(reviewed.cells().size()));b.set("#Skill.Text","Mining skill "+reviewed.cells().stream().mapToInt(ExcavationPlan.Cell::skill).max().orElse(1)+" for the hardest block");
        b.set("#Progress.Text",plan.id()==null?"NO ACTIVE TUNNEL":plan.completed()+" / "+plan.total()+" blocks"+(plan.fittingsTotal()>0?" | Fittings "+plan.fitted()+" / "+plan.fittingsTotal():" excavated"));
        var plannedFittings=ExcavationFittings.plan(c.site().area(),entrance,layout,fittings);
        long beams=plannedFittings.stream().filter(f->ExcavationFittings.BEAM.equals(f.item())).count(),torches=plannedFittings.size()-beams;
        b.set("#Fittings.Text",fittings.label);
        b.set("#FittingsCost.Text",fittings==ExcavationFittings.Mode.NONE?"Optional supports and lights are added by the worker after digging.":beams+" Hardwood Beams, "+torches+" Crude Torches from town storage. Used only as each piece is placed.");
        b.set("#Cargo.Text",plan.cargo().isEmpty()?"Drops are stored at this workplace. Collect them here or through the warehouse.":plan.cargo().values().stream().mapToInt(Integer::intValue).sum()+" items being carried to workplace storage.");
        b.set("#Start.Text",plan.id()==null?"Start reviewed tunnel":"Replace with this review");b.set("#Start.Disabled",!reviewed.ready()||plan.held()||!canFit(c.town()));
        b.set("#Pause.Text",plan.paused()?"Resume saved tunnel":"Pause saved tunnel");b.set("#Pause.Disabled",plan.id()==null||plan.edited()||plan.next()==null&&plan.nextFitting()==null);
        b.set("#Station.Visible",false); // Bench tiers do not affect excavation.
        b.set("#Status.Text",hint!=null?hint:!canFit(c.town())?"Fittings need building and town storage permission.":plan.id()!=null?workerStatus(world,c):reviewed.issue()!=null?reviewed.issue():"Review the rock, then choose Start reviewed tunnel. Saving its shape alone does not start digging.");
        b.clear("#Map");var points=ExcavationPlan.tunnel(c.site().area(),entrance,layout);int index=0;
        var columns=new LinkedHashMap<String,com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point>();for(var point:points)columns.putIfAbsent(point.x()+"/"+point.z(),point);
        double scale=Math.min(350.0/Math.max(1,c.site().area().maxX()-c.site().area().minX()),140.0/Math.max(1,c.site().area().maxZ()-c.site().area().minZ()));
        for(var point:columns.values()) {
            b.append("#Map","Aetherhaven/ExcavationCell.ui");String path="#Map["+(index++)+"]";var anchor=new Anchor();
            anchor.setLeft(Value.of(16+(int)((point.x()-c.site().area().minX())*scale)));anchor.setTop(Value.of(16+(int)((point.z()-c.site().area().minZ())*scale)));
            anchor.setWidth(Value.of(Math.max(3,(int)scale-1)));anchor.setHeight(Value.of(Math.max(3,(int)scale-1)));b.setObject(path+".Anchor",anchor);
            var cell=reviewed.cells().stream().filter(v->v.point().x()==point.x()&&v.point().z()==point.z()).findFirst().orElse(null);
            b.set(path+" #Rock.Background",cell==null?"#56756e":"#c6ac71");
            int floor=ExcavationPlan.floorAt(c.site().area(),entrance,layout,point);
            b.set(path+".TextTooltipStyle",Value.ref("Common.ui","DefaultTextTooltipStyle"));b.set(path+".TooltipText",point.x()+", "+point.z()+" | Walking height "+floor+(cell==null?" | Clear":" | Rock to excavate"));
        }
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}var plan=c.site().excavation();var world=store.getExternalData().getWorld();
        if("Entrance".equals(data.action)||"Layout".equals(data.action)){
            com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point ground;
            try{ground=MineshaftSelection.ground(c.site().area(),entrance,layout);}
            catch(IllegalArgumentException invalidLegacyArea){
                var area=c.site().area();
                ground=new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(area.minX()+(area.maxX()-area.minX()-1)/2,Math.max(0,area.floorY()-1),area.minZ()+(area.maxZ()-area.minZ()-1)/2);
            }
            store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,
                new MineshaftSetupPage(playerRef,townId,siteId,new Vector3i(ground.x(),ground.y(),ground.z())));return;
        }
        else if("SetStart".equals(data.action)){
            var hand=com.hypixel.hytale.server.core.inventory.InventoryComponent.getItemInHand(store,ref);
            if(hand==null||!WorkplaceToolInteraction.isTool(hand.getItemId()))hint="Hold a Building Staff to choose new entrance ground.";
            else{var state=WorkplaceToolState.obtain(ref,store);state.choose(WorkplaceToolState.Mode.MINESHAFT,ref,store);state.editing=siteId;state.townId=townId;state.hint="Choose the entrance ground for "+c.site().name()+". Then set its direction and length.";close();return;}
        }
        else if("Fittings".equals(data.action)){fittings=ExcavationFittings.Mode.values()[(fittings.ordinal()+1)%ExcavationFittings.Mode.values().length];hint=null;}
        else if("Refresh".equals(data.action))hint=null;
        else if("Pause".equals(data.action)){if(plan.pause(revision,!plan.paused())){c.manager().updateTown(c.town());hint=plan.paused()?"Excavation paused. Collected material can still be delivered.":"Saved tunnel resumed.";}else hint="The tunnel changed. Review it again.";}
        else if("Station".equals(data.action)&&station!=null){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplaceStationPage(playerRef,townId,siteId,station));return;}
        else if("Start".equals(data.action)) {
            var fresh=NativeExcavation.review(world,c.town(),c.site(),entrance,layout);var mine=NativeExcavation.mine(c.town(),AetherhavenPlugin.get());
            if(reviewed==null||!fresh.ready()||!fresh.cells().equals(reviewed.cells()))hint="The rock changed. Review it again before starting.";
            else if(!canFit(c.town()))hint="Fittings need building and town storage permission.";
            else {hint=plan.start(revision,playerRef.getUuid(),c.town().getApprenticeWorkplaces().mineBase(c.site()).id(),c.site().area(),entrance,layout,fresh.cells(),fittings);if(hint==null){c.manager().updateTown(c.town());hint="Tunnel planned. Enable Mining for an Apprentice in the town journal.";}}
        }else return;
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    private boolean canFit(TownRecord town){return fittings==ExcavationFittings.Mode.NONE||town.playerCanPlaceBlocks(playerRef.getUuid())&&town.playerCanOpenContainers(playerRef.getUuid());}
    private String workerStatus(com.hypixel.hytale.server.core.universe.world.World world,Context context){
        int skill=context.town().getTownsfolkProgress().entrySet().stream()
            .filter(e->e.getValue().canPerform(TownsfolkWorkType.MINING,false,context.town().getPayroll().overdue(e.getKey()),true))
            .mapToInt(e->e.getValue().skill(TownsfolkWorkType.MINING.skill)).max().orElse(0);
        if(skill==0)return "Employ a townsfolk with Mining skill and enable Mining in work priorities. Keep their wages paid.";
        return Objects.requireNonNullElse(NativeExcavation.workIssue(world,context.town(),context.site(),AetherhavenPlugin.get(),skill),"Ready for a Mining worker. Keep the entrance and route to workplace storage clear.");
    }
    public static final class Data {public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new).append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();String action;}
}
