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
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Stops players digging up the festival square. Crops and other gatherable blocks can still be harvested. */
public final class FestivalSquareBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public FestivalSquareBreakBlockSystem(@Nonnull AetherhavenPlugin plugin) {
        super(BreakBlockEvent.class);
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
        @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled() || TownTerritoryGuard.isHarvestStyleBreak(event.getBlockType())) {
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
