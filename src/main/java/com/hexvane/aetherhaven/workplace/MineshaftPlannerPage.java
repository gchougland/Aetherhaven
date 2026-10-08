package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** One base workplace owns a saved network. Map edits remain drafts until explicitly added. */
public final class MineshaftPlannerPage extends AetherhavenInteractiveCustomUIPage<MineshaftPlannerPage.Data> {
    private final UUID townId,siteId;
    private boolean appended,initialized,confirmRemoval,draftActive=true;
    private int centerX,centerZ,floor,x,z,startY,length=9,index;
    private long version,selectedRevision;
    private UUID selectedId;
    private MineshaftSelection.Direction direction=MineshaftSelection.Direction.NORTH;
    private ExcavationPlan.Layout layout=ExcavationPlan.Layout.STRAIGHT;
    private ExcavationFittings.Mode fittings=ExcavationFittings.Mode.NONE;
    private NativeExcavation.Review reviewed;
    private String hint="Place a Workbench inside the base workplace to reopen this planner. Your drafts do not change the world.";
    private record Context(TownManager manager,TownRecord town,ApprenticeWorkplace base){}
    public MineshaftPlannerPage(PlayerRef player,UUID town,UUID site){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;}
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store){
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);var town=manager.getTown(townId);
        if(town==null||!town.hasMemberOrOwner(playerRef.getUuid())||!town.playerCanManageConstructions(playerRef.getUuid())||!town.playerCanBreakBlocks(playerRef.getUuid()))return null;
        var sites=town.getApprenticeWorkplaces();var base=sites.mineBase(sites.find(siteId));var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(base==null||base.type()!=TownsfolkWorkType.MINING||transform==null)return null;
        var p=transform.getPosition();if(!TownTerritoryClaims.contains(town,com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),com.hypixel.hytale.math.util.ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        if(!initialized){home(base);var prefs=base.minePreferences();if(prefs!=null){if(prefs.direction()!=null)direction=prefs.direction();if(prefs.layout()!=null)layout=prefs.layout();length=Math.clamp(prefs.length(),minimumLength(),30);if(prefs.fittings()!=null)fittings=prefs.fittings();}initialized=true;}return new Context(manager,town,base);
    }
    private void home(ApprenticeWorkplace base){var a=base.area();centerX=x=a.minX()+(a.maxX()-a.minX())/2;centerZ=z=a.minZ()+(a.maxZ()-a.minZ())/2;floor=startY=a.floorY();}
    private ApprenticeWorkplace draft(){var site=new ApprenticeWorkplace("Tunnel",TownsfolkWorkType.MINING,MineshaftSelection.area(x,startY-1,z,direction,length,layout));site.setMineshaftConfiguration(MineshaftSelection.entrance(direction),layout);return site;}
    private UUID connection(Context c){return MineshaftNetwork.mapConnections(c.town().getApprenticeWorkplaces(),c.base()).get(new Point(x,startY,z));}
    private UUID prerequisite(Context c){var id=connection(c);return id!=null&&id.equals(c.base().id())&&(c.base().existingExcavation()==null||c.base().excavation().id()==null)?null:id;}
    private NativeExcavation.Review review(Context c,Store<EntityStore> store){
        if(!draftActive)return new NativeExcavation.Review(List.of(),"Select a base or passage cell to plan another tunnel.");
        if(connection(c)==null)return new NativeExcavation.Review(List.of(),"Choose a start on the blue base or a saved passage at this floor.");
        try{
            var draft=draft();var result=NativeExcavation.review(store.getExternalData().getWorld(),c.town(),draft,draft.mineshaftEntrance(),layout);
            var remaining=MineshaftNetwork.remaining(result.cells(),MineshaftNetwork.ancestorCells(c.town().getApprenticeWorkplaces(),prerequisite(c)));
            if(result.issue()!=null)return result;
            var protectedFloors=MineshaftNetwork.protectedFloors(c.town().getApprenticeWorkplaces(),c.base());
            if(remaining.stream().anyMatch(cell->protectedFloors.contains(cell.point())))return new NativeExcavation.Review(remaining,"This route would remove the floor of an existing passage. Choose a clear landing or another direction.");
            var occupied=new HashSet<Point>();
            for(var other:c.town().getApprenticeWorkplaces().tunnels(c.base().id()))occupied.addAll(ExcavationPlan.tunnel(other.area(),other.excavation().entrance(),other.excavation().layout()));
            if(remaining.stream().anyMatch(cell->occupied.contains(cell.point())))return new NativeExcavation.Review(remaining,"This branch crosses another planned tunnel. Start from that tunnel instead.");
            return new NativeExcavation.Review(remaining,remaining.isEmpty()?"This route has no new rock. Choose a longer route or another direction.":null);
        }catch(IllegalArgumentException invalid){return new NativeExcavation.Review(List.of(),invalid.getMessage());}
    }
    private void bind(UIEventBuilder e,String selector,String action,String key){e.addEventBinding(CustomUIEventBindingType.Activating,selector,new EventData().append("Action",action).append("Key",key).append("Revision",Long.toString(version)),false);}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store){
        var c=current(ref,store);if(c==null){close();return;}if(!appended){b.append("Aetherhaven/MineshaftPlannerPage.ui");appended=true;}version++;
        for(String action:List.of("Down","Up","North","East","South","West","Shorter","Longer","Straight","StairsUp","StairsDown","Beams","Torches","Lanterns","Rails","Room","Storage","Refresh","Add","Previous","Next","Pause","Remove","MapNorth","MapEast","MapSouth","MapWest","Home","Settings"))bind(e,"#"+action,action,"");
        b.set("#Name.Text",c.base().name());b.set("#Floor.Text","Height "+floor);b.set("#StartPoint.Text","Start: "+x+", "+startY+", "+z+" | Dig "+direction.name().toLowerCase(Locale.ROOT));
        b.set("#Length.Text",length+" blocks long");b.set("#Shape.Text",layout.label+": "+layout.description);b.set("#Beams.Text","Beams: "+(fittings.supports?"On":"Off"));b.set("#Torches.Text","Torches: "+(fittings.lights?"On":"Off"));b.set("#Lanterns.Text","Lanterns: "+(fittings.lanterns?"On":"Off"));
        b.set("#Rails.Text","Rails: "+(layout!=ExcavationPlan.Layout.ROOM&&fittings.rails?"On":"Off"));b.set("#Rails.Disabled",layout==ExcavationPlan.Layout.ROOM);
        b.set("#Shorter.Disabled",layout==ExcavationPlan.Layout.ROOM||length<=minimumLength());b.set("#Longer.Disabled",layout==ExcavationPlan.Layout.ROOM||length==30);b.set("#Down.Disabled",MineshaftNetwork.adjacentFloor(c.town().getApprenticeWorkplaces(),c.base(),floor,false)==floor);b.set("#Up.Disabled",MineshaftNetwork.adjacentFloor(c.town().getApprenticeWorkplaces(),c.base(),floor,true)==floor);
        reviewed=review(c,store);b.set("#Review.Text",reviewed.issue()!=null?reviewed.issue():reviewed.cells().size()+" blocks to excavate. Requires Mining "+reviewed.cells().stream().mapToInt(ExcavationPlan.Cell::skill).max().orElse(1)+". Adding authorizes permanent excavation by workers.");
        var mine=NativeExcavation.mine(c.town(),AetherhavenPlugin.get());b.set("#Add.Disabled",!reviewed.ready());
        var sites=c.town().getApprenticeWorkplaces();var tunnels=sites.tunnels(c.base().id());index=Math.clamp(index,0,Math.max(0,tunnels.size()-1));
        var selected=tunnels.isEmpty()?null:tunnels.get(index);selectedId=selected==null?null:selected.id();selectedRevision=selected==null?0:selected.excavation().revision();
        b.set("#Tunnel.Text",selected==null?"No saved tunnels yet":(index+1)+" / "+tunnels.size()+": "+selected.name()+" | "+selected.excavation().completed()+" / "+selected.excavation().total()+" blocks");
        b.set("#Previous.Disabled",index==0);b.set("#Next.Disabled",index+1>=tunnels.size());b.set("#Pause.Disabled",selected==null);b.set("#Remove.Disabled",selected==null||selected.id().equals(c.base().id()));
        b.set("#Pause.Text",selected!=null&&selected.excavation().paused()?"Resume tunnel":"Pause tunnel");b.set("#Remove.Text",confirmRemoval?"Confirm removal":"Remove tunnel");
        b.set("#Activity.Text",selected==null?"Enable Mining for an employed townsfolk in the journal. Keep wages paid and the workplace entrance clear.":Objects.requireNonNullElse(NativeExcavation.workIssue(store.getExternalData().getWorld(),c.town(),selected,AetherhavenPlugin.get(),bestSkill(c.town())),selected.excavation().cargoCount()>0?selected.excavation().cargoCount()+" items carried. "+(selected.excavation().loadReady()?"Returning to output storage.":"Filling this load before returning."):"Ready for a Mining worker."));
        var buffered=MineshaftOutput.find(c.town(),c.base().id()).getAmounts();
        b.set("#Buffer.Text","Stored output: "+buffered.values().stream().mapToLong(Long::longValue).sum()+" items | Hover for contents");
        b.set("#Buffer.TooltipText",buffered.isEmpty()?"Open output storage to collect items. Couriers collect supplies at this base. Miners carry 25 items at Mining 1, plus 10 per additional level.":buffered.entrySet().stream().map(entry->entry.getKey().replace('_',' ')+": "+entry.getValue()).collect(java.util.stream.Collectors.joining("\n")));
        var stairs=new HashMap<Point,Integer>();
        for(var tunnel:tunnels)MineshaftNetwork.stairs(tunnel.area(),tunnel.excavation().entrance(),tunnel.excavation().layout()).forEach((point,bits)->stairs.merge(point,bits,(left,right)->left|right));
        var floors=MineshaftNetwork.mapConnections(sites,c.base());var draftFloors=new HashSet<Point>();
        try{if(draftActive){var d=draft();MineshaftNetwork.stairs(d.area(),d.mineshaftEntrance(),layout).forEach((point,bits)->stairs.merge(point,bits,(left,right)->left|right));}}catch(IllegalArgumentException ignored){}
        try{var d=draft();if(draftActive)draftFloors.addAll(MineshaftNetwork.mapPoints(d.area(),d.mineshaftEntrance(),layout));}catch(IllegalArgumentException ignored){}
        b.clear("#Map");
        for(int row=0;row<21;row++)for(int col=0;col<21;col++){
            int px=centerX+(col-10)*3,pz=centerZ+(row-10)*3;var point=new Point(px,floor,pz);var connected=MineshaftNetwork.mapConnection(floors,point);var owner=connected==null?null:floors.get(connected);
            var start=MineshaftNetwork.mapStart(floors,point);Point connectionPoint=start==null?point:start.anchor();boolean draftHere=false;int stair=0;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                var sample=new Point(px+dx,floor,pz+dz);draftHere|=draftFloors.contains(sample);if(floors.containsKey(sample)||draftFloors.contains(sample))stair|=stairs.getOrDefault(sample,0);
            }
            b.append("#Map","Aetherhaven/MineshaftPlannerCell.ui");String path="#Map["+(row*21+col)+"]";
            var anchor=new Anchor();anchor.setLeft(Value.of(col*22));anchor.setTop(Value.of(row*22));anchor.setWidth(Value.of(21));anchor.setHeight(Value.of(21));b.setObject(path+".Anchor",anchor);
            String color=draftActive&&Math.abs((long)px-x)<=1&&Math.abs((long)pz-z)<=1&&floor==startY?"#ffffff":draftHere?"#d3b164":owner==null?"#273447":owner.equals(c.base().id())?"#58a7dc":"#79c49a";
            b.setObject(path+".Background",new PatchStyle(Value.of("Aetherhaven/MineshaftMapCell.png"),Value.of(3)).setColor(Value.of(color)));
            b.set(path+" #Stairs.Visible",stair!=0);
            if(stair!=0)b.set(path+" #Stairs.AssetPath","UI/Custom/Aetherhaven/MineshaftStairs"+(stair==1?"Up":stair==2?"Down":"Both")+".png");
            b.set(path+".TooltipText",(stair==0?"":stair==1?"Stairs up | ":stair==2?"Stairs down | ":"Stairs up and down | ")+px+", "+floor+", "+pz+(start==null?" | No connecting passage":owner==null?" | Extend the nearby passage":" | Start a branch here"));bind(e,path,"Cell",point.x()+","+point.z());
        }
        b.set("#Status.Text",hint);
    }
    private int minimumLength(){return layout==ExcavationPlan.Layout.DEEP_STAIRS_UP||layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN?9:3;}
    private int bestSkill(TownRecord town){return town.getTownsfolkProgress().entrySet().stream().filter(e->e.getValue().canPerform(TownsfolkWorkType.MINING,false,town.getPayroll().overdue(e.getKey()),true)).mapToInt(e->e.getValue().skill(TownsfolkWorkType.MINING.skill)).max().orElse(0);}
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data){
        var c=current(ref,store);if(c==null){close();return;}if(!Long.toString(version).equals(data.revision)||data.action==null)return;
        var sites=c.town().getApprenticeWorkplaces();var selected=sites.find(selectedId);
        if(!"Remove".equals(data.action))confirmRemoval=false;
        switch(data.action){
            case "Settings"->{store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplacePage(playerRef,townId,c.base().id(),c.base().area()));return;}
            case "Cell"->{try{String[] parts=data.key.split(",");int px=Integer.parseInt(parts[0]),pz=Integer.parseInt(parts[1]);if(Math.abs((long)px-centerX)>31||Math.abs((long)pz-centerZ)>31)return;var start=MineshaftNetwork.mapStart(MineshaftNetwork.mapConnections(sites,c.base()),new Point(px,floor,pz));if(start==null){draftActive=false;hint="Choose a passage or the cell just beyond its end.";break;}x=start.anchor().x();z=start.anchor().z();startY=start.anchor().y();if(start.direction()!=null)direction=start.direction();draftActive=true;var owner=connection(c);var list=sites.tunnels(c.base().id());for(int i=0;i<list.size();i++)if(list.get(i).id().equals(owner))index=i;hint="Choose direction and length, then add the reviewed tunnel.";}catch(Exception invalid){return;}}
            case "North","East","South","West"->direction=MineshaftSelection.Direction.valueOf(data.action.toUpperCase(Locale.ROOT));
            case "Shorter"->length=Math.max(minimumLength(),length-3);case "Longer"->length=Math.min(30,length+3);
            case "Straight"->layout=ExcavationPlan.Layout.STRAIGHT;
            case "StairsUp"->{layout=ExcavationPlan.Layout.DEEP_STAIRS_UP;length=Math.max(9,length);}
            case "StairsDown"->{layout=ExcavationPlan.Layout.DEEP_STAIRS_DOWN;length=Math.max(9,length);}
            case "Room"->{layout=ExcavationPlan.Layout.ROOM;length=9;}
            case "Storage"->{store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.warehouse.WarehousePage(playerRef,townId,c.base().id()));return;}
            case "Down","Up"->{floor=MineshaftNetwork.adjacentFloor(sites,c.base(),floor,"Up".equals(data.action));startY=floor;draftActive=false;hint="Select a passage or the cell beyond its end on this floor.";}
            case "MapNorth"->centerZ-=30;case "MapSouth"->centerZ+=30;case "MapEast"->centerX+=30;case "MapWest"->centerX-=30;case "Home"->{home(c.base());draftActive=false;}
            case "Beams","Torches","Lanterns","Rails"->fittings=ExcavationFittings.Mode.of(fittings.supports^"Beams".equals(data.action),fittings.lights^"Torches".equals(data.action),fittings.lanterns^"Lanterns".equals(data.action),fittings.rails^("Rails".equals(data.action)&&layout!=ExcavationPlan.Layout.ROOM));
            case "Refresh"->hint="Terrain reviewed. Existing orders are unchanged.";
            case "Previous","Next"->{var tunnels=sites.tunnels(c.base().id());index=Math.clamp(index+("Next".equals(data.action)?1:-1),0,Math.max(0,tunnels.size()-1));if(!tunnels.isEmpty()){var s=tunnels.get(index);var p=MineshaftSelection.ground(s.area(),s.excavation().entrance(),s.excavation().layout());centerX=p.x();centerZ=p.z();floor=startY=p.y()+1;draftActive=false;}}
            case "Pause"->{if(selected!=null){hint=selected.excavation().pause(selectedRevision,!selected.excavation().paused())?"Tunnel updated. Collected material can still be delivered.":"The tunnel changed. Review again.";c.manager().updateTown(c.town());}}
            case "Remove"->{if(selected!=null&&!selected.id().equals(c.base().id())){if(!confirmRemoval){confirmRemoval=true;hint="Remove this tunnel order? Excavated blocks remain removed. Connected branches must be removed first.";}else{String error=sites.remove(c.town(),playerRef.getUuid(),selected.id());hint=error==null?"Tunnel order removed. Its stored output remains at the mine base.":error;if(error==null){draftActive=false;if(connection(c)==null)home(c.base());}confirmRemoval=false;c.manager().updateTown(c.town());}}}
            case "Add"->{
                var fresh=review(c,store);var mine=NativeExcavation.mine(c.town(),AetherhavenPlugin.get());
                if(reviewed==null||!fresh.ready()||!fresh.cells().equals(reviewed.cells()))hint="The terrain changed. Review the map again.";

                else if(fittings!=ExcavationFittings.Mode.NONE&&(!c.town().playerCanPlaceBlocks(playerRef.getUuid())||!c.town().playerCanOpenContainers(playerRef.getUuid())))hint="Fittings require building and storage permission.";
                else{var draft=draft();draft.configure("Tunnel "+(sites.tunnels(c.base().id()).size()+1),TownsfolkWorkType.MINING,draft.area());
                    hint=draft.excavation().start(0,playerRef.getUuid(),c.base().id(),draft.area(),draft.mineshaftEntrance(),layout,fresh.cells(),fittings);
                    if(hint==null)hint=sites.addTunnel(c.town(),playerRef.getUuid(),c.base().id(),draft,prerequisite(c));
                    if(hint==null){draftActive=false;index=sites.tunnels(c.base().id()).size()-1;c.manager().updateTown(c.town());hint="Tunnel added. It will wait for its connecting passage. Click its end on the map to extend it.";}
                }
            }
            default->{return;}
        }
        c.base().minePreferences(new ApprenticeWorkplace.MinePreferences(direction,layout,length,fittings));
        c.manager().updateTown(c.town());
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    public static final class Data {public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
        .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
        .append(new KeyedCodec<>("Key",Codec.STRING),(d,v)->d.key=v,d->d.key).add()
        .append(new KeyedCodec<>("Revision",Codec.STRING),(d,v)->d.revision=v,d->d.revision).add().build();String action,key,revision;}
}
