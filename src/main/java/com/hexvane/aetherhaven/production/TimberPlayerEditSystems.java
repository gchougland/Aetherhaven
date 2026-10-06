package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Player edits invalidate a planting without cancelling the player's action or changing terrain. */
public final class TimberPlayerEditSystems {
    private TimberPlayerEditSystems() {}
    private static void mark(Store<EntityStore> store,AetherhavenPlugin plugin,TimberHarvestPlan.Position point) {
        var world=store.getExternalData().getWorld();var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
        var town=manager.findTownContainingBlock(world.getName(),point.x(),point.z());if(town==null)return;
        boolean changed=false;
        for(var plot:town.getPlotInstances()) {
            var production=town.peekPlotProduction(plot.getPlotId());
            if(production!=null)changed|=production.markGroveEdited(point);
        }
        for(var site:town.getApprenticeWorkplaces().sites())changed|=site.excavation().markEdited(new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(point.x(),point.y(),point.z()));
        if(changed)manager.updateTown(town);
    }
    public static final class Breaking extends EntityEventSystem<EntityStore,BreakBlockEvent> {
        private final AetherhavenPlugin plugin;
        public Breaking(AetherhavenPlugin plugin){super(BreakBlockEvent.class);this.plugin=plugin;}
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,BreakBlockEvent event) {
            var p=event.getTargetBlock();var point=new TimberHarvestPlan.Position(p.x,p.y,p.z);
            buffer.run(st->{if(!event.isCancelled())mark(st,plugin,point);});
        }
    }
    public static final class Placing extends EntityEventSystem<EntityStore,PlaceBlockEvent> {
        private final AetherhavenPlugin plugin;
        public Placing(AetherhavenPlugin plugin){super(PlaceBlockEvent.class);this.plugin=plugin;}
        @Override public Query<EntityStore> getQuery(){return Player.getComponentType();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,PlaceBlockEvent event) {
            var p=event.getTargetBlock();var point=new TimberHarvestPlan.Position(p.x,p.y,p.z);
            buffer.run(st->{if(!event.isCancelled())mark(st,plugin,point);});
        }
    }
}
