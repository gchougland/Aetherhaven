package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Keeps move-destination particles alive until all ordered guards finish traveling. */
public final class RtsMoveOrderVisualSystem extends EntityTickingSystem<EntityStore> {
    private static final int REFRESH_TICKS = 4;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public RtsMoveOrderVisualSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(RtsCommandPlayerComponent.getComponentType(), Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // No session check: an order given with the Command Flag in hand has a marker too. Leaving the command post
        // clears its marker itself, so an inactive session with a destination can only be a flag order.
        RtsCommandPlayerComponent session = chunk.getComponent(index, RtsCommandPlayerComponent.getComponentType());
        if (session == null) {
            return;
        }
        UUIDComponent commanderUuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (commanderUuid == null) {
            return;
        }
        UUID commanderId = commanderUuid.getUuid();
        Ref<EntityStore> commanderRef = chunk.getReferenceTo(index);
        RtsMoveOrderVisuals.ActiveDestination destination = RtsMoveOrderVisuals.getActive(commanderId);
        if (destination == null) {
            return;
        }
        if (destination.expired(store.getExternalData().getWorld().getTick())) {
            RtsMoveOrderVisuals.clear(commanderId);
            return;
        }

        Iterator<UUID> it = destination.guardIds().iterator();
        while (it.hasNext()) {
            UUID guardId = it.next();
            Ref<EntityStore> guardRef = RtsGuardDirectory.findByUuid(store, guardId);
            if (guardRef == null) {
                it.remove();
                continue;
            }
            GuardRtsCommandState cmd = store.getComponent(guardRef, GuardRtsCommandState.getComponentType());
            if (cmd == null || cmd.getPhase() != RtsCommandPhase.TRAVELING) {
                it.remove();
            }
        }

        if (destination.guardIds().isEmpty()) {
            RtsMoveOrderVisuals.clear(commanderId);
            return;
        }

        long worldTick = store.getExternalData().getWorld().getTick();
        if (worldTick - destination.lastSpawnWorldTick() < REFRESH_TICKS) {
            return;
        }
        RtsMoveOrderVisuals.refreshSpawn(store, commanderRef, destination, worldTick);
    }
}
