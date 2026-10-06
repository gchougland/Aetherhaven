package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
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

/** Real excavation in a disposable instance, with no registered town or player terrain. */
final class AetherhavenExcavationTestCommand extends AbstractCommandCollection {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    AetherhavenExcavationTestCommand(){super("excavation","Check Apprentice excavation in an isolated world");setPermissionGroups("hytale:WorldEditor");addSubCommand(new Check());}
    private static final class Check extends AbstractAsyncCommand {
        Check(){super("worldcheck","Check terrain removal, native drops and bounded delivery");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"An excavation check is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_excavation_check_"+UUID.randomUUID().toString().replace("-","");var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world->exercise(world).orTimeout(60,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                if(error==null)report(ctx,"Excavation isolated world check: PASSED. Real removal, native drops, saved cargo, full storage, delivery, plot protection, all sixteen layout and entrance combinations, and paid fittings in four orientations.");
                else {Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,"Excavation isolated world check: FAILED: "+cause.getMessage());}
                universe.removeWorld(name);
            })).whenComplete((done,error)->RUNNING.set(false));
        }
    }
    private static CompletableFuture<Void> exercise(World world) {
        var owner=UUID.randomUUID();var town=new TownRecord(UUID.randomUUID(),owner,world.getName(),16,128,16,1,1,0);
        var mine=new PlotInstance(UUID.randomUUID(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,PlotInstanceState.COMPLETE,new PlotFootprintRecord(22,127,22,25,132,25),23,128,23,0);town.getPlotInstances().add(mine);
        var storage=new WarehouseBatch.Point(23,128,23);var area=new WorkplaceArea(8,128,8,15,14);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
        require(town.getApprenticeWorkplaces().unlock(town,TownsfolkWorkType.MINING)==null,"Mining unlock failed");
        require(town.getApprenticeWorkplaces().save(town,null,TownsfolkWorkType.MINING,area,"Test shaft")==null,"Mining workplace failed");
        var site=town.getApprenticeWorkplaces().sites().getFirst();
        var tunnel=ExcavationPlan.tunnel(area,ExcavationPlan.Entrance.NORTH);var first=tunnel.getFirst();var plugin=AetherhavenPlugin.get();
        var chain=CompletableFuture.allOf(world.getChunkStore().getChunkSectionReferenceAsync(0,3,0,GetChunkFlags.SET_TICKING),world.getChunkStore().getChunkSectionReferenceAsync(0,4,0,GetChunkFlags.SET_TICKING)).thenCompose(ignored->onWorld(world,()->{
            for(int x=7;x<27;x++)for(int z=7;z<27;z++)require(ChunkSectionBlockUtil.setBlockByKey(world,x,127,z,"Rock_Stone",0),"Floor placement failed");
            for(var p:tunnel)require(ChunkSectionBlockUtil.setBlockByKey(world,p.x(),p.y(),p.z(),"Rock_Stone",0),"Face placement failed");
            require(ChunkSectionBlockUtil.setBlockByKey(world,first.x(),first.y(),first.z(),"Ore_Iron_Stone",0),"Ore placement failed");
            require(ChunkSectionBlockUtil.setBlockByKey(world,storage.x(),storage.y(),storage.z(),AetherhavenConstants.BLOCK_PRODUCTION_STORAGE,0),"Storage placement failed");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var review=NativeExcavation.review(world,town,site,ExcavationPlan.Entrance.NORTH);require(review.ready()&&review.cells().size()==27,"Review: "+review.issue());
            require(site.excavation().start(0,owner,mine.getPlotId(),area,ExcavationPlan.Entrance.NORTH,review.cells())==null,"Plan creation failed");
            require(NativeExcavation.issue(world,town,site,1)!=null,"Low Mining skill can excavate iron");require(NativeExcavation.issue(world,town,site,3)==null,"Qualified miner rejected: "+NativeExcavation.issue(world,town,site,3));
            var drops=NativeExcavation.drops(site.excavation().next());require(drops.equals(Map.of("Ore_Iron",1,"Rock_Stone_Cobble",1)),"Unexpected native iron drops: "+drops);
            require(site.excavation().prepare(drops),"Drops could not be prepared");
            for(int i=0;i<20;i++)site.excavation().attend(.5,true,1);
            var state=town.getOrCreatePlotProduction(mine.getPlotId());state.addAmount("Ore_Iron",1000000,1000000);
            require(!NativeExcavation.excavate(world,town,site,3,standing(site),()->{}),"Full mine allowed removal");require(NativeExcavation.observe(world,first).block().equals("Ore_Iron_Stone"),"Full storage still removed ore");
            state.removeAmountUpTo("Ore_Iron",1000000);
            require(!NativeExcavation.excavate(world,town,site,3,new WarehouseBatch.Point(25,128,25),()->{}),"Distant miner can remove rock");
            require(!NativeExcavation.excavate(world,town,site,3,new WarehouseBatch.Point(first.x(),128,first.z()),()->{}),"Miner can work from inside rock");
            require(NativeExcavation.excavate(world,town,site,3,standing(site),()->{}),"Real ore removal failed");require(site.excavation().cargo().isEmpty()&&site.excavation().pending(),"Cargo paid before confirmation");
            require(state.getAmount("Ore_Iron")==0,"Stock paid before delivery");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var plan=site.excavation();require(!plan.pending()&&plan.cargo().getOrDefault("Ore_Iron",0)==1,"Removal did not settle");
            require(plan.cargoPoint().equals(new WarehouseBatch.Point(9,128,first.z())),"Cargo pickup point is not the clear worker position");
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==2,"Native ore and cobble displays did not spawn");
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==2,"Cargo display refresh duplicated resources");
            require(NativeExcavation.observe(world,first).empty(),"Ore cell still exists");
            var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(plan),ExcavationPlan.class);require(copy.cargo().equals(plan.cargo()),"Reload lost cargo");
            require(!NativeExcavation.excavate(world,town,site,10,standing(site),()->{}),"A second block was removed with cargo held");
            require(!NativeExcavation.deliver(world,town,plugin,site,new WarehouseBatch.Point(0,128,0)),"Delivery accepted wrong storage");
            require(NativeExcavation.deliver(world,town,plugin,site,storage),"Physical storage delivery failed");
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==0,"Delivered cargo left visible duplicates");
            require(!NativeExcavation.deliver(world,town,plugin,site,storage),"Delivery duplicated ore");
            require(town.getOrCreatePlotProduction(mine.getPlotId()).getAmount("Ore_Iron")==1,"Incorrect delivered iron");
            var next=plan.next().point();var protectedPlot=new PlotInstance(UUID.randomUUID(),"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(next.x(),next.y(),next.z(),next.x(),next.y(),next.z()),next.x(),next.y(),next.z(),0);
            town.getPlotInstances().add(protectedPlot);
            for(var stage:PlotInstanceState.values()) {
                protectedPlot.setState(stage);require(NativeExcavation.issue(world,town,site,10)!=null,"Building plot did not protect terrain in "+stage);
                require(!NativeExcavation.review(world,town,site,ExcavationPlan.Entrance.NORTH).ready(),"Review accepted protected plot in "+stage);
            }
            town.getPlotInstances().remove(protectedPlot);
            require(ChunkSectionBlockUtil.setBlockByKey(world,next.x(),next.y(),next.z(),"Soil_Dirt",0),"Edit placement failed");
            require(NativeExcavation.issue(world,town,site,10)!=null,"Changed terrain can be removed from stale plan");
            require(site.excavation().markEdited(next),"Player edit did not pause plan");
            require(!site.excavation().pause(site.excavation().revision(),false),"Edited plan resumed without review");
            for(int x=10;x<=12;x++)for(int z=11;z<=13;z++)require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,127,z)).block()),"Tunnel floor was damaged");
        }));
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            var review=NativeExcavation.review(world,town,site,ExcavationPlan.Entrance.NORTH);
            require(review.ready()&&review.cells().size()==26,"Remaining face review failed");
            require(site.excavation().start(site.excavation().revision(),owner,mine.getPlotId(),area,ExcavationPlan.Entrance.NORTH,review.cells())==null,"Remaining tunnel restart failed");
        }));
        for(int i=0;i<26;i++) {
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                var plan=site.excavation();require(plan.prepare(NativeExcavation.drops(plan.next())),"Native face drops failed");
                for(int tick=0;tick<20;tick++)plan.attend(.5,true,1);
                require(NativeExcavation.excavate(world,town,site,10,standing(site),()->{}),"Next native face failed: "+NativeExcavation.issue(world,town,site,10));
            })).thenCompose(ignored->after(world,()->require(NativeExcavation.deliver(world,town,plugin,site,storage),"Face delivery failed")));
        }
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            require(site.excavation().next()==null&&site.excavation().cargo().isEmpty(),"Finished tunnel still has work");
            for(var p:tunnel)require(NativeExcavation.observe(world,p).empty(),"Tunnel contains unexcavated terrain");
            var stock=town.getOrCreatePlotProduction(mine.getPlotId());require(stock.getAmount("Ore_Iron")==1&&stock.getAmount("Rock_Stone_Cobble")==26&&stock.getAmount("Soil_Dirt")==1,"Native tunnel yields changed or duplicated");
            for(int x=10;x<=12;x++)for(int z=11;z<=13;z++)require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,127,z)).block()),"Completed tunnel damaged its floor");
        }));
        // Each additional layout uses only this unsaved world and a reviewed native rock face.
        // This checks terrain adapters and approach heights, not autonomous NPC navigation.
        var layoutArea=new WorkplaceArea(8,128,8,21,21);
        for(var entrance:ExcavationPlan.Entrance.values())for(var layout:ExcavationPlan.Layout.values()) {
            var points=ExcavationPlan.tunnel(layoutArea,entrance,layout);var face=points.getFirst();
            int floor=ExcavationPlan.floorAt(layoutArea,entrance,layout,face);
            var stand=new WarehouseBatch.Point(face.x()+(entrance==ExcavationPlan.Entrance.WEST?-1:entrance==ExcavationPlan.Entrance.EAST?1:0),floor,
                face.z()+(entrance==ExcavationPlan.Entrance.NORTH?-1:entrance==ExcavationPlan.Entrance.SOUTH?1:0));
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                require(town.getApprenticeWorkplaces().save(town,site.id(),TownsfolkWorkType.MINING,layoutArea,"Test shaft")==null,"Layout area change failed");
                for(var point:points) {
                    fixtureBlock(world,point.x(),point.y(),point.z(),"Rock_Stone");
                    int support=ExcavationPlan.floorAt(layoutArea,entrance,layout,point)-1;
                    fixtureBlock(world,point.x(),support,point.z(),"Rock_Stone");
                }
                fixtureBlock(world,stand.x(),stand.y()-1,stand.z(),"Rock_Stone");
                for(int dy=0;dy<3;dy++)fixtureBlock(world,stand.x(),stand.y()+dy,stand.z(),"Empty");
            })).thenCompose(ignored->after(world,()->{
                var review=NativeExcavation.review(world,town,site,entrance,layout);
                require(review.ready()&&review.cells().size()==points.size(),layout+" "+entrance+" review failed: "+review.issue());
                var plan=site.excavation();require(plan.start(plan.revision(),owner,mine.getPlotId(),layoutArea,entrance,layout,review.cells())==null,"Layout start failed");
                require(plan.floorAt(plan.next().point())==floor,"Saved layout lost floor height");
                require(plan.prepare(NativeExcavation.drops(plan.next())),"Layout drops failed");
                for(int tick=0;tick<12;tick++)plan.attend(.5,true,1);
                require(NativeExcavation.excavate(world,town,site,10,stand,()->{}),layout+" "+entrance+" removal failed: "+NativeExcavation.issue(world,town,site,10));
            })).thenCompose(ignored->after(world,()->{
                require(NativeExcavation.observe(world,face).empty(),"Layout face was not removed");
                require(site.excavation().cargoPoint().equals(stand),"Layout cargo left at the wrong height");
                require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(face.x(),floor-1,face.z())).block()),"Layout damaged its supporting floor");
                require(NativeExcavation.deliver(world,town,plugin,site,storage),"Layout cargo delivery failed");
            }));
        }
        for(var entrance:ExcavationPlan.Entrance.values()) {
            var face=ExcavationPlan.tunnel(layoutArea,entrance).getFirst();
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                for(int x=layoutArea.minX();x<layoutArea.maxX();x++)for(int z=layoutArea.minZ();z<layoutArea.maxZ();z++) {
                    fixtureBlock(world,x,127,z,"Rock_Stone");
                    for(int y=128;y<133;y++)fixtureBlock(world,x,y,z,"Empty");
                }
                fixtureBlock(world,face.x(),face.y(),face.z(),"Rock_Stone");
                var review=NativeExcavation.review(world,town,site,entrance);
                require(review.ready()&&review.cells().size()==1,"Fittings rock review failed");
                var plan=site.excavation();require(plan.start(plan.revision(),owner,mine.getPlotId(),layoutArea,entrance,ExcavationPlan.Layout.STRAIGHT,review.cells(),ExcavationFittings.Mode.BOTH)==null,"Fittings plan failed");
                require(plan.prepare(NativeExcavation.drops(plan.next())),"Fittings excavation drops failed");
                for(int tick=0;tick<12;tick++)plan.attend(.5,true,1);
                var stand=new WarehouseBatch.Point(face.x()+(entrance==ExcavationPlan.Entrance.WEST?-1:entrance==ExcavationPlan.Entrance.EAST?1:0),128,
                    face.z()+(entrance==ExcavationPlan.Entrance.NORTH?-1:entrance==ExcavationPlan.Entrance.SOUTH?1:0));
                require(NativeExcavation.excavate(world,town,site,10,stand,()->{}),"Fittings rock removal failed");
            })).thenCompose(ignored->after(world,()->{
                require(NativeExcavation.deliver(world,town,plugin,site,storage),"Fittings rock delivery failed");
                var plan=site.excavation();require(plan.fittingsTotal()==16,"Expected two support frames and torches");
                var firstFitting=plan.nextFitting();var p=firstFitting.point();var worker=new org.joml.Vector3d(p.x()+.5,128,p.z()+1.5);
                for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                require(!NativeExcavation.placeFitting(world,town,site,worker),"Fitting placed without materials");
                require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(ExcavationFittings.BEAM,14)),"Beam supply failed");
                require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(ExcavationFittings.TORCH,2)),"Torch supply failed");
                var stock=town.getWarehouse().craftingStock();
                fixtureBlock(world,p.x(),p.y(),p.z(),"Rock_Stone");
                require(!NativeExcavation.placeFitting(world,town,site,worker),"Fitting overwrote a solid block");require(stock.equals(town.getWarehouse().craftingStock()),"Blocked fitting consumed materials");
                fixtureBlock(world,p.x(),p.y(),p.z(),"Empty");
                var protection=new PlotInstance(UUID.randomUUID(),"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(p.x(),p.y(),p.z(),p.x(),p.y(),p.z()),p.x(),p.y(),p.z(),0);
                town.getPlotInstances().add(protection);require(!NativeExcavation.placeFitting(world,town,site,worker),"Fitting entered a protected plot");town.getPlotInstances().remove(protection);
                require(stock.equals(town.getWarehouse().craftingStock()),"Protected fitting consumed materials");
                require(!NativeExcavation.placeFitting(world,town,site,new org.joml.Vector3d(0,128,0)),"Distant fitting placement accepted");
                while(plan.nextFitting()!=null) {
                    var fitting=plan.nextFitting();var point=fitting.point();
                    for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                    require(NativeExcavation.placeFitting(world,town,site,new org.joml.Vector3d(point.x()+.5,128,point.z()+1.5)),"Native fitting failed "+entrance+" "+fitting+": "+NativeExcavation.issue(world,town,site,1));
                    var observed=NativeExcavation.observe(world,point);require(fitting.item().equals(observed.block())&&observed.rotation()==fitting.rotation(),"Placed fitting has wrong native orientation");
                }
                require(plan.fitted()==16,"Missing fittings");require(town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)==0&&town.getWarehouse().craftingStock().count(ExcavationFittings.TORCH)==0,"Incorrect fitting cost");
            }));
        }
        return chain;
    }
    private static int cargoProps(World world) {
        int[] count={0};var store=world.getEntityStore().getStore();
        store.forEachChunk(ProductionCargoDisplays.Marker.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                require(chunk.getComponent(i,com.hypixel.hytale.server.core.modules.entity.item.PreventPickup.getComponentType())!=null,"Cargo prop became collectible");
                require(chunk.getComponent(i,com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging.getComponentType())!=null,"Cargo prop can merge into drops");
                require(chunk.getComponent(i,com.hypixel.hytale.server.core.modules.entity.component.PropComponent.getComponentType())!=null,"Cargo prop lost static display behavior");
                require(chunk.getComponent(i,EntityStore.REGISTRY.getNonSerializedComponentType())!=null,"Cargo prop would persist independently of cargo");
                count[0]++;
            }
        });return count[0];
    }
    private static void fixtureBlock(World world,int x,int y,int z,String block) {
        var current=NativeExcavation.observe(world,new WarehouseBatch.Point(x,y,z));
        if(current.loaded()&&(block.equals(current.block())||"Empty".equals(block)&&current.empty()))return;
        require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,block,0),"Fixture block failed: "+block+" at "+x+", "+y+", "+z);
    }
    private static WarehouseBatch.Point standing(ApprenticeWorkplace site){var next=site.excavation().next();return next==null?new WarehouseBatch.Point(9,128,11):new WarehouseBatch.Point(9,128,next.point().z());}
    private static CompletableFuture<Void> after(World world,Runnable action){return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(150,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));}
    private static CompletableFuture<Void> onWorld(World world,Runnable action){return CompletableFuture.runAsync(()->{
        // No player holds this disposable fixture's one chunk active between delayed checks.
        // Production code still requires a naturally simulated chunk and never promotes it.
        var chunk=ChunkSectionBlockUtil.worldChunkIfInMemory(world,0,0);
        if(chunk!=null)chunk.setFlag(com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag.TICKING,true);
        action.run();
    },world);}
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
