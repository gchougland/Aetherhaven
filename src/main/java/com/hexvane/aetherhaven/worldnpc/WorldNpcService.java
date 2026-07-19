package com.hexvane.aetherhaven.worldnpc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Server mod facade for town independent world NPCs (hubs, tutorials, hand built experiences).
 */
public final class WorldNpcService {
    private final AetherhavenPlugin plugin;

    public WorldNpcService(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    public WorldNpcRegistry registry(@Nonnull World world) {
        return AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
    }

    @Nonnull
    public WorldNpcPlacementRecord place(
        @Nonnull World world,
        @Nonnull String placementId,
        @Nonnull String npcRoleId,
        double x,
        double y,
        double z,
        float yawDegrees
    ) {
        WorldNpcRegistry registry = registry(world);
        WorldNpcPlacementRecord placement = registry.findPlacement(placementId);
        if (placement == null) {
            placement = new WorldNpcPlacementRecord();
            placement.setPlacementId(placementId);
        }
        placement.setNpcRoleId(npcRoleId);
        placement.setPose(x, y, z, yawDegrees);
        placement.setScheduleMode(placement.scheduleModeOrDefault());
        registry.upsertPlacement(placement);
        WorldNpcPersistence.save(world, plugin, registry);
        WorldNpcPlacementRecord toSpawn = placement;
        world.execute(() -> WorldNpcSpawnService.ensurePlacement(world, plugin, toSpawn));
        return placement;
    }

    @Nullable
    public UUID ensure(@Nonnull World world, @Nonnull String placementId) {
        WorldNpcPlacementRecord placement = registry(world).findPlacement(placementId);
        if (placement == null) {
            return null;
        }
        return WorldNpcSpawnService.ensurePlacement(world, plugin, placement);
    }

    public void remove(@Nonnull World world, @Nonnull String placementId) {
        WorldNpcSpawnService.removePlacement(world, plugin, placementId);
    }

    @Nullable
    public WorldNpcPlacementRecord getPlacement(@Nonnull World world, @Nonnull String placementId) {
        return registry(world).findPlacement(placementId);
    }

    @Nonnull
    public List<WorldNpcPlacementRecord> listPlacements(@Nonnull World world) {
        return registry(world).allPlacements();
    }

    @Nonnull
    public WorldNpcPlayerProgress getPlayerProgress(@Nonnull World world, @Nonnull UUID playerUuid) {
        WorldNpcRegistry registry = registry(world);
        WorldNpcPlayerProgress progress = registry.getOrCreatePlayerProgress(playerUuid);
        registry.markPlayerDirty();
        return progress;
    }

    public void save(@Nonnull World world) {
        WorldNpcPersistence.save(world, plugin, registry(world));
    }

    public void setPoseFromPlayer(
        @Nonnull World world,
        @Nonnull String placementId,
        @Nonnull TransformComponent playerTransform
    ) {
        WorldNpcPlacementRecord placement = registry(world).findPlacement(placementId);
        if (placement == null) {
            return;
        }
        Vector3d pos = playerTransform.getPosition();
        float yawDeg = (float) Math.toDegrees(playerTransform.getRotation().yaw());
        placement.setPose(pos.x, pos.y, pos.z, yawDeg);
        registry(world).upsertPlacement(placement);
        save(world);
        world.execute(() -> {
            WorldNpcSpawnService.despawnPlacement(world, plugin, placementId);
            WorldNpcSpawnService.ensurePlacement(world, plugin, placement);
        });
    }

    /** Despawn and respawn so role, name, portrait, and freeze state refresh. */
    public void respawn(@Nonnull World world, @Nonnull String placementId) {
        WorldNpcSpawnService.respawnPlacement(world, plugin, placementId);
    }
}
