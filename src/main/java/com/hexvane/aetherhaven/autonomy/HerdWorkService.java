package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.npc.*;
import com.hexvane.aetherhaven.poi.*;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hexvane.aetherhaven.warehouse.*;
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

/** Core Rancher attendance and physical transport. Shared animal roles still have no passive item output. */
public final class HerdWorkService {
    private HerdWorkService() {}
    private enum Action { FEED, MATURE, COLLECT, DELIVER }
    private static final Map<Ref<EntityStore>,Visit> VISITS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        UUID plot,animal,cargo;String family,key;RanchProducts.Product product;Action action;
        WarehouseBatch.Point target;Vector3d approach;long began,retry,lastTick,nextVisual,revision;int attempt;
        boolean active,visual;double work;
        final Map<String,Double> attended=new HashMap<>();final Map<String,Long> failed=new HashMap<>();
    }
    public static boolean active(Ref<EntityStore> ref){var v=VISITS.get(ref);return v!=null&&v.active;}
    /** Read-only diagnostic used by the disposable attended preview. */
    public static String describe(Ref<EntityStore> ref) {
        var v=VISITS.get(ref);
        return v==null?"No herd visit":v.action+", active "+v.active+", work "+String.format(Locale.ROOT,"%.1f",v.work)
            +", target "+v.target+", approach "+v.approach+", attempt "+v.attempt;
    }
    private static boolean eligible(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,TownVillagerBinding binding,TownRecord town,PlotInstance plot,AetherhavenPlugin plugin) {
        return ref.isValid()&&store.getComponent(ref,DeathComponent.getComponentType())==null&&TownVillagerBinding.KIND_RANCHER.equals(binding.getKind())
            &&plot.getPlotId().equals(binding.getJobPlotId())&&town.getTownId().equals(binding.getTownId())&&town.findPlotById(plot.getPlotId())==plot&&plot.getState()==PlotInstanceState.COMPLETE
            &&ProductionLiveWorkDetection.isOnWorkSchedule(store,ref,npc,plugin.getVillagerDefinitionCatalog(),plugin.getVillagerScheduleRegistry())
            &&Universe.get().getPlayers().stream().anyMatch(p->town.hasMemberOrOwner(p.getUuid()));
    }
    public static boolean tick(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,NPCEntity npc,TownVillagerBinding binding,
            VillagerNeeds needs,VillagerAutonomyState autonomy,TownRecord town,TownManager manager,AetherhavenPlugin plugin) {
        var plot=town==null||binding.getJobPlotId()==null?null:town.findPlotById(binding.getJobPlotId());
        if(!TownVillagerBinding.KIND_RANCHER.equals(binding.getKind())||plot==null||!plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN)) {
            interrupt(ref,store,npc,buffer);return false;
        }
        var state=town.getOrCreatePlotProduction(plot.getPlotId());
        if(!state.usesPhysicalHerd()||needs.getHunger()<20||needs.getEnergy()<20||autonomy.isFillingHunger()||autonomy.isFillingEnergy()||!eligible(ref,store,npc,binding,town,plot,plugin)) {
            interrupt(ref,store,npc,buffer);return false;
        }
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return true;
        var world=store.getExternalData().getWorld();var position=transform.getPosition();var production=state.herd();
        var entry=ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(),plugin.getWorkplaceUnlockCatalog(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN,state);if(entry==null)return true;
        var visit=VISITS.computeIfAbsent(ref,ignored->new Visit());long now=System.currentTimeMillis();
        double step=visit.lastTick==0||now-visit.lastTick>2000?0:Math.min(1,Math.max(0,dt));visit.lastTick=now;
        if(visit.plot!=null&&!visit.plot.equals(plot.getPlotId())){stop(ref,store,npc,buffer,visit);visit.attended.clear();visit.failed.clear();}
        visit.plot=plot.getPlotId();if(now<visit.retry)return true;
        if(!visit.active) {
            if(!choose(world,town,plot,state,entry,visit,now)){visit.retry=now+2000;return true;}
            // Include the aisle beyond pen gates and the barn's recessed storage cabinet.
            // Animal visits still require real attendance within four blocks.
            visit.approach=visit.animal!=null||visit.action==Action.DELIVER
                ?CourierWorkService.approach(world,visit.target,position,visit.attempt,-1,0,3)
                :CourierWorkService.approach(world,visit.target,position,visit.attempt);
            if(visit.approach==null){visit.failed.put(visit.key,now+10000);visit.attempt++;visit.retry=now+1000;return true;}
            var registry=AetherhavenWorldRegistries.getOrCreatePoiRegistry(world,plugin);
            VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,registry,false);
            autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);
            visit.active=true;visit.began=now;visit.work=visit.attended.getOrDefault(visit.key,0.0);visit.revision=production.revision();visit.nextVisual=0;
            if(visit.action==Action.DELIVER&&production.cargo()!=null) {
                String item=production.cargo().output();visit.visual=true;buffer.run(st->{if(ref.isValid()&&visit.active)VillagerLifeProps.carry(ref,item,st);});
            }
            return true;
        }
        if(visit.revision!=production.revision()||!valid(world,town,plot,state,entry,visit)||!CourierWorkService.clearApproach(world,visit.approach)
            ||!CourierWorkService.arrived(position,visit.approach)&&now-visit.began>30000) {
            visit.failed.put(visit.key,now+3000);visit.attempt++;visit.retry=now+500;stop(ref,store,npc,buffer,visit);return true;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        if(!CourierWorkService.arrived(position,visit.approach)) {
            npc.setLeashPoint(new Vector3d(visit.approach));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,position,visit.approach,null);buffer.putComponent(ref,NPCEntity.getComponentType(),npc);return true;
        }
        if(visit.animal!=null) {
            var herd=production.herds().get(visit.family);var animal=herd==null?null:NativeHerdService.offspring(world,town,plot,herd);
            if(animal==null||store.getComponent(animal,TransformComponent.getComponentType()).getPosition().distanceSquared(position)>16) {
                stop(ref,store,npc,buffer,visit);visit.retry=now+300;return true;
            }
        }
        NpcStandStill.hold(ref,store,npc,visit.approach,buffer);
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(visit.target.x()+.5-position.x,visit.target.z()+.5-position.z)+Math.PI));
        visit.work+=step*WorkplaceProductionUpgrades.speedMultiplier(state);visit.attended.put(visit.key,visit.work);
        if(visit.attended.size()>32){visit.attended.clear();visit.attended.put(visit.key,visit.work);}
        if(visit.action!=Action.DELIVER&&now>=visit.nextVisual) {
            String tool=visit.action==Action.COLLECT?visit.product.tool():"Tool_Feedbag";visit.visual=true;visit.nextVisual=now+2500;
            buffer.run(st->{if(ref.isValid()&&visit.active){VillagerLifeProps.carry(ref,tool,st);
                AnimationUtils.playAnimation(ref,AnimationSlot.Action,NpcFaceVisuals.itemAnimationsForFaceRig(ref,"Aetherhaven_Life_Actions",st),"Tend",true,st);}});
        }
        if(visit.work<(visit.action==Action.DELIVER?1.5:visit.action==Action.FEED?8:6))return true;
        var action=visit.action;var family=visit.family;var animal=visit.animal;var cargo=visit.cargo;var product=visit.product;var target=visit.target;
        long revision=visit.revision;var standing=new Vector3d(visit.approach);String key=visit.key;
        buffer.run(st->{
            var actual=ref.isValid()?st.getComponent(ref,TransformComponent.getComponentType()):null;var bound=ref.isValid()?st.getComponent(ref,TownVillagerBinding.getComponentType()):null;
            if(actual==null||bound==null||production.revision()!=revision||!eligible(ref,st,npc,bound,town,plot,plugin)||!CourierWorkService.arrived(actual.getPosition(),standing))return;
            boolean changed=false;var herd=family==null?null:production.herds().get(family);
            if(action==Action.DELIVER) {
                var held=production.cargo();
                if(held!=null&&held.id().equals(cargo)&&ProductionStorageLocator.valid(world,plot,target)) {
                    int delivered=production.deliver(state,cargo,WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,held.output()));
                    changed=delivered>0;
                    if(changed)com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"rancher").earnXp(delivered);
                }
            } else if(herd!=null&&production.cargo()==null&&RanchProducts.selected(family,state,entry).stream().anyMatch(option->space(state,entry,option))) {
                if(action==Action.FEED)changed=NativeHerdService.feed(world,town,plot,state,family);
                else {
                    var live=NativeHerdService.offspring(world,town,plot,herd);
                    if(live!=null&&st.getComponent(live,TransformComponent.getComponentType()).getPosition().distanceSquared(actual.getPosition())<=16) {
                        if(action==Action.MATURE)changed=NativeHerdService.mature(world,town,plot,state,family,animal);
                        else if(RanchProducts.selected(family,state,entry).contains(product)&&space(state,entry,product))changed=NativeHerdService.collect(world,town,plot,state,family,animal,product);
                    }
                }
            }
            if(changed){visit.attended.remove(key);town.getWarehouse().markProductionWithdrawal();manager.updateTown(town);}
        });
        visit.attempt=0;visit.retry=now+1000;stop(ref,store,npc,buffer,visit);return true;
    }
    private static boolean choose(World world,TownRecord town,PlotInstance plot,PlotProductionState state,ProductionCatalog.Entry entry,Visit v,long now) {
        v.failed.values().removeIf(until->until<=now);var p=state.herd();v.animal=null;v.family=null;
        if(p.cargo()!=null) {
            if(state.getAmount(p.cargo().output())>=WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,p.cargo().output()))return false;
            v.action=Action.DELIVER;v.cargo=p.cargo().id();v.key="delivery/"+v.cargo;v.target=ProductionStorageLocator.find(world,plot,now);return v.target!=null&&!v.failed.containsKey(v.key);
        }
        for(var herd:p.herds().values()) {
            String family=herd.pair().family();var products=RanchProducts.selected(family,state,entry).stream()
                .filter(product->com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"rancher",product.output())==null).toList();
            if(products.isEmpty()||products.stream().noneMatch(product->space(state,entry,product)))continue;
            var care=herd.care();v.family=family;v.animal=null;
            if(herd.hungry()&&p.feed()>0&&NativeHerdService.pairReady(world,town,plot,herd.pair())) {
                v.action=Action.FEED;v.target=herd.pair().trough();
            } else if(care.ready()&&herd.sinceFeed()<HerdProductionState.NEGLECT) {
                var animal=NativeHerdService.offspring(world,town,plot,herd);if(animal==null)continue;
                v.animal=care.offspring();v.target=NativeHerdService.point(world.getEntityStore().getStore().getComponent(animal,TransformComponent.getComponentType()).getPosition());
                v.action=care.grown()?Action.COLLECT:Action.MATURE;v.product=products.get(herd.productIndex(products.size()));
                if(v.action==Action.COLLECT&&!space(state,entry,v.product))continue;
            } else continue;
            v.key=family+"/"+v.action+"/"+v.animal;
            if(!v.failed.containsKey(v.key))return true;
        }
        return false;
    }
    private static boolean valid(World world,TownRecord town,PlotInstance plot,PlotProductionState state,ProductionCatalog.Entry entry,Visit v) {
        var p=state.herd();if(v.action==Action.DELIVER)return p.cargo()!=null&&p.cargo().id().equals(v.cargo)&&ProductionStorageLocator.valid(world,plot,v.target);
        var herd=p.herds().get(v.family);if(herd==null||p.cargo()!=null||RanchProducts.selected(v.family,state,entry).stream().noneMatch(option->space(state,entry,option)))return false;
        if(v.action==Action.FEED)return herd.hungry()&&p.feed()>0&&NativeHerdService.pairReady(world,town,plot,herd.pair());
        if(!Objects.equals(v.animal,herd.care().offspring())||!herd.care().ready()||herd.sinceFeed()>=HerdProductionState.NEGLECT)return false;
        var animal=NativeHerdService.offspring(world,town,plot,herd);if(animal==null)return false;
        if(world.getEntityStore().getStore().getComponent(animal,TransformComponent.getComponentType()).getPosition().distanceSquared(v.target.x()+.5,v.target.y(),v.target.z()+.5)>4)return false;
        return v.action==Action.MATURE?!herd.care().grown():RanchProducts.selected(v.family,state,entry).contains(v.product)
            &&com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issueForKind(town,"rancher",v.product.output())==null&&space(state,entry,v.product);
    }
    private static boolean space(PlotProductionState state,ProductionCatalog.Entry entry,RanchProducts.Product product){return WorkplaceProductionUpgrades.effectiveMaxStorage(state,entry,product.output())-state.getAmount(product.output())>=product.amount();}
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer){var v=VISITS.get(ref);if(v!=null){stop(ref,store,npc,buffer,v);v.lastTick=0;}}
    private static void stop(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer,Visit v) {
        if(v.active)NpcStandStill.release(ref,npc,buffer);
        if(v.visual)buffer.run(st->{if(ref.isValid()){VillagerLifeProps.clear(ref,st);AnimationUtils.stopAnimation(ref,AnimationSlot.Action,st);}});
        v.active=false;v.visual=false;v.approach=null;
    }
}
