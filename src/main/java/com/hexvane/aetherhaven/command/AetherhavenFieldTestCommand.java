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
                // The Spirit Garden runs in a second world whose blocks tick, so the game's own support rule is at work
                // on every plant for its whole life, as it is in a real town.
                String gardenName="aetherhaven_garden_check_"+UUID.randomUUID().toString().replace("-","");int firstPlant=index;
                chain=chain.thenCompose(ignored->universe.makeWorld(gardenName,universe.validateWorldPath(gardenName),gardenWorld())).thenCompose(living->{
                    CompletableFuture<Void> plants=CompletableFuture.completedFuture(null);int offset=firstPlant;
                    for(var plant:SpiritGardenRules.get().plants()) {
                        int at=offset++;
                        plants=plants.thenCompose(ignored->exerciseGarden(living,plant,at).handle((done,error)->{
                            if(error==null){results[0]++;report(ctx,plant.plant()+": Spirit Planter sprout, three rites, harvest, regrowth, saved delivery and block support passed.");}
                            else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,plant.plant()+": FAILED: "+cause.getMessage());}
                            return null;
                        }));
                    }
                    return plants.whenComplete((done,error)->universe.removeWorld(gardenName));
                });
                // The Scarecrow Fields: every crop through the Farmer's whole round, a second Scarecrow, old saves and the Almanac.
                chain=chain.thenCompose(ignored->FarmFieldWorldCheck.run(universe,results,message->report(ctx,message)));
                return chain.orTimeout(420,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
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
            // Only the crop is chosen as an output. The harvest must still bring Life Essence back.
            require(NativeFarmCrops.harvest(world,town,plot,production,bed,Set.of(crop.output()),100,()->{}),"Native harvest failed");
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
    /** The farm fixture's world, except that blocks tick: block support is checked by the game itself, not only asked about. */
    private static WorldConfig gardenWorld() {
        var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(true);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        return config;
    }
    /** The game's own answer to "does the block in this cell stand here", asked directly so it needs no block tick. */
    private static int support(World world,int x,int y,int z) {
        var type=ChunkSectionBlockUtil.blockType(world,x,y,z);
        var noFluid=com.hypixel.hytale.server.core.asset.type.fluid.Fluid.getAssetMap().getAsset(com.hypixel.hytale.server.core.asset.type.fluid.Fluid.EMPTY_ID);
        return com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.testBlockPhysics(new com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.SupportReader() {
            @Override public boolean isPositionAvailable(int px,int py,int pz){return ChunkSectionBlockUtil.blockSectionAt(world,px,py,pz)!=null;}
            @Override public com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.SupportBlock getBlock(int px,int py,int pz) {
                return new com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.SupportBlock(ChunkSectionBlockUtil.blockId(world,px,py,pz),
                    ChunkSectionBlockUtil.rotationIndex(world,px,py,pz),ChunkSectionBlockUtil.filler(world,px,py,pz),
                    com.hypixel.hytale.server.core.asset.type.fluid.Fluid.EMPTY_ID,noFluid);
            }
            @Override public int getSupportValue(int px,int py,int pz){return 0;}
        },x,y,z,type,ChunkSectionBlockUtil.rotationIndex(world,x,y,z),0);
    }
    /**
     * One growth stage, judged by the game's own rules: it stands on a Spirit Planter, it would not stand on plain
     * stone or on Crystal Soil, losing its planter removes it without a drop, and a player who breaks it gets nothing.
     * The planter is swapped and put back inside one world task, so no block tick sees the swap.
     */
    private static void requireStands(World world,int x,int y,int z,String stage) {
        var type=ChunkSectionBlockUtil.blockType(world,x,y,z);
        int satisfied=com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.SATISFIES_SUPPORT,fails=com.hypixel.hytale.builtin.blockphysics.BlockPhysicsUtil.DOESNT_SATISFY;
        require(support(world,x,y,z)==satisfied,stage+" does not stand on a Spirit Planter by the game's support rule: "+support(world,x,y,z));
        for(String ground:List.of("Rock_Stone",com.hexvane.aetherhaven.workplace.AlchemicalCrop.SOIL)) {
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y-1,z,ground,0),"Test ground placement failed");
            int without=support(world,x,y,z);
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y-1,z,SpiritGarden.BLOCK,0),"The Spirit Planter could not be put back");
            require(without==fails,stage+" would stand on "+ground+" without its planter: "+without);
        }
        require(type.getSupportDropType()==com.hypixel.hytale.server.core.asset.type.blocktype.config.SupportDropType.DESTROY,stage+" would drop as an item when its planter goes");
        var soft=type.getGathering()==null?null:type.getGathering().getSoft();
        require(soft!=null&&com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils.getDrops(type,1,soft.getItemId(),soft.getDropListId()).isEmpty(),
            stage+" gives items when a player breaks it");
    }
    /** Waits for block ticks to do something, looking every 150 milliseconds for up to six seconds. */
    private static CompletableFuture<Void> eventually(World world,java.util.function.BooleanSupplier done,String issue,int tries) {
        var seen=new boolean[1];
        return after(world,()->seen[0]=done.getAsBoolean()).thenCompose(ignored->seen[0]?CompletableFuture.completedFuture(null)
            :tries<=1?CompletableFuture.failedFuture(new IllegalStateException(issue)):eventually(world,done,issue,tries-1));
    }
    /**
     * A Spirit Planter with one plant, taken from seed to harvest and on to a second sprout, in a world whose blocks
     * tick. After every change the plant's cell is given a block update and a few ticks, and it must still be there.
     */
    private static CompletableFuture<Void> exerciseGarden(World world,SpiritGardenRules.Plant plant,int offset) {
        int x=16+64*offset,y=128,z=16;var block=new WarehouseBatch.Point(x,y-1,z);
        var production=new PlotProductionState();GardenUpgrades.mapLegacy(production);var garden=production.garden();
        var entry=ProductionCatalog.loadFromClasspath(AetherhavenFieldTestCommand.class.getClassLoader()).get(SpiritGarden.CONSTRUCTION);
        var loads=new ArrayList<CompletableFuture<?>>();
        for(int section=3;section<=4;section++)loads.add(world.getChunkStore().getChunkSectionReferenceAsync(ChunkUtil.chunkCoordinate(x),section,0,GetChunkFlags.SET_TICKING));
        var chain=CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenCompose(ignored->onWorld(world,()->{
            require(entry!=null,"The Conservatory has no catalog entry");
            require(world.getWorldConfig().isBlockTicking(),"The garden world's blocks do not tick");
            require(!SpiritGardenWorld.planterStands(world,block),"Empty ground read as a planter");
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y-1,z,SpiritGarden.BLOCK,0),"Spirit Planter placement failed");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            require(SpiritGardenWorld.planterStands(world,block)&&!SpiritGardenWorld.planterMissing(world,block),"The Spirit Planter does not stand");
            require(garden.bind(0,2,0,block,plant.plant())==null,"Choosing a seed failed");
            var planter=garden.planters().get(0);long rest=GardenUpgrades.restSeconds(production),now=SpiritGardenWorld.now(world);
            require(now<0,"The game's clock was expected to count from year 1, far below zero: "+now);
            require(SpiritGardenWorld.cellFree(world,planter.cell())&&SpiritGardenWorld.stageAt(world,planter,plant)==SpiritGardenWorld.NONE,"The cell above a new planter is not free");
            require(garden.plant(0,planter.id(),now,rest,()->SpiritGardenWorld.show(world,planter,plant,0)),"The sprout was not planted");
            require(SpiritGardenWorld.stageAt(world,planter,plant)==0,"The sprout is not standing on the planter");
            require(!garden.rite(0,planter.id(),now,rest,stage->SpiritGardenWorld.show(world,planter,plant,stage)),"A resting plant grew");
            ChunkSectionBlockUtil.performBlockUpdate(world,x,y,z,false);
        }));
        for(int rite=1;rite<=SpiritGardenState.RIPE;rite++) {
            int stage=rite;
            chain=chain.thenCompose(ignored->after(world,()->{
                var planter=garden.planters().get(0);long rest=GardenUpgrades.restSeconds(production),now=planter.restUntil();
                require(SpiritGardenWorld.stageAt(world,planter,plant)==stage-1,"Stage "+(stage-1)+" did not keep standing while blocks ticked");
                requireStands(world,x,y,z,"Stage "+(stage-1));
                require(garden.rite(0,planter.id(),now,rest,next->SpiritGardenWorld.show(world,planter,plant,next)),"Rite "+stage+" failed");
                require(SpiritGardenWorld.stageAt(world,planter,plant)==stage,"Rite "+stage+" did not show its stage");
                ChunkSectionBlockUtil.performBlockUpdate(world,x,y,z,false);
            }));
        }
        chain=chain.thenCompose(ignored->after(world,()->{
            var planter=garden.planters().get(0);long rest=GardenUpgrades.restSeconds(production),now=SpiritGardenWorld.now(world)+10*rest;
            require(planter.ripe()&&SpiritGardenWorld.stageAt(world,planter,plant)==SpiritGardenState.RIPE,"The ripe plant did not keep standing while blocks ticked");
            requireStands(world,x,y,z,"The ripe plant");
            var yields=SpiritGardenWorld.rollHarvest(plant,GardenUpgrades.plantsPerHarvest(production),GardenUpgrades.petalRolls(production));
            require(yields.getOrDefault(plant.plant(),0)==GardenUpgrades.plantsPerHarvest(production),"Harvest gave "+yields+" plants");
            require(yields.getOrDefault(plant.petal(),0)>=GardenUpgrades.petalRolls(production),"Harvest gave too few petals: "+yields);
            require(garden.harvest(0,planter.id(),yields,now,rest,()->SpiritGardenWorld.show(world,planter,plant,0)),"Harvest failed");
            require(SpiritGardenWorld.stageAt(world,planter,plant)==0&&garden.planters().get(0).stage()==0,"The plant did not start again");
            require(production.getAmount(plant.plant())==0,"Harvest reached the stores without a delivery");
            var saved=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
            var delivered=saved.garden().deliverAll(saved,item->GardenUpgrades.capacity(saved,entry,item));
            require(delivered.equals(yields)&&saved.garden().deliverAll(saved,item->1000).isEmpty(),"Saved harvest was not delivered exactly once");
            require(saved.getAmount(plant.plant())==yields.get(plant.plant())&&saved.getAmount(plant.petal())==yields.get(plant.petal()),"Incorrect stock");
            // A block a player puts above the planter is never replaced.
            require(SpiritGardenWorld.clear(world,garden.planters().get(0),plant)&&SpiritGardenWorld.stageAt(world,planter,plant)==SpiritGardenWorld.NONE,"Uprooting left the plant behind");
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,"Rock_Stone",0),"Test block placement failed");
            require(!SpiritGardenWorld.show(world,planter,plant,0)&&!SpiritGardenWorld.clear(world,planter,plant),"The garden changed a block that is not its plant");
            require(ChunkSectionBlockUtil.setBlockEmpty(world,x,y,z,0),"Test cleanup failed");
            // The ripe plant goes back, and its cell is updated so the game checks it before the planter is broken.
            require(SpiritGardenWorld.show(world,planter,plant,SpiritGardenState.RIPE),"The ripe plant could not be set again");
            ChunkSectionBlockUtil.performBlockUpdate(world,x,y,z,false);
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var planter=garden.planters().get(0);
            require(SpiritGardenWorld.stageAt(world,planter,plant)==SpiritGardenState.RIPE,"The ripe plant did not stay on its planter before the planter was broken");
            // Breaking the planter the way the game breaks a block: gone, with a block update for its neighbours.
            require(ChunkSectionBlockUtil.setBlockEmpty(world,x,y-1,z,com.hypixel.hytale.server.core.universe.world.SetBlockSettings.PERFORM_BLOCK_UPDATE),"The planter could not be broken");
        }));
        chain=chain.thenCompose(ignored->eventually(world,()->SpiritGardenWorld.cellFree(world,new WarehouseBatch.Point(x,y,z)),
            "The plant stayed in the air after its planter was broken",40));
        return chain.thenCompose(ignored->onWorld(world,()->{
            var planter=garden.planters().get(0);
            require(SpiritGardenWorld.planterMissing(world,block)&&!SpiritGardenWorld.show(world,planter,plant,0),"A plant was set with no planter beneath it");
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
