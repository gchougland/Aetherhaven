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
    /** What the two newest checks measured, for the result line. */
    private static final String[] CHANGED_GROUND={""},BRANCH={""},DOORWAY={""};
    /** Chunks the fixture keeps simulating. No player is there to do it. */
    private static final List<int[]> FIXTURE_CHUNKS=List.of(new int[]{0,0},new int[]{15,-12},new int[]{16,-12});
    AetherhavenExcavationTestCommand(){super("excavation","Check Apprentice excavation in an isolated world");setPermissionGroups("hytale:WorldEditor");addSubCommand(new Check());}
    private static final class Check extends AbstractAsyncCommand {
        Check(){super("worldcheck","Check terrain removal, native drops and bounded delivery");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"An excavation check is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_excavation_check_"+UUID.randomUUID().toString().replace("-","");var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
            config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world->exercise(world).orTimeout(150,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                if(error==null)report(ctx,"Excavation isolated world check: PASSED. Real removal, native drops, saved cargo, full storage, delivery, plot protection, all twenty eight layout and entrance combinations, paid fittings in four orientations, "+CHANGED_GROUND[0]+", "+BRANCH[0]+", and "+DOORWAY[0]+".");
                else {Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,"Excavation isolated world check: FAILED: "+cause.getMessage());}
                universe.removeWorld(name);
            })).whenComplete((done,error)->RUNNING.set(false));
        }
    }
    private static boolean deliverBuffered(World world,TownRecord town,AetherhavenPlugin plugin,ApprenticeWorkplace site,WarehouseBatch.Point storage){
        var plan=site.excavation();if(!plan.cargo().isEmpty()&&!plan.haulingOutput())require(plan.deposit(plan.id()),"Buffer deposit failed");
        require(plan.pickupOutput(plan.id(),"fixture",NativeExcavation.bufferPoint(town,site),NativeExcavation.availableOutput(town,plugin,plan),10),"Buffer pickup failed");
        return NativeExcavation.deliver(world,town,plugin,site,storage);
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
        var stood=new WarehouseBatch.Point[1];
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
            require(!NativeExcavation.excavate(world,town,site,3,new WarehouseBatch.Point(25,128,25),()->{}),"Distant miner can remove rock");
            require(!NativeExcavation.excavate(world,town,site,3,new WarehouseBatch.Point(first.x(),128,first.z()),()->{}),"Miner can work from inside rock");
            // The miner starts on open ground beside the rock. He must come round to the passage's own floor.
            stood[0]=standing(world,site);
            require(ExcavationApproach.floorOf(site.excavation()).test(stood[0]),"Miner chose a stand off the passage floor: "+stood[0]);
            require(NativeExcavation.excavate(world,town,site,3,stood[0],()->{}),"Real ore removal failed");require(!site.excavation().pending()&&site.excavation().cargo().getOrDefault("Ore_Iron",0)==1,"Synchronous removal was not confirmed");
            require(state.getAmount("Ore_Iron")==1000000,"Full core stock changed while mining into a carried load");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var plan=site.excavation();require(!plan.pending()&&plan.cargo().getOrDefault("Ore_Iron",0)==1,"Removal did not settle");
            require(plan.cargoPoint().equals(stood[0]),"Cargo pickup point is not the clear worker position: "+plan.cargoPoint()+" for a miner at "+stood[0]);
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==2,"Native ore and cobble displays did not spawn");
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==2,"Cargo display refresh duplicated resources");
            require(NativeExcavation.observe(world,first).empty(),"Ore cell still exists");
            var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(plan),ExcavationPlan.class);require(copy.cargo().equals(plan.cargo()),"Reload lost cargo");
            require(!NativeExcavation.excavate(world,town,site,10,standing(world,site),()->{}),"A second block was removed with cargo held");
            require(!NativeExcavation.deliver(world,town,plugin,site,new WarehouseBatch.Point(0,128,0)),"Delivery accepted wrong storage");
            require(plan.deposit(plan.id()),"Base buffer deposit failed");
            require(!plan.output().isEmpty()&&NativeExcavation.availableOutput(town,plugin,plan).getOrDefault("Ore_Iron",0)==0,"Full core mine lost its buffered ore");
            town.getOrCreatePlotProduction(mine.getPlotId()).removeAmountUpTo("Ore_Iron",1000000);
            require(deliverBuffered(world,town,plugin,site,storage),"Physical storage delivery failed");
            ProductionCargoDisplays.sync(world,List.of(town));require(cargoProps(world)==0,"Delivered cargo left visible duplicates");
            require(!NativeExcavation.deliver(world,town,plugin,site,storage),"Delivery duplicated ore");
            require(town.getOrCreatePlotProduction(mine.getPlotId()).getAmount("Ore_Iron")==1,"Incorrect delivered iron");
            var next=plan.next().point();var protectedPlot=new PlotInstance(UUID.randomUUID(),"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(next.x(),next.y(),next.z(),next.x(),next.y(),next.z()),next.x(),next.y(),next.z(),0);
            town.getPlotInstances().add(protectedPlot);
            for(var stage:PlotInstanceState.values()) {
                // A plot in the way is worked round: its block is set aside and the next one is offered instead.
                protectedPlot.setState(stage);require(NativeExcavation.issue(world,town,site,10)==null,"A building plot stopped the whole tunnel in "+stage+": "+NativeExcavation.issue(world,town,site,10));
                require(plan.blocked().stream().anyMatch(cell->cell.point().equals(next))&&!plan.next().point().equals(next),"Building plot did not protect terrain in "+stage);
                require(!NativeExcavation.review(world,town,site,ExcavationPlan.Entrance.NORTH).ready(),"Review accepted protected plot in "+stage);
            }
            String around=NativeExcavation.obstruction(world,town,site);
            require(around!=null&&around.startsWith("A building plot at "+next.x()+", "+next.y()+", "+next.z()+" is in the way"),"The plot in the way was not named: "+around);
            town.getPlotInstances().remove(protectedPlot);
            // The next block is swapped after planning. It is dug as what it now is, with no review and no pause.
            var swapped=plan.next().point();
            require(ChunkSectionBlockUtil.setBlockByKey(world,swapped.x(),swapped.y(),swapped.z(),"Soil_Dirt",0),"Edit placement failed");
            require(NativeExcavation.issue(world,town,site,10)==null,"A swapped block stopped the tunnel: "+NativeExcavation.issue(world,town,site,10));
            require("Soil_Dirt".equals(plan.next().block())&&plan.next().point().equals(swapped)&&!plan.paused()&&!plan.edited(),"The order did not take up the swapped block");
            for(int x=10;x<=12;x++)for(int z=11;z<=13;z++)require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,127,z)).block()),"Tunnel floor was damaged");
        }));
        for(int i=0;i<26;i++) {
            if(i==25)chain=chain.thenCompose(ignored->after(world,()->require(NativeExcavation.issue(world,town,site,10)==null&&site.excavation().blocked().isEmpty(),"The block set aside for a plot did not come back once the plot had gone: "+NativeExcavation.issue(world,town,site,10))));
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                var plan=site.excavation();require(NativeExcavation.issue(world,town,site,10)==null,"Tunnel stopped: "+NativeExcavation.issue(world,town,site,10));
                require(plan.prepare(NativeExcavation.drops(plan.next())),"Native face drops failed");
                for(int tick=0;tick<20;tick++)plan.attend(.5,true,1);
                require(NativeExcavation.excavate(world,town,site,10,standing(world,site),()->{}),"Next native face failed: "+NativeExcavation.issue(world,town,site,10));
            })).thenCompose(ignored->after(world,()->require(deliverBuffered(world,town,plugin,site,storage),"Face delivery failed")));
        }
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            require(site.excavation().next()==null&&site.excavation().cargo().isEmpty(),"Finished tunnel still has work");
            for(var p:tunnel)require(NativeExcavation.observe(world,p).empty(),"Tunnel contains unexcavated terrain");
            var stock=town.getOrCreatePlotProduction(mine.getPlotId());require(stock.getAmount("Ore_Iron")==1&&stock.getAmount("Rock_Stone_Cobble")==26&&stock.getAmount("Soil_Dirt")==1,"Native tunnel yields changed or duplicated");
            for(int x=10;x<=12;x++)for(int z=11;z<=13;z++)require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,127,z)).block()),"Completed tunnel damaged its floor");
        }));
        // Each additional layout uses only this unsaved world and a reviewed native rock face.
        // This checks terrain adapters and approach heights, not autonomous NPC navigation.
        for(var entrance:ExcavationPlan.Entrance.values())for(var layout:ExcavationPlan.Layout.values()) {
            var layoutArea=new WorkplaceArea(8,128,8,21,21,10);
            var points=ExcavationPlan.tunnel(layoutArea,entrance,layout);var face=points.getFirst();
            int floor=ExcavationPlan.floorAt(layoutArea,entrance,layout,face);
            var stand=new WarehouseBatch.Point(face.x()+(entrance==ExcavationPlan.Entrance.WEST?-1:entrance==ExcavationPlan.Entrance.EAST?1:0),floor,
                face.z()+(entrance==ExcavationPlan.Entrance.NORTH?-1:entrance==ExcavationPlan.Entrance.SOUTH?1:0));
            var attended=new WarehouseBatch.Point[1];
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                require(town.getApprenticeWorkplaces().save(town,site.id(),TownsfolkWorkType.MINING,layoutArea,"Test shaft")==null,"Layout area change failed");
                for(int x=7;x<=22;x++)for(int z=7;z<=22;z++)for(int y=127;y<139;y++)fixtureBlock(world,x,y,z,y==127?"Rock_Stone":"Empty");
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
                attended[0]=standing(world,site);
                require(NativeExcavation.excavate(world,town,site,10,attended[0],()->{}),layout+" "+entrance+" removal failed: "+NativeExcavation.issue(world,town,site,10));
            })).thenCompose(ignored->after(world,()->{
                require(NativeExcavation.observe(world,face).empty(),"Layout face was not removed");
                require(site.excavation().cargoPoint().equals(attended[0]),"Layout cargo left away from its worker");
                require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(face.x(),floor-1,face.z())).block()),"Layout damaged its supporting floor");
                require(deliverBuffered(world,town,plugin,site,storage),"Layout cargo delivery failed");
            }));
        }
        var layoutArea=new WorkplaceArea(8,128,8,21,21);
        for(var entrance:ExcavationPlan.Entrance.values()) {
            var face=ExcavationPlan.tunnel(layoutArea,entrance).getFirst();
            chain=chain.thenCompose(ignored->onWorld(world,()->{
                for(int x=layoutArea.minX();x<layoutArea.maxX();x++)for(int z=layoutArea.minZ();z<layoutArea.maxZ();z++) {
                    fixtureBlock(world,x,127,z,"Rock_Stone");
                    for(int y=128;y<133;y++)fixtureBlock(world,x,y,z,"Empty");
                }
                require(town.getApprenticeWorkplaces().save(town,site.id(),TownsfolkWorkType.MINING,layoutArea,"Test shaft")==null,"Fittings area setup failed");
                var air=new HashSet<>(ExcavationPlan.tunnel(layoutArea,entrance));
                for(var f:ExcavationFittings.plan(layoutArea,entrance,ExcavationPlan.Layout.STRAIGHT,ExcavationFittings.Mode.BOTH))if(f.support()!=null&&!air.contains(f.support()))fixtureBlock(world,f.support().x(),f.support().y(),f.support().z(),"Rock_Stone");
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
                require(deliverBuffered(world,town,plugin,site,storage),"Fittings rock delivery failed");
                var plan=site.excavation();require(plan.fittingsTotal()==16,"Expected two support frames and torches");
                var firstFitting=plan.nextFitting();var p=firstFitting.point();var worker=new org.joml.Vector3d(p.x()+.5,128,p.z()+1.5);
                for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                require(!NativeExcavation.placeFitting(world,town,site,worker),"Fitting placed without materials");
                require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(ExcavationFittings.BEAM,14)),"Beam supply failed");
                require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(ExcavationFittings.TORCH,2)),"Torch supply failed");
                var stock=town.getWarehouse().craftingStock();
                // Rock in the post's place is queued to be dug first. The post waits and nothing is spent.
                fixtureBlock(world,p.x(),p.y(),p.z(),"Rock_Stone");
                require(!NativeExcavation.placeFitting(world,town,site,worker),"Fitting overwrote a solid block");require(stock.equals(town.getWarehouse().craftingStock()),"Blocked fitting consumed materials");
                require(plan.next()!=null&&plan.next().point().equals(p)&&firstFitting.equals(plan.nextFitting()),"Rock in a fitting's place was not queued to be dug");
                // The rock goes again. The order notices, and the post is next once more.
                fixtureBlock(world,p.x(),p.y(),p.z(),"Empty");
                require(NativeExcavation.issue(world,town,site,1)==null&&plan.next()==null&&firstFitting.equals(plan.nextFitting()),"A cleared fitting position was not taken up again: "+NativeExcavation.issue(world,town,site,1));
                // A building plot over the post's place: that post is left out, the next one is fitted, and nothing enters the plot.
                // The post that would stand on it and the corner above have nothing to hold them, so they are left out too.
                var protection=new PlotInstance(UUID.randomUUID(),"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(p.x(),p.y(),p.z(),p.x(),p.y(),p.z()),p.x(),p.y(),p.z(),0);
                town.getPlotInstances().add(protection);
                require(NativeExcavation.issue(world,town,site,1)==null&&plan.fittingsSkipped()==1&&!firstFitting.equals(plan.nextFitting()),"A fitting inside a plot held up the rest: "+NativeExcavation.issue(world,town,site,1));
                for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                require(NativeExcavation.placeFitting(world,town,site,worker),"The fitting after a protected one was not placed: "+NativeExcavation.issue(world,town,site,1));
                town.getPlotInstances().remove(protection);
                require(NativeExcavation.observe(world,p).empty()&&plan.fittingsSkipped()>=1,"Fitting entered a protected plot");
                require(town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)==13,"Protected fitting consumed materials");
                require(!NativeExcavation.placeFitting(world,town,site,new org.joml.Vector3d(0,128,0)),"Distant fitting placement accepted");
                while(plan.nextFitting()!=null) {
                    var fitting=plan.nextFitting();var point=fitting.point();
                    for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                    require(NativeExcavation.placeFitting(world,town,site,new org.joml.Vector3d(point.x()+.5,128,point.z()+1.5)),"Native fitting failed "+entrance+" "+fitting+": "+NativeExcavation.issue(world,town,site,1));
                    var observed=NativeExcavation.observe(world,point);require(fitting.block().equals(observed.block())&&observed.rotation()==fitting.rotation(),"Placed fitting has wrong native orientation");
                }
                // Thirteen pieces placed and paid for. The three left out cost nothing, so three beams are left.
                require(plan.fitted()==16&&plan.fittingsSkipped()==3,"Missing fittings: "+plan.fittingsSkipped()+" left out");require(town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)==3&&town.getWarehouse().craftingStock().count(ExcavationFittings.TORCH)==0,"Incorrect fitting cost");
                for(int spare=0;spare<3;spare++)require(town.getWarehouse().placeFromStock(ExcavationFittings.BEAM,()->true),"A spare beam could not be taken back");
                require(town.getWarehouse().craftingStock().count(ExcavationFittings.BEAM)==0,"Spare beams remain");
                String finished=NativeExcavation.issue(world,town,site,1);
                require(finished!=null&&finished.startsWith("Tunnel complete. 3 fittings were left out where something was in the way."),"The fitting left out was not reported: "+finished);
            }));
        }
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            // Ordinary placed blocks and natural timber are both valid excavation targets.
            require(town.getApprenticeWorkplaces().save(town,site.id(),TownsfolkWorkType.MINING,area,"Mixed blocks")==null,"Mixed area failed");
            for(int x=8;x<15;x++)for(int z=8;z<14;z++)for(int y=127;y<134;y++)fixtureBlock(world,x,y,z,y==127?"Rock_Stone":"Empty");
            var target=new WarehouseBatch.Point(10,128,11);var floor=new WarehouseBatch.Point(10,127,11);
            fixtureBlock(world,10,128,11,"Wood_Oak_Trunk_Full");
            fixtureBlock(world,11,128,11,"Rock_Stone_Brick");
            fixtureBlock(world,10,127,11,"Empty");
            fixtureBlock(world,10,127,12,"Empty");
            fixtureBlock(world,12,126,11,"Rock_Stone");
            fixtureBlock(world,12,127,11,"Plant_Moss_Rug_Green");
            var review=NativeExcavation.review(world,town,site,ExcavationPlan.Entrance.NORTH);
            require(review.ready()&&review.cells().size()==2,"Ordinary blocks rejected: "+review.issue());
            var plan=site.excavation();require(plan.start(plan.revision(),owner,mine.getPlotId(),area,ExcavationPlan.Entrance.NORTH,review.cells())==null,"Mixed plan failed");
            require(plan.prepare(NativeExcavation.drops(plan.next())),"Timber drops failed");
            for(int tick=0;tick<12;tick++)plan.attend(.5,true,1);
            var stand=new WarehouseBatch.Point(10,128,10);
            var remote=new org.joml.Vector3d(10.5,128,8.5);
            fixtureBlock(world,10,128,10,"Rock_Stone");
            fixtureBlock(world,10,129,10,"Rock_Stone");
            require(!NativeExcavation.clearReach(world,remote,target),"Worker can mine through an intervening wall");
            fixtureBlock(world,10,128,10,"Empty");
            fixtureBlock(world,10,129,10,"Empty");
            require(NativeExcavation.excavate(world,town,site,10,stand,()->{}),"Timber removal failed");
            require("Rock_Stone_Cobble".equals(NativeExcavation.observe(world,floor).block()),"Excavated floor gap was not filled");
            require("Rock_Stone_Cobble".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(10,127,12)).block()),"Existing air floor gap was not filled");
            require("Rock_Stone".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(11,127,11)).block()),"Existing solid floor was replaced");
            NativeExcavation.repairFloors(world,town,site,new WarehouseBatch.Point(12,128,10));
            require("Rock_Stone_Cobble".equals(NativeExcavation.observe(world,new WarehouseBatch.Point(12,127,11)).block()),"Soft moss floor was not replaced");
            var besideRail=new org.joml.Vector3d(11.1,129.17,10.35);
            fixtureBlock(world,10,129,10,"Rail");
            require(NativeExcavation.clearReach(world,besideRail,target),"Thin rail blocks the next lower step");
            fixtureBlock(world,10,129,10,"*Rail_State_Definitions_Slope");
            require(!NativeExcavation.clearReach(world,besideRail,target),"Tool path crossed the solid lower half of a slope rail");
            require(NativeExcavation.clearReach(world,new org.joml.Vector3d(11.5,129.17,10.5),target),"Worker beside the slope cannot reach the next step");
            fixtureBlock(world,10,129,10,"Rock_Stone");
            require(!NativeExcavation.clearReach(world,besideRail,target),"Hitbox reach ignores solid terrain");
        }));
        chain=changedGround(chain,world,town,mine,owner);
        chain=branchOffLanding(chain,world,owner);
        return chain;
    }
    /**
     * A tunnel is planned, then left the way a save from before this rule held a stalled one, and the ground under it is
     * changed: blocks swapped for others, blocks taken out, rock put where there was none, and a chest set in its path.
     * Miners must dig all of it but the chest without anyone reviewing anything, and finish once the chest is gone.
     */
    private static CompletableFuture<Void> changedGround(CompletableFuture<Void> chain,World world,TownRecord town,PlotInstance mine,UUID owner) {
        var area=new WorkplaceArea(8,128,8,15,14);var tunnel=ExcavationPlan.tunnel(area,ExcavationPlan.Entrance.NORTH);
        var open=List.of(tunnel.get(4),tunnel.get(13));            // empty when planned, rock put in afterwards
        var swaps=Map.of(tunnel.get(1),"Ore_Iron_Stone",tunnel.get(7),"Soil_Dirt",tunnel.get(16),"Rock_Stone_Brick");
        var removed=List.of(tunnel.get(2),tunnel.get(10),tunnel.get(20));
        var chest=tunnel.get(25);
        var stalled=new ApprenticeWorkplace[1];var dug=new LinkedHashMap<String,Integer>();
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            var fresh=new ApprenticeWorkplace("Changed ground",TownsfolkWorkType.MINING,area);
            for(int x=8;x<15;x++)for(int z=8;z<14;z++)for(int y=127;y<134;y++)fixtureBlock(world,x,y,z,y==127?"Rock_Stone":"Empty");
            for(var p:tunnel)if(!open.contains(p))fixtureBlock(world,p.x(),p.y(),p.z(),"Rock_Stone");
            var review=NativeExcavation.review(world,town,fresh,ExcavationPlan.Entrance.NORTH);
            require(review.ready()&&review.cells().size()==25,"Changed ground review failed: "+review.issue());
            var plan=fresh.excavation();require(plan.start(plan.revision(),owner,mine.getPlotId(),area,ExcavationPlan.Entrance.NORTH,review.cells())==null,"Changed ground plan failed");
            // The same order as an older save holds it: stopped for good by the old rule, with none of the new fields.
            var gson=new com.google.gson.Gson();var json=gson.toJsonTree(fresh).getAsJsonObject();var saved=json.getAsJsonObject("excavation");
            saved.addProperty("edited",true);saved.addProperty("paused",true);saved.remove("blocked");saved.remove("sweepArmed");saved.remove("fittingsSkipped");
            stalled[0]=gson.fromJson(json,ApprenticeWorkplace.class);
            require(stalled[0].excavation().paused()&&stalled[0].excavation().edited()&&stalled[0].excavation().total()==25,"The stalled order was not restored as saved");
            for(var swap:swaps.entrySet())fixtureBlock(world,swap.getKey().x(),swap.getKey().y(),swap.getKey().z(),swap.getValue());
            for(var p:removed)fixtureBlock(world,p.x(),p.y(),p.z(),"Empty");
            for(var p:open)fixtureBlock(world,p.x(),p.y(),p.z(),"Rock_Stone");
            fixtureBlock(world,chest.x(),chest.y(),chest.z(),"Furniture_Crude_Chest_Small");
        }));
        // Twenty one listed blocks are left to dig, then the two that arrived. Each pass digs one or finds nothing to do.
        for(int i=0;i<30;i++)chain=chain.thenCompose(ignored->after(world,()->{
            var work=stalled[0];var plan=work.excavation();
            if(NativeExcavation.issue(world,town,work,10)!=null||plan.next()==null)return;
            var cell=plan.next();var seen=NativeExcavation.observe(world,cell.point());
            require(cell.block().equals(seen.block()),"The order names "+cell.block()+" where the ground holds "+seen.block());
            var drops=NativeExcavation.drops(cell);require(plan.prepare(drops),"Changed ground drops failed for "+cell.block());
            for(int tick=0;tick<40;tick++)plan.attend(.5,true,1);
            require(NativeExcavation.excavate(world,town,work,10,standing(world,work),()->{}),"Changed ground removal failed at "+cell.point()+": "+NativeExcavation.issue(world,town,work,10));
            drops.forEach((item,count)->dug.merge(item,count,Integer::sum));
            require(plan.deposit(plan.id()),"Changed ground deposit failed");
        }));
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            var work=stalled[0];var plan=work.excavation();String issue=NativeExcavation.issue(world,town,work,10);
            require(!plan.paused()&&!plan.edited(),"The stalled tunnel did not resume by itself");
            require(plan.next()==null&&plan.blocked().size()==1&&plan.blocked().getFirst().point().equals(chest),"Expected only the chest to be left: next "+plan.next()+", set aside "+plan.blocked());
            for(var p:tunnel)require(p.equals(chest)||NativeExcavation.observe(world,p).empty(),"Tunnel was not dug through at "+p);
            require("Furniture_Crude_Chest_Small".equals(NativeExcavation.observe(world,chest).block()),"The chest was broken");
            require(issue!=null&&issue.contains(" at "+chest.x()+", "+chest.y()+", "+chest.z()+" is in the way")&&!issue.toLowerCase(Locale.ROOT).contains("review"),"The chest in the way was not explained: "+issue);
            require(dug.getOrDefault("Ore_Iron",0)==1&&dug.getOrDefault("Soil_Dirt",0)==1&&dug.getOrDefault("Rock_Stone_Brick",0)==1,"Swapped blocks did not give their own drops: "+dug);
            require(dug.equals(plan.output()),"The mine's output "+plan.output()+" is not what was dug "+dug);
            require(dug.values().stream().mapToInt(Integer::intValue).sum()>=23,"Expected twenty three blocks' worth of drops: "+dug);
            CHANGED_GROUND[0]="a stalled tunnel from an older save resumed by itself and was dug through three swapped, three removed and two added blocks ("+plan.completed()+" of "+plan.total()+" done, a chest left standing and named)";
            // The chest is taken away. The block behind it is ordinary rock now, and the tunnel finishes.
            fixtureBlock(world,chest.x(),chest.y(),chest.z(),"Rock_Stone");
        }));
        chain=chain.thenCompose(ignored->later(world,2200,()->{
            var work=stalled[0];var plan=work.excavation();
            require(NativeExcavation.issue(world,town,work,10)==null&&plan.next()!=null&&plan.next().point().equals(chest),"The block behind the chest did not come back: "+NativeExcavation.issue(world,town,work,10));
            require(plan.prepare(NativeExcavation.drops(plan.next())),"Last block drops failed");
            for(int tick=0;tick<40;tick++)plan.attend(.5,true,1);
            require(NativeExcavation.excavate(world,town,work,10,standing(world,work),()->{}),"Last block removal failed");
            require(plan.deposit(plan.id()),"Last block deposit failed");
        }));
        return chain.thenCompose(ignored->after(world,()->{
            var work=stalled[0];var plan=work.excavation();String issue=NativeExcavation.issue(world,town,work,10);
            for(var p:tunnel)require(NativeExcavation.observe(world,p).empty(),"Finished tunnel still holds a block at "+p);
            require(plan.next()==null&&plan.blocked().isEmpty()&&plan.completed()==plan.total()&&issue!=null&&issue.startsWith("Tunnel complete."),"Tunnel was not reported finished: "+issue);
            CHANGED_GROUND[0]+=", then finished ("+plan.completed()+" of "+plan.total()+") once the chest was moved";
        }));
    }
    /**
     * The owner's case, at his coordinates: stairs come down to a landing at 511, 104, -370, and a nine block tunnel is
     * planned north from that landing through the real planner checks, then dug. The step beside the landing must keep
     * its block.
     */
    private static CompletableFuture<Void> branchOffLanding(CompletableFuture<Void> chain,World world,UUID owner) {
        var town=new TownRecord(UUID.randomUUID(),owner,world.getName(),512,104,-370,1,1,0);
        var mine=new PlotInstance(UUID.randomUUID(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,PlotInstanceState.COMPLETE,new PlotFootprintRecord(530,110,-360,533,115,-357),531,111,-359,0);town.getPlotInstances().add(mine);
        return doorwayAndJunction(branchOffLanding(chain,world,owner,town,mine),world,owner,town,mine);
    }
    private static CompletableFuture<Void> branchOffLanding(CompletableFuture<Void> chain,World world,UUID owner,TownRecord town,PlotInstance mine) {
        var baseArea=new WorkplaceArea(520,111,-373,527,-366);var landing=new WarehouseBatch.Point(511,104,-370);var step=new WarehouseBatch.Point(512,104,-371);
        var sites=town.getApprenticeWorkplaces();var branch=new ApprenticeWorkplace[1];
        // The two chunks are asked for only now, so they are still in memory when the rock is laid a moment later.
        chain=chain.thenCompose(ignored->CompletableFuture.allOf(world.getChunkStore().getChunkSectionReferenceAsync(15,3,-12,GetChunkFlags.SET_TICKING),world.getChunkStore().getChunkSectionReferenceAsync(16,3,-12,GetChunkFlags.SET_TICKING)))
            .thenCompose(ignored->onWorld(world,()->{
            town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner","miner",null,UUID.randomUUID()));
            require(sites.unlock(town,TownsfolkWorkType.MINING)==null,"Branch mining unlock failed");
            require(sites.save(town,null,TownsfolkWorkType.MINING,baseArea,"Ridge mine")==null,"Branch base failed");
            for(int x=504;x<=528;x++)for(int z=-382;z<=-364;z++)for(int y=102;y<=116;y++)fixtureBlock(world,x,y,z,"Rock_Stone");
            for(int x=520;x<527;x++)for(int z=-373;z<-366;z++)for(int y=111;y<114;y++)fixtureBlock(world,x,y,z,"Empty");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var base=sites.sites().getFirst();
            var stair=planned(world,town,base,owner,mine,new WarehouseBatch.Point(520,111,-370),MineshaftSelection.Direction.WEST,ExcavationPlan.Layout.DEEP_STAIRS_DOWN,9,null,"Stairs");
            var bottom=MineshaftNetwork.mapConnections(sites,base).entrySet().stream().filter(e->e.getValue().equals(stair.id())&&e.getKey().y()==104).map(Map.Entry::getKey).toList();
            require(bottom.equals(List.of(landing)),"The stairs do not end on a single landing at 511, 104, -370: "+bottom);
            // The stairs are opened by hand. Their order must notice and count itself dug, with no miner and no review.
            for(var p:ExcavationPlan.tunnel(stair.area(),stair.excavation().entrance(),stair.excavation().layout()))fixtureBlock(world,p.x(),p.y(),p.z(),"Empty");
            String done=NativeExcavation.issue(world,town,stair,10);
            require(stair.excavation().passageReady()&&done!=null&&done.startsWith("Tunnel complete."),"Stairs opened by hand were not counted as dug: "+done);
            for(var direction:MineshaftSelection.Direction.values()) {
                var route=route(world,town,base,landing,direction,ExcavationPlan.Layout.STRAIGHT,9,stair.id());
                if(direction==MineshaftSelection.Direction.EAST)require(route.issue()!=null&&route.issue().startsWith(MineshaftNetwork.CUT_FLOOR+"Stairs at 512, 104, -370."),"A tunnel back under its own stairs was not refused with the place: "+route.issue());
                else require(route.ready(),"A level tunnel "+direction+" off the landing was refused: "+route.issue());
            }
            branch[0]=planned(world,town,base,owner,mine,landing,MineshaftSelection.Direction.NORTH,ExcavationPlan.Layout.STRAIGHT,9,stair.id(),"Branch");
            require(branch[0].excavation().pendingPoints().stream().noneMatch(step::equals),"The branch would dig the step beside the landing");
            require(branch[0].excavation().total()==75,"Expected 75 blocks north of the landing, the stairwell and its step left out: "+branch[0].excavation().total());
        }));
        for(int i=0;i<80;i++)chain=chain.thenCompose(ignored->onWorld(world,()->{
            var work=branch[0];var plan=work.excavation();
            if(NativeExcavation.issue(world,town,work,10)!=null||plan.next()==null)return;
            require(plan.prepare(NativeExcavation.drops(plan.next())),"Branch drops failed");
            for(int tick=0;tick<40;tick++)plan.attend(.5,true,1);
            var target=plan.next().point();var stand=standing(world,work,new org.joml.Vector3d(landing.x()+.5,landing.y(),landing.z()+.5));
            require(NativeExcavation.excavate(world,town,work,10,stand,()->{}),"Branch removal failed at "+target+" from "+stand);
            require(plan.deposit(plan.id()),"Branch deposit failed");
        }));
        return chain.thenCompose(ignored->after(world,()->{
            var plan=branch[0].excavation();String issue=NativeExcavation.issue(world,town,branch[0],10);
            require(plan.next()==null&&plan.blocked().isEmpty()&&issue!=null&&issue.startsWith("Tunnel complete."),"The branch was not finished: "+issue+", next "+plan.next());
            for(int z=-379;z<=-371;z++)for(int y=104;y<=106;y++)for(int x=510;x<=512;x++) {
                var p=new WarehouseBatch.Point(x,y,z);
                if(!p.equals(step))require(NativeExcavation.observe(world,p).empty(),"The branch is not open at "+p);
            }
            require("Rock_Stone".equals(NativeExcavation.observe(world,step).block()),"The step beside the landing lost its block");
            require(NativeExcavation.observe(world,new WarehouseBatch.Point(512,105,-371)).empty(),"The step beside the landing was filled in");
            BRANCH[0]="a 9 block tunnel planned north off the stair landing at 511, 104, -370 and dug ("+plan.completed()+" of "+plan.total()+", the stair's step kept)";
        }));
    }
    /**
     * In the same mine: a gallery is dug and fitted with frames, then a side passage is added exactly at its second
     * frame. The built frame must come down whole, its beams must come back, and the frame further along must stay.
     * Rock is then put in the shared column twice, once while the gallery still has work and once when only the side
     * passage has: each must clear it before anything else.
     */
    private static CompletableFuture<Void> doorwayAndJunction(CompletableFuture<Void> chain,World world,UUID owner,TownRecord town,PlotInstance mine) {
        var sites=town.getApprenticeWorkplaces();var from=new org.joml.Vector3d(523.5,111,-372.5);
        var gallery=new ApprenticeWorkplace[1];var side=new ApprenticeWorkplace[1];var frame=new ArrayList<WarehouseBatch.Point>();
        var first=new WarehouseBatch.Point(524,111,-380);var second=new WarehouseBatch.Point(524,112,-381);
        var beams=new int[1];
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            var base=sites.sites().getFirst();
            gallery[0]=planned(world,town,base,owner,mine,new WarehouseBatch.Point(523,111,-373),MineshaftSelection.Direction.NORTH,ExcavationPlan.Layout.STRAIGHT,9,null,"Gallery",ExcavationFittings.Mode.SUPPORTS);
            var plan=gallery[0].excavation();require(plan.fittingsTotal()==14,"Expected two frames of seven pieces: "+plan.fittingsTotal());
            for(var p:ExcavationPlan.tunnel(gallery[0].area(),plan.entrance(),plan.layout()))fixtureBlock(world,p.x(),p.y(),p.z(),"Empty");
            String waiting=NativeExcavation.issue(world,town,gallery[0],10);
            require(waiting!=null&&waiting.startsWith("Digging is done. Waiting for 14 ")&&waiting.endsWith("Put them in "+ExcavationFittings.STORAGE+"."),"Beams were not asked for by name, count and place: "+waiting);
            require(town.getWarehouse().acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(ExcavationFittings.BEAM,14)),"Gallery beam supply failed");
            while(plan.nextFitting()!=null) {
                var fitting=plan.nextFitting();var point=fitting.point();
                if(point.z()==-381)frame.add(point);
                for(int tick=0;tick<6;tick++)plan.attend(.5,true,1);
                require(NativeExcavation.placeFitting(world,town,gallery[0],new org.joml.Vector3d(point.x()+.5,111,point.z()+1.5)),"Gallery fitting failed at "+point);
            }
            require(plan.fitted()==14&&plan.fittingsSkipped()==0&&frame.size()==7,"Gallery frames were not built: "+plan.fitted()+" placed, "+frame.size()+" in the second frame");
            for(var p:frame)require(!NativeExcavation.observe(world,p).empty(),"No frame piece stands at "+p);
            // A side passage is added exactly at that frame. Its doorway is the gallery's east lane.
            side[0]=planned(world,town,base,owner,mine,new WarehouseBatch.Point(523,111,-381),MineshaftSelection.Direction.EAST,ExcavationPlan.Layout.STRAIGHT,3,gallery[0].id(),"Side",ExcavationFittings.Mode.NONE);
            require(plan.fitted()==7&&plan.fittingsTotal()==7&&plan.unbuild().size()==7&&!plan.passageReady(),"The frame in the new doorway was not listed to come down: "+plan.unbuild());
            // Rock turns up in the shared column while the gallery still has that work to do.
            fixtureBlock(world,first.x(),first.y(),first.z(),"Rock_Stone");
        }));
        // The gallery last looked at its junctions a moment ago. The next look is due within three seconds.
        chain=chain.thenCompose(ignored->later(world,3200,()->{
            var plan=gallery[0].excavation();
            require(NativeExcavation.issue(world,town,gallery[0],10)==null&&plan.next()!=null&&plan.next().point().equals(first),"The gallery did not put the rock in its junction first: "+plan.next());
            require(plan.unbuild().isEmpty()&&plan.total()-plan.completed()==8,"Expected the rock and seven frame pieces to do: "+(plan.total()-plan.completed()));
        }));
        for(int i=0;i<8;i++)chain=chain.thenCompose(ignored->onWorld(world,()->{
            var plan=gallery[0].excavation();
            require(NativeExcavation.issue(world,town,gallery[0],10)==null&&plan.next()!=null,"Gallery stopped: "+NativeExcavation.issue(world,town,gallery[0],10));
            var cell=plan.next();var drops=NativeExcavation.drops(cell);
            if(frame.contains(cell.point()))beams[0]+=drops.getOrDefault(ExcavationFittings.BEAM,0);
            require(plan.prepare(drops),"Doorway drops failed for "+cell.block());
            for(int tick=0;tick<40;tick++)plan.attend(.5,true,1);
            var stand=standing(world,gallery[0],from);
            require(NativeExcavation.excavate(world,town,gallery[0],10,stand,()->{}),"Doorway removal failed at "+cell.point()+" from "+stand);
            require(plan.deposit(plan.id()),"Doorway deposit failed");
        }));
        chain=chain.thenCompose(ignored->onWorld(world,()->{
            var plan=gallery[0].excavation();String done=NativeExcavation.issue(world,town,gallery[0],10);
            require(plan.passageReady()&&done!=null&&done.startsWith("Tunnel complete."),"The gallery did not finish taking its frame down: "+done);
            for(var p:frame)require(NativeExcavation.observe(world,p).empty(),"A frame piece still stands in or beside the doorway at "+p);
            require(beams[0]==7&&plan.output().getOrDefault(ExcavationFittings.BEAM,0)==7,"The frame's seven beams did not come back: "+plan.output());
            for(int x=522;x<=524;x+=2)for(int y=111;y<=112;y++)require(ExcavationFittings.BEAM.equals(NativeExcavation.observe(world,new WarehouseBatch.Point(x,y,-375)).block()),"The frame further along was disturbed");
            require(plan.fitted()==7&&plan.fittingsTotal()==7,"The gallery's count of fittings is wrong: "+plan.fitted()+" of "+plan.fittingsTotal());
            // The gallery is finished. Rock turns up in the shared column again, and only the side passage has work.
            fixtureBlock(world,second.x(),second.y(),second.z(),"Rock_Stone");
            var work=side[0].excavation();
            require(NativeExcavation.issue(world,town,side[0],10)==null&&work.next()!=null&&work.next().point().equals(second),"The side passage did not put the rock in its junction first: "+work.next());
        }));
        for(int i=0;i<24;i++)chain=chain.thenCompose(ignored->onWorld(world,()->{
            var work=side[0];var plan=work.excavation();
            if(NativeExcavation.issue(world,town,work,10)!=null||plan.next()==null)return;
            var target=plan.next().point();
            require(plan.prepare(NativeExcavation.drops(plan.next())),"Side passage drops failed");
            for(int tick=0;tick<40;tick++)plan.attend(.5,true,1);
            var stand=standing(world,work,from);
            require(NativeExcavation.excavate(world,town,work,10,stand,()->{}),"Side passage removal failed at "+target+" from "+stand);
            require(plan.deposit(plan.id()),"Side passage deposit failed");
        }));
        return chain.thenCompose(ignored->after(world,()->{
            var plan=side[0].excavation();String done=NativeExcavation.issue(world,town,side[0],10);
            require(plan.next()==null&&done!=null&&done.startsWith("Tunnel complete."),"The side passage was not finished: "+done);
            for(int x=524;x<=526;x++)for(int z=-382;z<=-380;z++)for(int y=111;y<=113;y++)require(NativeExcavation.observe(world,new WarehouseBatch.Point(x,y,z)).empty(),"The doorway or side passage is not open at "+x+", "+y+", "+z);
            DOORWAY[0]="a built frame taken down whole where a side passage opened at 524, 111, -381 (7 beams back, the frame at -375 left standing), and rock in that shared column dug first by the gallery and then by the side passage ("+plan.completed()+" of "+plan.total()+")";
        }));
    }
    /** The planner's own verdict on a draft: the rock as the world holds it, then the mine's rules. */
    private static MineshaftNetwork.Route route(World world,TownRecord town,ApprenticeWorkplace base,WarehouseBatch.Point start,MineshaftSelection.Direction direction,
        ExcavationPlan.Layout layout,int length,UUID prerequisite) {
        var sites=town.getApprenticeWorkplaces();var draft=draft(start,direction,layout,length);
        var review=NativeExcavation.review(world,town,draft,draft.mineshaftEntrance(),layout,MineshaftNetwork.leftAlone(sites,base,prerequisite));
        require(review.issue()==null||NativeExcavation.NOTHING_LEFT.equals(review.issue()),"Rock review failed "+direction+": "+review.issue());
        return MineshaftNetwork.route(sites,base,prerequisite,draft.area(),draft.mineshaftEntrance(),layout,review.cells());
    }
    private static ApprenticeWorkplace draft(WarehouseBatch.Point start,MineshaftSelection.Direction direction,ExcavationPlan.Layout layout,int length) {
        var draft=new ApprenticeWorkplace("Tunnel",TownsfolkWorkType.MINING,MineshaftSelection.area(start.x(),start.y()-1,start.z(),direction,length,layout));
        draft.setMineshaftConfiguration(MineshaftSelection.entrance(direction),layout);return draft;
    }
    /** Adds a tunnel the way the planner's Add button does. */
    private static ApprenticeWorkplace planned(World world,TownRecord town,ApprenticeWorkplace base,UUID owner,PlotInstance mine,WarehouseBatch.Point start,
        MineshaftSelection.Direction direction,ExcavationPlan.Layout layout,int length,UUID prerequisite,String name) {
        return planned(world,town,base,owner,mine,start,direction,layout,length,prerequisite,name,ExcavationFittings.Mode.NONE);
    }
    private static ApprenticeWorkplace planned(World world,TownRecord town,ApprenticeWorkplace base,UUID owner,PlotInstance mine,WarehouseBatch.Point start,
        MineshaftSelection.Direction direction,ExcavationPlan.Layout layout,int length,UUID prerequisite,String name,ExcavationFittings.Mode fittings) {
        var route=route(world,town,base,start,direction,layout,length,prerequisite);
        require(route.ready(),name+" was refused: "+route.issue());
        var draft=draft(start,direction,layout,length);
        var site=new ApprenticeWorkplace(name,TownsfolkWorkType.MINING,draft.area());site.setMineshaftConfiguration(draft.mineshaftEntrance(),layout);
        require(site.excavation().start(0,owner,mine.getPlotId(),site.area(),site.mineshaftEntrance(),layout,route.cells(),fittings)==null,name+" order failed");
        String refused=town.getApprenticeWorkplaces().addTunnel(town,owner,base.id(),site,prerequisite);
        require(refused==null,name+" was not added: "+refused);return site;
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
    private static WarehouseBatch.Point standing(World world,ApprenticeWorkplace site){
        return standing(world,site,new org.joml.Vector3d(9.5,128,site.excavation().next().point().z()+.5));
    }
    private static WarehouseBatch.Point standing(World world,ApprenticeWorkplace site,org.joml.Vector3d from){
        var plan=site.excavation();var target=plan.next().point();
        var position=ExcavationApproach.choose(plan,target,from,0,p->{
            if(!com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,p.x(),p.y(),p.z()))return null;
            return new org.joml.Vector3d(p.x()+.5,p.y(),p.z()+.5);
        },p->NativeExcavation.clearReach(world,p,target));
        require(position!=null,"No visible native face approach");return ExcavationApproach.feet(position);
    }
    private static CompletableFuture<Void> after(World world,Runnable action){return later(world,150,action);}
    private static CompletableFuture<Void> later(World world,long millis,Runnable action){return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(millis,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));}
    private static CompletableFuture<Void> onWorld(World world,Runnable action){return CompletableFuture.runAsync(()->{
        // No player holds this disposable fixture's one chunk active between delayed checks.
        // Production code still requires a naturally simulated chunk and never promotes it.
        for(var at:FIXTURE_CHUNKS) {
            var chunk=ChunkSectionBlockUtil.worldChunkIfInMemory(world,ChunkUtil.indexChunk(at[0],at[1]));
            if(chunk!=null)chunk.setFlag(com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag.TICKING,true);
        }
        action.run();
    },world);}
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
