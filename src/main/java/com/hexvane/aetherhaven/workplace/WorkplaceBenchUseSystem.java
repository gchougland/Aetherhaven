package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.territory.TownTerritoryUseBlockSystem;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Redirects only the matching primary benches inside registered workplaces. */
public final class WorkplaceBenchUseSystem extends EntityEventSystem<EntityStore,UseBlockEvent.Pre> {
    public WorkplaceBenchUseSystem(){super(UseBlockEvent.Pre.class);}
    @Override public Query<EntityStore> getQuery(){return Query.and(Player.getComponentType(),PlayerRef.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,TownTerritoryUseBlockSystem.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,UseBlockEvent.Pre event) {
        if(event.isCancelled()||event.getInteractionType()!=InteractionType.Use)return;
        var world=store.getExternalData().getWorld();
        var cauldron=com.hexvane.aetherhaven.alchemy.AlchemyCauldronAccess.find(world,event.getTargetBlock());
        if(cauldron!=null) {
            event.setCancelled(true);
            var ref=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
            if(com.hexvane.aetherhaven.alchemy.AlchemyCauldronAccess.allowed(cauldron,player.getUuid()))
                world.execute(()->{if(ref.isValid())com.hexvane.aetherhaven.alchemy.AlchemyCauldronAccess.open(ref,store,player,cauldron.position());});
            return;
        }
        var target=WorkplaceBenchAccess.find(world,event.getTargetBlock());
        if(target==null)return;
        event.setCancelled(true);
        var ref=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
        if(!WorkplaceBenchAccess.allowed(target,player.getUuid()))return;
        var pos=new org.joml.Vector3i(target.pos());
        world.execute(()->{if(ref.isValid())WorkplaceBenchAccess.open(ref,store,player,pos);});
    }
}
