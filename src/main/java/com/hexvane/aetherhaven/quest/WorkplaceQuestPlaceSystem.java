package com.hexvane.aetherhaven.quest;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Tells the quests when a player sets a block down in a town that is waiting for one. It never cancels or changes the
 * placement. The block does not stand yet when this event arrives, so the look at it is queued as a world task.
 */
public final class WorkplaceQuestPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    public WorkplaceQuestPlaceSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> buffer,
        @Nonnull PlaceBlockEvent event
    ) {
        var target = event.getTargetBlock();
        int x = target.x, y = target.y, z = target.z;
        World world = store.getExternalData().getWorld();
        // Almost every block a player places is in no town, or in a town that waits for nothing.
        if (!WorkplaceQuestHooks.watchesBlock(world, x, z)) {
            return;
        }
        buffer.run(ignored -> {
            if (!event.isCancelled()) {
                world.execute(() -> WorkplaceQuestHooks.blockPlaced(world, x, y, z));
            }
        });
    }
}
