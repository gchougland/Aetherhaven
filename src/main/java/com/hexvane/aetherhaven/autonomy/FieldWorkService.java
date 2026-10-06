package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.poi.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hexvane.aetherhaven.workplace.FoodSiteService;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;

/** Visible core Farmer and Druid tending, native harvest and local delivery. No Apprentice food output. */
public final class FieldWorkService {
    private FieldWorkService() {}
    private enum Action { WATER, HARVEST, DELIVER }
    private static final Map<Ref<EntityStore>,Visit> VISITS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        UUID plot,cargo;int index,attempt;long began,retry,lastTick,nextVisual,revision;
        FieldProductionState.Bed bed;Action action;WarehouseBatch.Point target;Vector3d approach;
        double attendance;boolean active,visual;
        final Map<WarehouseBatch.Point,Long> failed=new HashMap<>();
    }
    public static boolean active(Ref<EntityStore> ref){var v=VISITS.get(ref);return v!=null&&v.active;}
    public static boolean tick(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
            TownRecord town,TownManager manager,AetherhavenPlugin plugin) {
        var plot=town==null||binding.getJobPlotId()==null?null:town.findPlotById(binding.getJobPlotId());
        String expected="druid".equals(binding.getKind())?NativeDruidCrops.CONSTRUCTION:AetherhavenConstants.CONSTRUCTION_PLOT_FARM;
        if(!(TownVillagerBinding.KIND_FARMER.equals(binding.getKind())||"druid".equals(binding.getKind()))||plot==null||plot.getState()!=PlotInstanceState.COMPLETE
            ||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),expected)) {
            interrupt(ref,store,npc,buffer);return false;
        }
        var production=town.getOrCreatePlotProduction(plot.getPlotId());
        if(!production.usesPhysicalField()||needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()
            ||!eligible(ref,store,npc,binding,town,plot,plugin)) {interrupt(ref,store,npc,buffer);return false;}
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return true;
        var world=store.getExternalData().getWorld();var position=transform.getPosition();var field=production.field();
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),expected,production);
        if(entry==null)return true;
        if(field.pending()!=null) {
            interrupt(ref,store,npc,buffer);
            buffer.run(st->{if(NativeFarmCrops.settleHarvest(world,production))manager.updateTown(town);});
            return true;
        }
        var visit=VISITS.computeIfAbsent(ref,ignored->new Visit());long now=System.currentTimeMillis();
        double step=visit.lastTick==0||now-visit.lastTick>2000?0:Math.min(1,Math.max(0,dt));visit.lastTick=now;
        if(visit.plot!=null&&!visit.plot.equals(plot.getPlotId())){stop(ref,store,npc,buffer,visit);visit.failed.clear();}
        visit.plot=plot.getPlotId();if(now<visit.retry)return true;
        if(!visit.active) {
            if(!choose(world,plot,production,entry,town,visit,now)){visit.retry=now+2000;return true;}
            visit.approach=CourierWorkService.approach(world,visit.target,position,visit.attempt);
            if(visit.approach==null){visit.failed.put(visit.target,now+15000);visit.attempt++;visit.retry=now+1000;return true;}
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            visit.active=true;visit.began=now;visit.attendance=0;visit.revision=field.revision();visit.nextVisual=0;
            if(visit.action==Action.DELIVER&&field.cargo()!=null){String item=field.cargo().output();visit.visual=true;
                buffer.run(st->{if(ref.isValid()&&visit.active)VillagerLifeProps.carry(ref,item,st);});}
            return true;
        }
        if(visit.revision!=field.revision()||!valid(world,town,plot,production,entry,visit)||!CourierWorkService.clearApproach(world,visit.approach)
            ||!CourierWorkService.arrived(position,visit.approach)&&now-visit.began>30000) {
            visit.failed.put(visit.target,now+15000);visit.attempt++;visit.retry=now+1000;stop(ref,store,npc,buffer,visit);return true;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        if(!CourierWorkService.arrived(position,visit.approach)) {
            npc.setLeashPoint(new Vector3d(visit.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,visit.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        NpcStandStill.hold(ref,store,npc,visit.approach,buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(visit.target.x()+.5-position.x,visit.target.z()+.5-position.z)+Math.PI));
        visit.attendance+=step*WorkplaceProductionUpgrades.speedMultiplier(production);
        if(visit.action!=Action.DELIVER&&now>=visit.nextVisual) {
            boolean water=visit.action==Action.WATER;visit.visual=true;visit.nextVisual=now+2500;
            buffer.run(st->{if(ref.isValid()&&visit.active){
                if(water)VillagerLifeProps.equip(ref,"WaterCrops",st);else VillagerLifeProps.clear(ref,st);
                AnimationUtils.playAnimation(ref,AnimationSlot.Action,water?"Aetherhaven_Work_Actions":NpcFaceVisuals.itemAnimationsForFaceRig(ref,"Aetherhaven_Life_Actions",st),water?"WaterCrops":"Tend",true,st);
            }});
        }
        if(visit.attendance<(visit.action==Action.HARVEST?6:visit.action==Action.WATER?4:1.5))return true;
        var action=visit.action;var bed=visit.bed;var cargo=visit.cargo;var target=visit.target;var standing=new Vector3d(visit.approach);
        long revision=visit.revision;int index=visit.index;
        buffer.run(st->{
            var actual=ref.isValid()?st.getComponent(ref,TransformComponent.getComponentType()):null;
            var bound=ref.isValid()?st.getComponent(ref,TownVillagerBinding.getComponentType()):null;
            if(actual==null||bound==null||!eligible(ref,st,npc,bound,town,plot,plugin)||field.revision()!=revision
                ||!CourierWorkService.arrived(actual.getPosition(),standing))return;
            boolean changed=false;
            if(action==Action.DELIVER) {
                var held=field.cargo();
                if(held!=null&&held.id().equals(cargo)&&ProductionStorageLocator.valid(world,plot,target)) {
                    int delivered=field.deliver(production,cargo,WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,held.output()));
                    changed=delivered>0;
                    if(changed)com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,NativeDruidCrops.isPlot(plot)?"druid":"farmer").earnXp(delivered);
                }
            } else if(field.cargo()==null&&field.beds().contains(bed)&&masteryAllows(town,plot,bed)&&NativeFarmCrops.canTend(production,entry,bed)&&NativeFarmCrops.usable(world,town,plot,bed)) {
                changed=action==Action.HARVEST?NativeFarmCrops.harvest(world,town,plot,production,bed,NativeFarmCrops.selectedOutputs(production,entry,bed),()->manager.updateTown(town))
                    :NativeFarmCrops.needsWater(world,bed)&&FoodSiteService.waterSoil(world,bed.point());
                if(changed)field.rotateAfter(index);
            }
            if(changed){town.getWarehouse().markProductionWithdrawal();manager.updateTown(town);}
        });
        visit.attempt=0;visit.retry=now+1000;stop(ref,store,npc,buffer,visit);return true;
    }
    private static boolean eligible(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,TownVillagerBinding binding,TownRecord town,PlotInstance plot,AetherhavenPlugin plugin) {
        return ref.isValid()&&store.getComponent(ref,DeathComponent.getComponentType())==null
            &&(NativeDruidCrops.isPlot(plot)?"druid".equals(binding.getKind()):TownVillagerBinding.KIND_FARMER.equals(binding.getKind()))
            &&plot.getPlotId().equals(binding.getJobPlotId())&&town.getTownId().equals(binding.getTownId())
            &&town.findPlotById(plot.getPlotId())==plot&&plot.getState()==PlotInstanceState.COMPLETE
            &&ProductionLiveWorkDetection.isOnWorkSchedule(store,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
            &&com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(store,town);
    }
    private static boolean choose(World world,PlotInstance plot,PlotProductionState p,ProductionCatalog.Entry entry,TownRecord town,Visit v,long now) {
        v.failed.values().removeIf(until->until<=now);var field=p.field();var cargo=field.cargo();
        if(cargo!=null) {
            if(!hasSpace(p,entry,cargo.output()))return false;
            v.action=Action.DELIVER;v.cargo=cargo.id();v.target=ProductionStorageLocator.find(world,plot,now);return v.target!=null&&!v.failed.containsKey(v.target);
        }
        for(boolean harvest:new boolean[]{true,false})for(int offset=0;offset<field.beds().size();offset++) {
            int index=(field.firstBed()+offset)%field.beds().size();var bed=field.beds().get(index);
            if(v.failed.containsKey(bed.point())||!masteryAllows(town,plot,bed)||!NativeFarmCrops.canTend(p,entry,bed)||!NativeFarmCrops.usable(world,town,plot,bed))continue;
            if(harvest?!NativeFarmCrops.ripe(world,bed):!NativeFarmCrops.needsWater(world,bed))continue;
            v.action=harvest?Action.HARVEST:Action.WATER;v.bed=bed;v.index=index;v.target=bed.point();return true;
        }
        return false;
    }
    private static boolean valid(World world,TownRecord town,PlotInstance plot,PlotProductionState p,ProductionCatalog.Entry entry,Visit v) {
        if(v.action==Action.DELIVER)return p.field().cargo()!=null&&p.field().cargo().id().equals(v.cargo)
            &&hasSpace(p,entry,p.field().cargo().output())&&ProductionStorageLocator.valid(world,plot,v.target);
        return p.field().cargo()==null&&p.field().beds().contains(v.bed)&&masteryAllows(town,plot,v.bed)&&NativeFarmCrops.canTend(p,entry,v.bed)&&NativeFarmCrops.usable(world,town,plot,v.bed)
            &&(v.action==Action.HARVEST?NativeFarmCrops.ripe(world,v.bed):NativeFarmCrops.needsWater(world,v.bed));
    }
    private static boolean hasSpace(PlotProductionState p,ProductionCatalog.Entry entry,String item){return p.getAmount(item)<WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,item);}
    private static boolean masteryAllows(TownRecord town,PlotInstance plot,FieldProductionState.Bed bed) {
        return com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,NativeDruidCrops.isPlot(plot)?"druid":"farmer",bed.output())==null;
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var v=VISITS.get(ref);if(v!=null){stop(ref,store,npc,buffer,v);v.lastTick=0;}
    }
    private static void stop(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer,Visit v) {
        if(v.active)NpcStandStill.release(ref,npc,buffer);
        if(v.visual)buffer.run(st->{if(ref.isValid()){VillagerLifeProps.clear(ref,st);AnimationUtils.stopAnimation(ref,AnimationSlot.Action,st);}});
        v.active=false;v.visual=false;v.attendance=0;v.approach=null;
    }
}
