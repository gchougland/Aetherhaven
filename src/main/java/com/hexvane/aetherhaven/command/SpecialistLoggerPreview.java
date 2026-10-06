package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.world.*;
import com.hypixel.hytale.builtin.crafting.component.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.joml.Vector3d;

/** Opt-in attended Logger test. Every block, supply and resident belongs to the unsaved preview. */
record SpecialistLoggerPreview(UUID plotId, WarehouseBatch.Point heat) {
    static SpecialistLoggerPreview create(World world, UUID owner, Vector3d viewpoint) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")
            && !PersistentWorldSupport.shouldPersistWorldData(world),"Enter an unsaved specialist preview.");
        var plugin=AetherhavenPlugin.get();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownForOwnerInWorld(owner);
        var plot=town.getPlotInstances().stream().filter(p->"plot_lumbermill".equals(p.getConstructionId())).findFirst().orElseThrow();
        var p=town.getOrCreatePlotProduction(plot.getPlotId());require(!p.usesPhysicalTimber(),"Logger check was already prepared.");
        var storage=ProductionStorageLocator.find(world,plot,System.currentTimeMillis());require(storage!=null,"Preview lumbermill storage not found.");
        WarehouseBatch.Point fire=null;
        for(int y=storage.y()-1;y<=storage.y()+1&&fire==null;y++)for(int x=storage.x()-2;x<=storage.x()+2&&fire==null;x++)
            for(int z=storage.z()-2;z<=storage.z()+2&&fire==null;z++)
                if(plot.containsWorldBlock(x,y,z)&&ChunkSectionBlockUtil.blockId(world,x,y,z)==0
                    &&ChunkSectionBlockUtil.blockId(world,x,y-1,z)!=0&&ChunkSectionBlockUtil.filler(world,x,y-1,z)==0)
                    fire=new WarehouseBatch.Point(x,y,z);
        require(fire!=null,"No empty supported campfire position near preview storage.");
        var root=new TimberHarvestPlan.Position((int)Math.floor(viewpoint.x),(int)Math.floor(viewpoint.y),(int)Math.floor(viewpoint.z));
        var captured=NativeTimberGrowth.capture("Wood_Beech_Trunk",root,0,pos->true);require(captured.ready(),captured.issue());
        require(ChunkSectionBlockUtil.setBlockByKey(world,root.x(),root.y()-1,root.z(),"Soil_Dirt",0),"Preview soil failed.");
        p.setIronUpgrade(1);p.addWorkplaceOutputUnlock(TimberCharring.OUTPUT);
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_lumbermill",p);
        require(p.setSlotCursorToItem(0,entry,"Wood_Beech_Trunk")&&p.setSlotCursorToItem(1,entry,TimberCharring.OUTPUT),"Logger output catalog missing.");
        var timber=p.timber();
        String issue=timber.plan(0,2,timber.layoutRevision(),captured.receipt(),pos->true,pos->NativeTimberPlanting.observe(world,pos));
        require(issue==null,issue);
        require(timber.activate(timber.layoutRevision())==null,"Grove activation failed.");
        p.addAmount("Wood_Beech_Trunk",4,100);
        var mastery=CoreVillagerMasteryService.progress(town,"logger");while(mastery.level()<4)mastery.train();
        mastery.setTool(new ItemStack("Tool_Hatchet_Iron",1));
        var resident=town.getResidentNpcRecords().stream().filter(r->"logger".equals(r.getKind())).findFirst().orElseThrow();
        var store=world.getEntityStore().getStore();var ref=store.getExternalData().getRefFromUUID(resident.getLastEntityUuid());
        require(ref!=null&&ref.isValid(),"Preview Logger is not loaded.");store.putComponent(ref,VillagerNeeds.getComponentType(),VillagerNeeds.full());
        require(ChunkSectionBlockUtil.setBlockByKey(world,fire.x(),fire.y(),fire.z(),"Bench_Campfire",0),"Preview campfire failed.");
        var time=store.getResource(WorldTimeResource.getResourceType());
        time.setGameTime(time.getGameTime().truncatedTo(ChronoUnit.DAYS).plus(8,ChronoUnit.HOURS),world,store);
        world.getWorldConfig().setGameTimePaused(false);world.getWorldConfig().setBlockTicking(true);manager.updateTown(town);
        return new SpecialistLoggerPreview(plot.getPlotId(),fire);
    }
    String toggleFire(World world) {
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,heat.x(),heat.y(),heat.z());require(ref!=null&&ref.isValid(),"Preview campfire is not loaded.");
        var store=world.getChunkStore().getStore();var fire=store.getComponent(ref,ProcessingBenchBlock.getComponentType());require(fire!=null,"Preview campfire missing.");
        boolean light=!fire.isActive();
        if(light)fire.getFuelContainer().setItemStackForSlot((short)0,new ItemStack("Wood_Beech_Trunk",16),false);
        fire.setActive(light,store.getComponent(ref,BenchBlock.getComponentType()),null);
        return "Preview campfire "+(light?"lit":"extinguished")+" at "+heat+".";
    }
    String status(World world,UUID owner) {
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get()).findTownForOwnerInWorld(owner);
        var p=town.peekPlotProduction(plotId);
        var fireRef=ChunkSectionBlockUtil.blockEntityRefAt(world,heat.x(),heat.y(),heat.z());
        var fire=fireRef==null||!fireRef.isValid()?null:fireRef.getStore().getComponent(fireRef,ProcessingBenchBlock.getComponentType());
        return "Preview Logger: Beech "+p.getAmount("Wood_Beech_Trunk")+", Burnt "+p.getAmount(TimberCharring.OUTPUT)
            +", Beech saplings "+p.getAmount("Plant_Sapling_Beech")+", sap "+p.getAmount("Ingredient_Tree_Sap")+", sticks "+p.getAmount("Ingredient_Stick")
            +", planted trees "+p.timber().trees().size()+", pending products "+p.timber().waitingProducts()
            +", cargo "+p.timber().cargo()+", tool "+CoreVillagerMasteryService.toolId(town,"logger")
            +", fire active "+(fire!=null&&fire.isActive())+", fuel seconds "+(fire==null?0:fire.getFuelTime())
            +". Four fixture logs supplied; normal attended work must convert and deliver them. Use loggerfire, loggertool, loggergrove or visit --building=plot_lumbermill.";
    }
    String toggleTool(World world,UUID owner) {
        require(world.getName().startsWith("aetherhaven_specialist_preview_")
            && !PersistentWorldSupport.shouldPersistWorldData(world),"Enter an unsaved specialist preview.");
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,AetherhavenPlugin.get());
        var town=manager.findTownForOwnerInWorld(owner);
        var progress=CoreVillagerMasteryService.progress(town,"logger");
        String next="Tool_Hatchet_Iron".equals(CoreVillagerMasteryService.toolId(town,"logger"))
            ?"Tool_Hatchet_Crude":"Tool_Hatchet_Iron";
        // Exercise the ordinary equipment exchange against fixture inventory, never the player's bag.
        var bag=new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short)1);
        var tool=new ItemStack(next,1);bag.setItemStackForSlot((short)0,tool);
        String issue=CoreVillagerMasteryService.equip(progress,"logger",bag,(short)0,tool,progress.tool());
        require(issue==null,issue);manager.updateTown(town);
        return "Preview Logger equipped "+next+". Work and cargo were not changed.";
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
