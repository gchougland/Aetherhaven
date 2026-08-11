package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.territory.TownTerritoryGuard;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Stops players building on the festival square, since the next festival swap would wipe it out anyway. */
public final class FestivalSquarePlaceBlockSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private final AetherhavenPlugin plugin;

    public FestivalSquarePlaceBlockSystem(@Nonnull AetherhavenPlugin plugin) {
        super(PlaceBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlaceBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        UUID playerUuid = TownTerritoryGuard.resolvePlayerUuid(ref, store);
        if (player == null || playerUuid == null) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        World world = store.getExternalData().getWorld();
        if (!FestivalPlotProtection.isInsideFestivalSquare(plugin, world, pos)) {
            return;
        }
        if (FestivalPlotProtection.isBuildAllowed(playerUuid)) {
            return;
        }
        event.setCancelled(true);
        FestivalPlotProtection.warn(store, ref, playerUuid);
    }
}
