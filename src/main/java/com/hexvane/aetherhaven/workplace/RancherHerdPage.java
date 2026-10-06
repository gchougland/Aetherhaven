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

/** Explicit herd adoption. Opening this review never changes livestock or old barn production. */
public final class RancherHerdPage extends AetherhavenInteractiveCustomUIPage<RancherHerdPage.Data> {
    private record Context(TownManager manager,TownRecord town,PlotInstance plot,PlotProductionState production,ProductionCatalog.Entry entry) {}
    private final UUID townId,plotId;
    private boolean appended;
    private World world;
    private List<Integer> footprint;
    private long revision;
    private long feedOrderRevision;
    private int familyPage;
    private static final int FAMILIES_PER_PAGE=4;
    private NativeHerdService.Review reviewed;
    private String hint;
    public RancherHerdPage(PlayerRef player,UUID town,UUID plot) {
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
        if(plot==null||plot.getState()!=PlotInstanceState.COMPLETE||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN))return null;
        var f=plot.toFootprint();var bounds=List.of(f.getMinX(),f.getMinY(),f.getMinZ(),f.getMaxX(),f.getMaxY(),f.getMaxZ());
        if(world==null){world=actualWorld;footprint=bounds;}
        if(world!=actualWorld||!footprint.equals(bounds))return null;
        var production=town.getOrCreatePlotProduction(plotId);
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN,production);
        return entry==null?null:new Context(manager,town,plot,production,entry);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder b,UIEventBuilder e,Store<EntityStore> store) {
        var c=current(ref,store);if(c==null){close();return;}
        if(!appended) {
            b.append("Aetherhaven/RancherHerdPage.ui");appended=true;
            for(String action:List.of("Save","Refresh","Supply","Take","CourierFeed","PreviousFamily","NextFamily"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,EventData.of("Action",action),false);
        }
        var herd=c.production().herd();revision=herd.revision();reviewed=NativeHerdService.review(world,c.town(),c.plot());
        feedOrderRevision=herd.feedOrderRevision();
        b.set("#Mode.Text",herd.adopted()?"HERD WORK ACTIVE":"HERD PREVIEW");
        b.set("#Feed.Text",herd.feed()+" / "+HerdProductionState.FEED_CAPACITY+" wheat");
        b.set("#Supply.Disabled",herd.feed()>=HerdProductionState.FEED_CAPACITY);b.set("#Take.Disabled",herd.feed()==0);
        b.set("#CourierFeed.Text",herd.courierFeedTarget()==0?"Courier feed: Off":"Keep "+herd.courierFeedTarget()+" wheat");
        b.set("#CourierFeed.Disabled",!herd.adopted());
        b.set("#Save.Text",herd.adopted()?"Update breeding pairs":"Start herd work");
        boolean selected=reviewed.pairs().stream().anyMatch(pair->!RanchProducts.selected(pair.family(),c.production(),c.entry()).isEmpty());
        b.set("#Save.Disabled",!reviewed.ready()||!selected);
        b.set("#Status.Text",hint!=null?hint:reviewed.issue()!=null?reviewed.issue():!selected?"Select an output for one of these herds in barn storage.":"Breeding animals stay safe. Only livestock raised by the Rancher provides these products.");
        b.set("#Warning.Text",herd.adopted()?"One wheat feeds a family for six hours. A full day without care pauses its growth. Apprentice ranches provide town food support only.":"Starting herd work replaces all old output at this barn. Stock and upgrades stay. Mosshorns provide heavy hides, Skrills provide red feathers, and tended herds provide manure. Other specialty hides and feather colours need another source.");
        var feedTrip=c.town().getWarehouse().batches().stream().filter(batch->batch.herdFeed()&&plotId.equals(batch.workplaceId())).findFirst().orElse(null);
        String feedStatus=feedTrip==null?(herd.courierFeedTarget()==0?"Manual feed supply.":"Couriers keep up to "+herd.courierFeedTarget()+" wheat in the bin.")
            :feedTrip.cancelled()?"Spare wheat is being returned to general storage.":feedTrip.courierId()!=null?"A Courier is bringing wheat to barn storage.":"Wheat reserved for a Courier to collect.";
        b.set("#Cargo.Text",herd.cargo()==null?feedStatus:herd.cargo().quantity()+" products awaiting barn storage. "+feedStatus);
        var families=RanchProducts.families();
        familyPage=Math.clamp(familyPage,0,Math.max(0,(families.size()-1)/FAMILIES_PER_PAGE));
        int first=familyPage*FAMILIES_PER_PAGE,last=Math.min(families.size(),first+FAMILIES_PER_PAGE);
        b.set("#FamilyPage.Text","Herds "+(families.isEmpty()?0:first+1)+" to "+last+" of "+families.size());
        b.set("#PreviousFamily.Disabled",familyPage==0);b.set("#NextFamily.Disabled",last==families.size());
        b.clear("#Herds");int index=0;
        for(String family:families.subList(first,last)) {
            b.append("#Herds","Aetherhaven/RancherHerdCard.ui");String path="#Herds["+(index++)+"]";
            b.set(path+" #Family.Text",family.toUpperCase(Locale.ROOT));
            var pair=reviewed.pairs().stream().filter(p->p.family().equals(family)).findFirst().orElse(null);
            var saved=herd.herds().get(family);var care=saved==null?null:saved.care();
            var selectedProducts=RanchProducts.selected(family,c.production(),c.entry());
            String icon=selectedProducts.isEmpty()?switch(family){case "Cow"->ProductionWithdrawal.MILK_BUCKET_ITEM_ID;case "Sheep"->"Ingredient_Fabric_Scrap_Wool";case "Chicken"->"Food_Egg";case "Pig"->"Food_Pork_Raw";case "Mosshorn"->"Ingredient_Hide_Heavy";case "Skrill"->"Ingredient_Feathers_Red";case "Tetrabird"->"Ingredient_Feathers_Dark";case "Bluebird"->"Ingredient_Feathers_Blue";default->"Food_Wildmeat_Raw";}:selectedProducts.get(saved==null?0:saved.productIndex(selectedProducts.size())).output();
            b.set(path+" #Icon.ItemId",icon);
            b.set(path+" #Pair.Text",pair==null?"Pair not nearby":"Breeding pair found");
            b.set(path+" #Stage.Text",pair==null?"PAIR NEEDED":saved==null?"NOT STARTED":care==null||care.offspring()==null?"BREEDING":saved.recoveringOutput()!=null?"REGROWING FLEECE":care.grown()?"RAISED LIVESTOCK":"YOUNG LIVESTOCK");
            String activity=pair==null?"Keep two matching animals near a feeding trough.":selectedProducts.isEmpty()?"Choose an output in barn storage.":saved==null?"Ready to start herd work.":care.offspring()==null?(care.canBreed()?"Leave room near the trough for the young animal.":"Feeding visits prepare the next birth."):saved.sinceFeed()>=HerdProductionState.NEGLECT?"Needs care to resume growing.":NativeHerdService.offspring(world,c.town(),c.plot(),saved)==null?"Bring the raised animal back to its pen.":care.ready()?"Ready for the Rancher to visit.":((RanchCare.GROW_MINUTES-care.ageMinutes()+59)/60)+" hours of tended growth remaining.";
            b.set(path+" #Activity.Text",activity);
            if(pair==null&&"Bluebird".equals(family))b.set(path+" #Activity.Text","Keep two Bluebirds in a covered pen near a trough.");
            int progress=care==null||care.offspring()==null?0:(int)(1000*care.ageMinutes()/RanchCare.GROW_MINUTES);
            b.set(path+" #Fill.FlexWeight",Math.max(1,progress));b.set(path+" #Remainder.FlexWeight",Math.max(1,1000-progress));
            b.set(path+" #Progress.Visible",care!=null&&care.offspring()!=null);
            var item=Item.getAssetMap().getAsset(icon);
            b.set(path+" #Icon.TextTooltipStyle",Value.ref("Common.ui","DefaultTextTooltipStyle"));
            b.set(path+" #Icon.TooltipTextSpans",item==null?Message.raw(icon):Message.translation(item.getTranslationKey()));
        }
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        var c=current(ref,store);if(c==null){close();return;}
        if("Save".equals(data.action)) {
            var fresh=NativeHerdService.review(world,c.town(),c.plot());
            if(reviewed==null||!fresh.ready()||!fresh.pairs().equals(reviewed.pairs()))hint="The breeding pairs changed. Review the herd again.";
            else if(fresh.pairs().stream().noneMatch(pair->!RanchProducts.selected(pair.family(),c.production(),c.entry()).isEmpty()))hint="Select a herd output in barn storage first.";
            else {
                hint=c.production().herd().adopt(revision,fresh.pairs());
                if(hint==null){c.manager().updateTown(c.town());hint="The Rancher will care for these herds during work hours.";}
            }
        }else if("Supply".equals(data.action)||"Take".equals(data.action)) {
            var inventory=com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(store,ref,com.hypixel.hytale.server.core.inventory.InventoryComponent.EVERYTHING);
            boolean supply="Supply".equals(data.action);
            var stack=new com.hypixel.hytale.server.core.inventory.ItemStack(HerdProductionState.FEED,1);
            boolean changed=inventory!=null&&(supply?c.production().herd().supply(()->inventory.removeItemStack(stack,true,true).succeeded()):c.production().herd().takeFeed(()->inventory.addItemStack(stack,true,false,true).succeeded()));
            if(changed){c.manager().updateTown(c.town());hint=supply?"Wheat supplied to the barn.":"Wheat returned to your inventory.";}
            else hint=supply?"Carry wheat and leave room in the feed bin.":"Leave room in your inventory to take spare feed.";
        }else if("CourierFeed".equals(data.action)) {
            var herd=c.production().herd();
            int target=switch(herd.courierFeedTarget()){case 0->8;case 8->16;case 16->32;case 32->64;default->0;};
            if(!herd.adopted()||!herd.configureCourierFeed(feedOrderRevision,target))hint="The feed order changed. Choose it again.";
            else {
                if(target==0)for(var batch:List.copyOf(c.town().getWarehouse().batches()))
                    if(batch.herdFeed()&&plotId.equals(batch.workplaceId()))c.town().getWarehouse().cancel(batch.id());
                c.manager().updateTown(c.town());
                hint=target==0?"Courier feed stopped. Carried wheat will return to general storage.":"Couriers will bring wheat from town stock. A Warehouse Manager and employed townsfolk with Logistics enabled are needed.";
            }
        }else if("PreviousFamily".equals(data.action))familyPage=Math.max(0,familyPage-1);
        else if("NextFamily".equals(data.action))familyPage++;
        else if("Refresh".equals(data.action))hint=null;else return;
        var b=new UICommandBuilder();var e=new UIEventBuilder();build(ref,b,e,store);sendUpdate(b,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().build();
        String action;
    }
}
