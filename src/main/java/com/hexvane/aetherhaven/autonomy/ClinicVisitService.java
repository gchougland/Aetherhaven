package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.clinic.*;
import com.hexvane.aetherhaven.npc.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.lang.ref.WeakReference;
import java.util.*;
import org.joml.Vector3d;

/** Voluntary civilian visits, using native travel and no saved entity ownership or chunk loads. */
public final class ClinicVisitService {
    private ClinicVisitService() {}
    private static final Map<Ref<EntityStore>,Visit> VISITS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Visit {
        boolean active, treating;
        float health=Float.NaN;
        double quiet, travel, step;
        long checked, retry, lastTick;
        UUID clinic;
        Vector3d destination;
        WeakReference<Ref<EntityStore>> doctor=new WeakReference<>(null);
    }
    public static boolean active(Ref<EntityStore> ref) {
        var v=VISITS.get(ref); return v!=null && v.active;
    }
    public static String diagnostic(Ref<EntityStore> ref) {
        var v=VISITS.get(ref);
        return v==null?"none":"active="+v.active+" treating="+v.treating+" quiet="+(int)v.quiet+" destination="+v.destination;
    }
    /** Observe before other activities claim autonomy, so quiet time survives an ordinary conversation or emote. */
    public static void observe(float dt,Ref<EntityStore> ref,Store<EntityStore> store) {
        var v=VISITS.computeIfAbsent(ref,ignored->new Visit());
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        String state=NpcSupportUtil.stateName(store,ref);
        long now=System.currentTimeMillis();
        v.step=v.lastTick==0||now-v.lastTick>2000?0:Math.clamp(Float.isFinite(dt)?dt:0,0,.25);
        if(now-v.lastTick>2000) v.quiet=0;
        v.lastTick=now;
        if(hp!=null) {
            v.quiet=ClinicVisitPolicy.advanceQuiet(v.quiet,v.step,
                state!=null&&state.contains("Combat") || !Float.isNaN(v.health)&&hp.get()<v.health);
            v.health=hp.get();
        }
    }
    public static boolean tick(float dt,Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            NPCEntity npc,TownVillagerBinding binding,VillagerNeeds needs,VillagerAutonomyState autonomy,
            TownRecord town,AetherhavenPlugin plugin) {
        var v=VISITS.computeIfAbsent(ref,ignored->new Visit());
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        String state=NpcSupportUtil.stateName(store,ref);
        long now=System.currentTimeMillis();
        double step=v.step;
        boolean danger=state!=null&&(state.contains("Combat")||state.contains("Interaction"));
        if(town==null||uuid==null||!TownAdmission.isMember(town,uuid.getUuid())||hp==null||hp.get()<=0||transform==null
            ||danger||store.getComponent(ref,DeathComponent.getComponentType())!=null||needs.getHunger()<20||needs.getEnergy()<20
            ||autonomy.isFillingHunger()||autonomy.isFillingEnergy()) { interrupt(ref,store,npc,buffer); return false; }
        var clinic=town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(),ClinicCareSystem.CONSTRUCTION);
        if(clinic==null) { interrupt(ref,store,npc,buffer); return false; }
        if(ClinicCareSystem.KIND.equals(binding.getKind())) {
            var patient=ClinicCareSystem.currentPatient(ref,store);
            if(patient==null || ClinicCareSystem.availability(town,clinic,ref,store)!=ClinicCareSystem.Availability.READY) {
                interrupt(ref,store,npc,buffer); return false;
            }
            if(!v.active) begin(ref,store,buffer,npc,needs,autonomy,plugin,v);
            if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
            NpcStandStill.hold(ref,store,npc,transform.getPosition(),buffer);
            var target=store.getComponent(patient,TransformComponent.getComponentType());
            if(target!=null) face(ref,store,buffer,transform.getPosition(),target.getPosition());
            if(!v.treating) {
                v.treating=true;
                buffer.run(s->{if(ref.isValid())AnimationUtils.playAnimation(ref,AnimationSlot.Action,"Aetherhaven_Clinic_Care","Treat",false,s);});
            }
            return true;
        }
        if(!ClinicVisitPolicy.needsVisit(hp.get(),hp.getMax(),v.active,v.quiet)) { interrupt(ref,store,npc,buffer); return false; }
        var identity=store.getComponent(ref,com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType());
        if(identity!=null && town.getWarehouse().batches().stream().anyMatch(b->identity.getCharacterId().equals(b.courierId()))) {
            interrupt(ref,store,npc,buffer); return false;
        }
        var world=store.getExternalData().getWorld();
        var doctor=v.doctor.get();
        if(v.active && ClinicCareSystem.availability(town,clinic,doctor,store)!=ClinicCareSystem.Availability.READY) {
            interrupt(ref,store,npc,buffer); v.retry=now+5000; return false;
        }
        if(v.active && store.getComponent(doctor,TransformComponent.getComponentType()).getPosition().distanceSquared(v.destination)>36) {
            interrupt(ref,store,npc,buffer);v.retry=now+3000;return false;
        }
        if(!v.active) {
            if(now<v.retry||now<v.checked) return false;
            v.checked=now+3000;
            var nearby=new ArrayList<Ref<EntityStore>>();
            store.getResource(NPCPlugin.get().getNpcSpatialResource()).getSpatialStructure().collect(transform.getPosition(),96,nearby);
            doctor=nearby.stream().filter(other->{
                if(!other.isValid()||other.equals(ref))return false;
                var b=store.getComponent(other,TownVillagerBinding.getComponentType());
                return b!=null&&ClinicCareSystem.KIND.equals(b.getKind())&&town.getTownId().equals(b.getTownId())
                    &&ClinicCareSystem.availability(town,clinic,other,store)==ClinicCareSystem.Availability.READY;
            }).findFirst().orElse(null);
            if(doctor==null)return false;
            var docPos=store.getComponent(doctor,TransformComponent.getComponentType()).getPosition();
            Vector3d target=null; double best=Double.MAX_VALUE;
            List<Visit> reservations;
            synchronized(VISITS) { reservations=new ArrayList<>(VISITS.values()); }
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-1;dy<=1;dy++) {
                int x=(int)Math.floor(docPos.x)+dx,y=(int)Math.floor(docPos.y)+dy,z=(int)Math.floor(docPos.z)+dz;
                if(dx*dx+dz*dz<3||dx*dx+dz*dz>9||!clinic.containsWorldBlock(x,y,z)||!VillagerBlockUtil.isNpcStandColumn(world,x,y,z))continue;
                var p=new Vector3d(x+.5,VillagerBlockUtil.resolveFeetYForStandCell(world,x,y,z),z+.5);
                if(reservations.stream().anyMatch(other->other.active&&clinic.getPlotId().equals(other.clinic)
                    &&other.destination!=null&&other.destination.distanceSquared(p)<1))continue;
                double score=transform.getPosition().distanceSquared(p)+Math.abs(p.y-docPos.y)*10;
                if(score<best) {best=score;target=p;}
            }
            if(target==null)return false;
            begin(ref,store,buffer,npc,needs,autonomy,plugin,v);
            v.doctor=new WeakReference<>(doctor);v.clinic=clinic.getPlotId();v.destination=target;v.travel=0;
        }
        if(store.getComponent(ref,com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType())!=null)return true;
        v.travel+=step;
        if(!CourierWorkService.clearApproach(world,v.destination)||v.travel>ClinicVisitPolicy.TRAVEL_SECONDS) {
            interrupt(ref,store,npc,buffer);v.retry=now+30000;return false;
        }
        if(CourierWorkService.arrived(transform.getPosition(),v.destination)) {
            v.travel=0;
            NpcStandStill.hold(ref,store,npc,v.destination,buffer);
            var doc=store.getComponent(doctor,TransformComponent.getComponentType());
            if(doc!=null)face(ref,store,buffer,transform.getPosition(),doc.getPosition());
        } else {
            npc.setLeashPoint(new Vector3d(v.destination));VillagerAutonomySystem.applyAutonomyRoleState(ref,npc,buffer);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(world,transform.getPosition(),v.destination,null);
            buffer.putComponent(ref,NPCEntity.getComponentType(),npc);
        }
        return true;
    }
    private static void begin(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,NPCEntity npc,
            VillagerNeeds needs,VillagerAutonomyState autonomy,AetherhavenPlugin plugin,Visit v) {
        MiningWorkService.interrupt(ref,store,npc,buffer);TimberWorkService.interrupt(ref,store,npc,buffer);FieldWorkService.interrupt(ref,store,npc,buffer);ShopClerkWorkService.interrupt(ref,store,npc,buffer);
        ApprenticeWorkService.interrupt(ref,store,npc,buffer);CourierWorkService.interrupt(ref,store,npc,buffer);
        VillagerAutonomySystem.abortActivePoiUseAndDismount(ref,store,buffer,autonomy,needs,
            AetherhavenWorldRegistries.getOrCreatePoiRegistry(store.getExternalData().getWorld(),plugin),false);
        autonomy.setPhase(VillagerAutonomyState.PHASE_IDLE);autonomy.clearTravelAndPoiState();
        buffer.putComponent(ref,VillagerAutonomyState.getComponentType(),autonomy);v.active=true;
    }
    private static void face(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Vector3d from,Vector3d to) {
        PoiAutonomyVisuals.applyBodyYaw(ref,store,buffer,(float)(Math.atan2(to.x-from.x,to.z-from.z)+Math.PI));
    }
    public static void interrupt(Ref<EntityStore> ref,Store<EntityStore> store,NPCEntity npc,CommandBuffer<EntityStore> buffer) {
        var v=VISITS.get(ref);if(v==null||!v.active)return;
        NpcStandStill.release(ref,npc,buffer);
        if(v.treating)buffer.run(s->{if(ref.isValid())AnimationUtils.stopAnimation(ref,AnimationSlot.Action,s);});
        v.active=false;v.treating=false;v.destination=null;v.clinic=null;v.doctor.clear();
    }
}
