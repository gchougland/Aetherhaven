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
    private long version;
    private UUID selectedId;
    private MineshaftSelection.Direction direction=MineshaftSelection.Direction.NORTH;
    private ExcavationPlan.Layout layout=ExcavationPlan.Layout.STRAIGHT;
    private ExcavationFittings.Mode fittings=ExcavationFittings.Mode.NONE;
    private NativeExcavation.Review reviewed;
    /** Set by the review when the draft would cut through another passage: the reason, the fix and the blocks. */
    private MineshaftNetwork.Advice advised;
    private String hint="Place a Workbench inside the base workplace to reopen this planner. Your drafts do not change the world.";
    private record Context(TownManager manager,TownRecord town,ApprenticeWorkplace base){}
    /** The Workbench the player used to open the planner, when they came that way. */
    private final org.joml.Vector3i station;
    public MineshaftPlannerPage(PlayerRef player,UUID town,UUID site){this(player,town,site,null);}
    public MineshaftPlannerPage(PlayerRef player,UUID town,UUID site,org.joml.Vector3i station){
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;siteId=site;this.station=station==null?null:new org.joml.Vector3i(station);
    }
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
        advised=null;
        if(!draftActive)return new NativeExcavation.Review(List.of(),"Select a base or passage cell to plan another tunnel.");
        if(connection(c)==null)return new NativeExcavation.Review(List.of(),"Choose a start on the blue base or a saved passage at this floor.");
        try{
            var sites=c.town().getApprenticeWorkplaces();
            var draft=draft();var overlap=sites.tunnelOverlapIssue(c.base().id(),draft);
            if(overlap!=null)return new NativeExcavation.Review(List.of(),overlap);
            var covered=MineshaftNetwork.coveredPassageIssue(sites,c.base(),draft.area(),draft.mineshaftEntrance(),layout);
            if(covered!=null)return advise(c,List.of(),covered);
            // Ground that belongs to passages already there is not this tunnel's to dig, so it is not judged either.
            var result=NativeExcavation.review(store.getExternalData().getWorld(),c.town(),draft,draft.mineshaftEntrance(),layout,MineshaftNetwork.leftAlone(sites,c.base(),prerequisite(c)));
            if(result.issue()!=null&&!NativeExcavation.NOTHING_LEFT.equals(result.issue()))return result;
            var route=MineshaftNetwork.route(sites,c.base(),prerequisite(c),draft.area(),draft.mineshaftEntrance(),layout,result.cells());
            if(route.issue()!=null&&route.issue().startsWith(MineshaftNetwork.CUT_FLOOR))return advise(c,route.cells(),route.issue());
            return new NativeExcavation.Review(route.cells(),route.issue());
        }catch(IllegalArgumentException invalid){return new NativeExcavation.Review(List.of(),invalid.getMessage());}
    }
    /** Says what is in the draft's way in blocks counted from its start, and the shortest change that clears it. */
    private NativeExcavation.Review advise(Context c,List<ExcavationPlan.Cell> cells,String refusal){
        advised=MineshaftNetwork.advice(c.town().getApprenticeWorkplaces(),c.base(),new Point(x,startY,z),direction,length,layout);
        return new NativeExcavation.Review(cells,advised==null?refusal:advised.message());
    }
    private void bind(UIEventBuilder e,String selector,String action,String key){e.addEventBinding(CustomUIEventBindingType.Activating,selector,new EventData().append("Action",action).append("Key",key).append("Revision",Long.toString(version)),false);}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store){
        var c=current(ref,store);if(c==null){close();return;}if(!appended){b.append("Aetherhaven/MineshaftPlannerPage.ui");appended=true;}version++;
        for(String action:List.of("Down","Up","North","East","South","West","Shorter","Longer","Straight","StairsUp","StairsDown","Beams","Torches","Lanterns","Rails","Room","Storage","Refresh","Add","Fix","Previous","Next","Pause","Remove","MapNorth","MapEast","MapSouth","MapWest","Home","Settings","Bench"))bind(e,"#"+action,action,"");
        // Using the base's Workbench opens this planner, so the bench's own crafting screen is offered here.
        String ownScreen=WorkplaceNativeBench.label(store.getExternalData().getWorld(),station);b.set("#Bench.Visible",ownScreen!=null);if(ownScreen!=null)b.set("#Bench.Text",ownScreen);
        b.set("#Name.Text",c.base().name());b.set("#Floor.Text","Height "+floor);b.set("#StartPoint.Text","Start: "+x+", "+startY+", "+z+" | Dig "+direction.name().toLowerCase(Locale.ROOT));
        b.set("#Length.Text",length+" blocks long");b.set("#Shape.Text",layout.label+": "+layout.description);
        // Each fitting is named by the one item miners take for it, with what this route needs and what the town holds.
        var stock=c.town().getWarehouse().craftingStock();Map<String,Integer> needs=Map.of();
        // A frame that would stand where this route meets another passage is never built, so it is not counted.
        try{if(draftActive){var d=draft();needs=ExcavationFittings.needs(d.area(),d.mineshaftEntrance(),layout,ExcavationFittings.Mode.ALL_RAILS,MineshaftNetwork.openSpace(c.town().getApprenticeWorkplaces(),c.base(),null));}}catch(IllegalArgumentException ignored){}
        boolean[] chosen={fittings.supports,fittings.lights,fittings.lanterns,layout!=ExcavationPlan.Layout.ROOM&&fittings.rails};
        String[] controls={"Beams","Torches","Lanterns","Rails"};
        for(int i=0;i<controls.length;i++) {
            String item=ExcavationFittings.ITEMS.get(i);var asset=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(item);
            b.set("#"+controls[i]+"Icon.ItemId",item);
            b.set("#"+controls[i]+"Name.TextSpans",asset==null||asset.getTranslationKey()==null?com.hypixel.hytale.server.core.Message.raw(NativeExcavation.label(item)):com.hypixel.hytale.server.core.Message.translation(asset.getTranslationKey()));
            // With no start chosen there is no route to count for, so only the town's stock is shown.
            b.set("#"+controls[i]+"Count.Text",needs.isEmpty()?"Town storage has "+stock.count(item)+".":ExcavationFittings.countLine(chosen[i],needs.getOrDefault(item,0),stock.count(item)));
            b.set("#"+controls[i]+".Text",chosen[i]?"On":"Off");
        }
        b.set("#Rails.Disabled",layout==ExcavationPlan.Layout.ROOM);
        b.set("#Shorter.Disabled",layout==ExcavationPlan.Layout.ROOM||length<=minimumLength());b.set("#Longer.Disabled",layout==ExcavationPlan.Layout.ROOM||length==30);b.set("#Down.Disabled",MineshaftNetwork.adjacentFloor(c.town().getApprenticeWorkplaces(),c.base(),floor,false)==floor);b.set("#Up.Disabled",MineshaftNetwork.adjacentFloor(c.town().getApprenticeWorkplaces(),c.base(),floor,true)==floor);
        reviewed=review(c,store);b.set("#Review.Text",reviewed.issue()!=null?reviewed.issue():reviewed.cells().size()+" blocks to excavate. Requires Mining "+reviewed.cells().stream().mapToInt(ExcavationPlan.Cell::skill).max().orElse(1)+". Adding authorizes permanent excavation by workers.");
        b.set("#Add.Disabled",!reviewed.ready());
        // One button for the change the review suggests. It says exactly what it will do, and does nothing until chosen.
        var fix=advised==null?null:advised.fix();b.set("#Fix.Visible",fix!=null);if(fix!=null)b.set("#Fix.Text",fix.button());
        var sites=c.town().getApprenticeWorkplaces();var tunnels=sites.tunnels(c.base().id());index=Math.clamp(index,0,Math.max(0,tunnels.size()-1));
        var selected=tunnels.isEmpty()?null:tunnels.get(index);selectedId=selected==null?null:selected.id();
        // Judging the tunnel first brings its order up to date, so the lines below describe what miners will really do.
        String selectedIssue=selected==null?null:NativeExcavation.workIssue(store.getExternalData().getWorld(),c.town(),selected,AetherhavenPlugin.get(),bestSkill(c.town()));
        b.set("#Tunnel.Text",selected==null?"No saved tunnels yet":(index+1)+" / "+tunnels.size()+": "+selected.name()+" | "+selected.excavation().completed()+" / "+selected.excavation().total()+" blocks"
            +(selected.excavation().fittingsTotal()>0?" | "+selected.excavation().fitted()+" / "+selected.excavation().fittingsTotal()+" fittings":""));
        b.set("#Previous.Disabled",index==0);b.set("#Next.Disabled",index+1>=tunnels.size());b.set("#Pause.Disabled",selected==null);b.set("#Remove.Disabled",selected==null||selected.id().equals(c.base().id()));
        b.set("#Pause.Text",selected!=null&&selected.excavation().paused()?"Resume tunnel":"Pause tunnel");b.set("#Remove.Text",confirmRemoval?"Confirm removal":"Remove tunnel");
        String awaited=selected==null?null:NativeExcavation.awaitedFitting(c.town(),selected);
        b.set("#ActivityIcon.Visible",awaited!=null);if(awaited!=null)b.set("#ActivityIcon.ItemId",awaited);
        b.set("#Activity.Text",selected==null?"Enable Mining for an employed townsfolk in the journal. Keep wages paid and the workplace entrance clear.":activity(store.getExternalData().getWorld(),c.town(),selected,selectedIssue,stock));
        var buffered=MineshaftOutput.find(c.town(),c.base().id()).getAmounts();
        b.set("#Buffer.Text","Stored output: "+buffered.values().stream().mapToLong(Long::longValue).sum()+" items | Hover for contents");
        b.set("#Buffer.TooltipText",buffered.isEmpty()?"Open output storage to collect items. Couriers collect supplies at this base. Miners carry 25 items at Mining 1, plus 10 per additional level.":buffered.entrySet().stream().map(entry->entry.getKey().replace('_',' ')+": "+entry.getValue()).collect(java.util.stream.Collectors.joining("\n")));
        // The map: this floor's passages, the draft, and every passage that goes through this floor at another height.
        var drawn=MineshaftMap.cells(sites,c.base(),centerX,centerZ,floor,
            draftActive?new MineshaftMap.Draft(new Point(x,startY,z),direction,length,layout,advised==null?List.of():advised.clashes()):null);
        drawMap(b,drawn);
        for(int i=0;i<drawn.size();i++)bind(e,"#Map["+i+"]","Cell",drawn.get(i).point().x()+","+drawn.get(i).point().z());
        b.set("#Status.Text",hint);
    }
    /** Every command that draws the map. A fresh square has no picture, so only squares that show stairs are sent one. */
    static void drawMap(UICommandBuilder b,List<MineshaftMap.Cell> drawn){
        b.clear("#Map");
        for(int i=0;i<drawn.size();i++){
            var cell=drawn.get(i);int row=i/MineshaftMap.SIZE,col=i%MineshaftMap.SIZE;
            b.append("#Map","Aetherhaven/MineshaftPlannerCell.ui");String path="#Map["+i+"]";
            var anchor=new Anchor();anchor.setLeft(Value.of(col*22));anchor.setTop(Value.of(row*22));anchor.setWidth(Value.of(21));anchor.setHeight(Value.of(21));b.setObject(path+".Anchor",anchor);
            b.setObject(path+".Background",new PatchStyle(Value.of("Aetherhaven/MineshaftMapCell.png"),Value.of(3)).setColor(Value.of(color(cell.kind()))));
            // A landing shows its own stairs. A square the stairs only go through shows arrows pointing uphill.
            String picture=cell.landing()!=0?"MineshaftStairs"+(cell.landing()==1?"Up":cell.landing()==2?"Down":"Both")
                :cell.climbs()!=null?"MineshaftStairwell"+switch(cell.climbs()){case NORTH->"North";case EAST->"East";case SOUTH->"South";case WEST->"West";}:null;
            if(picture!=null){b.set(path+" #Stairs.Visible",true);b.set(path+" #Stairs.AssetPath","UI/Custom/Aetherhaven/"+picture+".png");}
            b.set(path+".TooltipText",cell.tooltip());
        }
    }
    static String color(MineshaftMap.Kind kind){
        return switch(kind){
            case START->"#ffffff";case CLASH->"#d9534f";case DRAFT->"#d3b164";case BASE->"#58a7dc";case PASSAGE->"#79c49a";
            case STAIRWELL->"#7d6aa8";case ROCK->"#273447";
        };
    }
    /** One saved tunnel in words: what holds it up, else what miners are working round, carrying or still fitting. */
    private static String activity(com.hypixel.hytale.server.core.universe.world.World world,TownRecord town,ApprenticeWorkplace site,String issue,com.hexvane.aetherhaven.warehouse.TownWarehouse.CraftingStock stock){
        if(issue!=null)return issue;
        var plan=site.excavation();
        String obstruction=NativeExcavation.obstruction(world,town,site);
        if(obstruction!=null)return obstruction;
        if(plan.cargoCount()>0)return plan.cargoCount()+" items carried. "+(plan.loadReady()?"Returning to output storage.":"Filling this load before returning.");
        if(plan.next()==null&&plan.nextFitting()!=null)return ExcavationFittings.fittingLine(plan.fittingsNeeded(),stock::count,NativeExcavation::label);
        return "Ready for a Mining worker.";
    }
    private int minimumLength(){return layout==ExcavationPlan.Layout.DEEP_STAIRS_UP||layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN?9:3;}
    private int bestSkill(TownRecord town){return town.getTownsfolkProgress().entrySet().stream().filter(e->e.getValue().canPerform(TownsfolkWorkType.MINING,false,town.getPayroll().overdue(e.getKey()),true)).mapToInt(e->e.getValue().skill(TownsfolkWorkType.MINING.skill)).max().orElse(0);}
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data){
        var c=current(ref,store);if(c==null){close();return;}if(!Long.toString(version).equals(data.revision)||data.action==null)return;
        var sites=c.town().getApprenticeWorkplaces();var selected=sites.find(selectedId);
        if(!"Remove".equals(data.action))confirmRemoval=false;
        switch(data.action){
            case "Settings"->{store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().openCustomPage(ref,store,new WorkplacePage(playerRef,townId,c.base().id(),c.base().area()));return;}
            case "Cell"->{try{String[] parts=data.key.split(",");int px=Integer.parseInt(parts[0]),pz=Integer.parseInt(parts[1]);if(Math.abs((long)px-centerX)>31||Math.abs((long)pz-centerZ)>31)return;
                // A square that stairs go through is never a place to start from or to extend into.
                var choice=MineshaftMap.choose(sites,c.base(),floor,px,pz);var start=choice.start();if(start==null){draftActive=false;hint=choice.refusal();break;}x=start.anchor().x();z=start.anchor().z();startY=start.anchor().y();if(start.direction()!=null)direction=start.direction();draftActive=true;var owner=connection(c);var list=sites.tunnels(c.base().id());for(int i=0;i<list.size();i++)if(list.get(i).id().equals(owner))index=i;hint="Choose direction and length, then add the reviewed tunnel.";}catch(Exception invalid){return;}}
            case "Fix"->{
                // Worked out again at this moment, and applied only because the player chose the button that names it.
                var advice=draftActive?MineshaftNetwork.advice(sites,c.base(),new Point(x,startY,z),direction,length,layout):null;
                if(advice==null||advice.fix()==null)hint="Nothing is in this route's way now.";
                else if(advice.fix().length()!=null){length=advice.fix().length();hint="Tunnel shortened to "+length+" blocks. It now stops short of the passage that was in its way.";}
                else{x=advice.fix().start().x();z=advice.fix().start().z();hint="Start moved to "+x+", "+startY+", "+z+". The same route is clear from there.";}
            }
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
            case "Refresh"->hint="Rock and town storage checked again. Saved tunnels are unchanged.";
            case "Bench"->{if(station==null)return;var opened=WorkplaceNativeBench.open(ref,store,station);if(opened==WorkplaceNativeBench.Outcome.OPENED||isDismissed())return;hint=opened.message();}
            case "Previous","Next"->{var tunnels=sites.tunnels(c.base().id());index=Math.clamp(index+("Next".equals(data.action)?1:-1),0,Math.max(0,tunnels.size()-1));if(!tunnels.isEmpty()){var s=tunnels.get(index);var p=MineshaftSelection.ground(s.area(),s.excavation().entrance(),s.excavation().layout());centerX=p.x();centerZ=p.z();floor=startY=p.y()+1;draftActive=false;}}
            case "Pause"->{if(selected!=null){
                // Miners change a tunnel's order as they work, so the button acts on the tunnel as it is now.
                var plan=selected.excavation();boolean pause=!plan.paused();plan.pause(plan.revision(),pause);
                hint=pause?"Tunnel paused. Miners still bring in what they have already dug.":"Tunnel resumed.";c.manager().updateTown(c.town());}}
            case "Remove"->{if(selected!=null&&!selected.id().equals(c.base().id())){if(!confirmRemoval){confirmRemoval=true;hint="Remove this tunnel order? Excavated blocks remain removed. Connected branches must be removed first.";}else{String error=sites.remove(c.town(),playerRef.getUuid(),selected.id());hint=error==null?"Tunnel order removed. Its stored output remains at the mine base.":error;if(error==null){draftActive=false;if(connection(c)==null)home(c.base());}confirmRemoval=false;c.manager().updateTown(c.town());}}}
            case "Add"->{
                // The rock is read again at this moment. A block that changed since the page was drawn is no reason to refuse.
                var fresh=review(c,store);
                if(!fresh.ready())hint=fresh.issue()!=null?fresh.issue():MineshaftNetwork.NO_NEW_ROCK;
                else if(fittings!=ExcavationFittings.Mode.NONE&&(!c.town().playerCanPlaceBlocks(playerRef.getUuid())||!c.town().playerCanOpenContainers(playerRef.getUuid())))hint="You need building and town storage permission to add beams, lights or rails. Turn them off to add the tunnel without them.";
                else{var draft=draft();draft.configure("Tunnel "+(sites.tunnels(c.base().id()).size()+1),TownsfolkWorkType.MINING,draft.area());
                    hint=draft.excavation().start(0,playerRef.getUuid(),c.base().id(),draft.area(),draft.mineshaftEntrance(),layout,fresh.cells(),fittings);
                    if(hint==null)hint=sites.addTunnel(c.town(),playerRef.getUuid(),c.base().id(),draft,prerequisite(c));
                    if(hint==null){draftActive=false;index=sites.tunnels(c.base().id()).size()-1;c.manager().updateTown(c.town());hint="Tunnel added. It will wait for its connecting passage. Choose its end on the map to extend it.";}
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
