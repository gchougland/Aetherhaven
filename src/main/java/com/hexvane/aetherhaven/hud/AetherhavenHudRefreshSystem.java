package com.hexvane.aetherhaven.hud;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.time.AetherhavenGameTimeSubscriber;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Reconciles each player's display from read-only snapshots. This ticking system sends UI packets but never writes
 * to the entity Store, inventory, or town persistence.
 */
public final class AetherhavenHudRefreshSystem
    extends EntityTickingSystem<EntityStore>
    implements AetherhavenGameTimeSubscriber {
    private static final float PLAYER_VALUE_REFRESH_SECONDS = 0.5f;

    @Nonnull
    private final AetherhavenHudSnapshotService snapshots;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @Nonnull
    private final Map<UUID, Float> elapsedByPlayer = new ConcurrentHashMap<>();
    @Nonnull
    private final Map<UUID, Long> renderedRevisionByPlayer = new ConcurrentHashMap<>();
    @Nonnull
    private final Map<String, Long> worldRevision = new ConcurrentHashMap<>();

    public AetherhavenHudRefreshSystem(@Nonnull AetherhavenPlugin plugin) {
        snapshots = new AetherhavenHudSnapshotService(plugin);
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
            UUIDComponent.getComponentType(),
            PlayerTownJournalState.getComponentType()
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
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        PlayerTownJournalState preferences =
            chunk.getComponent(index, PlayerTownJournalState.getComponentType());
        if (player == null || playerRef == null || uuid == null || preferences == null) {
            return;
        }
        if (!preferences.isHudEnabled()) {
            if (AetherhavenHudSupport.isActive(player)) {
                AetherhavenHudSupport.remove(player, playerRef);
            }
            return;
        }

        World world = store.getExternalData().getWorld();
        String worldName = world.getName();
        long revision = worldRevision.getOrDefault(worldName, 0L);
        float elapsed = elapsedByPlayer.getOrDefault(uuid.getUuid(), PLAYER_VALUE_REFRESH_SECONDS) + Math.max(0f, dt);
        boolean timeChanged = renderedRevisionByPlayer.getOrDefault(uuid.getUuid(), -1L) != revision;
        if (!timeChanged && elapsed < PLAYER_VALUE_REFRESH_SECONDS) {
            elapsedByPlayer.put(uuid.getUuid(), elapsed);
            return;
        }
        elapsedByPlayer.put(uuid.getUuid(), 0f);
        renderedRevisionByPlayer.put(uuid.getUuid(), revision);

        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        TownManager townManager = AetherhavenWorldRegistries.getTownManager(world);
        TownRecord town = townManager.findTownForPlayerInWorld(uuid.getUuid());
        var worldNpcRegistry = AetherhavenWorldRegistries.getWorldNpcRegistry(world);
        var worldProgress =
            worldNpcRegistry != null ? worldNpcRegistry.findPlayerProgress(uuid.getUuid()) : null;
        AetherhavenHud hud = AetherhavenHudSupport.obtain(player, playerRef);
        hud.setStatusPlacement(
            placement(
                preferences.getHudStatusPlacement(),
                preferences.getHudStatusX(),
                preferences.getHudStatusY(),
                HudPlacement.TOP_RIGHT
            )
        );
        hud.setQuestPlacement(
            placement(
                preferences.getHudQuestPlacement(),
                preferences.getHudQuestX(),
                preferences.getHudQuestY(),
                HudPlacement.TOP_RIGHT
            )
        );
        hud.refresh(snapshots.capture(playerRef, store, time.getGameDateTime(), town, worldProgress, preferences));
    }

    @Override
    public void onSmoothGameMinuteAdvanced(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        markWorldTimeChanged(world);
    }

    @Override
    public void onGameTimeDiscontinuity(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull LocalDateTime toDateTime,
        boolean backward
    ) {
        markWorldTimeChanged(world);
    }

    public void clearPlayer(@Nonnull UUID playerUuid) {
        elapsedByPlayer.remove(playerUuid);
        renderedRevisionByPlayer.remove(playerUuid);
    }

    public void clearWorld(@Nonnull String worldName) {
        worldRevision.remove(worldName);
    }

    private void markWorldTimeChanged(@Nonnull World world) {
        worldRevision.merge(world.getName(), 1L, (left, right) -> left == Long.MAX_VALUE ? 0L : left + 1L);
    }

    @Nonnull
    static HudPanelPlacement placement(
        @Nonnull String rawPlacement,
        int x,
        int y,
        @Nonnull HudPlacement fallback
    ) {
        HudPlacement parsed;
        try {
            parsed = HudPlacement.valueOf(rawPlacement.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            parsed = fallback;
        }
        return new HudPanelPlacement(parsed, clampOffset(x), clampOffset(y));
    }

    private static int clampOffset(int value) {
        return Math.max(0, Math.min(4000, value));
    }
}
