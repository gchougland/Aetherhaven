package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.territory.TownTerritoryUseBlockSystem;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3i;

public final class HalloweenGameSystems {
    private HalloweenGameSystems() {}
    public static final class Use extends EntityEventSystem<EntityStore,UseBlockEvent.Pre> {
        public Use() { super(UseBlockEvent.Pre.class); }
        @Override public Query<EntityStore> getQuery() { return Player.getComponentType(); }
        @Override public Set<Dependency<EntityStore>> getDependencies() { return Set.of(new SystemDependency<>(Order.AFTER,TownTerritoryUseBlockSystem.class)); }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,UseBlockEvent.Pre event) {
            if (event.isCancelled() || event.getInteractionType()!=InteractionType.Use || !HalloweenGameService.BLOCK.equals(event.getBlockType().getId())) return;
            event.setCancelled(true);
            var ref=chunk.getReferenceTo(index);var at=new Vector3i(event.getTargetBlock());
            store.getExternalData().getWorld().execute(()->{ if(ref.isValid())HalloweenGameService.pickup(ref,store,at); });
        }
    }
    public static final class Break extends EntityEventSystem<EntityStore,BreakBlockEvent> {
        public Break() { super(BreakBlockEvent.class); }
        @Override public Query<EntityStore> getQuery() { return Player.getComponentType(); }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,BreakBlockEvent event) {
            if(HalloweenGameService.BLOCK.equals(event.getBlockType().getId()))event.setCancelled(true);
        }
    }
    /** World-scoped weak timers retain no unloaded worlds. No chunk loading or per-tick entity scan. */
    public static final class Reconcile extends TickingSystem<EntityStore> {
        private final Map<World,Long> next = Collections.synchronizedMap(new WeakHashMap<>());
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var world=store.getExternalData().getWorld();long now=System.nanoTime();
            if(now<next.getOrDefault(world,0L))return;
            next.put(world,now+5_000_000_000L);
            world.execute(()->{
                var plugin=AetherhavenPlugin.get();if(plugin==null||!world.isAlive()||store.isShutdown())return;
                var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
                HalloweenGameService.reconcileRetired(world,manager);
                for(var town:manager.allTowns())HalloweenGameService.reconcile(world,store,manager,town);
            });
        }
    }
}
