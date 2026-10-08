package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;

/** One physical worker owns a site's prepared batch. Travel, conversations and offline time earn no work. */
public final class ApprenticeWorkService {
    private ApprenticeWorkService() {}
    private record Claim(String worker,long until) {}
    private static final Map<World,Map<UUID,Claim>> CLAIMS=Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean tick(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
                               NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
                               TownRecord town,TownManager manager,AetherhavenPlugin plugin) {
        var state=store.getComponent(ref,ApprenticeWorkState.getComponentType());
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        String id=identity==null?null:identity.getCharacterId();
        var progress=town==null||id==null?null:town.getTownsfolkProgress().get(id.toLowerCase(Locale.ROOT));
        if(progress==null||!progress.isEmployed()||uuid==null||!TownAdmission.isMember(town,uuid.getUuid())
            ||TownVillagerBinding.KIND_GUARD.equals(binding.getKind())||town.getPayroll().overdue(id)||store.getComponent(ref,DeathComponent.getComponentType())!=null) {
            stop(ref,npc,buffer,state);return false;
        }
        long now=System.currentTimeMillis();
        if(state==null){state=new ApprenticeWorkState();buffer.putComponent(ref,ApprenticeWorkState.getComponentType(),state);}
        if(now<state.retryAt||needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()
            ||!TownEconomyPresence.hasMemberHere(store,town)) {stop(ref,npc,buffer,state);return false;}
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null){stop(ref,npc,buffer,state);return false;}
        var world=store.getExternalData().getWorld();var position=transform.getPosition();
        var retries=state.routeRetries;
        Map<UUID,Claim> claims;synchronized(CLAIMS){claims=CLAIMS.computeIfAbsent(world,w->new HashMap<>());}
        if(state.buildingSite!=null)return tickBuilding(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
        if(state.excavationSite!=null)return tickExcavation(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
        if (state.foodSite != null) return tickFood(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
        if(now>=state.nextDecision) {
            state.nextDecision=now+250;claims.values().removeIf(c->c.until()<now);
            WorkplaceSupplyPlanner.tick(world,manager,town,plugin,now);
            var batch=state.batch==null?null:town.getWarehouse().batch(state.batch);
            if(!usable(world,batch,town,plugin,progress)||!CourierWorkService.destinationReady(world,town,plugin,batch)
                ||!state.active&&!state.routeRetries.canTry(batch.workplaceId(),now)) {
                stop(ref,npc,buffer,state);state.batch=null;
                batch=town.getWarehouse().batches().stream().filter(b->usable(world,b,town,plugin,progress))
                    .filter(b->retries.canTry(b.workplaceId(),now))
                    .filter(b->{var claim=claims.get(b.workplaceId());return claim==null||id.equals(claim.worker());})
                    .sorted(Comparator.comparingInt((WarehouseBatch b)->progress.priority(b.nativeLoaded()?TownsfolkWorkType.SMELTING:plugin.getApprenticeRecipes().find(b.recipeId()).type()))
                        .thenComparingInt(b->orderPriority(town,b))
                        .thenComparingInt(b->b.cultivation()&&b.cropPlanted()?0:1)
                        .thenComparingDouble(b->position.distanceSquared(b.destination().x(),b.destination().y(),b.destination().z())))
                    .filter(b->CourierWorkService.destinationReady(world,town,plugin,b))
                    .filter(b->!preferSkilledWorker(store,town,b.nativeLoaded()?TownsfolkWorkType.SMELTING:plugin.getApprenticeRecipes().find(b.recipeId()).type(),id,progress,now,b.workplaceId())).findFirst().orElse(null);
            }
            if (!state.active) {
                var food = town.getApprenticeWorkplaces().sites().stream()
                    .filter(s -> WorkplaceFood.supports(s.type()) && s.food().needsTending() && progress.canPerform(s.type(),false,false,true))
                    .filter(s -> retries.canTry(s.id(),now))
                    .filter(s -> { var claim=claims.get(s.id()); return claim==null || claim.until()<now || id.equals(claim.worker()); })
                    .filter(s -> FoodSiteService.inspect(world,s).ready())
                    .filter(s -> !preferSkilledWorker(store,town,s.type(),id,progress,now,s.id()))
                    .min(FoodWorkSelection.order(progress,position.x,position.y,position.z)).orElse(null);
                int batchPriority = batch==null ? Integer.MAX_VALUE : progress.priority(batch.nativeLoaded()?TownsfolkWorkType.SMELTING:plugin.getApprenticeRecipes().find(batch.recipeId()).type());
                var excavation=town.getApprenticeWorkplaces().sites().stream()
                    .filter(s->retries.canTry(s.id(),now)&&excavationUsable(world,town,s,plugin,progress))
                    .filter(s->{var claim=claims.get(s.id());return claim==null||claim.until()<now||id.equals(claim.worker());})
                    .filter(s->!preferSkilledWorker(store,town,s.type(),id,progress,now,s.id()))
                    .min(Comparator.comparingDouble(s->position.distanceSquared(s.area().minX(),s.area().floorY(),s.area().minZ()))).orElse(null);
                var building=town.getApprenticeWorkplaces().sites().stream()
                    .filter(s->retries.canTry(s.id(),now)&&buildingUsable(world,town,s,progress))
                    .filter(s->{var claim=claims.get(s.id());return claim==null||claim.until()<now||id.equals(claim.worker());})
                    .filter(s->!preferSkilledWorker(store,town,s.type(),id,progress,now,s.id()))
                    .min(Comparator.comparingDouble(s->position.distanceSquared(s.area().minX(),s.area().floorY(),s.area().minZ()))).orElse(null);
                if(building!=null&&progress.priority(TownsfolkWorkType.BUILDING)<=batchPriority
                    &&(food==null||progress.priority(TownsfolkWorkType.BUILDING)<progress.priority(food.type()))
                    &&(excavation==null||progress.priority(TownsfolkWorkType.BUILDING)<progress.priority(TownsfolkWorkType.MINING))) {
                    state.buildingSite=building.id();state.batch=null;
                    return tickBuilding(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
                }
                if(excavation!=null&&progress.priority(TownsfolkWorkType.MINING)<=batchPriority
                    &&(food==null||progress.priority(TownsfolkWorkType.MINING)<progress.priority(food.type()))) {
                    state.excavationSite=excavation.id();state.batch=null;
                    return tickExcavation(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
                }
                if (food != null && progress.priority(food.type()) <= batchPriority) {
                    state.foodSite=food.id(); state.batch=null;
                    return tickFood(dt,ref,store,buffer,npc,needs,autonomy,town,manager,plugin,progress,id,state,claims,now);
                }
            }
            if(batch==null)return false;
            var claim=claims.get(batch.workplaceId());
            if(claim!=null&&!id.equals(claim.worker())){stop(ref,npc,buffer,state);state.batch=null;return false;}
            if(!state.active||!batch.id().equals(state.batch)) {
                CourierWorkService.interrupt(ref,store,npc,buffer);
                var approach=CourierWorkService.approach(world,batch.destination(),position,state.routeRetries.approachAttempt(batch.workplaceId(),now));
                if(approach==null){state.routeRetries.failed(batch.workplaceId(),now);state.retryAt=now+1000;return false;}
                var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
                VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
                autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
                buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
                var tourist=store.getComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType());
                if(tourist!=null){tourist.clearVisitPlot();tourist.clearTravelTarget();tourist.resetAutonomyStallTracking();tourist.setPhase(0);buffer.putComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType(),tourist);}
                state.batch=batch.id();state.approach=approach;state.active=true;state.beganAt=now;state.nextVisual=0;
            }
            if(!CourierWorkService.clearApproach(world,state.approach)) {
                state.routeRetries.failed(batch.workplaceId(),now);state.retryAt=now+1000;stop(ref,npc,buffer,state);return false;
            }
            claims.put(batch.workplaceId(),new Claim(id,now+1500));
            // Let a queued seat exit finish before changing navigation or starting work.
            if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        }
        if(!state.active)return false;
        var batch=town.getWarehouse().batch(state.batch);
        if(!usable(world,batch,town,plugin,progress)||!CourierWorkService.destinationReady(world,town,plugin,batch)){stop(ref,npc,buffer,state);return false;}
        if(!CourierWorkService.arrived(position,state.approach)) {
            if(state.working)stopVisual(ref,buffer,state);
            if(now-state.beganAt>30_000){state.routeRetries.failed(batch.workplaceId(),now);state.retryAt=now+1000;claims.remove(batch.workplaceId());stop(ref,npc,buffer,state);return false;}
            npc.setLeashPoint(new Vector3d(state.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,state.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        if(!state.working){state.beganAt=now;state.routeRetries.succeeded(batch.workplaceId());}
        state.working=true;NpcStandStill.hold(ref,store,npc,state.approach,buffer);
        var recipe=plugin.getApprenticeRecipes().find(batch.recipeId());
        if(now>=state.nextVisual) {
            String action=batch.cultivation()?"Tend":"Craft";
            state.nextVisual=now+VillagerLifeTiming.durationMs(action);
            var destination=batch.destination();
            PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(destination.x()+.5-position.x,destination.z()+.5-position.z)+Math.PI));
            buffer.run(st->{if(!ref.isValid())return;
                if(batch.cultivation())VillagerLifeProps.clear(ref,st);else VillagerLifeProps.equip(ref,"Craft",st);
                AnimationUtils.playAnimation(ref,AnimationSlot.Action,NpcFaceVisuals.itemAnimationsForFaceRig(ref,"Aetherhaven_Life_Actions",st),action,true,st);});
        }
        if(batch.smelting()) {
            // A short physical handling gesture, never a replacement furnace processing timer.
            if(now-state.beganAt<1000)return true;
            if(NativeFurnaceService.service(world,town,batch,
                ()->TownsfolkMastery.completed(progress,TownsfolkMastery.Work.SMELTING_COLLECTION,true)))manager.updateTown(town);
            claims.remove(batch.workplaceId());state.retryAt=now+1000;stop(ref,npc,buffer,state);state.batch=null;return true;
        }
        // Ordinary bench work only. Native furnaces advance through their own game systems.
        double duration=Math.max(6,recipe.source().getTimeSeconds());
        var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
        double furnishingMultiplier=1;
        if(!batch.cultivation()) {
            if(state.furnishingEquipment==null||!site.id().equals(state.furnishingSite)
                    ||!site.area().equals(state.furnishingArea)||now>=state.nextFurnishingCheck) {
                state.furnishingEquipment=WorkplaceEquipment.scan(world,site.area());
                state.furnishingSite=site.id();state.furnishingArea=site.area();state.nextFurnishingCheck=now+500;
            }
            furnishingMultiplier=state.furnishingEquipment.workMultiplier(site.type());
        }
        town.getWarehouse().advanceWork(batch.id(),Math.min(.25,Math.max(0,dt)),
            com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,recipe.skill(),plugin.getProfessionKits())*furnishingMultiplier);
        if(batch.cultivation()) {
            double phaseEnd=batch.cropPlanted()?duration:6;
            if(batch.workSeconds()>=phaseEnd) {
                var standing=new Vector3d(state.approach);
                buffer.run(st->{
                    if(!eligibleAtCommit(ref,st,town,id,site.type())||!site.orders().canComplete(batch.orderId(),batch.recipeId())
                        ||town.getApprenticeWorkplaces().find(site.id())!=site
                        ||!NativeCultivationService.destinationReady(world,site,recipe,batch)
                        ||recipe.equipmentIssue(site,WorkplaceEquipment.scan(world,site.area()))!=null)return;
                    var actual=st.getComponent(ref,TransformComponent.getComponentType());
                    if(actual==null||!CourierWorkService.arrived(actual.getPosition(),standing))return;
                    boolean harvesting=batch.cropPlanted();
                    boolean changed=harvesting?NativeCultivationService.harvest(world,town.getWarehouse(),batch,site.orders(),duration)
                        :NativeCultivationService.plant(world,town.getWarehouse(),batch);
                    if(changed) {
                        TownsfolkMastery.completed(progress,harvesting?TownsfolkMastery.Work.CULTIVATION_HARVEST:TownsfolkMastery.Work.CULTIVATION_PLANT,true);
                        manager.updateTown(town);
                    }
                });
                claims.remove(batch.workplaceId());state.retryAt=now+1000;stop(ref,npc,buffer,state);state.batch=null;
            } else if(now>=state.nextSave){manager.updateTown(town);state.nextSave=now+2000;}
            return true;
        }
        boolean appraise=recipe.rarity()!=null && batch.workSeconds()>=duration
            && WorkplaceEquipment.scan(world,site.area()).tier(WorkplaceEquipment.APPRAISAL_BENCH)>0;
        if(town.getWarehouse().finishWork(batch.id(),site.orders(),duration,
                planned->JewelryWorkplaceOutput.finish(recipe,planned,appraise))) {
            TownsfolkMastery.completed(progress,TownsfolkMastery.Work.CRAFT,true);
            manager.updateTown(town);claims.remove(batch.workplaceId());stop(ref,npc,buffer,state);state.batch=null;return true;
        }
        if(now>=state.nextSave){manager.updateTown(town);state.nextSave=now+2000;}
        return true;
    }
    /** Deferred crop changes must still belong to a living, paid civilian when the buffer commits. */
    private static boolean eligibleAtCommit(Ref<EntityStore> ref,Store<EntityStore> store,TownRecord town,
            String worker,TownsfolkWorkType type) {
        if(!ref.isValid()||!TownEconomyPresence.hasMemberHere(store,town))return false;
        var identity=store.getComponent(ref,TownsfolkCharacterBinding.getComponentType());
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        if(identity==null||!worker.equalsIgnoreCase(identity.getCharacterId())||binding==null||uuid==null
            ||!town.getTownId().equals(binding.getTownId())||!TownAdmission.isMember(town,uuid.getUuid()))return false;
        var current=town.getTownsfolkProgress().get(worker.toLowerCase(Locale.ROOT));
        return current!=null&&current.canPerform(type,TownVillagerBinding.KIND_GUARD.equals(binding.getKind()),
            town.getPayroll().overdue(worker),store.getComponent(ref,DeathComponent.getComponentType())==null);
    }
    private static boolean preferSkilledWorker(Store<EntityStore> store,TownRecord town,TownsfolkWorkType type,String worker,TownsfolkProgress current,long now,UUID workplace) {
        // Do not preempt an attended job or transfer a miner's unfinished load to a smaller bag.
        var site=town.getApprenticeWorkplaces().find(workplace);
        if(site!=null&&site.existingExcavation()!=null&&!site.excavation().cargo().isEmpty())return false;
        for(var row:town.getResidentNpcRecords()){
            var identity=row.getTownsfolkIdentity();if(identity==null||row.getLastEntityUuid()==null||!TownAdmission.isMember(town,row.getLastEntityUuid()))continue;
            String id=identity.getCharacterId();if(id.equalsIgnoreCase(worker))continue;
            var progress=town.getTownsfolkProgress().get(id.toLowerCase(Locale.ROOT));
            if(progress==null||progress.skill(type.skill)<=current.skill(type.skill)||!progress.canPerform(type,TownVillagerBinding.KIND_GUARD.equals(row.getKind()),town.getPayroll().overdue(id),!row.isPendingDawnRevival()))continue;
            var ref=store.getExternalData().getRefFromUUID(row.getLastEntityUuid());if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)continue;
            if(VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(ref,VillagerFollowPlayerState.getComponentType()))
                ||NpcSupportUtil.stateName(store,ref).contains("Interaction")||VillagerLifeSystem.ownsActivity(ref,store)
                ||store.getComponent(ref,VillagerSeatExit.getComponentType())!=null)continue;
            var courier=store.getComponent(ref,CourierTravelState.getComponentType());if(courier!=null&&courier.active)continue;
            var work=store.getComponent(ref,ApprenticeWorkState.getComponentType());
            if(work!=null&&(work.active||work.batch!=null||work.foodSite!=null||work.buildingSite!=null||work.excavationSite!=null||now<work.retryAt||!work.routeRetries.canTry(workplace,now)))continue;
            var needs=store.getComponent(ref,VillagerNeeds.getComponentType());var autonomy=store.getComponent(ref,VillagerAutonomyState.getComponentType());
            if(needs==null||needs.getHunger()<20||needs.getEnergy()<20||autonomy==null||autonomy.isFillingHunger()||autonomy.isFillingEnergy())continue;
            return true;
        }
        return false;
    }
    private static int orderPriority(TownRecord town,WarehouseBatch batch) {
        var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
        if(site==null)return Integer.MAX_VALUE;
        var orders=site.orders().list();
        for(int i=0;i<orders.size();i++)if(orders.get(i).id().equals(batch.orderId()))return i;
        return Integer.MAX_VALUE;
    }
    private static boolean tickFood(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
        NPCEntity npc,VillagerNeeds needs,VillagerAutonomyState autonomy,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
        TownsfolkProgress progress,String worker,ApprenticeWorkState state,Map<UUID,Claim> claims,long now) {
        var world=store.getExternalData().getWorld();
        var site=town.getApprenticeWorkplaces().find(state.foodSite);
        var habitat=site==null?null:FoodSiteService.inspect(world,site);
        var claim=site==null?null:claims.get(site.id());
        if (site==null || !WorkplaceFood.supports(site.type()) || !site.food().needsTending()
            || !progress.canPerform(site.type(),false,false,true) || !habitat.ready()
            || claim!=null && claim.until()>now && !worker.equals(claim.worker())) {
            stop(ref,npc,buffer,state);state.foodSite=null;return false;
        }
        var position=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        var habitatTarget=habitat.targets().get(Math.floorMod(site.food().completedVisits(),habitat.targets().size()));
        var ranchJob=site.type()==TownsfolkWorkType.RANCHING?RanchWorkService.job(world,site,habitatTarget):null;
        var target=ranchJob==null?habitatTarget:ranchJob.target();
        if (!state.active) {
            CourierWorkService.interrupt(ref,store,npc,buffer);
            state.foodHarvest=site.type()==TownsfolkWorkType.FARMING&&FoodSiteService.readyToHarvest(world,target);
            var approach=CourierWorkService.approach(world,target,position,state.routeRetries.approachAttempt(site.id(),now));
            if (approach==null) {
                state.routeRetries.failed(site.id(),now);site.food().careFailed("No clear place to stand beside the work area.");
                state.retryAt=now+1000;state.foodSite=null;return false;
            }
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            var tourist=store.getComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType());
            if(tourist!=null){tourist.clearVisitPlot();tourist.clearTravelTarget();tourist.resetAutonomyStallTracking();tourist.setPhase(0);buffer.putComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType(),tourist);}
            state.approach=approach;state.active=true;state.beganAt=now;state.nextVisual=0;
        }
        claims.put(site.id(),new Claim(worker,now+1500));
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        if(ranchJob!=null && state.approach.distanceSquared(target.x()+.5,target.y(),target.z()+.5)>16) {
            claims.remove(site.id());stop(ref,npc,buffer,state);return false;
        }
        if (!CourierWorkService.clearApproach(world,state.approach) || now-state.beganAt>30_000 && !state.working) {
            state.routeRetries.failed(site.id(),now);site.food().careFailed("An Apprentice could not reach this site. Check the path and entrances.");
            claims.remove(site.id());state.retryAt=now+1000;stop(ref,npc,buffer,state);return false;
        }
        if (!CourierWorkService.arrived(position,state.approach)) {
            if(state.working)stopVisual(ref,buffer,state);
            npc.setLeashPoint(new Vector3d(state.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,state.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        state.working=true;NpcStandStill.hold(ref,store,npc,state.approach,buffer);
        if (now>=state.nextVisual) {
            boolean watering=site.type()==TownsfolkWorkType.FARMING&&!state.foodHarvest;
            state.nextVisual=now+(watering?2500:VillagerLifeTiming.durationMs("Tend"));
            PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(target.x()+.5-position.x,target.z()+.5-position.z)+Math.PI));
            buffer.run(st->{if(!ref.isValid())return;
                if(watering)VillagerLifeProps.equip(ref,"WaterCrops",st);
                else if(ranchJob!=null)VillagerLifeProps.equip(ref,ranchJob.slaughter()?"Slaughter":"FeedAnimals",st);
                else VillagerLifeProps.clear(ref,st);
                AnimationUtils.playAnimation(ref,AnimationSlot.Action,watering?"Aetherhaven_Work_Actions":NpcFaceVisuals.itemAnimationsForFaceRig(ref,"Aetherhaven_Life_Actions",st),watering?"WaterCrops":"Tend",true,st);});
        }
        if (site.food().work(dt,true,habitat.ready(),
            com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,site.type().skill,plugin.getProfessionKits()))) {
            boolean harvested=state.foodHarvest;
            var area=site.area();
            buffer.run(st->{
                if(!eligibleAtCommit(ref,st,town,worker,site.type())
                    || town.getApprenticeWorkplaces().find(site.id())!=site || !area.equals(site.area()))return;
                var actual=st.getComponent(ref,TransformComponent.getComponentType());
                if(actual==null || actual.getPosition().distanceSquared(target.x()+.5,target.y(),target.z()+.5)>16)return;
                boolean completed=site.food().completeCare(()->{
                    if(ranchJob!=null)return RanchWorkService.complete(world,site,ranchJob);
                    boolean applied=harvested?FoodSiteService.harvestForTown(world,target):FoodSiteService.waterSoil(world,target);
                    if(applied && !harvested)com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect("Water_Can_Splash",new Vector3d(target.x()+.5,target.y()+.1,target.z()+.5),st);
                    return applied;
                },ranchJob!=null ? ranchJob.slaughter()?"Livestock prepared for town food.":ranchJob.animal()!=null?"Young livestock grew into an adult.":"Breeding animals fed."
                    : harvested?"Ripe crops harvested and replanted for town food.":"Crop soil watered.");
                var current=st.getComponent(ref,ApprenticeWorkState.getComponentType());
                if(current!=null) {
                    if(completed)current.routeRetries.succeeded(site.id());
                    else current.routeRetries.failed(site.id(),now);
                }
                TownsfolkMastery.completed(progress,TownsfolkMastery.Work.FOOD_CARE,completed);
                if(!completed && ranchJob==null)site.food().careFailed("The crop could not be tended. Check its soil and leave room to work.");
                manager.updateTown(town);
            });
            claims.remove(site.id());state.retryAt=now+1500;stop(ref,npc,buffer,state);return true;
        }
        if (now>=state.nextSave) { manager.updateTown(town);state.nextSave=now+2000; }
        return true;
    }
    private static boolean excavationUsable(World world,TownRecord town,ApprenticeWorkplace site,AetherhavenPlugin plugin,TownsfolkProgress progress) {
        if(site.type()!=TownsfolkWorkType.MINING||!progress.canPerform(TownsfolkWorkType.MINING,false,false,true)||site.excavation().id()==null)return false;
        return NativeExcavation.workIssue(world,town,site,plugin,progress.skill(TownsfolkSkill.MINING))==null;
    }
    private static boolean tickExcavation(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
        NPCEntity npc,VillagerNeeds needs,VillagerAutonomyState autonomy,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
        TownsfolkProgress progress,String worker,ApprenticeWorkState state,Map<UUID,Claim> claims,long now) {
        var world=store.getExternalData().getWorld();var site=town.getApprenticeWorkplaces().find(state.excavationSite);
        var claim=site==null?null:claims.get(site.id());
        if(site==null||claim!=null&&claim.until()>now&&!worker.equals(claim.worker())||!excavationUsable(world,town,site,plugin,progress)) {stop(ref,npc,buffer,state);return false;}
        var plan=site.excavation();
        if(plan.pending()) {buffer.run(st->{if(NativeExcavation.settle(world,site))manager.updateTown(town);});return true;}
        var currentPosition=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        var feet=ExcavationApproach.feet(currentPosition);
        int miningSkill=progress.skill(TownsfolkSkill.MINING);plan.carryLevel(miningSkill);plan.returnLegacyDelivery();
        if(plan.next()==null&&plan.nextFitting()==null&&!plan.cargo().isEmpty()&&worker.equals(plan.carrier())){
            var base=town.getApprenticeWorkplaces().mineBase(site);
            for(var next:town.getApprenticeWorkplaces().tunnels(base.id())) {
                var nextClaim=claims.get(next.id());
                if(next==site||nextClaim!=null&&nextClaim.until()>now&&!worker.equals(nextClaim.worker()))continue;
                if(next.minePrerequisiteId()!=null&&!next.minePrerequisiteId().equals(site.id())&&NativeExcavation.workIssue(world,town,next,plugin,miningSkill)!=null)continue;
                if(NativeExcavation.issue(world,town,next,miningSkill)==null&&plan.continueLoad(next.excavation(),worker)){
                    claims.remove(site.id());claims.put(next.id(),new Claim(worker,now+2000));stop(ref,npc,buffer,state);state.excavationSite=next.id();manager.updateTown(town);return true;
                }
            }
        }
        boolean hasCargo=!plan.cargo().isEmpty();
        boolean ownsCargo=hasCargo&&worker.equals(plan.carrier())&&(state.active&&state.excavationCarrying||ExcavationPlan.withinPickupReach(feet,plan.cargoPoint()));
        String digIssue=NativeExcavation.issue(world,town,site,miningSkill);
        if((!hasCargo||ownsCargo)&&!plan.loadReady()&&plan.next()!=null&&digIssue==null&&!plan.dropsPrepared()){
            if(!plan.prepareNext(()->NativeExcavation.drops(plan.next()))){stop(ref,npc,buffer,state);state.retryAt=now+2000;return false;}
            manager.updateTown(town);
        }
        boolean delivery=ownsCargo&&(plan.loadReady()||digIssue!=null);
        boolean pickup=hasCargo&&!ownsCargo;
        boolean movingCargo=pickup||delivery;
        boolean fitting=!movingCargo&&plan.next()==null&&plan.nextFitting()!=null;
        var target=pickup?plan.cargoPoint()
            :delivery?NativeExcavation.bufferPoint(town,site)
            :fitting?plan.nextFitting().point():plan.next()==null?null:plan.next().point();
        if(target==null){
            stop(ref,npc,buffer,state);return false;
        }
        var position=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        if(state.active&&(!Objects.equals(state.excavationPlan,plan.id())||state.excavationDelivery!=delivery||state.excavationPickup!=pickup||!target.equals(state.excavationTarget))) {stop(ref,npc,buffer,state);state.excavationSite=site.id();return true;}
        if(!state.active) {
            if(!movingCargo)NativeExcavation.repairFloors(world,town,site,feet);
            // Reach the reviewed floor, including the preceding higher step when digging down.
            var feetTarget=movingCargo?target:new WarehouseBatch.Point(target.x(),plan.floorAt(target),target.z());
            var approach=delivery?CourierWorkService.approach(world,feetTarget,position,state.routeRetries.approachAttempt(site.id(),now),-1,0)
                :ExcavationApproach.choose(plan,target,position,state.routeRetries.approachAttempt(site.id(),now),cell->{
                    if(!CourierWorkService.loaded(world,cell)||!VillagerBlockUtil.isNpcStandColumn(world,cell.x(),cell.y(),cell.z()))return null;
                    return new Vector3d(cell.x()+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,cell.x(),cell.y(),cell.z()),cell.z()+.5);
                },candidate->NativeExcavation.clearReach(world,candidate,target)&&(!pickup||ExcavationPlan.withinPickupReach(ExcavationApproach.feet(candidate),target))&&(!fitting||!ExcavationFittings.RAIL.equals(plan.nextFitting().item())||ExcavationApproach.besideRail(target,candidate)));
            if(approach==null){state.routeRetries.failed(site.id(),now);stop(ref,npc,buffer,state);state.retryAt=now+1000;return false;}
            CourierWorkService.interrupt(ref,store,npc,buffer);
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            state.approach=approach;state.active=true;state.working=false;state.beganAt=now;state.nextVisual=0;
            state.excavationPlan=plan.id();state.excavationTarget=target;state.excavationDelivery=delivery;state.excavationPickup=pickup;
            if(delivery){state.excavationCarrying=true;String item=plan.cargo().keySet().iterator().next();buffer.run(st->{if(ref.isValid())VillagerLifeProps.carry(ref,item,st);});}
        }
        claims.put(site.id(),new Claim(worker,now+10000));
        if(ownsCargo){plan.trackCarrier(worker,new WarehouseBatch.Point((int)Math.floor(position.x),(int)Math.floor(position.y),(int)Math.floor(position.z)));if(now>=state.nextSave){manager.updateTown(town);state.nextSave=now+2000;}}
        if(!CourierWorkService.clearApproach(world,state.approach)||now-state.beganAt>30000&&!state.working) {
            state.routeRetries.failed(site.id(),now);stop(ref,npc,buffer,state);
            // Try another reachable stand with the same miner before handing the job away.
            if(state.routeRetries.approachAttempt(site.id(),now)<4){state.excavationSite=site.id();state.retryAt=0;return true;}
            claims.remove(site.id());state.retryAt=now+1000;return false;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        boolean rail=fitting&&ExcavationFittings.RAIL.equals(plan.nextFitting().item());
        if(!(movingCargo?CourierWorkService.arrived(position,state.approach):ExcavationApproach.arrived(plan,target,position,state.approach)
            &&(!rail||ExcavationApproach.besideRail(target,position))&&NativeExcavation.clearReach(world,position,target))) {
            if(state.working)stopVisual(ref,buffer,state);
            npc.setLeashPoint(new Vector3d(state.approach));
            if(rail||plan.layout()==ExcavationPlan.Layout.ROOM)NpcSupportUtil.setState(ref,com.hexvane.aetherhaven.AetherhavenConstants.NPC_STATE_AUTONOMY_POI,"Fitting",buffer);
            else VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,state.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        if(!state.working)state.beganAt=now;
        state.working=true;NpcStandStill.hold(ref,store,npc,state.approach,buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(target.x()+.5-position.x,target.z()+.5-position.z)+Math.PI));
        if(!pickup&&now>=state.nextVisual) {
            state.nextVisual=now+1500;
            String held=delivery?plan.cargo().keySet().iterator().next():fitting?plan.nextFitting().item():"Tool_Pickaxe_"+(progress.skill(TownsfolkSkill.MINING)>=9?"Adamantite":progress.skill(TownsfolkSkill.MINING)>=7?"Cobalt":progress.skill(TownsfolkSkill.MINING)>=5?"Thorium":progress.skill(TownsfolkSkill.MINING)>=3?"Iron":"Copper");
            buffer.run(st->{if(ref.isValid())VillagerLifeProps.carry(ref,held,st);});
            if(!delivery&&!fitting) {
                var visual=new com.hexvane.aetherhaven.poi.PoiEntry(site.id(),town.getTownId(),target.x(),target.y(),target.z(),Set.of("WORK"),1,null,null,com.hexvane.aetherhaven.poi.PoiInteractionKind.WORK_SURFACE);
                VillagerWorkVisuals.tickHit(ref,store,buffer,npc,visual,TownVillagerBinding.KIND_MINER,now,0);
            }
        }
        if(!movingCargo)plan.attend(dt,true,com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,TownsfolkSkill.MINING,plugin.getProfessionKits()));
        boolean complete=movingCargo?now-state.beganAt>=1000:plan.work()>=(fitting?3:NativeExcavation.duration(plan.next()));
        if(complete) {
            var planId=plan.id();var standing=new Vector3d(state.approach);
            stop(ref,npc,buffer,state);
            if(!delivery){state.excavationSite=site.id();state.retryAt=0;}
            else state.retryAt=now+500;
            long generation=state.excavationGeneration;
            buffer.run(st->{
                var owner=claims.get(site.id());
                if(owner==null||!worker.equals(owner.worker())||state.excavationGeneration!=generation)return;
                var identity=ref.isValid()?st.getComponent(ref,TownsfolkCharacterBinding.getComponentType()):null;
                var current=identity==null?null:town.getTownsfolkProgress().get(identity.getCharacterId().toLowerCase(Locale.ROOT));
                var binding=ref.isValid()?st.getComponent(ref,TownVillagerBinding.getComponentType()):null;
                var actual=ref.isValid()?st.getComponent(ref,TransformComponent.getComponentType()):null;
                if(actual==null||binding==null||current==null||TownVillagerBinding.KIND_GUARD.equals(binding.getKind())||town.getPayroll().overdue(worker)
                    ||st.getComponent(ref,DeathComponent.getComponentType())!=null||!current.canPerform(TownsfolkWorkType.MINING,false,false,true)
                    ||town.getApprenticeWorkplaces().find(site.id())!=site||!planId.equals(plan.id())
                    ||!(movingCargo?CourierWorkService.arrived(actual.getPosition(),standing):ExcavationApproach.arrived(plan,target,actual.getPosition(),standing))
                    ||!TownEconomyPresence.hasMemberHere(store,town))return;
                if(!movingCargo&&(!NativeExcavation.clearReach(world,actual.getPosition(),target)
                    ||!fitting&&(plan.next()==null||!target.equals(plan.next().point()))))return;
                var actualFeet=ExcavationApproach.feet(actual.getPosition());
                var body=st.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType());
                int completedBlocks=delivery&&!plan.haulingOutput()?plan.cargoWork():0;
                boolean changed=pickup?plan.pickup(planId,worker,actualFeet)
                    :delivery?worker.equals(plan.carrier())&&plan.deposit(planId)
                    :fitting?plan.nextFitting()!=null&&target.equals(plan.nextFitting().point())&&NativeExcavation.placeFitting(world,town,site,actual.getPosition())
                    :NativeExcavation.excavate(world,town,site,current.skill(TownsfolkSkill.MINING),actual.getPosition(),body==null?null:body.getBoundingBox(),()->manager.updateTown(town));
                if(changed) {
                    state.routeRetries.succeeded(site.id());
                    // Award each confirmed block once on base deposit, never again on core delivery.
                    if(completedBlocks>0)current.earnMasteryXp((long)completedBlocks*TownsfolkMastery.Work.MINING_DELIVERY.xp);
                    if(delivery)town.getWarehouse().markProductionWithdrawal();
                    manager.updateTown(town);
                }else if(!movingCargo)state.routeRetries.failed(site.id(),now);
                if(delivery) {
                    if(changed&&NativeExcavation.workIssue(world,town,site,plugin,current.skill(TownsfolkSkill.MINING))==null) {
                        state.excavationSite=site.id();state.retryAt=0;
                        claims.put(site.id(),new Claim(worker,now+10000));
                    }else claims.remove(site.id(),owner);
                }
            });
        }else if(now>=state.nextSave){manager.updateTown(town);state.nextSave=now+2000;}
        return true;
    }
    private static boolean buildingUsable(World world,TownRecord town,ApprenticeWorkplace site,TownsfolkProgress progress) {
        return site.type()==TownsfolkWorkType.BUILDING&&!site.building().paused()&&progress.canPerform(site.type(),false,false,true)
            &&site.building().list().stream().anyMatch(order->NativeBuildingWork.issue(world,town,order)==null);
    }
    private static boolean tickBuilding(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
        NPCEntity npc,VillagerNeeds needs,VillagerAutonomyState autonomy,TownRecord town,TownManager manager,AetherhavenPlugin plugin,
        TownsfolkProgress progress,String worker,ApprenticeWorkState state,Map<UUID,Claim> claims,long now) {
        var world=store.getExternalData().getWorld();var site=town.getApprenticeWorkplaces().find(state.buildingSite);
        var claim=site==null?null:claims.get(site.id());
        if(site==null||!buildingUsable(world,town,site,progress)||claim!=null&&claim.until()>now&&!worker.equals(claim.worker())){stop(ref,npc,buffer,state);return false;}
        if(state.furnishingEquipment==null||!site.id().equals(state.furnishingSite)||!site.area().equals(state.furnishingArea)||now>=state.nextFurnishingCheck) {
            state.furnishingEquipment=WorkplaceEquipment.scan(world,site.area());state.furnishingSite=site.id();state.furnishingArea=site.area();state.nextFurnishingCheck=now+1000;
        }
        if(state.furnishingEquipment.requirement(WorkplaceEquipment.BUILDERS_BENCH,1)!=null){stop(ref,npc,buffer,state);state.retryAt=now+2000;return false;}
        var position=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
        if(state.active&&!NativeBuildingWork.valid(world,town,site,state.buildingTarget)){stop(ref,npc,buffer,state);return true;}
        if(!state.active) {
            NativeBuildingWork.Target chosen=null;Vector3d approach=null;
            for(var candidate:NativeBuildingWork.candidates(world,town,site,position)) {
                // Search beneath a wall cell as well as beside it, without granting distant or vertical work.
                for(int down=0;down<=3;down++) {
                    var point=new WarehouseBatch.Point(candidate.cell().x(),candidate.cell().y()-down,candidate.cell().z());
                    var stand=CourierWorkService.approach(world,point,position,state.routeRetries.approachAttempt(site.id(),now));
                    if(stand!=null&&NativeBuildingWork.inReach(stand,candidate)){chosen=candidate;approach=stand;break;}
                }
                if(chosen!=null)break;
            }
            if(chosen==null){state.routeRetries.failed(site.id(),now);stop(ref,npc,buffer,state);state.retryAt=now+1000;return false;}
            CourierWorkService.interrupt(ref,store,npc,buffer);
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            var tourist=store.getComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType());
            if(tourist!=null){tourist.clearVisitPlot();tourist.clearTravelTarget();tourist.resetAutonomyStallTracking();tourist.setPhase(0);buffer.putComponent(ref,com.hexvane.aetherhaven.tourist.TouristAutonomyState.getComponentType(),tourist);}
            state.buildingTarget=chosen;state.approach=approach;state.active=true;state.beganAt=now;state.nextVisual=0;site.building().focus(chosen.cell());
        }
        claims.put(site.id(),new Claim(worker,now+1500));
        if(!CourierWorkService.clearApproach(world,state.approach)||now-state.beganAt>30000&&!state.working) {
            claims.remove(site.id());state.routeRetries.failed(site.id(),now);stop(ref,npc,buffer,state);state.retryAt=now+1000;return false;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        if(!CourierWorkService.arrived(position,state.approach)) {
            if(state.working)stopVisual(ref,buffer,state);
            npc.setLeashPoint(new Vector3d(state.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,state.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        var target=state.buildingTarget;int tier=state.furnishingEquipment.tier(WorkplaceEquipment.BUILDERS_BENCH);
        if(!NativeBuildingWork.inReach(position,target)){stop(ref,npc,buffer,state);return false;}
        state.routeRetries.succeeded(site.id());state.working=true;NpcStandStill.hold(ref,store,npc,state.approach,buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(target.cell().x()+.5-position.x,target.cell().z()+.5-position.z)+Math.PI));
        if(now>=state.nextVisual) {
            state.nextVisual=now+1000;buffer.run(st->{if(ref.isValid())VillagerLifeProps.carry(ref,"Tool_Hammer_Iron",st);});
            com.hexvane.aetherhaven.builder.BuilderConstructionVisuals.swingHammer(ref,store,buffer,npc);
        }
        site.building().attend(target.cell(),dt,true,com.hexvane.aetherhaven.equipment.ProfessionKitService.workMultiplier(progress,TownsfolkSkill.BUILDING,plugin.getProfessionKits())*state.furnishingEquipment.workMultiplier(site.type()));
        if(site.building().ready(target.cell(),BuildingOrders.duration(progress.skill(TownsfolkSkill.BUILDING),tier))) {
            var area=site.area();var standing=new Vector3d(state.approach);
            buffer.run(st->{
                var identity=ref.isValid()?st.getComponent(ref,TownsfolkCharacterBinding.getComponentType()):null;
                var current=identity==null?null:town.getTownsfolkProgress().get(identity.getCharacterId().toLowerCase(Locale.ROOT));
                var binding=ref.isValid()?st.getComponent(ref,TownVillagerBinding.getComponentType()):null;
                var actual=ref.isValid()?st.getComponent(ref,TransformComponent.getComponentType()):null;
                var member=ref.isValid()?st.getComponent(ref,UUIDComponent.getComponentType()):null;
                if(actual==null||member==null||!TownAdmission.isMember(town,member.getUuid())||binding==null||current==null||!current.isEmployed()
                    ||TownVillagerBinding.KIND_GUARD.equals(binding.getKind())||town.getPayroll().overdue(worker)||st.getComponent(ref,DeathComponent.getComponentType())!=null
                    ||!current.canPerform(TownsfolkWorkType.BUILDING,false,false,true)||town.getApprenticeWorkplaces().find(site.id())!=site||!area.equals(site.area())
                    ||!CourierWorkService.arrived(actual.getPosition(),standing)||!CourierWorkService.clearApproach(world,standing)
                    ||!TownEconomyPresence.hasMemberHere(store,town))return;
                var equipment=WorkplaceEquipment.scan(world,site.area());
                if(equipment.requirement(WorkplaceEquipment.BUILDERS_BENCH,1)==null&&NativeBuildingWork.place(world,plugin,town,site,target,actual.getPosition(),current.skill(TownsfolkSkill.BUILDING),equipment.tier(WorkplaceEquipment.BUILDERS_BENCH))) {
                    TownsfolkMastery.completed(current,TownsfolkMastery.Work.BUILDING_BLOCK,true);
                    manager.updateTown(town);
                }
            });
            stop(ref,npc,buffer,state);state.retryAt=now+250;
        }else if(now>=state.nextSave){manager.updateTown(town);state.nextSave=now+2000;}
        return true;
    }
    private static boolean usable(com.hypixel.hytale.server.core.universe.world.World world,WarehouseBatch batch,TownRecord town,AetherhavenPlugin plugin,TownsfolkProgress progress) {
        if(batch==null||batch.destination()==null)return false;
        if(batch.smelting()){
            if(!NativeFurnaceService.needsService(world,batch))return false;
            // Finishing native work is cleanup, even when the player removes or changes the site.
            if(batch.nativeLoaded())return progress.canPerform(TownsfolkWorkType.SMELTING,false,false,true);
        }
        else if(batch.state()!=WarehouseBatch.State.READY||!town.getWarehouse().hasOutputRoom(batch.id()))return false;
        if(batch.cultivation()&&!NativeCultivationService.needsWork(world,batch))return false;
        var recipe=plugin.getApprenticeRecipes().find(batch.recipeId());var site=town.getApprenticeWorkplaces().find(batch.workplaceId());
        return recipe!=null&&site!=null&&recipe.unlocked(town)&&(recipe.type()!=TownsfolkWorkType.SMELTING||batch.smelting())&&recipe.source()!=null&&recipe.nativeUnlockIssue(town,world)==null
            &&(recipe.type()==TownsfolkWorkType.CULTIVATION)==batch.cultivation()
            &&(batch.nativeLoaded()||site.orders().canComplete(batch.orderId(),batch.recipeId()))&&progress.skill(recipe.skill())>=recipe.skillLevel()
            &&progress.canPerform(recipe.type(),false,false,true);
    }
    /** A pending recipe without delivered materials must not prevent its worker from bringing those materials. */
    static int availablePriority(World world,TownRecord town,AetherhavenPlugin plugin,TownsfolkProgress progress,
            String worker,ApprenticeWorkState state,long now) {
        if(state!=null&&now<state.retryAt)return 0;
        Map<UUID,Claim> claims;synchronized(CLAIMS){claims=CLAIMS.computeIfAbsent(world,w->new HashMap<>());}
        int best=Integer.MAX_VALUE;
        for(var batch:town.getWarehouse().batches()) {
            if(state!=null&&!state.routeRetries.canTry(batch.workplaceId(),now))continue;
            var claim=claims.get(batch.workplaceId());
            if(claim!=null&&claim.until()>now&&!worker.equals(claim.worker()))continue;
            if(usable(world,batch,town,plugin,progress)&&CourierWorkService.destinationReady(world,town,plugin,batch))
                best=Math.min(best,progress.priority(batch.nativeLoaded()?TownsfolkWorkType.SMELTING:plugin.getApprenticeRecipes().find(batch.recipeId()).type()));
        }
        for(var site:town.getApprenticeWorkplaces().sites()) {
            if(state!=null&&!state.routeRetries.canTry(site.id(),now))continue;
            var claim=claims.get(site.id());
            if(claim!=null&&claim.until()>now&&!worker.equals(claim.worker()))continue;
            if(excavationUsable(world,town,site,plugin,progress))best=Math.min(best,progress.priority(TownsfolkWorkType.MINING));
            if(buildingUsable(world,town,site,progress))best=Math.min(best,progress.priority(TownsfolkWorkType.BUILDING));
            if(WorkplaceFood.supports(site.type())&&site.food().needsTending()&&progress.canPerform(site.type(),false,false,true)
                &&FoodSiteService.inspect(world,site).ready())best=Math.min(best,progress.priority(site.type()));
        }
        return best==Integer.MAX_VALUE?0:best;
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var state=store.getComponent(ref,ApprenticeWorkState.getComponentType());
        stop(ref,npc,buffer,state);
    }
    private static void stopVisual(Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer,ApprenticeWorkState state) {
        state.working=false;state.excavationCarrying=false;state.nextVisual=0;
        NpcAnimationPlayback.stop(ref,AnimationSlot.Action,buffer);
        buffer.run(st->{if(ref.isValid())VillagerLifeProps.clear(ref,st);});
    }
    private static void stop(Ref<EntityStore> ref,NPCEntity npc,CommandBuffer<EntityStore> buffer,ApprenticeWorkState state) {
        if(state==null)return;
        state.excavationGeneration++;
        state.foodSite=null;state.excavationSite=null;state.buildingSite=null;state.buildingTarget=null;
        if(!state.active)return;
        if(state.working||state.excavationCarrying)stopVisual(ref,buffer,state);
        NpcStandStill.release(ref,npc,buffer);state.active=false;state.approach=null;buffer.putComponent(ref,ApprenticeWorkState.getComponentType(),state);
    }
}
