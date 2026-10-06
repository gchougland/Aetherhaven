package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.FoodSiteService;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Operator-only disposable world. Never adopts a player's farm or writes a town to the town manager. */
final class AetherhavenFieldTestCommand extends AbstractCommandCollection {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    AetherhavenFieldTestCommand(){super("fields","Inspect native Farmer crops in a temporary world");setPermissionGroups("hytale:WorldEditor");addSubCommand(new WorldCheck());}
    private static final class WorldCheck extends AbstractAsyncCommand {
        WorldCheck(){super("worldcheck","Test core crop harvest and regrowth in an isolated world");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"A field test is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_field_check_"+UUID.randomUUID().toString().replace("-","");
            var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture path exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
            config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
            config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world->{
                CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);int[] results={0,0};int index=0;
                for(var crop:NativeFarmCrops.crops()) {
                    int offset=index++;
                    chain=chain.thenCompose(ignored->exercise(world,crop,offset).handle((done,error)->{
                        if(error==null){results[0]++;report(ctx,crop.output()+": native produce, Life Essence, regrowth and saved delivery passed.");}
                        else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,crop.output()+": FAILED: "+cause.getMessage());}
                        return null;
                    }));
                }
                for(String family:List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
                    var crop=new com.hexvane.aetherhaven.workplace.AlchemicalCrop(family,tier);int offset=index++;
                    chain=chain.thenCompose(ignored->exerciseDruid(world,crop,offset).handle((done,error)->{
                        if(error==null){results[0]++;report(ctx,crop.item()+": Druid review, native regrowth and saved delivery passed.");}
                        else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,crop.item()+": FAILED: "+cause.getMessage());}
                        return null;
                    }));
                }
                return chain.orTimeout(90,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                    report(ctx,"Field isolated world check: "+results[0]+" passed, "+results[1]+" failed"+(error==null?".":"; interrupted."));universe.removeWorld(name);
                });
            }).whenComplete((done,error)->RUNNING.set(false));
        }
    }
    private static CompletableFuture<Void> exercise(World world,NativeFarmCrops.Crop crop,int offset) {
        int x=16+64*offset,y=128,z=16;var point=new WarehouseBatch.Point(x,y,z);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),world.getName(),x,y,z,1,1,0);
        var plot=new PlotInstance(UUID.randomUUID(),"plot_farm",PlotInstanceState.COMPLETE,new PlotFootprintRecord(x-2,y-1,z-2,x+2,y+5,z+2),x,y,z,0);
        town.getPlotInstances().add(plot);var production=town.getOrCreatePlotProduction(plot.getPlotId());
        var bed=new FieldProductionState.Bed(point,crop.block(),crop.output());
        var loads=new ArrayList<CompletableFuture<?>>();
        for(int section=3;section<=4;section++)loads.add(world.getChunkStore().getChunkSectionReferenceAsync(ChunkUtil.chunkCoordinate(x),section,0,GetChunkFlags.SET_TICKING));
        var chain=CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenCompose(ignored->onWorld(world,()->{
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y-1,z,"Soil_Dirt_Tilled",0),"Soil placement failed");
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,crop.block(),0),"Crop placement failed");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var review=NativeFarmCrops.review(world,town,plot);require(review.ready()&&review.beds().equals(List.of(bed)),"Field review: "+review.issue());
            require(production.field().adopt(0,review.beds())==null,"Field adoption failed");
            require(NativeFarmCrops.needsWater(world,bed),"Fresh soil does not need watering");
            require(FoodSiteService.waterSoil(world,point),"Native soil could not be watered");
            require(!NativeFarmCrops.needsWater(world,bed),"Watering did not persist on native soil");
            forceMature(world,point,crop);
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            require(NativeFarmCrops.ripe(world,bed),"Final native stage is not harvestable");
            verifyMasteryGate(world,town,plot,production,bed,"farmer");
            require(NativeFarmCrops.harvest(world,town,plot,production,bed,Set.of(crop.output(),NativeFarmCrops.LIFE_ESSENCE),()->{}),"Native harvest failed");
            require(production.getAmount(crop.output())==0,"Harvest credited stock before delivery");
            require(production.field().pending()!=null&&production.field().cargo()==null,"Harvest credited cargo before its deferred crop transition");
            require(!NativeFarmCrops.harvest(world,town,plot,production,bed),"Pending transition allowed a second harvest");
        }));
        return chain.thenCompose(ignored->after(world,()->{
            require(!NativeFarmCrops.ripe(world,bed),"Crop did not regrow after harvest");
            require(production.field().pending()==null&&production.field().cargo()!=null,"Native transition did not settle its harvest");
            require(!NativeFarmCrops.harvest(world,town,plot,production,bed),"Same crop harvested twice");
            var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
            require(copy.field().cargo()!=null,"Held harvest lost on reload");
            int total=copy.field().heldQuantity();require(total>=2,"Missing native harvest byproducts");
            var delivered=new HashMap<String,Integer>();
            while(copy.field().cargo()!=null) {
                var held=copy.field().cargo();var id=held.id();
                require(copy.field().deliver(copy,id,0)==0,"Full storage accepted harvest");
                require(copy.field().deliver(copy,id,1)==1,"First partial delivery failed");
                require(copy.field().deliver(copy,id,1)==0,"Full storage or stale claim accepted harvest");
                if(held.quantity()>1)require(copy.field().deliver(copy,id,held.quantity())==held.quantity()-1,"Remaining harvest lost");
                require(copy.field().deliver(copy,id,4096)==0,"Old claim duplicated another product");
                delivered.put(held.output(),held.quantity());
            }
            require(delivered.size()==2&&delivered.containsKey(crop.output())&&delivered.containsKey(NativeFarmCrops.LIFE_ESSENCE),"Unexpected native products: "+delivered);
            require(delivered.values().stream().mapToInt(Integer::intValue).sum()==total,"Held total changed");
            for(var output:delivered.entrySet())require(copy.getAmount(output.getKey())==output.getValue(),"Harvest lost or duplicated");
            require(NativeFarmCrops.usable(world,town,plot,bed),"Regrowth lost crop identity");
            var resident=new ResidentNpcRecord();resident.setKind("farmer");town.getResidentNpcRecords().add(resident);
            var work=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.FARMING;
            require(town.getApprenticeWorkplaces().unlock(town,work)==null,"Test food workplace unlock failed");
            require(town.getApprenticeWorkplaces().save(town,null,work,new com.hexvane.aetherhaven.workplace.WorkplaceArea(x,y,z,x+2,z+2),"Test food beds")==null,"Test food workplace placement failed");
            require(!NativeFarmCrops.usable(world,town,plot,bed),"Core Farmer can harvest an Apprentice food bed");
            require(NativeFarmCrops.review(world,town,plot).beds().isEmpty(),"Field review includes an Apprentice workplace");
        }));
    }
    private static CompletableFuture<Void> exerciseDruid(World world,com.hexvane.aetherhaven.workplace.AlchemicalCrop crop,int offset) {
        int x=16+64*offset,y=128,z=16;var point=new WarehouseBatch.Point(x,y,z);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),world.getName(),x,y,z,1,1,0);
        var plot=new PlotInstance(UUID.randomUUID(),NativeDruidCrops.CONSTRUCTION,PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(x-2,y-1,z-2,x+2,y+5,z+2),x,y,z,0);
        town.getPlotInstances().add(plot);var production=town.getOrCreatePlotProduction(plot.getPlotId());
        var bed=new FieldProductionState.Bed(point,crop.plantedBlock(),crop.item());
        var loads=new ArrayList<CompletableFuture<?>>();
        for(int section=3;section<=4;section++)loads.add(world.getChunkStore().getChunkSectionReferenceAsync(ChunkUtil.chunkCoordinate(x),section,0,GetChunkFlags.SET_TICKING));
        var chain=CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenCompose(ignored->onWorld(world,()->{
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y-1,z,com.hexvane.aetherhaven.workplace.AlchemicalCrop.SOIL,0),"Crystal soil placement failed");
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,crop.plantedBlock(),0),"Plant placement failed");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var review=NativeFarmCrops.review(world,town,plot);
            require(review.ready()&&review.beds().equals(List.of(bed)),"Conservatory review: "+review.issue());
            require(production.field().adopt(0,review.beds())==null,"Conservatory adoption failed");
            require(!NativeFarmCrops.needsWater(world,bed),"Crystal soil incorrectly requires ordinary watering");
            require(!NativeFarmCrops.harvest(world,town,plot,production,bed),"Immature plant harvested");
            forceMature(world,point,new NativeFarmCrops.Crop(crop.plantedBlock(),crop.item()));
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            require(NativeFarmCrops.ripe(world,bed),"Final alchemical stage is not mature");
            verifyMasteryGate(world,town,plot,production,bed,"druid");
            require(NativeFarmCrops.harvest(world,town,plot,production,bed),"Druid harvest failed");
            require(production.field().pending()!=null&&production.getAmount(crop.item())==0,"Harvest skipped deferred confirmation or physical delivery");
            require(!NativeFarmCrops.harvest(world,town,plot,production,bed),"Pending harvest repeated");
        }));
        return chain.thenCompose(ignored->after(world,()->{
            require(!NativeFarmCrops.ripe(world,bed)&&NativeFarmCrops.usable(world,town,plot,bed),"Native regrowth lost crop identity");
            require(production.field().pending()==null&&production.field().heldQuantity()==1,"Regrowth did not settle exactly one ingredient");
            var saved=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
            var cargo=saved.field().cargo();require(cargo!=null,"Saved harvest missing");
            require(saved.field().deliver(saved,cargo.id(),0)==0,"Full storage accepted delivery");
            require(saved.field().deliver(saved,cargo.id(),25)==1,"Physical delivery lost ingredient");
            require(saved.field().deliver(saved,cargo.id(),25)==0,"Saved cargo paid twice");
            require(saved.getAmount(crop.item())==1,"Incorrect ingredient count");
        }));
    }
    private static void verifyMasteryGate(World world,TownRecord town,PlotInstance plot,PlotProductionState production,FieldProductionState.Bed bed,String kind) {
        if(com.hexvane.aetherhaven.mastery.CoreProductionRequirements.requirement(kind,bed.output()).mastery()>1) {
            require(!NativeFarmCrops.harvest(world,town,plot,production,bed),"Untrained core villager harvested a locked crop");
            require(production.field().pending()==null&&production.field().cargo()==null&&NativeFarmCrops.ripe(world,bed),"Blocked harvest changed the crop or its accounting");
        }
        var mastery=com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,kind);
        while(mastery.train()){}
    }
    private static void forceMature(World world,WarehouseBatch.Point p,NativeFarmCrops.Crop crop) {
        var base=BlockType.getAssetMap().getAsset(crop.block());var farming=base.getFarming();var stages=farming.getStages().get(farming.getStartingStageSet());
        var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,p.x(),p.y(),p.z());require(ref!=null&&ref.isValid(),"Crop has no farming entity");
        var store=world.getChunkStore().getStore();var growth=store.getComponent(ref,FarmingBlock.getComponentType());
        require(growth!=null,"Crop has no growth component");
        growth.setCurrentStageSet(farming.getStartingStageSet());growth.setPreviousBlockType(crop.block());
        growth.setGeneration(stages.length-1);growth.setGrowthProgress(stages.length-1);
        var section=ChunkSectionBlockUtil.sectionRefAt(world,p.x(),p.y(),p.z());
        stages[stages.length-1].apply(store,section,ref,ChunkUtil.localCoordinate(p.x()),ChunkUtil.localCoordinate(p.y()),ChunkUtil.localCoordinate(p.z()),stages[0]);
    }
    private static CompletableFuture<Void> after(World world,Runnable action){return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(150,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));}
    private static CompletableFuture<Void> onWorld(World world,Runnable action){return CompletableFuture.runAsync(action,world);}
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
