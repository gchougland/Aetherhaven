package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;

/** Refreshes flask display metadata without changing existing mixtures or charges. */
public final class AlchemyFlaskInventoryNormalizeSystem extends EntityEventSystem<EntityStore,InventoryChangeEvent> {
    public AlchemyFlaskInventoryNormalizeSystem(){super(InventoryChangeEvent.class);}
    @Override public void handle(int index,@Nonnull ArchetypeChunk<EntityStore> chunk,@Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commands,@Nonnull InventoryChangeEvent event) {
        normalize(chunk.getReferenceTo(index),store);
    }
    private static void normalize(Ref<EntityStore> player,Store<EntityStore> store) {
        var inventory=InventoryComponent.getCombined(store,player,InventoryComponent.EVERYTHING);
        if(inventory==null)return;
        for(short slot=0;slot<inventory.getCapacity();slot++) {
            var flask=inventory.getItemStack(slot);
            if(AlchemyFlask.needsDisplayRefresh(flask))
                AlchemyInventory.exchange(inventory,slot,flask,AlchemyFlask.refreshDisplay(flask),Map.of());
        }
    }
    @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
    public static final class PlayerAdded extends RefSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void onEntityAdded(@Nonnull Ref<EntityStore> ref,@Nonnull AddReason reason,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> commands) {
            store.getExternalData().getWorld().execute(()->{if(ref.isValid())normalize(ref,store);});
        }
        @Override public void onEntityRemove(@Nonnull Ref<EntityStore> ref,@Nonnull RemoveReason reason,@Nonnull Store<EntityStore> store,@Nonnull CommandBuffer<EntityStore> commands) {}
    }
}
