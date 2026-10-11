package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.poi.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import java.util.function.Predicate;
import org.joml.Vector3d;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;

/** Attended planting, chopping and bounded delivery for explicitly adopted groves. */
public final class TimberWorkService {
    private TimberWorkService() {}
    private enum Action { PLANT, TEND, CHOP, PICKUP, DELIVER, CHAR }
    private static final Map<Ref<EntityStore>,Visit> VISITS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        UUID plot,tree;
        int slot,attempt;
        Action action;
        WarehouseBatch.Point target;
        WarehouseBatch.Point heat;
        String charInput;
        Vector3d approach;
        long began,retry,lastTick,lastHit,nextSave;
        double attendance,tending;
        boolean active,working,carrying;
    }
    public static boolean active(Ref<EntityStore> ref) {var v=VISITS.get(ref);return v!=null&&v.active;}
    public static Predicate<Position> bounds(TownRecord town) {
        return p->p.y()>=ChunkUtil.MIN_Y
            &&TownTerritoryClaims.contains(town,ChunkUtil.chunkCoordinate(p.x()),ChunkUtil.chunkCoordinate(p.z()));
    }
    public static boolean tick(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
            TownRecord town,TownManager manager,AetherhavenPlugin plugin) {
        var plot=town==null||binding.getJobPlotId()==null?null:town.findPlotById(binding.getJobPlotId());
        if(!TownVillagerBinding.KIND_LOGGER.equals(binding.getKind())||plot==null||plot.getState()!=PlotInstanceState.COMPLETE
            ||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL)) {
            interrupt(ref,store,npc,buffer);return false;
        }
        var production=town.getOrCreatePlotProduction(plot.getPlotId());
        if(!production.usesPhysicalTimber()){interrupt(ref,store,npc,buffer);return false;}
        if(store.getComponent(ref,DeathComponent.getComponentType())!=null){interrupt(ref,store,npc,buffer);return false;}
        boolean offDuty=needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()
            ||!ProductionLiveWorkDetection.isOnWorkSchedule(store,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
            ||!com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(store,town);
        // A felling under way and a harvest in her arms are always seen through, even when her shift has ended.
        var underway=VISITS.get(ref);
        boolean finishing=production.timber().cargo()!=null||underway!=null&&underway.active&&underway.action==Action.CHOP;
        if(offDuty&&!finishing){interrupt(ref,store,npc,buffer);return false;}
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return true;
        var world=store.getExternalData().getWorld();var position=transform.getPosition();var timber=production.timber();
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL,production);
        if(entry==null)return true;
        var visit=VISITS.computeIfAbsent(ref,ignored->new Visit());long now=System.currentTimeMillis();
        double step=visit.lastTick==0||now-visit.lastTick>2000?0:Math.min(1,Math.max(0,dt));visit.lastTick=now;
        if(visit.plot!=null&&!visit.plot.equals(plot.getPlotId()))stop(ref,store,npc,buffer,visit);
        visit.plot=plot.getPlotId();
        if(now<visit.retry)return true;
        if(!visit.active) {
            if(!choose(world,plot,production,entry,town,visit,now)||offDuty&&visit.action!=Action.DELIVER) {
                // Nothing she can finish right now, such as stores she cannot reach after hours. She keeps the harvest and rests.
                if(offDuty){interrupt(ref,store,npc,buffer);return false;}
                visit.retry=now+2000;return true;
            }
            visit.approach=CourierWorkService.approach(world,visit.target,position,visit.attempt);
            if(visit.approach==null){visit.attempt++;visit.retry=now+3000;if(offDuty){interrupt(ref,store,npc,buffer);return false;}return true;}
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            visit.active=true;visit.began=now;visit.attendance=0;visit.tending=0;
            String held=visit.action==Action.DELIVER&&timber.cargo()!=null?timber.cargo().output():
                visit.action==Action.PLANT?timber.groves().get(visit.slot).sapling():null;
            if(held!=null){visit.carrying=true;buffer.run(st->{if(ref.isValid()&&visit.active)VillagerLifeProps.carry(ref,held,st);});}
            return true;
        }
        if(!masteryAllows(town,production,visit.action,visit.slot)||!valid(world,plot,production,entry,visit)||!CourierWorkService.clearApproach(world,visit.approach)
            ||!reached(position,visit)&&now-visit.began>30000) {
            visit.attempt++;visit.retry=now+3000;stop(ref,store,npc,buffer,visit);return true;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        if(!reached(position,visit)) {
            WorkTravelRecovery.keepMoving(ref,store,buffer,npc,position,visit.approach,now);
            npc.setLeashPoint(new Vector3d(visit.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,visit.approach,null);
            buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        NpcStandStill.hold(ref,store,npc,visit.approach,buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(visit.target.x()+.5-position.x,visit.target.z()+.5-position.z)+Math.PI));
        visit.attendance+=step;
        if(visit.action==Action.TEND) {
            visit.carrying=true;
            if(now-visit.lastHit>=2500) {
                // The growth song: an open-handed gesture toward the tree while light gathers at its roots.
                visit.lastHit=now;var song=new Vector3d(visit.target.x()+.5,visit.target.y()+.1,visit.target.z()+.5);
                buffer.run(st->{if(ref.isValid()&&visit.active){
                    AnimationUtils.playAnimation(ref,AnimationSlot.Action,"Aetherhaven_Work_Actions","WaterCrops",true,st);
                    VillagerWorkVisuals.groveSong(st,song);
                }});
            }
            visit.tending+=step;
            if(visit.tending>=1) {
                int tendingSlot=visit.slot;UUID tendingTree=visit.tree;Vector3d tendingStand=new Vector3d(visit.approach);
                double assisted=visit.tending;visit.tending=0;
                buffer.run(st->{
                    var actual=ref.isValid()?st.getComponent(ref,TransformComponent.getComponentType()):null;
                    var worker=ref.isValid()?st.getComponent(ref,TownVillagerBinding.getComponentType()):null;
                    var planted=timber.trees().get(tendingSlot);
                    if(actual!=null&&worker!=null&&plot.getPlotId().equals(worker.getJobPlotId())&&town.getTownId().equals(worker.getTownId())
                        &&TownVillagerBinding.KIND_LOGGER.equals(worker.getKind())&&planted!=null&&planted.tree().id().equals(tendingTree)
                        &&NativeTimberProducts.hasRoom(production,entry,planted.tree())
                        &&st.getComponent(ref,DeathComponent.getComponentType())==null&&CourierWorkService.arrived(actual.getPosition(),tendingStand)
                        &&visit.active&&visit.action==Action.TEND&&plot.getState()==PlotInstanceState.COMPLETE
                        &&town.findPlotById(plot.getPlotId())==plot
                        &&com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",planted.tree().output())==null
                        &&ProductionLiveWorkDetection.isOnWorkSchedule(st,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
                        &&com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(st,town))
                        NativeTimberTending.tend(world,planted.tree(),assisted,tendingBudget(plugin,world,plot,production,entry,planted.tree()));
                });
            }
            if(visit.attendance>=12){timber.rotateAfter(visit.slot);manager.updateTown(town);stop(ref,store,npc,buffer,visit);}
            return true;
        }
        if(visit.action==Action.CHAR) {
            if(!visit.carrying) {
                visit.carrying=true;
                buffer.run(st->{if(ref.isValid()&&visit.active)VillagerLifeProps.carry(ref,visit.charInput,st);});
            }
            if(visit.attendance*WorkplaceProductionUpgrades.speedMultiplier(production)<TimberCharring.WORK_SECONDS)return true;
        }
        if(visit.action==Action.CHOP) {
            if(!visit.working) {
                com.hexvane.aetherhaven.equipment.VillagerEquipmentService.applyProfile(ref,store,buffer,plugin.getEquipmentProfileCatalog(),"work_logger");visit.working=true;
                buffer.run(st->{if(ref.isValid())VillagerLifeProps.carry(ref,
                    com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.toolId(town,"logger"),st);});
            }
            var visual=new PoiEntry(plot.getPlotId(),town.getTownId(),visit.target.x(),visit.target.y(),visit.target.z(),Set.of("WORK"),1,plot.getPlotId(),null,PoiInteractionKind.WORK_SURFACE);
            if(VillagerWorkVisuals.tickHit(ref,store,buffer,npc,visual,binding.getKind(),now,visit.lastHit))visit.lastHit=now;
            timber.work(visit.slot,step*WorkplaceProductionUpgrades.speedMultiplier(production));
            if(now>=visit.nextSave){manager.updateTown(town);visit.nextSave=now+5000;}
            // Inspect full geometry at selection and completion, not on every NPC tick.
            var tree=timber.trees().get(visit.slot).tree();
            long logs=tree.mature().cells().stream().filter(c->c.kind()==TimberHarvestPlan.Kind.TIMBER&&c.filler()==0).count();
            if(timber.chopping(visit.slot)<Math.max(8,logs*2))return true;
        }else if(visit.attendance<(visit.action==Action.PLANT?4:visit.action==Action.DELIVER?.75:1.5))return true;
        // All terrain and storage mutations happen after the entity command buffer, with fresh attendance checks.
        final int slot=visit.slot;final UUID identity=visit.tree;final Action action=visit.action;
        final Vector3d standing=new Vector3d(visit.approach);final WarehouseBatch.Point target=visit.target;
        final WarehouseBatch.Point heat=visit.heat;final String charInput=visit.charInput;
        buffer.run(st->{
            if(!ref.isValid()||st.getComponent(ref,DeathComponent.getComponentType())!=null)return;
            var actual=st.getComponent(ref,TransformComponent.getComponentType());
            var bound=st.getComponent(ref,TownVillagerBinding.getComponentType());
            if(actual==null||bound==null||!plot.getPlotId().equals(bound.getJobPlotId())
                ||!town.getTownId().equals(bound.getTownId())||!TownVillagerBinding.KIND_LOGGER.equals(bound.getKind())
                ||plot.getState()!=PlotInstanceState.COMPLETE
                ||!(CourierWorkService.arrived(actual.getPosition(),standing)||action==Action.DELIVER&&beside(actual.getPosition(),target))
                ||town.findPlotById(plot.getPlotId())!=plot)return;
            // Felling and putting the harvest away may finish after hours. Everything else needs her shift.
            if(action!=Action.DELIVER&&action!=Action.CHOP
                &&(!ProductionLiveWorkDetection.isOnWorkSchedule(st,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
                ||!com.hexvane.aetherhaven.economy.TownEconomyPresence.hasMemberHere(st,town)))return;
            if(!masteryAllows(town,production,action,slot))return;
            boolean changed=false;
            if(action==Action.CHAR) {
                if(ProductionStorageLocator.valid(world,plot,target)&&TimberCharring.heatAvailable(world,plot,target,heat))
                    changed=TimberCharring.process(production,entry,town.getWarehouse(),plot.getPlotId(),charInput,
                        new Position(target.x(),target.y(),target.z()));
            }else if(action==Action.PLANT) {
                var planned=timber.groves().get(slot);
                if(planned!=null&&planned.id().equals(identity)
                    &&slot<WorkplaceProductionUpgrades.stoneCount(production)&&!timber.trees().containsKey(slot)
                    &&timber.cargo()==null
                    &&NativeTimberProducts.hasRoom(production,entry,planned)&&!otherGrove(town,plot,planned)) {
                    var fresh=NativeTimberGrowth.capture(planned.output(),planned.root(),0,bounds(town));
                    if(fresh.ready()&&fresh.receipt().stages().equals(planned.stages())&&fresh.receipt().clearance().equals(planned.clearance())) {
                        var prepared=NativeTimberPlanting.prepare(world,production,slot,fresh.receipt(),bounds(town));
                        changed=prepared.ready()&&NativeTimberPlanting.plant(world,production,slot,prepared,bounds(town))==null;
                        if(changed)timber.rotateAfter(slot);
                    }
                }
            }else if(action==Action.CHOP) {
                var planted=timber.trees().get(slot);
                if(planted!=null&&planted.tree().id().equals(identity)&&NativeTimberProducts.hasRoom(production,entry,planted.tree()))
                    changed=NativeTimberHarvest.fell(world,production,slot,entry,bounds(town));
                // She gathers the whole harvest as the tree comes down: wood, sapling, sap, sticks and any fibre.
                if(changed)timber.carryAll();
            }else if(timber.cargo()!=null&&timber.cargo().tree().equals(identity)) {
                if(ProductionStorageLocator.valid(world,plot,target)) {
                    timber.carryAll();
                    int delivered=timber.deliverAll(production,item->WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,item));
                    changed=true;
                    if(changed)com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"logger").earnXp(delivered);
                }
            }
            if(changed){town.getWarehouse().markProductionWithdrawal();manager.updateTown(town);}
        });
        visit.attempt=0;visit.retry=now+1500;stop(ref,store,npc,buffer,visit);return true;
    }
    private static boolean choose(World world,PlotInstance plot,PlotProductionState production,ProductionCatalog.Entry entry,TownRecord town,Visit v,long now) {
        var timber=production.timber();var cargo=timber.cargo();
        if(cargo!=null) {
            // One trip home with everything. Older saves may still have a harvest waiting at its stump.
            timber.carryAll();
            v.tree=cargo.tree();v.action=Action.DELIVER;
            v.target=ProductionStorageLocator.find(world,plot,now);return v.target!=null;
        }
        // Stone numbers are stable, so rotate across every possible stone and skip the unused ones.
        int slots=TimberProductionState.MAX_STONES;int allowed=WorkplaceProductionUpgrades.stoneCount(production);
        String charInput=TimberCharring.input(production,entry,town.getWarehouse(),plot.getPlotId());
        if(charInput!=null&&com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",TimberCharring.OUTPUT)==null) {
            var storage=ProductionStorageLocator.find(world,plot,now);
            var heat=TimberCharring.findHeat(world,plot,storage);
            if(heat!=null) {
                v.action=Action.CHAR;v.target=storage;v.heat=heat;v.charInput=charInput;v.tree=null;return true;
            }
        }
        for(int offset=0;offset<slots;offset++) {
            int slot=(timber.firstSlot(slots)+offset)%slots;
            var planted=timber.trees().get(slot);var planned=timber.groves().get(slot);
            if(planted!=null) {
                if(com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",planted.tree().output())!=null
                    ||!NativeTimberProducts.hasRoom(production,entry,planted.tree()))continue;
                var ready=NativeTimberHarvest.inspect(world,planted,bounds(town));
                // Nothing of this tree is left to walk to. Its stone is free to grow again.
                if(TimberFellingPlan.GONE.equals(ready.issue())){timber.release(slot,planted.tree().id());continue;}
                if(!ready.ready())continue;
                v.action=Action.CHOP;v.tree=planted.tree().id();v.target=point(planted.tree().root());
            }else {
                if(planned==null||slot>=allowed||com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",planned.output())!=null
                    ||!NativeTimberProducts.hasRoom(production,entry,planned)||otherGrove(town,plot,planned)
                    ||!NativeTimberPlanting.prepare(world,production,slot,planned,bounds(town)).ready())continue;
                v.action=Action.PLANT;v.tree=planned.id();v.target=point(planned.root());
            }
            v.slot=slot;return true;
        }
        for(int offset=0;offset<slots;offset++) {
            int slot=(timber.firstSlot(slots)+offset)%slots;var planted=timber.trees().get(slot);
            if(planted==null||com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",planted.tree().output())!=null
                ||!NativeTimberProducts.hasRoom(production,entry,planted.tree())
                ||!NativeTimberTending.growing(world,planted.tree()))continue;
            v.slot=slot;v.tree=planted.tree().id();v.action=Action.TEND;v.target=point(planted.tree().root());return true;
        }
        return false;
    }
    private static boolean masteryAllows(TownRecord town,PlotProductionState p,Action action,int slot) {
        if(action==Action.CHAR)return com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",TimberCharring.OUTPUT)==null;
        if(action==Action.PICKUP||action==Action.DELIVER)return true;
        var tree=p.timber().trees().get(slot);var plan=p.timber().groves().get(slot);
        String output=tree!=null?tree.tree().output():plan!=null?plan.output():null;
        return output!=null&&com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"logger",output)==null;
    }
    private static boolean valid(World world,PlotInstance plot,PlotProductionState p,ProductionCatalog.Entry entry,Visit v) {
        if(v.action==Action.CHAR)return p.timber().cargo()==null
            &&TimberCharring.enabled(p)&&hasSpace(p,entry,TimberCharring.OUTPUT)
            &&ProductionStorageLocator.valid(world,plot,v.target)&&TimberCharring.heatAvailable(world,plot,v.target,v.heat);
        var t=p.timber();if(v.action==Action.DELIVER)return t.cargo()!=null&&v.tree.equals(t.cargo().tree())&&ProductionStorageLocator.valid(world,plot,v.target);
        if(v.action==Action.PICKUP)return t.cargo()!=null&&v.tree.equals(t.cargo().tree())&&hasSpace(p,entry,t.cargo().output());
        if(t.cargo()!=null||v.slot>=TimberProductionState.MAX_STONES)return false;
        if(v.action==Action.CHOP||v.action==Action.TEND){var tree=t.trees().get(v.slot);return tree!=null&&tree.tree().id().equals(v.tree)&&NativeTimberProducts.hasRoom(p,entry,tree.tree())
            &&(v.action!=Action.TEND||NativeTimberTending.growing(world,tree.tree()));}
        var plan=t.groves().get(v.slot);
        boolean same=plan!=null&&plan.id().equals(v.tree)&&!t.trees().containsKey(v.slot);
        return v.action==Action.PLANT&&same&&NativeTimberProducts.hasRoom(p,entry,plan);
    }
    private static boolean otherGrove(TownRecord town,PlotInstance plot,TimberPlantingReceipt tree) {
        for(var other:town.getPlotInstances())if(!other.getPlotId().equals(plot.getPlotId())) {
            var state=town.peekPlotProduction(other.getPlotId());if(state!=null&&state.groveConflictsWith(tree))return true;
        }
        return false;
    }
    private static boolean hasSpace(PlotProductionState p,ProductionCatalog.Entry entry,String item) {return WorkplaceProductionUpgrades.effectiveMaxStorage(p,entry,item)>p.getAmount(item);}
    private static double tendingBudget(AetherhavenPlugin plugin,World world,PlotInstance plot,PlotProductionState p,ProductionCatalog.Entry entry,TimberPlantingReceipt tree) {
        int cursor=0;for(int i=0;i<entry.catalogSize();i++)if(tree.output().equals(entry.itemAtCursor(i))){cursor=i;break;}
        int ticks=ProductionTimeScaling.effectiveTicksForItemAtPlot(plugin.getConfig().get(),entry.ticksAtCursor(cursor),
            WorkplaceProductionUpgrades.speedMultiplier(p),world,plot.getSignX(),plot.getSignZ(),tree.output());
        long logs=tree.mature().cells().stream().filter(c->c.kind()==TimberHarvestPlan.Kind.TIMBER&&c.filler()==0).count();
        // Singing a tree up takes about as long as the old timers took to make the same wood across the whole mill.
        return Math.max(30,logs*ticks/AetherhavenConstants.PRODUCTION_ENTITY_TICKS_PER_SECOND/WorkplaceProductionUpgrades.stoneCount(p));
    }
    /** Standing at the chosen spot, or for a delivery simply close enough to reach the stores. */
    private static boolean reached(Vector3d position,Visit v) {
        return CourierWorkService.arrived(position,v.approach)||v.action==Action.DELIVER&&beside(position,v.target);
    }
    private static boolean beside(Vector3d position,WarehouseBatch.Point target) {
        if(target==null)return false;
        double dx=target.x()+.5-position.x,dz=target.z()+.5-position.z;
        return dx*dx+dz*dz<=2.5*2.5&&Math.abs(target.y()-position.y)<=1.5;
    }
    private static WarehouseBatch.Point point(Position p){return new WarehouseBatch.Point(p.x(),p.y(),p.z());}
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var visit=VISITS.get(ref);if(visit!=null){stop(ref,store,npc,buffer,visit);visit.lastTick=0;}
    }
    private static void stop(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer,Visit v) {
        if(v.active)NpcStandStill.release(ref,npc,buffer);
        if(v.working) {
            buffer.run(st->{if(ref.isValid()){VillagerLifeProps.clear(ref,st);AnimationUtils.stopAnimation(ref,AnimationSlot.Action,st);}});
            com.hexvane.aetherhaven.equipment.VillagerEquipmentService.clearHotbar(ref,store,buffer);
        }
        if(v.carrying)buffer.run(st->{if(ref.isValid()){VillagerLifeProps.clear(ref,st);AnimationUtils.stopAnimation(ref,AnimationSlot.Action,st);}});
        v.active=false;v.working=false;v.carrying=false;v.approach=null;v.attendance=0;
    }
}
