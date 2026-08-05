package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.ui.TouristPortalTravelPage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Opens the visitor portal travel UI when a player steps onto an active portal platform. */
public final class TouristPortalPlayerStandSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public TouristPortalPlayerStandSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            TouristPortalTravelPlayerState.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.COMMERCE)) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        TouristPortalTravelPlayerState travelState =
            chunk.getComponent(index, TouristPortalTravelPlayerState.getComponentType());
        if (player == null || playerRef == null || transform == null || travelState == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        Vector3d feet = transform.getPosition();
        int probeX = (int) Math.floor(feet.x);
        int probeZ = (int) Math.floor(feet.z);
        boolean wasOnPortal = travelState.wasOnPortal();
        if (!wasOnPortal && travelState.sameProbeColumn(probeX, probeZ)) {
            return;
        }

        TouristPortalRecord onPortal = TouristPortalTravelService.resolvePortalAtPlayerFeet(world, plugin, feet);
        travelState.setLastProbeBlock(probeX, probeZ);
        boolean onPortalNow = onPortal != null;

        if (onPortalNow && !wasOnPortal && player.getPageManager().getCustomPage() == null) {
            UUID portalId = onPortal.getPortalId();
            player.getPageManager()
                .openCustomPage(ref, store, new TouristPortalTravelPage(playerRef, portalId));
        }

        if (onPortalNow != wasOnPortal) {
            travelState.setWasOnPortal(onPortalNow);
            commandBuffer.putComponent(ref, TouristPortalTravelPlayerState.getComponentType(), travelState);
        }
    }
}
