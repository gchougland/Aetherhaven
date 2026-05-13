package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class FloatingGiftSchedulerSystem extends EntityTickingSystem<EntityStore> {
    private static final float CHECK_INTERVAL_SEC = 2.0f;
    private float checkTimer = 0.0f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(PlayerRef.getComponentType(), UUIDComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        checkTimer += dt;
        if (checkTimer < CHECK_INTERVAL_SEC) {
            return;
        }
        checkTimer = 0.0f;
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        if (!cfg.isFloatingGiftEnabled()) {
            return;
        }
        if (FloatingGiftSystem.countActiveGifts(store) >= cfg.getFloatingGiftMaxActivePerWorld()) {
            return;
        }
        UUIDComponent uuid = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (uuid == null || transform == null) {
            return;
        }
        Instant now = wtr.getGameTime();
        UUID playerUuid = uuid.getUuid();
        Instant next = FloatingGiftSpawnSchedule.ensureAndGet(playerUuid, now, cfg);
        if (now.isBefore(next)) {
            return;
        }
        if (!FloatingGiftSpawnService.isModelAssetRegistered()) {
            FloatingGiftSpawnSchedule.onSpawnFailed(playerUuid, now);
            return;
        }
        Vector3d pos = transform.getPosition();
        World world = store.getExternalData().getWorld();
        FloatingGiftSpawnService.scheduleNaturalSpawnAfterEntityTick(world, playerUuid, pos.clone(), pos.clone());
    }
}
