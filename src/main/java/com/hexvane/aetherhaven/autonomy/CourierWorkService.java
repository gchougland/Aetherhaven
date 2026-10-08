package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.ApprenticeWorkState;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;

/** Native Seek travel with physical arrival gates. Never teleports a courier or loads a chunk. */
public final class CourierWorkService {
    private CourierWorkService() {}
    private record Claim(String courier,long until) {}
    private static final Map<World,Map<UUID,Claim>> CLAIMS=Collections.synchronizedMap(new WeakHashMap<>());
    private record Trip(WarehouseBatch batch,String action,WarehouseBatch.Point endpoint,UUID sourcePlot) {}

    public static boolean tick(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
                               NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
                               TownRecord town,TownManager manager,AetherhavenPlugin plugin) {
        var route=store.getComponent(ref,CourierTravelState.getComponentType());
        long now=System.currentTimeMillis();
        if(route!=null&&now<route.nextCheck)return route.active;
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        String courier=identity==null?null:identity.getCharacterId();
        var progress=town==null||courier==null?null:town.getTownsfolkProgress().get(courier.toLowerCase(Locale.ROOT));
        boolean carrying=town!=null&&courier!=null&&town.getWarehouse().batches().stream().anyMatch(b->courier.equals(b.courierId()));
        boolean eligible=progress!=null&&progress.isEmployed()&&(progress.priority(TownsfolkWorkType.LOGISTICS)>0||carrying)
            &&uuid!=null&&TownAdmission.isMember(town,uuid.getUuid())&&!TownVillagerBinding.KIND_GUARD.equals(binding.getKind())
            &&!town.getPayroll().overdue(courier)&&store.getComponent(ref,DeathComponent.getComponentType())==null;
        if(!eligible) { stop(ref,npc,buffer,route);return false; }
        if(route==null) {route=new CourierTravelState();buffer.putComponent(ref,CourierTravelState.getComponentType(),route);}
        route.nextCheck=now+250;
        // Pause with cargo intact for needs, unpaid wages or an inactive town.
        if(now<route.retryAt||needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()
            ||!TownEconomyPresence.hasMemberHere(store,town)) {
            stop(ref,npc,buffer,route);return false;
        }
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return false;
        var world=store.getExternalData().getWorld();var pos=transform.getPosition();
        WorkplaceSupplyPlanner.tick(world,manager,town,plugin,now);
        var apprentice=store.getComponent(ref,ApprenticeWorkState.getComponentType());
        int otherPriority=carrying||route.active||apprentice!=null&&apprentice.active?0:
            ApprenticeWorkService.availablePriority(world,town,plugin,progress,courier,apprentice,now);
        if(!WorkSelection.chooseDelivery(progress.priority(TownsfolkWorkType.LOGISTICS),otherPriority,
                carrying,route.active,apprentice!=null&&apprentice.active)) {
            stop(ref,npc,buffer,route);return false;
        }
        Map<UUID,Claim> claims;
        synchronized(CLAIMS) { claims=CLAIMS.computeIfAbsent(world,w->new HashMap<>()); }
        claims.values().removeIf(c->c.until()<now);
        var trip=chooseTrip(world,manager,town,courier,pos,claims,now,route);
        if(trip==null||!loaded(world,trip.endpoint())) { stop(ref,npc,buffer,route);return false; }
        // The original desk can be broken; another loaded desk still accesses the same town stock.
        if((trip.action().equals("pickup")||trip.action().equals("return"))&&
            (trip.sourcePlot()==null?!deskExists(world,trip.endpoint()):!sourceAvailable(world,town,trip.sourcePlot(),trip.endpoint()))) {
            stop(ref,npc,buffer,route);return false;
        }
        if(trip.action().equals("deliver")&&!destinationReady(world,town,plugin,trip.batch())) {
            stop(ref,npc,buffer,route);return false;
        }
        boolean changed=!route.active||!trip.batch().id().equals(route.batchId)||!trip.action().equals(route.action)||!trip.endpoint().equals(route.endpoint);
        if(changed) {
            var approach=approach(world,trip.endpoint(),pos,route.approachAttempt);
            if(approach==null) { route.retryAt=now+3000;stop(ref,npc,buffer,route);return false; }
            if(!route.active) {
                ApprenticeWorkService.interrupt(ref,store,npc,buffer);
                var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
                VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
                buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
                var tourist=store.getComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType());
                if(tourist!=null) {
                    tourist.clearVisitPlot();tourist.clearTravelTarget();tourist.resetAutonomyStallTracking();
                    tourist.setPhase(com.hexvane.aetherhaven.tourist.TouristAutonomyState.PHASE_IDLE);
                    tourist.setNextDecisionEpochMs(now+1000);
                    buffer.putComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType(),tourist);
                }
            }
            route.batchId=trip.batch().id();route.action=trip.action();route.endpoint=trip.endpoint();
            route.approach=approach;route.beganAt=now;route.arrivedAt=0;route.active=true;
        }
        claims.put(trip.batch().id(),new Claim(courier,now+1500));
        if(!loaded(world,new WarehouseBatch.Point((int)Math.floor(route.approach.x),(int)Math.floor(route.approach.y),(int)Math.floor(route.approach.z)))) {
            stop(ref,npc,buffer,route);return false;
        }
        if(!clearApproach(world,route.approach)) {
            route.approachAttempt++;route.retryAt=now+1000;stop(ref,npc,buffer,route);return false;
        }
        if(arrived(pos,route.approach)) {
            NpcStandStill.hold(ref,store,npc,route.approach,buffer);
            if(route.arrivedAt==0) {route.arrivedAt=now;return true;}
            if(now-route.arrivedAt<750)return true;
            var stock=town.getWarehouse();int limit=CourierAssignments.carryingStacks(progress,plugin.getProfessionKits());
            boolean moved=switch(route.action) {
                case "pickup" -> stock.smelting().origin(trip.sourcePlot())!=null?
                    stock.pickupSmeltingAt(route.batchId,courier,limit,trip.sourcePlot(),trip.endpoint())>0:
                    stock.pickupAt(route.batchId,courier,limit,trip.sourcePlot()==null?null:
                    ProductionStockSources.at(world,town,plugin,trip.sourcePlot(),trip.endpoint()),trip.endpoint())>0;
                case "deliver" -> trip.batch().herdFeed()?HerdFeedSupplyPlanner.deliver(world,town,plugin,trip.batch(),courier):stock.deliver(route.batchId,courier);
                case "collect" -> stock.collectReturn(route.batchId,courier,limit)>0;
                case "return" -> stock.returnCargo(route.batchId,courier);
                default -> false;
            };
            if(moved) {
                // Only an accepted destination delivery earns mastery, never reservation or return loops.
                TownsfolkMastery.completed(progress,TownsfolkMastery.Work.DELIVERY,"deliver".equals(route.action));
                manager.updateTown(town);
            }
            claims.remove(route.batchId);route.retryAt=moved?0:now+3000;route.approachAttempt=0;
            stop(ref,npc,buffer,route);return true;
        }
        route.arrivedAt=0;
        if(now-route.beganAt>30_000) {
            // Try another clear side next time. No stuck recovery teleport may complete a delivery.
            route.approachAttempt++;route.retryAt=now+3000;claims.remove(route.batchId);
            stop(ref,npc,buffer,route);return false;
        }
        npc.setLeashPoint(new Vector3d(route.approach));
        VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
        VillagerDoorUtil.tryOpenDoorsTowardLeash(world,pos,route.approach,null);
        buffer.putComponent(ref,NPCEntity.getComponentType(),npc);
        return true;
    }

    private static Trip chooseTrip(World world,TownManager manager,TownRecord town,String courier,Vector3d pos,
                                   Map<UUID,Claim> claims,long now,CourierTravelState route) {
        var stock=town.getWarehouse();
        var carrying=stock.batches().stream().filter(b->courier.equals(b.courierId())).findFirst().orElse(null);
        if(carrying!=null) {
            if(carrying.cancelled())return deskTrip(world,manager,town,carrying,"return",pos,route);
            return carrying.destination()==null?null:new Trip(carrying,"deliver",carrying.destination(),null);
        }
        for(var batch:stock.batches()) {
            if(batch.courierId()!=null)continue;
            var claim=claims.get(batch.id());if(claim!=null&&claim.until()>now&&!courier.equals(claim.courier()))continue;
            if(batch.cancelled()&&!batch.deliveredItems().isEmpty()&&loaded(world,batch.destination()))
                return new Trip(batch,"collect",batch.destination(),null);
            if(!batch.cancelled())for(var source:batch.pendingSources()) {
                if(source.plot()!=null) {
                    if(sourceAvailable(world,town,source.plot(),source.pickup()))
                        return new Trip(batch,"pickup",source.pickup(),source.plot());
                } else if(source.page()==TownWarehouse.Page.GENERAL) {
                    var trip=deskTrip(world,manager,town,batch,"pickup",pos,route);if(trip!=null)return trip;
                }
            }
        }
        return null;
    }
    private static boolean sourceAvailable(World world,TownRecord town,UUID id,WarehouseBatch.Point point) {
        var origin=town.getWarehouse().smelting().origin(id);
        return origin!=null?origin.location().equals(point)&&NativeFurnaceService.available(world,point)
            :ProductionStockSources.at(world,town,AetherhavenPlugin.get(),id,point)!=null;
    }
    private static Trip deskTrip(World world,TownManager manager,TownRecord town,WarehouseBatch batch,String action,Vector3d pos,CourierTravelState route) {
        if(route.active&&batch.id().equals(route.batchId)&&action.equals(route.action)&&loaded(world,route.endpoint)&&deskExists(world,route.endpoint))
            return new Trip(batch,action,route.endpoint,null);
        return WarehouseDeskBlock.loaded(world,manager,town).stream().min(Comparator.comparingDouble(p->pos.distanceSquared(p.x()+.5,p.y(),p.z()+.5)))
            .map(p->new Trip(batch,action,p,null)).orElse(null);
    }
    public static boolean arrived(Vector3d position,Vector3d destination) {
        double x=position.x-destination.x,z=position.z-destination.z;
        return x*x+z*z<=.7*.7&&Math.abs(position.y-destination.y)<=.65;
    }
    static boolean destinationReady(World world,TownRecord town,AetherhavenPlugin plugin,WarehouseBatch batch) {
        if(batch.herdFeed())return HerdFeedSupplyPlanner.destinationReady(world,town,plugin,batch);
        var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
        var recipe=plugin.getApprenticeRecipes().find(batch.recipeId());var point=batch.destination();
        if(point==null||!loaded(world,point))return false;
        // Native contents already exist at this furnace. Collect them even if its site was edited.
        if(batch.nativeLoaded())return NativeFurnaceService.available(world,point);
        if(site==null||recipe==null||recipe.type()!=site.type()||!site.area().contains(point.x()+.5,point.y()+.5,point.z()+.5))return false;
        if(batch.cultivation())return com.hexvane.aetherhaven.workplace.NativeCultivationService.destinationReady(world,site,recipe,batch);
        var block=ChunkSectionBlockUtil.blockType(world,point.x(),point.y(),point.z());
        if(!recipe.bench().equals(com.hexvane.aetherhaven.workplace.WorkplaceEquipment.benchId(block)))return false;
        var benchRef=ChunkSectionBlockUtil.blockEntityRefAt(world,point.x(),point.y(),point.z());
        var bench=benchRef!=null&&benchRef.isValid()?world.getChunkStore().getStore().getComponent(benchRef,com.hypixel.hytale.builtin.crafting.component.BenchBlock.getComponentType()):null;
        if(bench!=null)com.hypixel.hytale.builtin.crafting.component.BenchBlock.refreshGrantedAugmentTags(
            world.getChunkStore().getStore(),benchRef,new org.joml.Vector3i(point.x(),point.y(),point.z()));
        return recipe.acceptsBench(new com.hexvane.aetherhaven.workplace.WorkplaceEquipment.Bench(recipe.bench(),
            bench==null?1:Math.max(1,bench.getTierLevel()),point.x(),point.y(),point.z(),bench==null?Set.of():bench.getGrantedAugmentTags()));
    }
    static boolean loaded(World world,WarehouseBatch.Point point) {
        return point!=null&&ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(ChunkUtil.chunkCoordinate(point.x()),ChunkUtil.chunkCoordinate(point.z())))!=null;
    }
    static boolean clearApproach(World world,Vector3d point) {
        int x=(int)Math.floor(point.x),y=(int)Math.floor(point.y),z=(int)Math.floor(point.z);
        for(int stand=y;stand<=y+1;stand++)
            if(VillagerBlockUtil.isNpcStandColumn(world,x,stand,z)&&Math.abs(VillagerBlockUtil.resolveFeetYForStandCell(world,x,stand,z)-point.y)<.1)return true;
        return false;
    }
    private static boolean deskExists(World world,WarehouseBatch.Point point) {
        var type=ChunkSectionBlockUtil.blockType(world,point.x(),point.y(),point.z());return type!=null&&WarehouseAccess.DESK_ITEM.equals(type.getId());
    }
    public static Vector3d approach(World world,WarehouseBatch.Point point,Vector3d from,int attempt) {
        return approach(world,point,from,attempt,-1,0);
    }
    static Vector3d approach(World world,WarehouseBatch.Point point,Vector3d from,int attempt,int minDy,int maxDy) {
        return approach(world,point,from,attempt,minDy,maxDy,2);
    }
    static Vector3d approach(World world,WarehouseBatch.Point point,Vector3d from,int attempt,int minDy,int maxDy,int radius) {
        List<Vector3d> options=new ArrayList<>();
        for(int distance=1;distance<=radius;distance++)for(int[] offset:new int[][]{{distance,0},{-distance,0},{0,distance},{0,-distance}}) {
            int x=point.x()+offset[0],z=point.z()+offset[1];
            if(!loaded(world,new WarehouseBatch.Point(x,point.y(),z)))continue;
            for(int dy=maxDy;dy>=minDy;dy--) {
                int y=point.y()+dy;
                if(VillagerBlockUtil.isNpcStandColumn(world,x,y,z))options.add(new Vector3d(x+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,x,y,z),z+.5));
            }
        }
        options.sort(Comparator.comparingDouble(from::distanceSquared));
        return options.isEmpty()?null:options.get(Math.floorMod(attempt,options.size()));
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        stop(ref,npc,buffer,store.getComponent(ref,CourierTravelState.getComponentType()));
    }
    private static void stop(Ref<EntityStore> ref,NPCEntity npc,CommandBuffer<EntityStore> buffer,CourierTravelState route) {
        if(route==null||!route.active)return;
        NpcStandStill.release(ref,npc,buffer);route.active=false;route.arrivedAt=0;route.approach=null;
        buffer.putComponent(ref,CourierTravelState.getComponentType(),route);
    }
}
