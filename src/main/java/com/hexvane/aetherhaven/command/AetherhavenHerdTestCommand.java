package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
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

/** Operator-only disposable world with real livestock. Never adopts a player barn or saves a town. */
final class AetherhavenHerdTestCommand extends AbstractCommandCollection {
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    AetherhavenHerdTestCommand(){super("herds","Inspect native Rancher livestock in a temporary world");setPermissionGroups("hytale:WorldEditor");addSubCommand(new WorldCheck());}
    private static final class WorldCheck extends AbstractAsyncCommand {
        WorldCheck(){super("worldcheck","Test core breeding, maturation and products in an isolated world");setPermissionGroups("hytale:WorldEditor");}
        @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            verifyCaptureGroups();
            if(!RUNNING.compareAndSet(false,true)){report(ctx,"A herd test is already running.");return CompletableFuture.completedFuture(null);}
            String name="aetherhaven_herd_check_"+UUID.randomUUID().toString().replace("-","");
            var universe=Universe.get();var path=universe.validateWorldPath(name);
            if(Files.exists(path)){RUNNING.set(false);return CompletableFuture.failedFuture(new IllegalStateException("Fixture path exists"));}
            var config=new WorldConfig();com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);config.setWorldGenProvider(new VoidWorldGenProvider());config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
            config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
            config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
            return universe.makeWorld(name,path,config).thenCompose(world->{
                CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);int[] results={0,0};int index=0;
                for(var family:List.of("Cow","Sheep","Goat","Pig","Chicken","Mosshorn","Skrill","Tetrabird","Bluebird")) {
                    int offset=index++;
                    chain=chain.thenCompose(ignored->exercise(world,family,offset).handle((done,error)->{
                        if(error==null){results[0]++;report(ctx,family+": physical feed sources, breeding, maturation and saved delivery passed.");}
                        else{results[1]++;Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();report(ctx,family+": FAILED: "+cause.getMessage());}
                        return null;
                    }));
                }
                return chain.orTimeout(90,TimeUnit.SECONDS).whenCompleteAsync((done,error)->{
                    report(ctx,"Herd isolated world check: "+results[0]+" passed, "+results[1]+" failed"+(error==null?".":"; interrupted."));universe.removeWorld(name);
                });
            }).whenComplete((done,error)->RUNNING.set(false));
        }
    }
    private static void verifyCaptureGroups() {
        var groups=com.hypixel.hytale.builtin.tagset.config.NPCGroup.getAssetMap();
        int capture=groups.getIndex(RanchCaptureCompatibility.CAPTURE_GROUP);
        require(capture>=0,"Native Capture Crate group is missing");
        var lookup=com.hypixel.hytale.builtin.tagset.TagSetPlugin.get(com.hypixel.hytale.builtin.tagset.config.NPCGroup.class);
        for(String role:List.of("Chicken","Tetrabird","Bluebird"))
            require(lookup.tagInSet(capture,NPCPlugin.get().getIndex(role)),"Native Capture Crate cannot transport "+role);
        for(String role:List.of("Aetherhaven_Ranch_Chicken","Aetherhaven_Ranch_Bluebird","Aetherhaven_Ranch_Tetrabird_Young"))
            require(!lookup.tagInSet(capture,NPCPlugin.get().getIndex(role)),"Native Capture Crate can detach managed offspring "+role);
    }
    private static CompletableFuture<Void> exercise(World world,String family,int offset) {
        int x=16+64*offset,y=128,z=16;var point=new WarehouseBatch.Point(x,y,z);
        var town=new TownRecord(UUID.randomUUID(),UUID.randomUUID(),world.getName(),x,y,z,1,1,0);
        var mastery=com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"rancher");
        while(mastery.train()){}
        var plot=new PlotInstance(UUID.randomUUID(),"plot_barn",PlotInstanceState.COMPLETE,new PlotFootprintRecord(x-8,y-1,z-8,x+8,y+5,z+8),x,y,z,0);
        town.getPlotInstances().add(plot);var production=town.getOrCreatePlotProduction(plot.getPlotId());
        var loads=new ArrayList<CompletableFuture<?>>();
        for(int section=3;section<=4;section++)loads.add(world.getChunkStore().getChunkSectionReferenceAsync(ChunkUtil.chunkCoordinate(x),section,0,GetChunkFlags.SET_TICKING));
        var breeders=new ArrayList<UUID>();var offspring=new UUID[1];
        var chain=CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenCompose(ignored->onWorld(world,()->{
            for(int xx=x-8;xx<=x+8;xx++)for(int zz=z-8;zz<=z+8;zz++)require(ChunkSectionBlockUtil.setBlockByKey(world,xx,y-1,zz,"Soil_Dirt",0),"Ground placement failed");
            require(ChunkSectionBlockUtil.setBlockByKey(world,x,y,z,"Bench_Trough",0),"Trough placement failed");
            if("Bluebird".equals(family)) {
                for(int xx=x-6;xx<=x+6;xx++)for(int zz=z-6;zz<=z+6;zz++) {
                    require(ChunkSectionBlockUtil.setBlockByKey(world,xx,y+3,zz,"Rock_Stone",0),"Aviary roof placement failed");
                    if(xx==x-6||xx==x+6||zz==z-6||zz==z+6)for(int yy=y;yy<y+3;yy++)
                        require(ChunkSectionBlockUtil.setBlockByKey(world,xx,yy,zz,"Rock_Stone",0),"Aviary wall placement failed");
                }
            }
            var store=world.getEntityStore().getStore();
            // Regression: the shipped Barn has two-block-wide pens. No three-by-three spawn fits here.
            if("Sheep".equals(family))for(int xx=x-2;xx<=x+1;xx++)for(int zz=z-1;zz<=z+4;zz++)
                if(xx==x-2||xx==x+1||zz==z-1||zz==z+4)for(int yy=y;yy<=y+1;yy++)
                    require(ChunkSectionBlockUtil.setBlockByKey(world,xx,yy,zz,"Rock_Stone",0),"Narrow pen wall placement failed");
            for(int dx:"Sheep".equals(family)?List.of(-1,0):List.of(-3,3)) {
                var animal=NPCPlugin.get().spawnNPC(store,family,null,new Vector3d(x+dx+.5,y,z+("Sheep".equals(family)?2:0)+.5),Rotation3f.ZERO);
                require(animal!=null,"Breeder spawn failed");breeders.add(store.getComponent(animal.first(),UUIDComponent.getComponentType()).getUuid());
            }
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var review=NativeHerdService.review(world,town,plot);require(review.ready()&&review.pairs().size()==1,"Herd review: "+review.issue());
            require(new HashSet<>(review.pairs().getFirst().breeders()).equals(new HashSet<>(breeders)),"Review changed breeding animals");
            require(production.herd().adopt(0,review.pairs())==null,"Herd adoption failed");
            require(NativeHerdService.pairReady(world,town,plot,review.pairs().getFirst()),"Pair not available after review");
            verifyFeedDelivery(world,town,plot,production,x,y,z);
            for(int i=0;i<4;i++) {
                if(i>0)production.herd().advance(family,HerdProductionState.FEED_INTERVAL,true);
                require(production.herd().supply(()->true),"Feed supply failed");
                require(NativeHerdService.feed(world,town,plot,production,family),"Real feeding failed");
                require(production.herd().feed()==0,"Attended feeding did not consume wheat");
                if(i<3)require(production.herd().herds().get(family).care().offspring()==null,"Birth occurred before four feeds");
            }
            var care=production.herd().herds().get(family).care();offspring[0]=care.offspring();
            require(offspring[0]!=null&&!breeders.contains(offspring[0]),"No separate offspring spawned");
            var live=NativeHerdService.offspring(world,town,plot,production.herd().herds().get(family));require(live!=null,"Spawned offspring is unavailable");
            if("Sheep".equals(family)) {
                var p=world.getEntityStore().getStore().getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()).getPosition();
                require(p.x>=x-1&&p.x<x+1&&p.z>=z+1&&p.z<z+4,"Lamb spawned outside its narrow pen despite empty space across the walls");
                var body=new com.hypixel.hytale.math.shape.Box(world.getEntityStore().getStore().getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType()).getBoundingBox()).offset(p);
                for(var id:breeders) {
                    var breeder=world.getEntityStore().getStore().getExternalData().getRefFromUUID(id);
                    var at=world.getEntityStore().getStore().getComponent(breeder,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()).getPosition();
                    var bounds=new com.hypixel.hytale.math.shape.Box(world.getEntityStore().getStore().getComponent(breeder,com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType()).getBoundingBox()).offset(at);
                    require(!body.isIntersecting(bounds),"Lamb overlaps its breeding animals");
                }
            }
            require(world.getEntityStore().getStore().getComponent(live,NPCEntity.getComponentType()).getRoleName().equals("Aetherhaven_Ranch_"+family+"_Young"),"Unexpected juvenile role");
            if(Set.of("Mosshorn","Tetrabird","Bluebird").contains(family)) {
                var model=world.getEntityStore().getStore().getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel();
                require(model.getScale()>=.54&&model.getScale()<=.61,"Young animal did not use its smaller native model scale");
            }
            growWithCare(world,town,plot,production,family);
            require(NativeHerdService.mature(world,town,plot,production,family,offspring[0]),"Native maturation request failed");
        }));
        chain=chain.thenCompose(ignored->after(world,()->{
            var herd=production.herd().herds().get(family);var live=NativeHerdService.offspring(world,town,plot,herd);require(live!=null,"Mature offspring disappeared");
            require(world.getEntityStore().getStore().getComponent(live,NPCEntity.getComponentType()).getRoleName().equals("Aetherhaven_Ranch_"+family),"Native role change did not mature offspring");
            if("Mosshorn".equals(family))require(world.getEntityStore().getStore().getComponent(live,
                com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel().getScale()>1,"Mature Mosshorn kept its calf scale");
            if(Set.of("Tetrabird","Bluebird").contains(family)) {
                var adult=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(family);
                float scale=world.getEntityStore().getStore().getComponent(live,
                    com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel().getScale();
                require(adult!=null&&scale>=adult.getMinScale()&&scale<=adult.getMaxScale(),"Mature bird scale "+scale+" is outside its native adult range");
            }
            require(!NativeHerdService.collect(world,town,plot,production,family,offspring[0],meat(family)),"Freshly matured livestock paid immediately");
            growWithCare(world,town,plot,production,family);
            RanchProducts.Product living=switch(family) {
                case "Cow"->new RanchProducts.Product(family,ProductionWithdrawal.MILK_BUCKET_ITEM_ID,1,false,"Container_Bucket");
                case "Sheep"->new RanchProducts.Product(family,"Ingredient_Fabric_Scrap_Wool",2,false,"Tool_Shears_Basic");
                case "Chicken"->new RanchProducts.Product(family,"Food_Egg",1,false,"Tool_Feedbag");
                case "Skrill"->RanchProducts.product(family,"Ingredient_Feathers_Red");
                case "Tetrabird"->RanchProducts.product(family,"Ingredient_Feathers_Dark");
                case "Bluebird"->RanchProducts.product(family,"Ingredient_Feathers_Blue");
                default->null;
            };
            if(living!=null) {
                verifyAssets(living);require(NativeHerdService.collect(world,town,plot,production,family,offspring[0],living),"Living product collection failed");
                require(live.isValid()&&offspring[0].equals(herd.care().offspring()),"Living collection removed livestock");
                if("Sheep".equals(family))verifyShearedRecovery(world,town,plot,production,live);
                verifyDelivery(production,living);growWithCare(world,town,plot,production,family);
                if("Sheep".equals(family)) {
                    require(NativeHerdService.reconcileAppearance(world,town,plot,herd),"Tended fleece did not regrow");
                    verifyFleece(live,world,true);
                    require(herd.recoveringOutput()==null,"Recovered fleece still marked shorn");
                    require(production.herd().cargo()==null,"Regrowth created extra wool");
                }
            }
            var manure=RanchProducts.product(family,"Ingredient_Poop");verifyAssets(manure);
            require(NativeHerdService.collect(world,town,plot,production,family,offspring[0],manure),"Attended manure collection failed");
            require(live.isValid()&&offspring[0].equals(herd.care().offspring()),"Manure collection removed livestock");
            require(!NativeHerdService.collect(world,town,plot,production,family,offspring[0],manure),"Manure collection paid twice");
            verifyDelivery(production,manure);growWithCare(world,town,plot,production,family);
            var meat="Mosshorn".equals(family)?RanchProducts.product(family,"Ingredient_Hide_Heavy"):meat(family);verifyAssets(meat);
            require(!NativeHerdService.collect(world,town,plot,production,family,breeders.getFirst(),meat),"Breeding animal harvested");
            require(NativeHerdService.collect(world,town,plot,production,family,offspring[0],meat),"Raised livestock harvest failed");
            require(!live.isValid()&&herd.care().offspring()==null,"Harvested offspring remains alive");
            require(!NativeHerdService.collect(world,town,plot,production,family,offspring[0],meat),"Same offspring paid twice");
            verifyDelivery(production,meat);
            var store=world.getEntityStore().getStore();
            for(var id:breeders) {
                var breeder=store.getExternalData().getRefFromUUID(id);require(breeder!=null&&breeder.isValid(),"Breeding stock was removed");
                require(family.equals(store.getComponent(breeder,NPCEntity.getComponentType()).getRoleName()),"Breeding role was changed");
            }
            require(production.herd().cargo()==null,"Fixture left undelivered cargo");
            var resident=new ResidentNpcRecord();resident.setKind("rancher");town.getResidentNpcRecords().add(resident);
            var work=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.RANCHING;
            require(town.getApprenticeWorkplaces().unlock(town,work)==null,"Test food workplace unlock failed");
            require(town.getApprenticeWorkplaces().save(town,null,work,new WorkplaceArea(x-7,y,z-7,x+8,z+8),"Test food herd")==null,"Test food workplace placement failed");
            require(NativeHerdService.review(world,town,plot).pairs().isEmpty(),"Core review includes Apprentice livestock");
            require(!NativeHerdService.pairReady(world,town,plot,herd.pair()),"Core tending can use an Apprentice trough");
        }));
        return chain;
    }
    private static void verifyShearedRecovery(World world,TownRecord town,PlotInstance plot,PlotProductionState production,
            com.hypixel.hytale.component.Ref<EntityStore> live) {
        var herd=production.herd().herds().get("Sheep");verifyFleece(live,world,false);
        var store=world.getEntityStore().getStore();
        var before=store.getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel();
        require(herd.recoveringOutput()!=null,"Shearing did not record recovery");
        var saved=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
        var restored=saved.herd().herds().get("Sheep");
        saved.herd().advance("Sheep",RanchCare.GROW_MINUTES,false);
        require(restored.care().ageMinutes()==0,"Unsimulated fleece grew");
        var appearance=RanchProductAppearance.get("Sheep","Ingredient_Fabric_Scrap_Wool");
        require(RanchProductAppearance.apply(live,store,appearance,true),"Fixture appearance change failed");
        require(!NativeHerdService.reconcileAppearance(world,town,plot,restored),"Reload prematurely completed fleece growth");
        verifyFleece(live,world,false);
        var after=store.getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel();
        require(before.getScale()==after.getScale()&&Objects.equals(before.getRandomAttachmentIds(),after.getRandomAttachmentIds()),"Reconciliation changed other appearance details");
        require(!NativeHerdService.collect(world,town,plot,production,"Sheep",herd.care().offspring(),RanchProducts.product("Sheep","Ingredient_Fabric_Scrap_Wool")),"Shorn sheep paid twice");
        require(com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getIndex("SFX_Sheep_Sheared")>=0,"Native shearing sound missing");
    }
    private static void verifyFleece(com.hypixel.hytale.component.Ref<EntityStore> live,World world,boolean ready) {
        var store=world.getEntityStore().getStore();
        var model=store.getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType()).getModel();
        var saved=store.getComponent(live,com.hypixel.hytale.server.core.modules.entity.component.PersistentModel.getComponentType()).getModelReference();
        require(ready=="Wool".equals(model.getRandomAttachmentIds().get("Fleece")),"Rendered fleece differs from its care state");
        require(ready=="Wool".equals(saved.getRandomAttachmentIds().get("Fleece")),"Persistent fleece differs from its care state");
    }
    private static void growWithCare(World world,TownRecord town,PlotInstance plot,PlotProductionState production,String family) {
        for(int i=0;i<4;i++) {
            production.herd().advance(family,HerdProductionState.FEED_INTERVAL,true);
            require(production.herd().supply(()->true),"Care supply failed");
            require(NativeHerdService.feed(world,town,plot,production,family),"Care feeding failed");
        }
    }
    private static void verifyFeedDelivery(World world,TownRecord town,PlotInstance barn,PlotProductionState production,int x,int y,int z) {
        var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
        var barnStorage=new WarehouseBatch.Point(x-6,y,z-6);var farmStorage=new WarehouseBatch.Point(x+12,y,z);
        var farm=new PlotInstance(UUID.randomUUID(),"plot_farm",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(x+11,y-1,z-1,x+13,y+1,z+1),x+12,y,z,0);
        town.getPlotInstances().add(farm);
        String block=com.hexvane.aetherhaven.AetherhavenConstants.BLOCK_PRODUCTION_STORAGE;
        require(ChunkSectionBlockUtil.setBlockByKey(world,barnStorage.x(),y,barnStorage.z(),block,0),"Barn storage placement failed");
        require(ChunkSectionBlockUtil.setBlockByKey(world,farmStorage.x(),y-1,z,"Soil_Dirt",0),"Farm storage floor failed");
        require(ChunkSectionBlockUtil.setBlockByKey(world,farmStorage.x(),y,z,block,0),"Farm storage placement failed");
        var farmStock=town.getOrCreatePlotProduction(farm.getPlotId());farmStock.addAmount(HerdProductionState.FEED,6,64);
        var source=ProductionStockSources.at(world,town,plugin,farm.getPlotId(),farmStorage);
        require(source!=null,"Real farm pickup source missing");
        require(production.herd().configureCourierFeed(0,8),"Courier feed target failed");
        require(HerdFeedSupplyPlanner.queue(world,town,plugin,List.of(source),System.currentTimeMillis()),"Partial wheat supply was not queued");
        var stock=town.getWarehouse();var batch=stock.workplaceBatch(barn.getPlotId());
        require(batch!=null&&batch.herdFeed()&&barnStorage.equals(batch.destination()),"Feed delivery points to the wrong barn storage: "+(batch==null?"no batch":batch.destination())+" expected "+barnStorage);
        require(farmStock.getAmount(HerdProductionState.FEED)==6&&production.herd().feed()==0,"Reservation moved wheat remotely");
        require(!HerdFeedSupplyPlanner.queue(world,town,plugin,List.of(source),System.currentTimeMillis()),"Duplicate feed order queued");
        require(stock.pickup(batch.id(),"feed_fixture",1)==0,"Farm wheat was picked up from general storage");
        require(stock.pickupAt(batch.id(),"feed_fixture",1,source,farmStorage)==6,"Physical farm pickup failed");
        require(HerdFeedSupplyPlanner.destinationReady(world,town,plugin,batch),"Barn delivery destination unavailable");
        require(!HerdFeedSupplyPlanner.deliver(world,town,plugin,batch,"other_courier"),"A different Courier delivered this cargo");
        require(ChunkSectionBlockUtil.setBlockByKey(world,barnStorage.x(),y,barnStorage.z(),"Empty",0),"Removing fixture barn storage failed");
        require(HerdFeedSupplyPlanner.invalid(world,town,plugin,batch),"Broken barn storage did not invalidate delivery");
        require(!HerdFeedSupplyPlanner.deliver(world,town,plugin,batch,"feed_fixture"),"Delivered through a missing storage block");
        require(ChunkSectionBlockUtil.setBlockByKey(world,barnStorage.x(),y,barnStorage.z(),block,0),"Restoring fixture barn storage failed");
        require(HerdFeedSupplyPlanner.deliver(world,town,plugin,batch,"feed_fixture"),"Feed delivery failed at restored barn storage");
        require(production.herd().feed()==6&&farmStock.getAmount(HerdProductionState.FEED)==0,"Feed delivery accounting mismatch");
        require(!HerdFeedSupplyPlanner.deliver(world,town,plugin,batch,"feed_fixture"),"Repeated feed delivery duplicated wheat");
        require(stock.batches().isEmpty()&&stock.count(TownWarehouse.Page.OUTPUT,HerdProductionState.FEED)==0,"Feed created recipe output or retained a finished batch");
        for(int i=0;i<6;i++)require(production.herd().takeFeed(()->true),"Fixture feed reset failed");
        require(production.herd().configureCourierFeed(1,0),"Fixture feed target reset failed");
    }
    private static RanchProducts.Product meat(String family) {
        String kind=switch(family){case "Cow"->"Beef";case "Pig"->"Pork";case "Chicken"->"Chicken";default->"Wildmeat";};
        return RanchProducts.product(family,"Food_"+kind+"_Raw");
    }
    private static void verifyAssets(RanchProducts.Product product) {
        require(Item.getAssetMap().getAsset(product.output())!=null,"Output asset is missing: "+product.output());
        require(Item.getAssetMap().getAsset(product.tool())!=null,"Held tool asset is missing: "+product.tool());
    }
    private static void verifyDelivery(PlotProductionState production,RanchProducts.Product product) {
        require(production.getAmount(product.output())==0,"Item reached storage before delivery");
        var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(production),PlotProductionState.class);
        var id=production.herd().cargo().id();require(copy.herd().cargo()!=null&&copy.herd().cargo().id().equals(id),"Cargo lost on reload");
        require(copy.herd().deliver(copy,id,0)==0,"Full storage accepted products");
        require(copy.herd().deliver(copy,id,1)==1,"Partial delivery failed");
        require(copy.herd().deliver(copy,id,product.amount())==product.amount()-1,"Final saved delivery failed");
        require(copy.herd().deliver(copy,id,100)==0,"Saved delivery duplicated products");
        require(production.herd().deliver(production,id,product.amount())==product.amount(),"Live delivery failed");
    }
    private static CompletableFuture<Void> after(World world,Runnable action){return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(250,TimeUnit.MILLISECONDS)).thenCompose(ignored->onWorld(world,action));}
    private static CompletableFuture<Void> onWorld(World world,Runnable action){return CompletableFuture.runAsync(action,world);}
    private static void require(boolean yes,String issue){if(!yes)throw new IllegalStateException(issue);}
    private static void report(CommandContext ctx,String message){ctx.sendMessage(Message.raw(message));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(message);}
}
