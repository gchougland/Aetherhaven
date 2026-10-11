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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Explicit opt in, with a spatial view of actual planted beds. Never edits an old building on opening. */
public final class FarmerFieldPage extends AetherhavenInteractiveCustomUIPage<FarmerFieldPage.Data> {
    private record Context(TownManager manager,TownRecord town,PlotInstance plot,PlotProductionState production,ProductionCatalog.Entry entry) {}
    private final UUID townId,plotId;
    private boolean appended;
    private World world;
    private List<Integer> footprint;
    private long revision;
    private NativeFarmCrops.Review reviewed;
    private String hint;
    public FarmerFieldPage(PlayerRef player,UUID town,UUID plot) {
        super(player,CustomPageLifetime.CanDismiss,Data.CODEC);townId=town;plotId=plot;
    }
    private Context current(Ref<EntityStore> ref,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();var player=store.getComponent(ref,PlayerRef.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(plugin==null||player==null||transform==null)return null;
        var actualWorld=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(actualWorld,plugin);
        var town=manager.getTown(townId);var p=transform.getPosition();
        if(town==null||!town.hasMemberOrOwner(player.getUuid())||!town.playerCanManageConstructions(player.getUuid())
            ||!TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate((int)Math.floor(p.x)),ChunkUtil.chunkCoordinate((int)Math.floor(p.z))))return null;
        var plot=town.findPlotById(plotId);
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!(NativeDruidCrops.isPlot(plot)
            ||plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_FARM)))return null;
        var f=plot.toFootprint();var bounds=List.of(f.getMinX(),f.getMinY(),f.getMinZ(),f.getMaxX(),f.getMaxY(),f.getMaxZ());
        if(world==null){world=actualWorld;footprint=bounds;}
        if(world!=actualWorld||!footprint.equals(bounds))return null;
        var production=town.getOrCreatePlotProduction(plotId);
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),NativeDruidCrops.isPlot(plot)?NativeDruidCrops.CONSTRUCTION:AetherhavenConstants.CONSTRUCTION_PLOT_FARM,production);
        return entry==null?null:new Context(manager,town,plot,production,entry);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        if(!appended) {
            b.append("Aetherhaven/FarmerFieldPage.ui");appended=true;
            for(String action:List.of("Save","Refresh"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        var field=c.production().field();revision=field.revision();reviewed=NativeFarmCrops.review(world,c.town(),c.plot());
        boolean druid=NativeDruidCrops.isPlot(c.plot());
        String worker=druid?"Druid":"Farmer",building=druid?"conservatory":"farm";
        b.set("#TitleText.Text",druid?"CONSERVATORY BEDS":"FARM FIELDS");
        b.set("#Intro.Text",druid?"Plant alchemical seeds on crystal soil and select their outputs in storage. The Druid grows, harvests and carries them."
            :"Plant crops in the farm's beds, select their outputs in storage, then let the Farmer tend them.");
        long selected=reviewed.beds().stream().filter(bed->!NativeFarmCrops.selectedOutputs(c.production(),c.entry(),bed).isEmpty()&&masteryIssue(c,bed)==null).count();
        long locked=reviewed.beds().stream().filter(bed->masteryIssue(c,bed)!=null).count();
        long ripe=reviewed.beds().stream().filter(bed->NativeFarmCrops.ripe(world,bed)).count();
        b.set("#BedCount.Text",Integer.toString(reviewed.beds().size()));
        b.set("#ActiveCount.Text",selected+(selected==1?" bed matches selected outputs":" beds match selected outputs"));b.set("#RipeCount.Text",ripe+" ready to harvest");
        var cargo=field.cargo();
        boolean storageFull=cargo!=null&&c.production().getAmount(cargo.output())>=WorkplaceProductionUpgrades.effectiveMaxStorage(c.production(),c.entry(),cargo.output());
        b.set("#Cargo.Text",cargo==null?"The "+worker+" carries each harvest to "+building+" storage.":field.heldQuantity()
            +(field.heldQuantity()==1?" item awaiting delivery.":" items awaiting delivery.")
            +(storageFull?" Make room in "+building+" storage to continue.":" The "+worker+" will carry the harvest to "+building+" storage."));
        b.set("#Mode.Text",field.adopted()?druid?"CONSERVATORY WORK ACTIVE":"FIELD WORK ACTIVE":druid?"CONSERVATORY PREVIEW":"FIELD PREVIEW");
        b.set("#Status.Text",hint!=null?hint:reviewed.issue()!=null?reviewed.issue():selected==0?
            locked>0?"Train the "+worker+" to the required Mastery, then select an available crop in storage.":"Select "+(druid?"an alchemical crop":"a crop")+" as an output in "+building+" storage before starting work.":
            "Green beds are ripe. Gold beds are growing. Grey beds need their output selected or more Mastery.");
        b.set("#Save.Text",field.adopted()?"Update tended beds":druid?"Start growing":"Start field work");
        b.set("#Save.Disabled",!reviewed.ready()||selected==0);
        b.set("#Warning.Text",druid?"Established plants regrow after the Druid harvests them. Plant missing beds yourself and review again. Saved Apprentice gardens keep their existing orders and plants.":field.adopted()?"Replant missing crops yourself, then update these beds. Crops inside Apprentice workplaces are excluded. Apprentice farming provides town food support only.":
            "Starting field work stops automatic production. Stored items and upgrades are kept. The Farmer harvests real planted crops. Decorative vegetables do not count as crop beds.");
        drawMap(b,c);
    }
    private void drawMap(UICommandBuilder b,Context c) {
        b.clear("#Map");
        if(reviewed.beds().isEmpty()) {
            b.append("#Map","Aetherhaven/ProductionMapEmpty.ui");
            b.set("#Map #Icon.ItemId",NativeDruidCrops.isPlot(c.plot())?"Plant_Crop_Health1":"Plant_Crop_Wheat_Item");
            b.set("#Map #Message.Text",NativeDruidCrops.isPlot(c.plot())?"Plant alchemical seeds on crystal soil, then refresh. Decorative plants do not count.":"Plant seeds in tilled soil, then refresh. Decorative vegetables do not count as planted crops.");
            return;
        }
        int minX=reviewed.beds().stream().mapToInt(bed->bed.point().x()).min().orElse(0),minZ=reviewed.beds().stream().mapToInt(bed->bed.point().z()).min().orElse(0);
        int dx=reviewed.beds().stream().mapToInt(bed->bed.point().x()).max().orElse(minX)-minX,dz=reviewed.beds().stream().mapToInt(bed->bed.point().z()).max().orElse(minZ)-minZ;
        // Keep neighboring beds together and use the available space for readable icons.
        int size=(int)Math.max(12,Math.min(44,Math.min(388.0/(dx+1),208.0/(dz+1))-4));
        double scale=Math.min(56,Math.min((388.0-size)/Math.max(1,dx),(208.0-size)/Math.max(1,dz)));int index=0;
        for(var bed:reviewed.beds()) {
            b.append("#Map","Aetherhaven/FarmerFieldBed.ui");String path="#Map["+(index++)+"]";
            var a=new Anchor();a.setLeft(Value.of(18+(int)Math.round((388-size-dx*scale)/2+(bed.point().x()-minX)*scale)));
            a.setTop(Value.of(18+(int)Math.round((208-size-dz*scale)/2+(bed.point().z()-minZ)*scale)));a.setWidth(Value.of(size));a.setHeight(Value.of(size));
            b.setObject(path+".Anchor",a);b.set(path+" #Icon.ItemId",bed.output());
            var icon=new Anchor();icon.setLeft(Value.of(2));icon.setTop(Value.of(1));icon.setWidth(Value.of(size-4));icon.setHeight(Value.of(size-7));
            b.setObject(path+" #Icon.Anchor",icon);
            String issue=masteryIssue(c,bed);
            boolean selected=issue==null&&!NativeFarmCrops.selectedOutputs(c.production(),c.entry(),bed).isEmpty(),ripe=NativeFarmCrops.ripe(world,bed);
            b.set(path+" #State.Background",selected?ripe?"#80bf78":"#d6af65":"#77818d");
            b.set(path+".TextTooltipStyle",Value.ref("Common.ui","DefaultTextTooltipStyle"));
            var item=Item.getAssetMap().getAsset(bed.output());var name=item==null?Message.raw(bed.output()):Message.translation(item.getTranslationKey());
            b.set(path+".TooltipTextSpans",Message.join(name,Message.raw("\n"+bed.point().x()+", "+bed.point().y()+", "+bed.point().z()+"\n"+(issue!=null?issue:selected?ripe?"Ready to harvest":"Growing":"Output not selected"))));
        }
    }
    private static String masteryIssue(Context c,FieldProductionState.Bed bed) {
        return com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(c.town(),NativeDruidCrops.isPlot(c.plot())?"druid":"farmer",bed.output());
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}
        if("Save".equals(data.action)) {
            var fresh=NativeFarmCrops.review(world,c.town(),c.plot());
            if(reviewed==null||!fresh.ready()||!fresh.beds().equals(reviewed.beds()))hint=NativeDruidCrops.isPlot(c.plot())?"The planted beds changed. Review the beds before starting work.":"The planted beds changed. Review the field before starting work.";
            else if(fresh.beds().stream().noneMatch(bed->masteryIssue(c,bed)==null&&!NativeFarmCrops.selectedOutputs(c.production(),c.entry(),bed).isEmpty()))hint="Select an available crop in storage and train the villager to its required Mastery first.";
            else {
                hint=c.production().field().adopt(revision,fresh.beds());
                if(hint==null){c.manager().updateTown(c.town());hint="The "+(NativeDruidCrops.isPlot(c.plot())?"Druid":"Farmer")+" will tend these beds during work hours.";}
            }
        }else if("Refresh".equals(data.action))hint=null;else return;
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();
        String action;
    }
}
