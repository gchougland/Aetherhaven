package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.function.Predicate;

/** Explicit grove planning and one-way adoption of physical Logger work. */
public final class TimberGrovePage extends AetherhavenInteractiveCustomUIPage<TimberGrovePage.Data> {
    record Context(TownManager manager,TownRecord town,PlotInstance plot,PlotProductionState production,ProductionCatalog.Entry entry) {}
    private final UUID townId,plotId;
    private int slot;
    private TimberPlantingReceipt candidate;
    private long revision;
    private World world;
    private TimberHarvestPlan.Position anchor;
    private com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation yaw;
    private boolean appended;
    private String hint;
    public TimberGrovePage(PlayerRef player,UUID town,UUID plot) {this(player,town,plot,0,null,-1);}
    TimberGrovePage(PlayerRef player,UUID town,UUID plot,int slot,TimberPlantingReceipt candidate,long revision) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);
        townId=town;plotId=plot;this.slot=slot;this.candidate=candidate;this.revision=revision;
    }
    static Context resolve(Ref<EntityStore> ref,Store<EntityStore> store,UUID townId,UUID plotId) {
        var plugin=AetherhavenPlugin.get();var pr=store.getComponent(ref,PlayerRef.getComponentType());
        var pos=store.getComponent(ref,TransformComponent.getComponentType());if(plugin==null||pr==null||pos==null)return null;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);var p=pos.getPosition();
        if(town==null||!town.hasMemberOrOwner(pr.getUuid())||!town.playerCanManageConstructions(pr.getUuid())
            ||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        var plot=town.findPlotById(plotId);
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!plot.hasStoredPrefabWorldAnchor()
            ||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL))return null;
        var production=town.getOrCreatePlotProduction(plotId);
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL,production);
        return entry==null||entry.catalogSize()<=0?null:new Context(manager,town,plot,production,entry);
    }
    static Predicate<TimberHarvestPlan.Position> bounds(TownRecord town) {
        return p->p.y()>=ChunkUtil.MIN_Y&&p.y()<=ChunkUtil.HEIGHT_MINUS_1
            &&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()));
    }
    static String otherGroveIssue(Context c,TimberPlantingReceipt tree) {
        for(var plot:c.town().getPlotInstances()) {
            if(plot.getPlotId().equals(c.plot().getPlotId()))continue;
            var other=c.town().peekPlotProduction(plot.getPlotId());
            if(other!=null&&other.groveConflictsWith(tree))return "This tree would overlap a grove belonging to another lumbermill.";
        }
        return null;
    }
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var c=resolve(ref,store,townId,plotId);if(c==null)return null;
        var a=c.plot().getStoredPrefabWorldAnchor();var point=new TimberHarvestPlan.Position(a.x,a.y,a.z);
        if(world==null){world=store.getExternalData().getWorld();anchor=point;yaw=c.plot().resolvePrefabYaw();}
        if(world!=store.getExternalData().getWorld()||!anchor.equals(point)||yaw!=c.plot().resolvePrefabYaw())return null;
        if(revision<0)revision=c.production().timber().layoutRevision();
        return c;
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        var timber=c.production().timber();int count=WorkplaceProductionUpgrades.slotCount(c.production());
        if(slot<0||slot>=count){close();return;}
        String output=c.entry().itemAtCursor(c.production().getSlotCursor(slot));
        if(!appended){b.append("Aetherhaven/TimberGrovePage.ui");appended=true;
            for(int i=0;i<WorkplaceProductionUpgrades.MAX_SLOT_COUNT;i++) {
                b.append("#Slots","Aetherhaven/TimberGroveSlot.ui");
                e.addEventBinding(CustomUIEventBindingType.Activating,"#Slots["+i+"]",EventData.of("Action","Slot").append("Slot",Integer.toString(i)),false);
            }
            for(String action:List.of("Choose","Save","Remove","Activate","Release","Refresh"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        for(int i=0;i<WorkplaceProductionUpgrades.MAX_SLOT_COUNT;i++) {
            String path="#Slots["+i+"]";boolean locked=i>=count;
            b.set(path+".Visible",true);b.set(path+".Disabled",locked);b.set(path+" #Selected.Visible",i==slot);
            b.set(path+" #LockedShade.Visible",locked);b.set(path+" #Lock.Visible",locked);b.set(path+" #Icon.Visible",!locked);
            b.set(path+".TooltipText",locked?"Unlock more material slots in Building Upgrades.":"Review this material's grove work.");
            b.set(path+" #Number.Text","SLOT "+(i+1));b.set(path+" #Icon.ItemId",c.entry().itemAtCursor(c.production().getSlotCursor(i)));
            var planned=timber.groves().get(i);
            boolean changed=planned!=null&&!planned.output().equals(c.entry().itemAtCursor(c.production().getSlotCursor(i)));
            var planted=timber.trees().get(i);
            boolean extra=NativeTimberProducts.supported(c.entry().itemAtCursor(c.production().getSlotCursor(i)));
            b.set(path+" #Status.Text",locked?"Locked":TimberCharring.OUTPUT.equals(c.entry().itemAtCursor(c.production().getSlotCursor(i)))?"Charring":extra?"Tree products":planted!=null?(timber.edited(planted.tree().id())?"Changed tree":"Planted"):changed?"Review timber":planned!=null?"Planned":"Choose soil");
        }
        b.set("#GroveMode.Text",timber.adopted()?"GROVE WORK ACTIVE":"PLAN ROOM TO GROW");
        var tree=candidate!=null?candidate:timber.groves().get(slot);
        b.set("#TreeIcon.ItemId",output);b.set("#SlotTitle.Text","SLOT "+(slot+1));
        var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(output);
        b.set("#TimberName.TextSpans",item!=null&&item.getTranslationKey()!=null
            ?com.hypixel.hytale.server.core.Message.translation(item.getTranslationKey()):com.hypixel.hytale.server.core.Message.raw(output));
        b.set("#Location.Text",tree==null?"No tree location selected.":"Root: "+tree.root().x()+", "+tree.root().y()+", "+tree.root().z());
        b.set("#Footprint.Text",tree==null?"Native saplings need room for their full canopy and roots.":dimensions(tree));
        if(tree==null&&NativeTimberProducts.supported(output)) {
            long free=Math.max(0,WorkplaceProductionUpgrades.effectiveMaxStorage(c.production(),c.entry(),output)-c.production().getAmount(output));
            b.set("#Location.Text","Collection room: "+free+" items");
            b.set("#Footprint.Text","Storage upgrades allow more collection from each tree. No separate tree location is needed.");
        }
        if(TimberCharring.OUTPUT.equals(output)) {
            b.set("#Location.Text","Uses gathered timber from this lumbermill.");
            b.set("#Footprint.Text","Keep a lit campfire within two blocks of storage, inside the building bounds.");
        }
        String issue=null;
        if(candidate!=null) {
            if(!candidate.output().equals(output))issue="The selected timber changed. Choose a location for the new tree.";
            else if(timber.layoutRevision()!=revision)issue="The grove plan changed. Close this review and open it again.";
            else issue=NativeTimberPlanting.prepare(world,c.production(),slot,candidate,bounds(c.town())).issue();
            if(issue==null)issue=otherGroveIssue(c,candidate);
        }
        else if(tree!=null&&!tree.output().equals(output))issue="This slot's timber changed. Choose a location again so its new canopy can be checked.";
        b.set("#GroveStatus.Text",issue!=null?issue:candidate!=null?"Review this location. Save plan reserves its growing space without placing a tree.":hint!=null?hint:status(c,slot));
        b.set("#Save.Visible",candidate!=null);b.set("#Save.Disabled",candidate==null||issue!=null);
        var species=NativeTimberGrowth.species(output);var planted=timber.trees().get(slot);
        b.set("#Choose.Visible",species!=null);b.set("#Remove.Visible",species!=null||timber.groves().containsKey(slot));
        b.set("#Choose.Disabled",timber.trees().containsKey(slot)||species==null);
        b.set("#Remove.Disabled",timber.trees().containsKey(slot)||!timber.groves().containsKey(slot)||timber.layoutRevision()!=revision);
        b.set("#SeedIcon.ItemId",species==null?output:species.sapling());
        b.set("#SeedTitle.Text",TimberCharring.OUTPUT.equals(output)?"CHARRING":species==null?"TREE PRODUCTS":"FREE PLANTING");
        b.set("#SeedCount.Text",TimberCharring.OUTPUT.equals(output)?"Two gathered logs make one Burnt log.":species==null?"Collected when a matching tree is felled.":
            "Each tree yields its wood and sapling, plus sap and sticks. Use Collect all to take the harvest.");
        b.set("#Activate.Visible",!timber.adopted());
        b.set("#Activate.Disabled",candidate!=null||timber.groves().isEmpty()||timber.layoutRevision()!=revision);
        b.set("#Release.Visible",timber.adopted()&&planted!=null);
        b.set("#Release.Disabled",planted==null||!timber.edited(planted.tree().id())||timber.layoutRevision()!=revision);
        b.set("#ModeHint.Text",TimberCharring.OUTPUT.equals(output)?"Logs are consumed only when the Logger finishes charring. Finished Burnt logs are carried into lumbermill storage.":timber.adopted()
            ?"Planting needs no supplies. Harvests go to lumbermill storage. Releasing an edited tree leaves its blocks in place and gives no harvest."
            :"Starting grove work replaces all old outputs at this lumbermill. Only saved tree locations produce timber. Stored items and upgrades are kept.");
        drawMap(b,e,c,tree);
    }
    private String status(Context c,int selected) {
        var timber=c.production().timber();var planted=timber.trees().get(selected);var plan=timber.groves().get(selected);
        if(planted!=null&&timber.edited(planted.tree().id()))return "This tree was changed by building or breaking. Release it, then clear its growing space before replanting.";
        if(timber.cargo()!=null) {
            var cargo=timber.cargo();
            boolean full=c.production().getAmount(cargo.output())>=WorkplaceProductionUpgrades.effectiveMaxStorage(c.production(),c.entry(),cargo.output());
            return timber.waitingProducts().values().stream().mapToInt(Integer::intValue).sum()+" items await delivery, including "+cargo.carried()+" carried by the Logger. "
                +(full?"Make room in lumbermill storage to continue.":"The Logger carries the harvest to storage during work hours.");
        }
        String output=c.entry().itemAtCursor(c.production().getSlotCursor(selected));
        if(TimberCharring.OUTPUT.equals(output)) {
            String requirement=com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(c.town(),"logger",output);
            if(requirement!=null)return requirement;
            return "The Logger chars two unreserved logs from your first stocked timber slot into one Burnt log. Keep that timber selected and a nearby campfire fueled and lit. Charring needs attended work and free storage.";
        }
        if(NativeTimberGrowth.species(output)==null&&NativeTimberProducts.supported(output))return "Sap, sticks and matching saplings are collected automatically with each tree. Fibre is optional. Keep a timber slot to grow trees; extra slots do not multiply a harvest.";
        if(!timber.adopted())return plan==null?"Choose a supported timber slot and mark soil with the Building Staff. Each native tree needs room for its full growth.":"Start grove work to let the Logger plant and tend this tree. No sapling is required.";
        if(planted!=null) {
            if(!NativeTimberProducts.hasRoom(c.production(),c.entry(),planted.tree()))return "Storage for a tree product is full. Use Collect all in lumbermill storage before the Logger can chop this tree.";
            var harvest=NativeTimberHarvest.inspect(world,planted,bounds(c.town()));
            return harvest.ready()?"This tree is ready for the Logger to chop during work hours.":harvest.issue();
        }
        if(plan==null)return "This material slot has no tree location. It does not produce items until a tree is planned and planted.";
        if(!NativeTimberProducts.hasRoom(c.production(),c.entry(),plan))return "Storage for a tree product is full. Use Collect all in lumbermill storage before the Logger can plant this tree.";
        var prepared=NativeTimberPlanting.prepare(world,c.production(),selected,plan,bounds(c.town()));
        return prepared.ready()?"Ready for free planting. The Logger needs a clear route and available work time.":prepared.issue();
    }
    private String activationIssue(Context c) {
        var timber=c.production().timber();
        if(timber.layoutRevision()!=revision)return "The grove plan changed. Open it again before starting work.";
        for(var planned:timber.groves().entrySet()) {
            if(planned.getKey()<0||planned.getKey()>=WorkplaceProductionUpgrades.slotCount(c.production()))return "A planned tree uses a locked material slot.";
            var tree=planned.getValue();
            if(!tree.output().equals(c.entry().itemAtCursor(c.production().getSlotCursor(planned.getKey()))))return "A planned tree no longer matches its selected timber. Review its location first.";
            var captured=NativeTimberGrowth.capture(tree.output(),tree.root(),0,bounds(c.town()));
            if(!captured.ready()||!captured.receipt().stages().equals(tree.stages())||!captured.receipt().clearance().equals(tree.clearance()))return "A tree's growing space has changed. Choose its location again.";
            var prepared=NativeTimberPlanting.prepare(world,c.production(),planned.getKey(),tree,bounds(c.town()));
            if(!prepared.ready())return prepared.issue();
            String conflict=otherGroveIssue(c,tree);if(conflict!=null)return conflict;
        }
        return null;
    }
    private static String dimensions(TimberPlantingReceipt tree) {
        var cells=tree.footprint();int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX,minY=minX,maxY=maxX;
        for(var p:cells){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());}
        return "Growing space: "+(maxX-minX+1)+" by "+(maxZ-minZ+1)+" blocks, "+(maxY-minY+1)+" blocks tall.";
    }
    private void drawMap(UICommandBuilder b,UIEventBuilder e,Context c,TimberPlantingReceipt selected) {
        b.clear("#Map");var plans=new LinkedHashMap<>(c.production().timber().groves());if(selected!=null)plans.put(slot,selected);
        if(plans.isEmpty()) {
            b.append("#Map","Aetherhaven/ProductionMapEmpty.ui");
            b.set("#Map #Icon.ItemId",c.entry().itemAtCursor(c.production().getSlotCursor(slot)));
            b.set("#Map #Message.Text","Choose ground with the Building Staff to plan a tree here.");
            return;
        }
        var roots=plans.values().stream().map(TimberPlantingReceipt::root).toList();
        int minX=roots.stream().mapToInt(TimberHarvestPlan.Position::x).min().orElse(0),minZ=roots.stream().mapToInt(TimberHarvestPlan.Position::z).min().orElse(0);
        int spanX=roots.stream().mapToInt(TimberHarvestPlan.Position::x).max().orElse(minX)-minX,spanZ=roots.stream().mapToInt(TimberHarvestPlan.Position::z).max().orElse(minZ)-minZ;
        double scale=Math.min(260.0/Math.max(1,spanX),90.0/Math.max(1,spanZ));int index=0;
        for(var entry:plans.entrySet()) {
            var root=entry.getValue().root();b.append("#Map","Aetherhaven/MiningReviewNode.ui");String path="#Map["+(index++)+"]";
            var a=new Anchor();a.setLeft(Value.of(24+(int)Math.round((260-spanX*scale)/2+(root.x()-minX)*scale)));
            a.setTop(Value.of(24+(int)Math.round((90-spanZ*scale)/2+(root.z()-minZ)*scale)));a.setWidth(Value.of(30));a.setHeight(Value.of(30));
            b.setObject(path+".Anchor",a);b.set(path+" #Number.Text",Integer.toString(entry.getKey()+1));b.set(path+" #Selected.Visible",entry.getKey()==slot);
            e.addEventBinding(CustomUIEventBindingType.Activating,path,EventData.of("Action","Slot").append("Slot",entry.getKey().toString()),false);
        }
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}var timber=c.production().timber();
        if(slot<0||slot>=WorkplaceProductionUpgrades.slotCount(c.production())){close();return;}
        if("Slot".equals(data.action)) {
            try{int chosen=Integer.parseInt(data.slot);if(chosen>=0&&chosen<WorkplaceProductionUpgrades.slotCount(c.production())&&(chosen!=slot||candidate==null)){
                slot=chosen;candidate=null;revision=timber.layoutRevision();hint=null;
                var tool=WorkplaceToolState.obtain(ref,store);if(tool.grove!=null)tool.clearSelection();
            }}catch(NumberFormatException ignored){}
        }else if("Refresh".equals(data.action)) {
            hint=null;if(candidate==null)revision=timber.layoutRevision();
        }else if("Activate".equals(data.action)&&candidate==null&&!timber.adopted()) {
            String issue=activationIssue(c);if(issue==null)issue=timber.activate(revision);
            if(issue==null){revision=timber.layoutRevision();c.manager().updateTown(c.town());hint="Grove work started. The Logger will plant and tend the saved tree locations during work hours. No saplings are required.";}else hint=issue;
        }else if("Release".equals(data.action)) {
            String issue=timber.releaseEdited(slot,revision);
            if(issue==null){revision=timber.layoutRevision();c.manager().updateTown(c.town());hint="Tree released. Its blocks are unchanged. Clear its growing space or choose a new location.";}else hint=issue;
        }else if("Choose".equals(data.action)&&!timber.trees().containsKey(slot)) {
            TimberGroveSelection.begin(ref,store,WorkplaceToolState.obtain(ref,store),c,slot);
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("Hold the Building Staff and choose soil for this tree. Use opens the grove review. Nothing is planted yet."));close();return;
        }else if("Save".equals(data.action)&&candidate!=null) {
            String output=c.entry().itemAtCursor(c.production().getSlotCursor(slot));
            String issue=!output.equals(candidate.output())?"The selected timber changed. Choose its tree location again.":otherGroveIssue(c,candidate);
            if(issue==null)issue=timber.plan(slot,WorkplaceProductionUpgrades.slotCount(c.production()),revision,candidate,bounds(c.town()),p->NativeTimberPlanting.observe(world,p));
            if(issue==null){
                c.manager().updateTown(c.town());candidate=null;revision=timber.layoutRevision();
                var tool=WorkplaceToolState.obtain(ref,store);tool.clearSelection();tool.hint="Grove plan saved. Open the lumbermill storage to review it.";
                hint=timber.adopted()?"Tree location saved. The Logger can plant it without saplings.":"Grove plan saved. Current lumbermill production continues until you start grove work.";
            }else hint=issue;
        }else if("Remove".equals(data.action)) {
            String issue=timber.removePlan(slot,revision);if(issue==null){c.manager().updateTown(c.town());candidate=null;revision=timber.layoutRevision();hint="Planned location removed. No terrain changed.";}else hint=issue;
        }
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Slot",Codec.STRING),(d,v)->d.slot=v,d->d.slot).add().build();
        String action,slot;
    }
}
