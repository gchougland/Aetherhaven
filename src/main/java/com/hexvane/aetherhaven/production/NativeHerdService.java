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
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Displays active owned animals at authored or compatible legacy pens; care and output share the daily clock. */
public final class NativeHerdService {
    private static final HytaleLogger LOGGER=HytaleLogger.forEnclosingClass();
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
    /**
     * The display must name its animal and be unsaved before it enters the world. Tagging it afterwards left a
     * moment in which the engine could park or save an ordinary, untracked animal.
     */
    public static void prepareDisplay(Holder<EntityStore> holder,BarnAnimalBinding binding,ComponentRegistry<EntityStore> registry) {
        holder.putComponent(BarnAnimalBinding.getComponentType(),binding);
        holder.ensureComponent(registry.getNonSerializedComponentType());
    }
    private static boolean sectionActive(World world,Vector3i point) {
        var chunks=world.getChunkStore();var section=chunks.getChunkSectionReferenceAtBlock(point.x,point.y,point.z);
        return section!=null&&section.isValid()
            &&!chunks.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }
    /** Removes barn display animals that no town record accounts for. Returns how many were removed. */
    public static int removeStrays(Store<EntityStore> store,ComponentType<EntityStore,NPCEntity> npcType) {
        var strays=new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(npcType,(chunk,buffer)->{
            boolean bound=chunk.getArchetype().contains(BarnAnimalBinding.getComponentType());
            for(int i=0;i<chunk.size();i++)
                if(HerdDisplayPlan.stray(chunk.getComponent(i,npcType).getRoleName(),bound))strays.add(chunk.getReferenceTo(i));
        });
        int removed=0;
        for(var ref:strays)if(ref.isValid()){store.removeEntity(ref,RemoveReason.REMOVE);removed++;}
        if(removed>0)LOGGER.atInfo().log("Removed %s stray barn display animals that no owned animal accounts for",removed);
        return removed;
    }
    public static Set<UUID> sync(World world,AetherhavenPlugin plugin,TownRecord town,PlotInstance plot) {
        var state=town.getOrCreatePlotProduction(plot.getPlotId());var herd=state.herd();
        var store=world.getEntityStore().getStore();int slots=WorkplaceProductionUpgrades.slotCount(state);
        var positions=BarnPenCompatibility.resolve(world,plugin,plot,herd);
        var shown=new ArrayList<HerdDisplayPlan.Shown<Ref<EntityStore>>>();
        store.forEachChunk(BarnAnimalBinding.getComponentType(),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var binding=chunk.getComponent(i,BarnAnimalBinding.getComponentType());
                shown.add(new HerdDisplayPlan.Shown<>(chunk.getReferenceTo(i),binding.plot,binding.animal));
            }
        });
        var plan=HerdDisplayPlan.reconcile(plot.getPlotId(),shown,id->{
            int slot=herd.assignedSlot(id);return slot<slots&&positions.containsKey(slot)?slot:-1;
        });
        for(var ref:plan.remove())if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
        var existing=plan.keep();
        var present=new HashSet<UUID>();
        for(int slot=0;slot<slots;slot++) {
            var animal=herd.slot(slot);var pen=positions.get(slot);var point=BarnPenCompatibility.worldPoint(plot,pen);
            if(animal==null||point==null||!plot.containsWorldBlock(point.x,point.y,point.z)
                ||ChunkSectionBlockUtil.worldChunkIfTicking(world,point.x,point.z)==null)continue;
            var species=BarnAnimalCatalog.get(animal.species());if(species==null)continue;
            if(pen.automatic()&&BarnPenCompatibility.space(world,plot,point)!=BarnPenCompatibility.Space.CLEAR)continue;
            float yaw=com.hexvane.aetherhaven.construction.PrefabYaw.worldFromPrefabLocal(plot.resolvePrefabYaw(),(float)Math.toRadians(pen.yaw()));
            var position=new Vector3d(point.x+.5,point.y,point.z+.5);
            var ref=existing.get(animal.id());
            if(ref==null||!ref.isValid()) {
                // A ticking column can still hold an inactive section. The engine parks whatever is added there
                // and reports no entity, so every later attempt would leave one more animal behind.
                if(!sectionActive(world,point))continue;
                int role=NPCPlugin.get().getIndex(species.role());if(role<0)continue;
                var binding=new BarnAnimalBinding(town.getTownId(),plot.getPlotId(),animal.id());
                var spawned=NPCPlugin.get().spawnEntity(store,role,position,new Rotation3f(0,yaw,0),null,
                    (npc,holder,accessor)->prepareDisplay(holder,binding,store.getRegistry()),null);
                if(spawned==null||!spawned.first().isValid())continue;ref=spawned.first();
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
        if(made>0){com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.progress(town,"rancher").earnXp(com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.ranchXp(made));town.getWarehouse().markProductionWithdrawal();}
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
        // Copies saved by older builds load back as ordinary animals with no binding; clear them as they appear.
        removeStrays(store,NPCEntity.getComponentType());
        for(var town:manager.allTowns()) {
            if(!world.getName().equals(town.getWorldName()))continue;
            boolean changed=false;
            for(var plot:town.getPlotInstances())if(barn(plugin,plot)){update(world,plugin,town,plot);changed=true;}
            if(changed)manager.updateTown(town);
        }
    }
}
