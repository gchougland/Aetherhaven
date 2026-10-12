package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Player edits never cancel the player's action. Breaking a Spirit Planter frees it from its Conservatory, and the
 * plant above it goes with it because it only stands on a planter.
 */
public final class SpiritGardenEditSystems {
    private SpiritGardenEditSystems() {}

    public static final class Breaking extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final AetherhavenPlugin plugin;
        public Breaking(AetherhavenPlugin plugin) { super(BreakBlockEvent.class); this.plugin = plugin; }
        @Override public Query<EntityStore> getQuery() { return Player.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            if (!SpiritGarden.is(event.getBlockType().getId())) return;
            var p = event.getTargetBlock(); var point = new Point(p.x, p.y, p.z);
            buffer.run(st -> { if (!event.isCancelled()) forget(st, plugin, point); });
        }
    }

    private static void forget(Store<EntityStore> store, AetherhavenPlugin plugin, Point point) {
        var world = store.getExternalData().getWorld(); var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.findTownContainingBlock(world.getName(), point.x(), point.z()); if (town == null) return;
        boolean changed = false;
        for (var plot : town.getPlotInstances()) {
            var production = town.peekPlotProduction(plot.getPlotId());
            if (production != null) changed |= production.uprootPlanterAt(point);
        }
        if (changed) manager.updateTown(town);
    }
}
