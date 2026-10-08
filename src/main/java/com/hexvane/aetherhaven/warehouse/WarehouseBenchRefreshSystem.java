package com.hexvane.aetherhaven.warehouse;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Refresh only open linked crafting windows. Does not scan chunks or closed benches. */
public final class WarehouseBenchRefreshSystem extends EntityTickingSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        if((store.getExternalData().getWorld().getTick()+index)%15!=0)return;
        for(var window:chunk.getComponent(index,Player.getComponentType()).getWindowManager().getWindows())
            if(window instanceof WarehouseLinkedWindow && window instanceof BenchWindow bench)bench.invalidateExtraResources();
    }
}
