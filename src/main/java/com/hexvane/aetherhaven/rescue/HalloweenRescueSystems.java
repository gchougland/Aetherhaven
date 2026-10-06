package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Rescue-only lifecycle. Unload is not death and cannot release a persisted encounter. */
public final class HalloweenRescueSystems extends RefSystem<EntityStore> {
    private static Query<EntityStore> rescues(){return Query.and(TownVillagerBinding.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
    @Override public Query<EntityStore> getQuery(){return rescues();}
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        buffer.run(st->HalloweenRescueEncounters.observe(ref,st));
    }
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        if(reason==RemoveReason.REMOVE||reason==RemoveReason.UNLOAD)HalloweenRescueEncounters.removed(ref,store,reason==RemoveReason.UNLOAD);
    }
    public static final class Deaths extends DeathSystems.OnDeathSystem {
        @Override public Query<EntityStore> getQuery(){return rescues();}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
            HalloweenRescueEncounters.removed(ref,store,false);
        }
    }
    public static final class Reconcile extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var ids=HalloweenRescueEncounters.due(store,System.nanoTime());if(ids.isEmpty())return;
            var world=store.getExternalData().getWorld();
            world.execute(()->{if(world.isAlive()&&!store.isShutdown())for(var id:ids)HalloweenRescueEncounters.reconcile(store,id);});
        }
    }
    public static final class Lifecycle extends StoreSystem<EntityStore> {
        @Override public void onSystemAddedToStore(Store<EntityStore> store) {}
        @Override public void onSystemRemovedFromStore(Store<EntityStore> store){HalloweenRescueEncounters.close(store);}
    }
}
