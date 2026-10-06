package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.shopspot.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** A clerk walks to an existing sales counter. No new workplace or forced chunk loading. */
public final class ShopClerkWorkService {
    private ShopClerkWorkService() {}
    private static final Map<World,Map<UUID,Ref<EntityStore>>> ATTENDANCE=Collections.synchronizedMap(new WeakHashMap<>());
    private static Map<UUID,Ref<EntityStore>> attendance(World world) {
        synchronized(ATTENDANCE){return ATTENDANCE.computeIfAbsent(world,w->new HashMap<>());}
    }
    public static int attendingSkill(Store<EntityStore> store,TownRecord town,UUID plotId) {
        if(plotId==null||!ShopSpotOpenService.isGameDay(store)||!TownEconomyPresence.hasMemberHere(store,town))return 0;
        var world=store.getExternalData().getWorld();var ref=attendance(world).get(plotId);
        if(ref==null||!ref.isValid())return 0;
        var state=store.getComponent(ref,ShopClerkState.getComponentType());
        var plot=town.findPlotById(plotId);var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());var needs=store.getComponent(ref,VillagerNeeds.getComponentType());
        var plugin=AetherhavenPlugin.get();
        if(plugin==null||!ShopStaffing.isShop(plot,plugin.getConstructionCatalog())||identity==null||binding==null||uuid==null
            ||!town.getTownId().equals(binding.getTownId())||!Objects.equals(plot.getShopClerkId(),identity.getCharacterId())
            ||state==null||!state.active||!state.attending||System.currentTimeMillis()-state.lastAttendance>1500
            ||transform==null||state.approach==null||!CourierWorkService.arrived(transform.getPosition(),state.approach)
            ||needs==null||needs.getHunger()<20||needs.getEnergy()<20)return 0;
        var progress=town.getTownsfolkProgress().get(identity.getCharacterId());
        return ShopStaffing.canWork(progress,TownAdmission.isMember(town,uuid.getUuid()),
            store.getComponent(ref,DeathComponent.getComponentType())==null,TownVillagerBinding.KIND_GUARD.equals(binding.getKind()),
            town.getPayroll().overdue(identity.getCharacterId()))?progress.skill(TownsfolkSkill.COMMERCE):0;
    }
    /** Called only after the shop has persisted an actual completed sale. */
    public static long completedSale(Store<EntityStore> store,TownRecord town,UUID plotId) {
        if(attendingSkill(store,town,plotId)<=0)return 0;
        var plot=town.findPlotById(plotId);
        return TownsfolkMastery.completed(town.getTownsfolkProgress().get(plot.getShopClerkId()),TownsfolkMastery.Work.SHOP_SALE,true);
    }
    public static boolean tick(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
            TownRecord town,AetherhavenPlugin plugin) {
        var state=store.getComponent(ref,ShopClerkState.getComponentType());
        long now=System.currentTimeMillis();if(state!=null&&now<state.nextCheck)return state.active;
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        String id=identity==null?null:identity.getCharacterId();
        var progress=town==null||id==null?null:town.getTownsfolkProgress().get(id);
        var plot=town==null||id==null?null:town.getPlotInstances().stream().filter(p->id.equals(p.getShopClerkId())
            &&ShopStaffing.isShop(p,plugin.getConstructionCatalog())).findFirst().orElse(null);
        if(plot==null||uuid==null||!ShopStaffing.canWork(progress,TownAdmission.isMember(town,uuid.getUuid()),
            store.getComponent(ref,DeathComponent.getComponentType())==null,TownVillagerBinding.KIND_GUARD.equals(binding.getKind()),town.getPayroll().overdue(id))
            ||!ShopSpotOpenService.isGameDay(store)||needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()
            ||!TownEconomyPresence.hasMemberHere(store,town)
            ||town.getWarehouse().batches().stream().anyMatch(b->id.equals(b.courierId()))) {
            interrupt(ref,store,npc,buffer);return false;
        }
        if(state==null){state=new ShopClerkState();buffer.putComponent(ref,ShopClerkState.getComponentType(),state);}
        state.nextCheck=now+250;
        if(now<state.retryAt)return false;
        var world=store.getExternalData().getWorld();var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(transform==null)return false;var pos=transform.getPosition();
        var registry=AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world,plugin);
        var counter=registry.listForPlot(plot.getPlotId()).stream().filter(ShopSpotRecord::isPlayerControlled)
            .filter(r->CourierWorkService.loaded(world,new WarehouseBatch.Point(r.getBlockX(),r.getBlockY(),r.getBlockZ())))
            .min(Comparator.comparingDouble(r->pos.distanceSquared(r.getBlockX()+.5,r.getBlockY(),r.getBlockZ()+.5))).orElse(null);
        if(counter==null){interrupt(ref,store,npc,buffer);return false;}
        if(!state.active||!plot.getPlotId().equals(state.plot)) {
            var approach=clerkPosition(world,town,plot,plugin,counter,pos,state.attempt);
            if(approach==null){state.retryAt=now+3000;interrupt(ref,store,npc,buffer);return false;}
            interrupt(ref,store,npc,buffer);ApprenticeWorkService.interrupt(ref,store,npc,buffer);CourierWorkService.interrupt(ref,store,npc,buffer);
            var pois=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,pois,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            var tourist=store.getComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType());
            if(tourist!=null) {
                tourist.clearVisitPlot();tourist.clearTravelTarget();tourist.resetAutonomyStallTracking();
                tourist.setPhase(com.hexvane.aetherhaven.tourist.TouristAutonomyState.PHASE_IDLE);
                tourist.setNextDecisionEpochMs(now+1000);
                buffer.putComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType(),tourist);
            }
            state.plot=plot.getPlotId();state.approach=approach;state.active=true;state.beganAt=now;
            state.entrance=Math.abs(pos.y-approach.y)>1?entrance(world,plot,pos,approach):null;
        }
        if(!CourierWorkService.clearApproach(world,state.approach)) {
            state.attempt++;state.retryAt=now+3000;interrupt(ref,store,npc,buffer);return false;
        }
        if(state.entrance!=null&&CourierWorkService.arrived(pos,state.entrance)) {
            state.entrance=null;state.beganAt=now;
        }
        if(state.entrance==null&&CourierWorkService.arrived(pos,state.approach)) {
            NpcStandStill.hold(ref,store,npc,state.approach,buffer);state.attending=true;state.lastAttendance=now;
            PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(counter.getBlockX()+.5-pos.x,counter.getBlockZ()+.5-pos.z)+Math.PI));
            attendance(world).put(state.plot,ref);return true;
        }
        state.attending=false;
        if(now-state.beganAt>30000){state.attempt++;state.retryAt=now+3000;interrupt(ref,store,npc,buffer);return false;}
        var destination=state.entrance==null?state.approach:state.entrance;
        npc.setLeashPoint(new org.joml.Vector3d(destination));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
        VillagerDoorUtil.tryOpenDoorsTowardLeash(world,pos,state.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
    }
    /** A doorway approach prevents native target projection from choosing the terrain under a raised shop. */
    private static org.joml.Vector3d entrance(World world,PlotInstance plot,org.joml.Vector3d from,org.joml.Vector3d target) {
        var bounds=plot.toFootprint();List<org.joml.Vector3d> candidates=new ArrayList<>();
        // Bound the scan to nearby loaded geometry, even for very large community plots.
        int minX=Math.max(bounds.getMinX(),(int)target.x-24),maxX=Math.min(bounds.getMaxX(),(int)target.x+24);
        int minZ=Math.max(bounds.getMinZ(),(int)target.z-24),maxZ=Math.min(bounds.getMaxZ(),(int)target.z+24);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            if(!CourierWorkService.loaded(world,new WarehouseBatch.Point(x,(int)target.y,z)))continue;
            for(int y=(int)target.y-1;y<=(int)target.y+1;y++) {
                if(!VillagerDoorUtil.isDoorPrimaryBlock(world,new org.joml.Vector3i(x,y,z)))continue;
                for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int distance=1;distance<=3;distance++) {
                    int px=x+direction[0]*distance,pz=z+direction[1]*distance;
                    if(px>=bounds.getMinX()&&px<=bounds.getMaxX()&&pz>=bounds.getMinZ()&&pz<=bounds.getMaxZ())continue;
                    if(!CourierWorkService.loaded(world,new WarehouseBatch.Point(px,y,pz)))continue;
                    for(int py=y;py>=y-2;py--)if(VillagerBlockUtil.isNpcStandColumn(world,px,py,pz)) {
                        var point=new org.joml.Vector3d(px+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,px,py,pz),pz+.5);
                        if(Math.abs(point.y-target.y)<=1.3)candidates.add(point);
                    }
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(from::distanceSquared)).orElse(null);
    }
    /** Existing buildings can provide a work spot behind the counter; no new marker is required. */
    private static org.joml.Vector3d clerkPosition(World world,TownRecord town,PlotInstance plot,AetherhavenPlugin plugin,
            ShopSpotRecord counter,org.joml.Vector3d from,int attempt) {
        var authored=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin).listByTownAndTag(town.getTownId(),"WORK").stream()
            .filter(p->plot.getPlotId().equals(p.getPlotId()))
            .map(p->new org.joml.Vector3d(p.getX()+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,p.getX(),p.getY(),p.getZ()),p.getZ()+.5))
            .filter(p->CourierWorkService.clearApproach(world,p))
            .sorted(Comparator.comparingDouble(from::distanceSquared)).toList();
        List<org.joml.Vector3d> options=new ArrayList<>(authored);
        List<org.joml.Vector3d> fallback=new ArrayList<>();
        var point=new WarehouseBatch.Point(counter.getBlockX(),counter.getBlockY(),counter.getBlockZ());
        // A clear column can be the top of a chest. Keep fallback stands at the work floor.
        for(int i=0;i<16;i++) {
            var candidate=CourierWorkService.approach(world,point,from,i);
            if(candidate==null)break;
            if(!fallback.contains(candidate))fallback.add(candidate);
        }
        double floor=authored.stream().mapToDouble(p->p.y).min()
            .orElseGet(()->fallback.stream().mapToDouble(p->p.y).min().orElse(counter.getBlockY()));
        fallback.stream().filter(p->Math.abs(p.y-floor)<=.5).filter(p->!options.contains(p)).forEach(options::add);
        // Retry authored positions as well, rather than abandoning them forever after an obstruction.
        return options.isEmpty()?null:options.get(Math.floorMod(attempt,options.size()));
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var state=store.getComponent(ref,ShopClerkState.getComponentType());if(state==null||!state.active)return;
        attendance(store.getExternalData().getWorld()).remove(state.plot,ref);
        NpcStandStill.release(ref,npc,buffer);state.active=false;state.attending=false;
        buffer.putComponent(ref,ShopClerkState.getComponentType(),state);
    }
}
