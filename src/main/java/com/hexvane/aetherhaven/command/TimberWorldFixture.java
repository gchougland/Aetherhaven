package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/** Explicit operator test only. Creates and removes its own unique void world; never receives a player world. */
final class TimberWorldFixture {
    private TimberWorldFixture() {}
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    static CompletableFuture<Void> run(Consumer<String> report) {
        return run(report,false);
    }
    static CompletableFuture<Void> runCharring(Consumer<String> report) {
        return run(report,true);
    }
    private static CompletableFuture<Void> run(Consumer<String> report,boolean charringOnly) {
        if(!RUNNING.compareAndSet(false,true)){report.accept("A timber world check is already running.");return CompletableFuture.completedFuture(null);}
        String name="aetherhaven_timber_check_"+UUID.randomUUID().toString().replace("-","");
        var universe=Universe.get();var path=universe.validateWorldPath(name);
        if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture path already exists"));}
        var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);config.setSavingPlayers(false);
        config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(false);
        config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        report.accept("Starting isolated timber world check. Your town is not used.");
        return universe.makeWorld(name,path,config).thenCompose(world->{
            CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);var results=new int[2];
            var woods=charringOnly?List.<String>of():NativeTimberGrowth.species().stream().map(NativeTimberGrowth.Species::output).toList();
            for(int i=0;i<woods.size();i++) {
                String wood=woods.get(i);int index=i;
                chain=chain.thenCompose(ignored->exercise(world,wood,index).handle((done,error)->{
                    if(error==null){results[0]++;report.accept(wood+": native stages, harvest and ground restoration passed.");}
                    else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report.accept(wood+": FAILED: "+cause.getMessage());}
                    return null;
                }));
            }
            if(charringOnly)chain=chain.thenCompose(ignored->charring(world)).handle((done,error)->{
                if(error==null){results[0]++;report.accept("Burnt wood: native cold/lit/extinguished campfire, bounded conversion and saved delivery passed.");}
                else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report.accept("Burnt wood FAILED: "+cause.getMessage());}
                return null;
            });
            // Removal from outside the world thread waits for shutdown before deleting its temporary directory.
            return chain.orTimeout(Math.max(90,woods.size()*10L),TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                report.accept("Timber isolated world check: "+results[0]+" passed, "+results[1]+" failed"+(error==null?".":"; interrupted: "+error.getClass().getSimpleName()));
                universe.removeWorld(name);
            });
        }).whenComplete((done,error)->RUNNING.set(false));
    }
    private static CompletableFuture<Void> charring(World world) {
        var storage=new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(16,128,16);
        var heat=new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(17,128,16);
        var plot=new com.hexvane.aetherhaven.town.PlotInstance(UUID.randomUUID(),"plot_lumbermill",
            com.hexvane.aetherhaven.town.PlotInstanceState.COMPLETE,
            new com.hexvane.aetherhaven.town.PlotFootprintRecord(13,127,13,20,132,20),16,128,16,System.currentTimeMillis());
        return CompletableFuture.allOf(
            world.getChunkStore().getChunkSectionReferenceAsync(0,3,0,GetChunkFlags.SET_TICKING),
            world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING))
            .thenCompose(ignored->onWorld(world,()->{
                require(ChunkSectionBlockUtil.setBlockByKey(world,16,127,16,"Rock_Stone",0)
                    &&ChunkSectionBlockUtil.setBlockByKey(world,17,127,16,"Rock_Stone",0),"Charring floor placement failed");
                require(ChunkSectionBlockUtil.setBlockByKey(world,16,128,16,com.hexvane.aetherhaven.AetherhavenConstants.BLOCK_PRODUCTION_STORAGE,0),"Charring storage placement failed");
                require(ChunkSectionBlockUtil.setBlockByKey(world,17,128,16,"Bench_Campfire",0),"Charring campfire placement failed");
            })).thenCompose(ignored->afterNativeQueue(world,()->{
                require(!TimberCharring.heatAvailable(world,plot,storage,heat),"Cold campfire supplied heat");
                var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,17,128,16);require(ref!=null&&ref.isValid(),"Campfire block entity missing");
                var store=world.getChunkStore().getStore();
                var fire=store.getComponent(ref,com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock.getComponentType());
                require(fire!=null,"Native campfire processor missing");
                fire.getFuelContainer().setItemStackForSlot((short)0,new com.hypixel.hytale.server.core.inventory.ItemStack("Wood_Beech_Trunk",4),false);
                require(fire.setActive(true,store.getComponent(ref,com.hypixel.hytale.builtin.crafting.component.BenchBlock.getComponentType()),
                    store.getComponent(ref,com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo.getComponentType())),"Campfire did not light");
                world.getWorldConfig().setGameTimePaused(false);world.getWorldConfig().setBlockTicking(true);
            })).thenCompose(ignored->awaitHeat(world,plot,storage,heat,0)).thenCompose(ignored->onWorld(world,()->{
                require(heat.equals(TimberCharring.findHeat(world,plot,storage)),"Nearby lit campfire not found");
                var p=new com.google.gson.Gson().fromJson("{\"timber\":{\"adopted\":true}}",PlotProductionState.class);
                p.addWorkplaceOutputUnlock(TimberCharring.OUTPUT);p.setIronUpgrade(1);
                var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
                var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_lumbermill",p);
                require(p.setSlotCursorToItem(0,entry,"Wood_Beech_Trunk")&&p.setSlotCursorToItem(1,entry,TimberCharring.OUTPUT),"Charring output catalog missing");
                p.addAmount("Wood_Beech_Trunk",2,100);var stock=new com.hexvane.aetherhaven.warehouse.TownWarehouse();
                require(TimberCharring.process(p,entry,stock,plot.getPlotId(),"Wood_Beech_Trunk",new Position(16,128,16)),"Charring transaction failed");
                require(p.getAmount("Wood_Beech_Trunk")==0&&p.getAmount(TimberCharring.OUTPUT)==0,"Charring did not keep its cost/output boundary");
                p=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(p),PlotProductionState.class);
                require(p.timber().deliver(p,100,16)==1&&p.timber().deliver(p,100,16)==0,"Charring saved delivery duplicated or lost output");
                var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,17,128,16);var store=world.getChunkStore().getStore();
                var fire=store.getComponent(ref,com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock.getComponentType());
                fire.setActive(false,store.getComponent(ref,com.hypixel.hytale.builtin.crafting.component.BenchBlock.getComponentType()),null);
                require(!TimberCharring.heatAvailable(world,plot,storage,heat),"Extinguished campfire supplied heat");
            }));
    }
    private static CompletableFuture<Void> awaitHeat(World world,com.hexvane.aetherhaven.town.PlotInstance plot,
            com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point storage,com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point heat,int attempt) {
        return CompletableFuture.supplyAsync(()->TimberCharring.heatAvailable(world,plot,storage,heat),world).thenCompose(ready->{
            if(ready)return CompletableFuture.completedFuture(null);
            if(attempt>=25)return CompletableFuture.failedFuture(new IllegalStateException("Native campfire did not consume fuel"));
            return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(200,TimeUnit.MILLISECONDS))
                .thenCompose(ignored->awaitHeat(world,plot,storage,heat,attempt+1));
        });
    }
    private static CompletableFuture<Void> exercise(World world,String wood,int index) {
        var root=new Position(16+256*index,128,16);var production=new PlotProductionState();
        var captured=NativeTimberGrowth.capture(wood,root,0,p->p.y()>=0&&p.y()<320);
        if(!captured.ready())return CompletableFuture.failedFuture(new IllegalStateException(captured.issue()));
        var tree=captured.receipt();
        var loads=new ArrayList<CompletableFuture<?>>();
        // Only this freshly created fixture world is loaded or made ticking.
        var sections=new HashSet<Position>();
        var points=new HashSet<>(tree.footprint());points.add(new Position(root.x(),root.y()-1,root.z()));
        for(var p:points)sections.add(new Position(ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.y()),ChunkUtil.chunkCoordinate(p.z())));
        for(var section:sections)loads.add(world.getChunkStore().getChunkSectionReferenceAsync(section.x(),section.y(),section.z(),GetChunkFlags.SET_TICKING));
        CompletableFuture<Void> chain=CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenCompose(ignored->onWorld(world,()->{
            world.getWorldConfig().setGameTimePaused(true);world.getWorldConfig().setBlockTicking(false);
            var ground=new LinkedHashSet<>(tree.footprint());ground.add(new Position(root.x(),root.y()-1,root.z()));
            for(var p:ground)if(p.y()<root.y())require(ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),"Soil_Dirt",0),"Fixture ground placement failed");
            require(production.timber().plan(0,1,0,tree,p->true,p->NativeTimberPlanting.observe(world,p))==null,"Free grove plan failed");
            require(production.timber().activate(1)==null,"Grove activation still requires a sapling");
            var ready=NativeTimberPlanting.prepare(world,production,0,tree,p->p.y()>=0&&p.y()<320);
            require(ready.ready(),"Plant preparation: "+ready.issue());
            String issue=NativeTimberPlanting.plant(world,production,0,ready,p->true);require(issue==null,"Plant commit: "+issue);
        }));
        chain=chain.thenCompose(ignored->afterNativeQueue(world,()->{
            require(NativeTimberTending.growing(world,tree),"Planted sapling cannot be tended");
            require(NativeTimberTending.tend(world,tree,1,300),"Native growth assistance failed");
            var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,root.x(),root.y(),root.z());
            var growth=ref.getStore().getComponent(ref,FarmingBlock.getComponentType());
            require(growth.getGeneration()==0&&growth.getGrowthProgress()>0&&growth.getGrowthProgress()<1,
                "Growth assistance skipped native stage application");
            require(NativeTimberTending.tend(world,tree,1,.01),"Could not prepare native clock transition");
            require(growth.getGeneration()==0&&growth.getGrowthProgress()<1,"Assistance applied a native stage itself");
            world.getWorldConfig().setGameTimePaused(false);world.getWorldConfig().setBlockTicking(true);
        }));
        chain=chain.thenCompose(ignored->awaitFirstNativeStage(world,production,tree,0)).thenCompose(ignored->onWorld(world,()->{
            world.getWorldConfig().setGameTimePaused(true);world.getWorldConfig().setBlockTicking(false);
        }));
        var stages=BlockType.getAssetMap().getAsset(tree.sapling()).getFarming().getStages().get("Default");
        for(int stage=2;stage<stages.length;stage++) {
            int next=stage;
            chain=chain.thenCompose(ignored->afterNativeQueue(world,()->{
                var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,root.x(),root.y(),root.z());
                require(ref!=null&&ref.isValid(),"Native planting has no live growth entity at stage "+next);
                var store=world.getChunkStore().getStore();var growth=store.getComponent(ref,FarmingBlock.getComponentType());
                require(growth!=null,"Native planting has no farming data");
                growth.setPreviousBlockType(tree.sapling());growth.setCurrentStageSet("Default");
                growth.setGeneration(next);growth.setGrowthProgress(next);
                var section=ChunkSectionBlockUtil.sectionRefAt(world,root.x(),root.y(),root.z());
                stages[next].apply(store,section,ref,ChunkUtil.localCoordinate(root.x()),ChunkUtil.localCoordinate(root.y()),ChunkUtil.localCoordinate(root.z()),stages[next-1]);
            }));
        }
        return chain.thenCompose(ignored->afterNativeQueue(world,()->{
            var rootRef=ChunkSectionBlockUtil.blockEntityRefAt(world,root.x(),root.y(),root.z());
            if(rootRef!=null&&rootRef.isValid())world.getChunkStore().getStore().removeEntity(rootRef,RemoveReason.REMOVE);
            var planted=production.timber().trees().get(0);
            var inspected=NativeTimberHarvest.inspect(world,planted,p->true);
            require(inspected.ready(),"Mature harvest: "+inspected.issue()+diagnostic(world,planted));
            var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
            production.addWorkplaceOutputUnlock(tree.output());
            var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),"plot_lumbermill",production);
            var selected=List.of(tree.output(),"Ingredient_Stick","Ingredient_Tree_Sap",tree.sapling());
            require(WorkplaceProductionUpgrades.slotCount(production)==1&&production.setSlotCursorToItem(0,entry,tree.output()),"Single timber slot unavailable");
            for(String product:selected) {
                long cap=WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,product);
                production.addAmount(product,cap,cap);
                require(!NativeTimberHarvest.fell(world,production,0,entry,p->true),"Full storage did not block felling: "+product);
                require(NativeTimberHarvest.inspect(world,planted,p->true).ready(),"Refused felling changed the tree");
                production.removeAmountUpTo(product,cap);
            }
            require(NativeTimberHarvest.fell(world,production,0,entry,p->true),"Native tree removal refused");
            for(var original:planted.originals()) {
                var p=original.position();var actual=NativeTimberPlanting.observe(world,p);
                require(actual.loaded()&&original.block().equals(actual.block())&&original.rotation()==actual.rotation()&&actual.filler()==0,
                    "Original ground not restored at "+p+": "+actual.block());
            }
            require(production.getAmount(tree.output())==0,"Harvest credited stock before delivery");
            require(production.timber().cargo()!=null,"Harvest has no pending delivery");
            var manifest=production.timber().waitingProducts();
            require(manifest.getOrDefault(tree.sapling(),0)==1,"Automatic matching sapling missing");
            require(manifest.getOrDefault("Ingredient_Tree_Sap",0)>0&&manifest.getOrDefault("Ingredient_Stick",0)>0,"Automatic sap or sticks missing");
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Timber harvest products %s: %s",tree.output(),manifest);
            require(manifest.keySet().stream().allMatch(selected::contains),"Unselected resource awarded");
            for(String extra:NativeTimberProducts.BYPRODUCTS)
                require(manifest.getOrDefault(extra,0)<=WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,extra),
                    "Forest extras exceed collection room for "+extra);
            require(manifest.get(tree.output())==inspected.plan().yield(),"Native byproducts changed timber yield");
            var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
            require(manifest.equals(copy.timber().waitingProducts()),"Saved forest products changed");
            var totals=new HashMap<String,Integer>();
            while(copy.timber().cargo()!=null) {
                var held=copy.timber().cargo();require(copy.timber().deliver(copy,100,16)==0,"Product credited without pickup");
                require(copy.timber().pickUp(16)>0,"Product pickup failed");
                int accepted=copy.timber().deliver(copy,7,16);require(accepted>0,"Product delivery failed");totals.merge(held.output(),accepted,Integer::sum);
                copy.removeAmountUpTo(held.output(),7);
                while(copy.timber().cargo()!=null&&copy.timber().cargo().carried()>0) {
                    accepted=copy.timber().deliver(copy,7,16);require(accepted>0,"Partial armful stalled");totals.merge(held.output(),accepted,Integer::sum);copy.removeAmountUpTo(held.output(),7);
                }
            }
            require(manifest.equals(totals),"Products lost or duplicated during native deliveries");
            var next=NativeTimberGrowth.capture(wood,root,0,p->true);
            require(next.ready(),"Replant capture failed");
            var replant=NativeTimberPlanting.prepare(world,copy,0,next.receipt(),p->true);
            require(replant.ready()&&NativeTimberPlanting.plant(world,copy,0,replant,p->true)==null,"Free replant failed after harvest");
        }));
    }
    private static CompletableFuture<Void> awaitFirstNativeStage(World world,PlotProductionState production,TimberPlantingReceipt tree,int attempt) {
        return CompletableFuture.supplyAsync(()->{
            var root=tree.root();var ref=ChunkSectionBlockUtil.blockEntityRefAt(world,root.x(),root.y(),root.z());
            var growth=ref==null||!ref.isValid()?null:ref.getStore().getComponent(ref,FarmingBlock.getComponentType());
            if(growth==null)return tree.mature().index()==1&&NativeTimberHarvest.inspect(world,production.timber().trees().get(0),p->true).ready();
            return growth.getGeneration()==1&&growth.getGrowthProgress()>=1;
        },world).thenCompose(ready->{
            if(ready)return CompletableFuture.completedFuture(null);
            if(attempt>=25)return CompletableFuture.failedFuture(new IllegalStateException("Native clock did not apply the assisted first stage"));
            return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(200,TimeUnit.MILLISECONDS))
                .thenCompose(ignored->awaitFirstNativeStage(world,production,tree,attempt+1));
        });
    }
    private static String diagnostic(World world,TimberProductionState.Planting planted) {
        var cells=NativeTimberHarvest.matureCells(planted.tree());var differences=new ArrayList<String>();
        for(var original:planted.originals()) {
            var p=original.position();var now=NativeTimberPlanting.observe(world,p);var expected=cells.get(p);
            boolean same=expected==null?original.block().equals(now.block()):expected.blockId().equals(now.block())&&expected.rotation()==now.rotation()&&expected.filler()==now.filler();
            if(!same||now.protectedCell())differences.add(p+" expected="+(expected==null?original.block():expected.blockId()+"/"+expected.filler())+" actual="+now.block()+"/"+now.filler()+" protected="+now.protectedCell());
            if(differences.size()==5)break;
        }
        return " "+String.join("; ",differences);
    }
    private static CompletableFuture<Void> afterNativeQueue(World world,Runnable action) {
        return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(100,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));
    }
    private static CompletableFuture<Void> onWorld(World world,Runnable action) {
        return CompletableFuture.runAsync(action,world);
    }
    private static void require(boolean okay,String reason){if(!okay)throw new IllegalStateException(reason);}
}
