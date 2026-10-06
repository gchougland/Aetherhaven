package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3i;

/** All native entity/section reads stay on their source world. Receipt updates use compare-and-retire snapshots. */
public final class HalloweenRescueEncounters {
    private HalloweenRescueEncounters() {}
    private enum Presence { PRESENT, MISSING, UNLOADED }

    static boolean mayOpen(World world,Store<EntityStore> store,TownRecord town,Vector3i coffin,UUID player,java.time.Clock calendar) {
        var state=town.halloweenRescue();var saved=state.current();
        if(saved==null)return true;
        if(state.inFlight(saved))return false;
        // A reservation interrupted before an entity was bound has no entity to recover.
        if(saved.entity()==null) {boolean retired=state.retire(saved);if(retired)changed(town);return retired;}
        var source=Universe.get().getWorld(saved.world());
        if(source==null||!source.isAlive()) {
            // The old world cannot contain a live Jack. Its saved entity is fenced out if that world returns.
            boolean retired=state.retire(saved);if(retired)changed(town);return retired;
        }
        if(source==world) {
            if(presence(source,saved)==Presence.MISSING) {
                boolean retired=state.retire(saved);if(retired)changed(town);return retired;
            }
            waiting(store,player,saved);return false;
        }
        // Never wait on another world's executor or touch its Store from this thread.
        var at=new Vector3i(coffin);
        source.execute(()->{
            if(!source.isAlive()||source.getEntityStore().getStore().isShutdown())return;
            if(!saved.equals(state.current()))return;
            boolean absent=presence(source,saved)==Presence.MISSING;
            boolean retired=absent&&state.retire(saved);
            if(retired)changed(town);
            if(!world.isAlive())return;
            world.execute(()->{
                if(!world.isAlive()||store.isShutdown())return;
                var ref=player==null?null:store.getExternalData().getRefFromUUID(player);
                if(ref==null||!ref.isValid())return;
                var position=store.getComponent(ref,TransformComponent.getComponentType());
                if(position==null||position.getPosition().distanceSquared(at.x+.5,at.y+.5,at.z+.5)>256)return;
                if(retired&&RescueVillagerSpawnService.resolveTownForFieldRescue(player,
                        AetherhavenWorldRegistries.getTownManagerIfLoaded(world),RescueVillagerTriggers.HALLOWEEN)==town)
                    RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,at,player,calendar);
                else waiting(store,player,saved);
            });
        });
        return false;
    }

    private static Presence presence(World world,HalloweenRescueReceipt.Encounter saved) {
        var store=world.getEntityStore().getStore();
        var ref=store.getExternalData().getRefFromUUID(saved.entity());
        if(ref!=null&&ref.isValid())return Presence.PRESENT;
        var sectionRef=ChunkSectionBlockUtil.sectionRefAt(world,saved.x(),saved.y(),saved.z());
        if(sectionRef==null)return Presence.UNLOADED;
        var section=world.getChunkStore().getStore().getComponent(sectionRef,EntitySection.getComponentType());
        if(section==null)return Presence.UNLOADED;
        // Non-ticking sections retain parked holders even though getRefFromUUID returns null.
        for(var holder:section.getEntityHolders()) {
            var uuid=holder.getComponent(UUIDComponent.getComponentType());
            if(uuid!=null&&saved.entity().equals(uuid.getUuid()))return Presence.PRESENT;
        }
        for(var entity:section.getEntityReferences()) {
            if(entity==null||!entity.isValid())continue;
            var uuid=store.getComponent(entity,UUIDComponent.getComponentType());
            if(uuid!=null&&saved.entity().equals(uuid.getUuid()))return Presence.PRESENT;
        }
        return Presence.MISSING;
    }

    private static void waiting(Store<EntityStore> store,UUID player,HalloweenRescueReceipt.Encounter saved) {
        var ref=player==null?null:store.getExternalData().getRefFromUUID(player);
        var identity=ref==null||!ref.isValid()?null:store.getComponent(ref,PlayerRef.getComponentType());
        if(identity!=null)identity.sendMessage(Message.raw("Jack is already waiting at "+saved.x()+", "+saved.y()+", "+saved.z()+" in "+saved.world()+". Visit him to finish the rescue."));
    }

    static void observe(Ref<EntityStore> ref,Store<EntityStore> store) {
        if(ref==null||!ref.isValid())return;
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(binding==null||!HalloweenResident.RESCUE_KIND.equals(binding.getKind())||uuid==null||transform==null)return;
        track(store,uuid.getUuid());
        var town=AetherhavenWorldRegistries.getTownAcrossWorlds(binding.getTownId(),null);
        if(town==null)return; // Its owning world may not have loaded its towns yet.
        var world=store.getExternalData().getWorld();var p=transform.getPosition();var state=town.halloweenRescue();
        if(town.hasQuestCompleted(HalloweenResident.RESCUE_QUEST)
            ||town.getResidentNpcRecords().stream().anyMatch(r->r!=null&&HalloweenResident.KIND.equals(r.getKind()))) {
            if(state.current()!=null){state.complete();changed(town);}
            PendingEntityRemovalService.schedule(world,uuid.getUuid(),"halloween_completed_encounter");return;
        }
        var result=state.observe(uuid.getUuid(),world.getName(),floor(p.x),floor(p.y),floor(p.z));
        if(result==HalloweenRescueReceipt.Observation.REJECTED)
            PendingEntityRemovalService.schedule(world,uuid.getUuid(),"halloween_obsolete_encounter");
        else if(result!=HalloweenRescueReceipt.Observation.CURRENT)changed(town);
    }

    static void removed(Ref<EntityStore> ref,Store<EntityStore> store,boolean unload) {
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        var uuid=store.getComponent(ref,UUIDComponent.getComponentType());
        var transform=store.getComponent(ref,TransformComponent.getComponentType());
        if(binding==null||!HalloweenResident.RESCUE_KIND.equals(binding.getKind())||uuid==null)return;
        untrack(store,uuid.getUuid());
        var town=AetherhavenWorldRegistries.getTownAcrossWorlds(binding.getTownId(),null);if(town==null)return;
        var saved=town.halloweenRescue().current();if(saved==null)return;
        var p=transform==null?null:transform.getPosition();
        if(town.halloweenRescue().removed(uuid.getUuid(),store.getExternalData().getWorld().getName(),unload,
                p==null?saved.x():floor(p.x),p==null?saved.y():floor(p.y),p==null?saved.z():floor(p.z)))changed(town);
    }

    static void changed(TownRecord town) {
        var manager=AetherhavenWorldRegistries.townManagerForTown(town);
        var universe=Universe.get();var owner=universe==null?null:universe.getWorld(town.getWorldName());
        if(manager==null||owner==null||!owner.isAlive())return;
        Runnable save=()->{if(manager.getTown(town.getTownId())==town){manager.updateTown(town);TownSaveCoordinator.requestImmediateSave(manager);}};
        if(owner.isInThread())save.run();else owner.execute(save);
    }

    private static int floor(double value){return (int)Math.floor(value);}
    private static final class Tracked {final Set<UUID> ids=new LinkedHashSet<>();long next;}
    // Values contain identities only, so the weak keys do not retain unloaded stores through references.
    private static final Map<Store<EntityStore>,Tracked> TRACKED=new WeakHashMap<>();
    private static synchronized void track(Store<EntityStore> store,UUID id){TRACKED.computeIfAbsent(store,s->new Tracked()).ids.add(id);}
    private static synchronized void untrack(Store<EntityStore> store,UUID id){var t=TRACKED.get(store);if(t!=null)t.ids.remove(id);}
    static synchronized void close(Store<EntityStore> store){TRACKED.remove(store);}
    static synchronized List<UUID> due(Store<EntityStore> store,long now) {
        var t=TRACKED.get(store);if(t==null||now<t.next)return List.of();t.next=now+5_000_000_000L;return List.copyOf(t.ids);
    }
    static void reconcile(Store<EntityStore> store,UUID id) {
        var ref=store.getExternalData().getRefFromUUID(id);
        if(ref==null||!ref.isValid())untrack(store,id);else observe(ref,store);
    }
}
