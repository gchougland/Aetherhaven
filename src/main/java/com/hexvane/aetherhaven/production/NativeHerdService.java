package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Displays only active owned animals at authored pen markers; care and output use the same daily clock. */
public final class NativeHerdService {
    public static final String PEN_TAG="BARN_PEN";
    private NativeHerdService() {}
    public static boolean barn(AetherhavenPlugin plugin,PlotInstance plot) {
        return plot!=null&&plot.getState()==PlotInstanceState.COMPLETE&&plugin.getConstructionCatalog()
            .matchesGameplayConstruction(plot.getConstructionId(),AetherhavenConstants.CONSTRUCTION_PLOT_BARN);
    }
    public static long minute(Store<EntityStore> store) {
        var time=store.getResource(WorldTimeResource.getResourceType());
        var dt=time.getGameDateTime();
        return dt.toLocalDate().toEpochDay()*1440+dt.toLocalTime().toSecondOfDay()/60-WorldTimeResource.SUNRISE_SECONDS/60;
    }
    public static List<BuildingPoisDefinition.PoiRow> pens(AetherhavenPlugin plugin,PlotInstance plot) {
        var def=plugin.getConstructionCatalog().get(plot.getConstructionId());
        return def==null?List.of():def.getPois().stream().filter(p->p.getTags().contains(PEN_TAG)).limit(HerdProductionState.MAX_SLOTS).toList();
    }
    public static Vector3i penPoint(AetherhavenPlugin plugin,PlotInstance plot,int slot) {
        var pens=pens(plugin,plot);if(slot<0||slot>=pens.size()||!plot.hasStoredPrefabWorldAnchor())return null;
        var pen=pens.get(slot);
        return plot.getStoredPrefabWorldAnchor().add(PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),pen.getLocalX(),pen.getLocalY(),pen.getLocalZ()));
    }
    public static Ref<EntityStore> displayed(Store<EntityStore> store,UUID animal) {
        var found=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(BarnAnimalBinding.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++)if(animal.equals(chunk.getComponent(i,BarnAnimalBinding.getComponentType()).animal))found.add(chunk.getReferenceTo(i));
        });
        return found.isEmpty()?null:found.getFirst();
    }
    public static Set<UUID> sync(World world,AetherhavenPlugin plugin,TownRecord town,PlotInstance plot) {
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var herd=state.herd();
        var store=world.getEntityStore().getStore();int slots=WorkplaceProductionUpgrades.slotCount(state);
        var existing=new HashMap<UUID,Ref<EntityStore>>();var remove=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(BarnAnimalBinding.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var binding=chunk.getComponent(i,BarnAnimalBinding.getComponentType());if(!plot.getPlotId().equals(binding.plot))continue;
                var ref=chunk.getReferenceTo(i);int slot=herd.assignedSlot(binding.animal);
                if(slot<0||slot>=slots||penPoint(plugin,plot,slot)==null||existing.putIfAbsent(binding.animal,ref)!=null)remove.add(ref);
            }
        });
        for(var ref:remove)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
        var present=new HashSet<UUID>();
        for(int slot=0;slot<slots;slot++) {
            var animal=herd.slot(slot);var point=penPoint(plugin,plot,slot);
            if(animal==null||point==null||!plot.containsWorldBlock(point.x,point.y,point.z)
                ||ChunkSectionBlockUtil.worldChunkIfTicking(world,point.x,point.z)==null)continue;
            var species=BarnAnimalCatalog.get(animal.species());if(species==null)continue;
            var pen=pens(plugin,plot).get(slot);
            float yaw=com.hexvane.aetherhaven.construction.PrefabYaw.worldFromPrefabLocal(plot.resolvePrefabYaw(),(float)Math.toRadians(pen.getInteractionTargetYawDegrees()==null?0:pen.getInteractionTargetYawDegrees()));
            var position=new Vector3d(point.x+.5,point.y,point.z+.5);
            var ref=existing.get(animal.id());
            if(ref==null||!ref.isValid()) {
                var spawned=NPCPlugin.get().spawnNPC(store,species.role(),null,position,new Rotation3f(0,yaw,0));
                if(spawned==null)continue;ref=spawned.first();
                store.putComponent(ref,BarnAnimalBinding.getComponentType(),new BarnAnimalBinding(town.getTownId(),plot.getPlotId(),animal.id()));
                store.ensureComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType());
                store.ensureComponent(ref,Frozen.getComponentType());
                // Frozen skips the walking controller, including its initial movement flags.
                // All-false flags otherwise make the client choose a moving animation.
                var idle=new com.hypixel.hytale.protocol.MovementStates();
                idle.idle=true;idle.horizontalIdle=true;idle.onGround=true;
                var movement=new com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent();
                movement.setMovementStates(idle);
                store.putComponent(ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType(),movement);
                com.hypixel.hytale.server.core.entity.AnimationUtils.playAnimation(ref,com.hypixel.hytale.protocol.AnimationSlot.Movement,"Idle",store);
                store.putComponent(ref,Invulnerable.getComponentType(),Invulnerable.INSTANCE);
                store.putComponent(ref,Interactable.getComponentType(),Interactable.INSTANCE);
                var interactions=new Interactions(Map.of(InteractionType.Use,"Aetherhaven_Barn_Pet"));
                interactions.setInteractionHint("aetherhaven_barn.barn.petHint");
                store.putComponent(ref,Interactions.getComponentType(),interactions);
            }
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform!=null){transform.setPosition(position);transform.setRotation(new Rotation3f(0,yaw,0));}
            var nameplate=store.getComponent(ref,Nameplate.getComponentType());
            if(nameplate==null||!animal.name().equals(nameplate.getText())) {
                var name=Message.raw(animal.name());
                store.putComponent(ref,PersistentDisplayName.getComponentType(),new PersistentDisplayName(name));
                store.putComponent(ref,DisplayNameComponent.getComponentType(),new DisplayNameComponent(name));
                store.putComponent(ref,Nameplate.getComponentType(),new Nameplate(animal.name()));
            }
            present.add(animal.id());
        }
        return present;
    }
    public static void update(World world,AetherhavenPlugin plugin,TownRecord town,PlotInstance plot) {
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var herd=state.herd();
        var present=sync(world,plugin,town,plot);int slots=WorkplaceProductionUpgrades.slotCount(state);
        long minute=minute(world.getEntityStore().getStore()),day=Math.floorDiv(minute,1440);
        long before=state.getAmounts().values().stream().mapToLong(Long::longValue).sum();
        herd.advance(minute,slots,present,state,WorkplaceProductionUpgrades.barnCapacity(state),1.0);
        for(UUID id:present)herd.feedAnimal(id,day,slots);
        long made=state.getAmounts().values().stream().mapToLong(Long::longValue).sum()-before;
        if(made>0){com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"rancher").earnXp((int)Math.min(100,made));town.getWarehouse().markProductionWithdrawal();}
    }
    public static void advance(World world,AetherhavenPlugin plugin,long minutes) {
        if(minutes<=0)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        // Remove only our temporary displays when their plot or ownership has gone away.
        var store=world.getEntityStore().getStore();var stale=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(BarnAnimalBinding.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var binding=chunk.getComponent(i,BarnAnimalBinding.getComponentType());var town=manager.getTown(binding.town);
                if(town==null||!barn(plugin,town.findPlotById(binding.plot)))stale.add(chunk.getReferenceTo(i));
            }
        });
        for(var ref:stale)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
        for(var town:manager.allTowns()) {
            if(!world.getName().equals(town.getWorldName()))continue;
            boolean changed=false;
            for(var plot:town.getPlotInstances())if(barn(plugin,plot)){update(world,plugin,town,plot);changed=true;}
            if(changed)manager.updateTown(town);
        }
    }
}
