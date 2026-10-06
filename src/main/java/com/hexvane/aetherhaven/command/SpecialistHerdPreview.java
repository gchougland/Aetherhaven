package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.joml.Vector3d;

/** Ordinary Rancher actions with opt-in accelerated care time in an unsaved Barn copy only. */
record SpecialistHerdPreview(UUID plotId) {
    private static final String OUTPUT="Ingredient_Fabric_Scrap_Wool";
    static SpecialistHerdPreview create(World world,UUID owner) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")&&!PersistentWorldSupport.shouldPersistWorldData(world),"Use the unsaved preview.");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);require(town!=null,"Preview town missing.");
        var plot=town.getPlotInstances().stream().filter(p->"plot_barn".equals(p.getConstructionId())).findFirst().orElseThrow();
        var state=town.getOrCreatePlotProduction(plot.getPlotId());require(!state.usesPhysicalHerd(),"Preview barn is already active.");
        require(ProductionStorageLocator.find(world,plot,System.currentTimeMillis())!=null,"Preview barn storage missing.");
        var anchor=plot.getStoredPrefabWorldAnchor();require(anchor!=null,"Preview barn placement missing.");
        var trough=new WarehouseBatch.Point(anchor.x+4,anchor.y,anchor.z+4);
        // The native trough occupies its base and the block to its west.
        for(int x=trough.x()-1;x<=trough.x();x++)for(int z=trough.z();z<=trough.z()+2;z++)
            require(plot.containsWorldBlock(x,trough.y(),z)&&VillagerBlockUtil.isNpcStandColumn(world,x,trough.y(),z),"Barn test pen is not clear.");
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_barn",state);
        require(entry!=null&&state.setSlotCursorToItem(0,entry,OUTPUT),"Wool output unavailable.");
        require(ChunkSectionBlockUtil.setBlockByKey(world,trough.x(),trough.y(),trough.z(),"Bench_Trough",0),"Preview trough placement failed.");
        var store=world.getEntityStore().getStore();var breeders=new ArrayList<UUID>();
        for(int dx=-1;dx<=0;dx++) {
            var npc=NPCPlugin.get().spawnNPC(store,"Sheep",null,new Vector3d(trough.x()+dx+.5,trough.y(),trough.z()+2.5),Rotation3f.ZERO);
            require(npc!=null,"Preview sheep spawn failed.");breeders.add(store.getComponent(npc.first(),UUIDComponent.getComponentType()).getUuid());
        }
        require(state.herd().adopt(state.herd().revision(),List.of(new HerdProductionState.Pair("Sheep",breeders,trough)))==null,"Preview herd adoption failed.");
        for(int i=0;i<16;i++)require(state.herd().supply(()->true),"Preview feed supply failed.");
        var mastery=com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"rancher");while(mastery.level()<10)mastery.train();
        var resident=town.getResidentNpcRecords().stream().filter(r->"rancher".equals(r.getKind())).findFirst().orElseThrow();
        var ref=store.getExternalData().getRefFromUUID(resident.getLastEntityUuid());require(ref!=null&&ref.isValid(),"Preview Rancher is not loaded.");
        store.putComponent(ref,VillagerNeeds.getComponentType(),VillagerNeeds.full());
        var time=store.getResource(WorldTimeResource.getResourceType());
        time.setGameTime(time.getGameTime().truncatedTo(ChronoUnit.DAYS).plus(8,ChronoUnit.HOURS),world,store);
        world.getWorldConfig().setGameTimePaused(false);world.getWorldConfig().setBlockTicking(true);manager.updateTown(town);
        return new SpecialistHerdPreview(plot.getPlotId());
    }
    void advance(World world,UUID owner) {
        var plugin=AetherhavenPlugin.get();var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin).findTownForOwnerInWorld(owner);
        var plot=town.findPlotById(plotId);var state=town.peekPlotProduction(plotId);var herd=state.herd().herds().get("Sheep");
        require(!herd.hungry()&&state.herd().cargo()==null,"Wait for ordinary feeding and delivery before advancing care time.");
        require(!herd.care().ready(),"Wait for the Rancher to tend the ready animal.");
        require(NativeHerdService.pairReady(world,town,plot,herd.pair()),"Breeders must remain in their loaded pen.");
        require(herd.care().offspring()==null||NativeHerdService.offspring(world,town,plot,herd)!=null,"The raised animal is outside its loaded herd area.");
        NativeHerdService.advance(world,plugin,HerdProductionState.FEED_INTERVAL);
    }
    String status(World world,UUID owner) {
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        var state=town.peekPlotProduction(plotId);var herd=state.herd().herds().get("Sheep");
        var store=world.getEntityStore().getStore();
        var ref=herd.care().offspring()==null?null:store.getExternalData().getRefFromUUID(herd.care().offspring());
        var transform=ref==null||!ref.isValid()?null:store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        String position=transform==null?"none":transform.getPosition().toString();
        var resident=town.getResidentNpcRecords().stream().filter(r->"rancher".equals(r.getKind())).findFirst().orElseThrow();
        var worker=store.getExternalData().getRefFromUUID(resident.getLastEntityUuid());
        var workerTransform=worker==null||!worker.isValid()?null:store.getComponent(worker,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        return "Preview Rancher: wool "+state.getAmount(OUTPUT)+", feed "+state.herd().feed()+", hungry "+herd.hungry()+", cargo "+state.herd().cargo()
            +", breeders ready "+NativeHerdService.pairReady(world,town,town.findPlotById(plotId),herd.pair())+", offspring "+herd.care().offspring()+" at "+position+". "+herd.care().status()
            +" Worker "+(workerTransform==null?"unloaded":workerTransform.getPosition())+". "+com.hexvane.aetherhaven.autonomy.HerdWorkService.describe(worker)
            +". Use herdage only to advance six simulated care hours. Feeding, birth, maturation, collection and delivery are ordinary worker actions.";
    }
    private static void require(boolean value,String issue){if(!value)throw new IllegalStateException(issue);}
}
