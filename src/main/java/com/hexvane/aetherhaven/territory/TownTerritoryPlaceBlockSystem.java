package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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

public final class TownTerritoryPlaceBlockSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private final AetherhavenPlugin plugin;

    public TownTerritoryPlaceBlockSystem(@Nonnull AetherhavenPlugin plugin) {
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
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Vector3i pos = event.getTargetBlock();
        TownRecord town = TownTerritoryGuard.findClaimTown(tm, world.getName(), pos);
        if (town == null) {
            return;
        }
        if (TownTerritoryGuard.shouldBypassPlayer(player, playerUuid, pos, store, ref)) {
            return;
        }
        if (!TownTerritoryGuard.playerMayPlace(town, playerUuid)) {
            event.setCancelled(true);
        }
    }
}
