package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.routevisual.RouteParticleConfig;
import com.hexvane.aetherhaven.routevisual.RouteParticleRenderer;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentLastKnownPositionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Renders a particle trail from the player toward their active villager locate target. */
public final class VillagerLocateTrailSystem extends EntityTickingSystem<EntityStore> {
    private static final double ARRIVAL_RADIUS = 3.0;
    private static final double ARRIVAL_RADIUS_SQ = ARRIVAL_RADIUS * ARRIVAL_RADIUS;
    private static final double MIN_TRAIL_DISTANCE = 0.5;
    private static final double MIN_TRAIL_DISTANCE_SQ = MIN_TRAIL_DISTANCE * MIN_TRAIL_DISTANCE;

    private static final ConcurrentHashMap<UUID, Integer> TICK_COUNTERS = new ConcurrentHashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    private final AetherhavenPlugin plugin;

    public VillagerLocateTrailSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(Player.getComponentType(), VillagerLocatePlayerComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        VillagerLocatePlayerComponent session = chunk.getComponent(index, VillagerLocatePlayerComponent.getComponentType());
        if (session == null || !session.isActive()) {
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUID playerUuid = pr.getUuid();
        if (playerUuid == null) {
            return;
        }
        int counter = TICK_COUNTERS.merge(playerUuid, 1, Integer::sum);
        if (counter < RouteParticleConfig.SELECTED.getTickRate()) {
            return;
        }
        TICK_COUNTERS.put(playerUuid, 0);

        UUID townId = session.getTownId();
        UUID targetUuid = session.getTargetEntityUuid();
        if (townId == null || targetUuid == null) {
            VillagerLocatePlayerComponent.clear(commandBuffer, store, playerRef);
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            VillagerLocatePlayerComponent.clear(commandBuffer, store, playerRef);
            return;
        }

        ResidentLastKnownPositionService.LocateTarget target =
            ResidentLastKnownPositionService.resolveLocateTarget(store, town, targetUuid);
        if (!target.isValid()) {
            pr.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateNoPosition")
            );
            VillagerLocatePlayerComponent.clear(commandBuffer, store, playerRef);
            return;
        }

        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }
        Vector3d playerPos = playerTransform.getPosition();
        Vector3d targetPos = target.getPosition();

        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= ARRIVAL_RADIUS_SQ) {
            handleArrival(pr, commandBuffer, store, playerRef, session, target.isLastKnown());
            return;
        }

        List<Ref<EntityStore>> audience = Collections.singletonList(playerRef);
        RouteParticleConfig config = RouteParticleConfig.SELECTED;
        if (distSq >= MIN_TRAIL_DISTANCE_SQ) {
            RouteParticleRenderer.renderTrailToward(
                world,
                store,
                new Vector3d(playerPos.x, playerPos.y, playerPos.z),
                targetPos,
                audience,
                config
            );
        }
        if (target.isLastKnown()) {
            RouteParticleRenderer.renderNodeMarkers(
                world,
                store,
                List.of(targetPos),
                audience,
                config
            );
        }
    }

    private void handleArrival(
        @Nonnull PlayerRef pr,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull VillagerLocatePlayerComponent session,
        boolean lastKnown
    ) {
        World world = store.getExternalData().getWorld();
        List<Ref<EntityStore>> audience = Collections.singletonList(playerRef);
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            Vector3d burst = new Vector3d(pos.x, pos.y + 1.0, pos.z);
            ParticleUtil.spawnParticleEffect(
                RouteParticleConfig.SELECTED.getNodeParticleId(),
                burst,
                audience,
                store
            );
        }
        String name = session.getTargetLabel();
        if (lastKnown) {
            pr.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateArrivedLastKnown")
                    .param("name", name)
            );
        } else {
            pr.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.locateArrived")
                    .param("name", name)
            );
        }
        VillagerLocatePlayerComponent.clear(commandBuffer, store, playerRef);
        @Nullable UUID uuid = pr.getUuid();
        if (uuid != null) {
            TICK_COUNTERS.remove(uuid);
        }
    }
}
