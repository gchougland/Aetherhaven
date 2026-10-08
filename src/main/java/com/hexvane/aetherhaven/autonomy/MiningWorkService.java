package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.poi.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
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
import org.joml.Vector3i;

/** Physical core Miner work. Old WORK markers never become node cells or teleport destinations. */
public final class MiningWorkService {
    private MiningWorkService() {}
    private static final Map<Ref<EntityStore>, Visit> VISITS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        UUID plot;
        int slot=-1, node, attempt;
        Vector3d approach;
        long began, retry, lastHit, nextSave, lastTick;
        boolean active, working;
    }
    public static boolean active(Ref<EntityStore> ref) {
        var v=VISITS.get(ref); return v!=null && v.active;
    }
    public static String diagnostic(Ref<EntityStore> ref) {
        var v=VISITS.get(ref);
        return v==null?"idle":"slot="+v.slot+" node="+v.node+" approach="+(v.approach==null?null:
            String.format(Locale.ROOT,"%.3f,%.3f,%.3f",v.approach.x,v.approach.y,v.approach.z))
            +" working="+v.working+" attempt="+v.attempt;
    }
    public static boolean tick(float dt, Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
            NPCEntity npc, TownVillagerBinding binding, VillagerNeeds needs, VillagerAutonomyState autonomy,
            TownRecord town, TownManager manager, AetherhavenPlugin plugin) {
        if (!TownVillagerBinding.KIND_MINER.equals(binding.getKind()) || binding.getJobPlotId()==null) {
            interrupt(ref,store,npc,buffer); return false;
        }
        var plot=town==null?null:town.findPlotById(binding.getJobPlotId());
        if (!TownVillagerBinding.KIND_MINER.equals(binding.getKind()) || plot==null || plot.getState()!=PlotInstanceState.COMPLETE
            || plot.getProductionLayout().legacy()) { interrupt(ref,store,npc,buffer); return false; }
        if (plot.getProductionLayout().validationError()!=null || !plugin.getConstructionCatalog().matchesGameplayConstruction(
                plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT)) {
            interrupt(ref,store,npc,buffer); return true;
        }
        if (needs.getHunger()<20 || needs.getEnergy()<20 || autonomy.isFillingHunger() || autonomy.isFillingEnergy()
            || store.getComponent(ref,DeathComponent.getComponentType())!=null
            || !ProductionLiveWorkDetection.isOnWorkSchedule(store,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
            || Universe.get().getPlayers().stream().noneMatch(p->town.hasMemberOrOwner(p.getUuid()))) {
            interrupt(ref,store,npc,buffer); return false;
        }
        var world=store.getExternalData().getWorld();
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if (transform==null) return true;
        var position=transform.getPosition();
        var state=town.getOrCreatePlotProduction(plot.getPlotId());
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),
            AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT,state);
        if (entry==null) return true;
        var mining=state.mining(); int count=WorkplaceProductionUpgrades.slotCount(state);
        long outputCount=CharterSpecializationModifiers.productionAmountPerCycle(town,plugin.getConstructionCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
        var visit=VISITS.computeIfAbsent(ref,ignored->new Visit()); long now=System.currentTimeMillis();
        double step=visit.lastTick==0 || now-visit.lastTick>2000?0:dt; visit.lastTick=now;
        if (visit.plot!=null && !visit.plot.equals(plot.getPlotId())) stop(ref,store,npc,buffer,visit);
        visit.plot=plot.getPlotId();
        // Only simulated nodes advance. A save reload or time command cannot mature a vein.
        boolean loaded=plot.getProductionLayout().nodes().size()>0
            && MiningNodeBlocks.loaded(world,MiningNodeBlocks.point(plot,mining.node(plot.getProductionLayout().nodes().size())));
        if (!loaded) { stop(ref,store,npc,buffer,visit); return true; }
        for (int slot=0;slot<count;slot++) {
            var cycle=mining.cycle(slot); String item=entry.itemAtCursor(state.getSlotCursor(slot));
            cycle.select(item,period(plugin,world,plot,state,entry,slot));
            if (com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"miner",item)==null) cycle.simulate(step);
        }
        if (visit.active && (visit.slot>=count || !mining.cycle(visit.slot).ready()
            || com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"miner",mining.cycle(visit.slot).item())!=null
            || !MiningNodeBlocks.intact(world,plot,mining,visit.node))) stop(ref,store,npc,buffer,visit);
        if (now>=visit.nextSave) { buffer.run(st->{if(MiningNodeBlocks.migrateLegacy(world,plot,mining))manager.updateTown(town);}); manager.updateTown(town); visit.nextSave=now+5000; }
        if (now<visit.retry) return true;
        if (!visit.active) {
            int selected=-1;
            for (int i=0;i<count;i++) {
                int slot=(mining.firstSlot(count)+i)%count; var cycle=mining.cycle(slot);
                if (cycle.ready() && MiningNodeBlocks.supported(cycle.item())
                    && com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"miner",cycle.item())==null
                    && hasSpace(state,entry,cycle.item(),outputCount)) { selected=slot; break; }
            }
            if (selected<0) return true;
            int nodes=plot.getProductionLayout().nodes().size(); Vector3d approach=null; int node=-1;
            for (int i=0;i<nodes;i++) {
                int candidate=(mining.node(nodes)+i)%nodes;
                if (!MiningNodeBlocks.intact(world,plot,mining,candidate)) continue;
                approach=approach(world,MiningNodeBlocks.point(plot,candidate),position,visit.attempt);
                if (approach!=null) { node=candidate; break; }
            }
            if (approach==null) { visit.retry=now+3000; return true; }
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE); autonomy.clearTravelAndPoiState();
            buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            visit.slot=selected; visit.node=node; visit.approach=approach; visit.began=now; visit.active=true;
            int ownedNode=node; String block=MiningNodeBlocks.blockFor(mining.cycle(selected).item());
            buffer.run(st->{ if (ref.isValid() && MiningNodeBlocks.replace(world,plot,mining,ownedNode,block)) manager.updateTown(town); });
            return true;
        }
        var cycle=mining.cycle(visit.slot); var cell=MiningNodeBlocks.point(plot,visit.node);
        if (!hasSpace(state,entry,cycle.item(),outputCount)) { stop(ref,store,npc,buffer,visit); return true; }
        if (!CourierWorkService.clearApproach(world,visit.approach) || !CourierWorkService.arrived(position,visit.approach) && now-visit.began>30000) {
            visit.attempt++; visit.retry=now+3000; stop(ref,store,npc,buffer,visit); return true;
        }
        if (store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null) return true;
        if (!CourierWorkService.arrived(position,visit.approach)) {
            npc.setLeashPoint(new Vector3d(visit.approach)); VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,visit.approach,null);
            buffer.putComponent(ref,NPCEntity.getComponentType(),npc); return true;
        }
        if (!Objects.equals(MiningNodeBlocks.current(world,cell),MiningNodeBlocks.blockFor(cycle.item()))) return true;
        NpcStandStill.hold(ref,store,npc,visit.approach,buffer);
        if (!visit.working) {
            com.hexvane.aetherhaven.equipment.VillagerEquipmentService.applyProfile(ref,store,buffer,plugin.getEquipmentProfileCatalog(),"work_miner");
            buffer.run(st->{if(ref.isValid())VillagerLifeProps.carry(ref,
                com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.toolId(town,"miner"),st);});
            visit.working=true;
        }
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(cell.x+.5-position.x,cell.z+.5-position.z)+Math.PI));
        var visual=new PoiEntry(plot.getPlotId(),town.getTownId(),cell.x,cell.y,cell.z,Set.of("WORK"),1,plot.getPlotId(),null,PoiInteractionKind.WORK_SURFACE);
        if (VillagerWorkVisuals.tickHit(ref,store,buffer,npc,visual,binding.getKind(),now,visit.lastHit)) visit.lastHit=now;
        cycle.work(step,true,true);
        if (cycle.progress()>=1) {
            int slot=visit.slot, node=visit.node; String item=cycle.item();
            buffer.run(st->{
                if (!ref.isValid() || !Objects.equals(item,entry.itemAtCursor(state.getSlotCursor(slot)))
                    || com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"miner",item)!=null) return;
                if (cycle.extract(item,period(plugin,world,plot,state,entry,slot),hasSpace(state,entry,item,outputCount),
                    ()->MiningNodeBlocks.replace(world,plot,mining,node,MiningNodeBlocks.DEPLETED))) {
                    state.addAmount(item,outputCount,WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,item));
                    com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"miner").earnXp(outputCount);
                    mining.completed(slot); manager.updateTown(town);
                }
            });
            visit.attempt=0; stop(ref,store,npc,buffer,visit);
        }
        return true;
    }
    private static boolean hasSpace(PlotProductionState state, ProductionCatalog.Entry entry, String item,long amount) {
        return item!=null && amount>0 && WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,item)-state.getAmount(item)>=amount;
    }
    private static double period(AetherhavenPlugin plugin, World world, PlotInstance plot, PlotProductionState state, ProductionCatalog.Entry entry,int slot) {
        var cfg=plugin.getConfig().get(); String item=entry.itemAtCursor(state.getSlotCursor(slot));
        double zone=ProductionResourceZoneAffinity.timeMultiplierForResolvedZone(
            ProductionResourceZoneAffinity.resolveAdventureZoneOrSkip(world,plot.getSignX(),plot.getSignZ()),item,cfg.getProductionZoneMismatchTimeMultiplier());
        // Preserve established base throughput before travel; a physical swing cycle is part of the total.
        int ticks=ProductionTimeScaling.effectiveTicksWithWorkplaceSpeedAndZone(cfg,entry.ticksAtCursor(state.getSlotCursor(slot)),
            WorkplaceProductionUpgrades.speedMultiplier(state),zone);
        return Math.max(1,ticks/AetherhavenConstants.PRODUCTION_ENTITY_TICKS_PER_SECOND-MiningCycle.EXTRACTION_SECONDS);
    }
    private static Vector3d approach(World world, Vector3i node, Vector3d from,int attempt) {
        List<Vector3d> choices=new ArrayList<>();
        for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dy=1;dy>=-1;dy--) {
            int x=node.x+offset[0], y=node.y+dy, z=node.z+offset[1];
            if(MiningNodeBlocks.loaded(world,new Vector3i(x,y,z)) && VillagerBlockUtil.isNpcStandColumn(world,x,y,z))
                choices.add(new Vector3d(x+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,x,y,z),z+.5));
        }
        // Prefer a side at the vein's base over a nearby ledge above it. Native travel can stop
        // horizontally close to a ledge before completing the step down into a shallow mine.
        choices.sort(Comparator.comparingDouble((Vector3d p)->Math.abs(p.y-node.y)).thenComparingDouble(from::distanceSquared));
        return choices.isEmpty()?null:choices.get(Math.floorMod(attempt,choices.size()));
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var visit=VISITS.get(ref); if(visit!=null) stop(ref,store,npc,buffer,visit);
    }
    private static void stop(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer,Visit v) {
        if(v.active) NpcStandStill.release(ref,npc,buffer);
        if(v.working) {
            buffer.run(st->{ if(ref.isValid()) { VillagerLifeProps.clear(ref,st); AnimationUtils.stopAnimation(ref,AnimationSlot.Action,st); } });
            com.hexvane.aetherhaven.equipment.VillagerEquipmentService.clearHotbar(ref,store,buffer);
        }
        v.active=false; v.working=false; v.approach=null; v.slot=-1;
    }
}
