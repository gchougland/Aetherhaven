package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Records why and where an Aetherhaven NPC was spawned (telemetry for duplicate-NPC debugging). */
public final class AetherhavenNpcSpawnOrigin implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<AetherhavenNpcSpawnOrigin> CODEC =
        BuilderCodec.builder(AetherhavenNpcSpawnOrigin.class, AetherhavenNpcSpawnOrigin::new)
            .append(new KeyedCodec<>("SpawnSource", Codec.STRING), (o, v) -> o.spawnSource = v != null ? v : "", o -> o.spawnSource)
            .add()
            .append(new KeyedCodec<>("SpawnDetail", Codec.STRING), (o, v) -> o.spawnDetail = v != null ? v : "", o -> o.spawnDetail)
            .add()
            .append(new KeyedCodec<>("SpawnWorldName", Codec.STRING), (o, v) -> o.spawnWorldName = v != null ? v : "", o -> o.spawnWorldName)
            .add()
            .append(new KeyedCodec<>("SpawnX", Codec.DOUBLE), (o, v) -> o.spawnX = v != null ? v : 0.0, o -> o.spawnX)
            .add()
            .append(new KeyedCodec<>("SpawnY", Codec.DOUBLE), (o, v) -> o.spawnY = v != null ? v : 0.0, o -> o.spawnY)
            .add()
            .append(new KeyedCodec<>("SpawnZ", Codec.DOUBLE), (o, v) -> o.spawnZ = v != null ? v : 0.0, o -> o.spawnZ)
            .add()
            .append(new KeyedCodec<>("SpawnEpochMs", Codec.LONG), (o, v) -> o.spawnEpochMs = v != null ? v : 0L, o -> o.spawnEpochMs)
            .add()
            .append(new KeyedCodec<>("SpawnGameEpochDay", Codec.LONG), (o, v) -> o.spawnGameEpochDay = v != null ? v : 0L, o -> o.spawnGameEpochDay)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, AetherhavenNpcSpawnOrigin> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType = registry.registerComponent(AetherhavenNpcSpawnOrigin.class, "AetherhavenNpcSpawnOrigin", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, AetherhavenNpcSpawnOrigin> getComponentType() {
        ComponentType<EntityStore, AetherhavenNpcSpawnOrigin> t = componentType;
        if (t == null) {
            throw new IllegalStateException("AetherhavenNpcSpawnOrigin not registered");
        }
        return t;
    }

    private String spawnSource = "";
    private String spawnDetail = "";
    private String spawnWorldName = "";
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private long spawnEpochMs;
    private long spawnGameEpochDay;

    public AetherhavenNpcSpawnOrigin() {}

    public AetherhavenNpcSpawnOrigin(
        @Nonnull String spawnSource,
        @Nonnull String spawnDetail,
        @Nonnull String spawnWorldName,
        double spawnX,
        double spawnY,
        double spawnZ,
        long spawnEpochMs,
        long spawnGameEpochDay
    ) {
        this.spawnSource = spawnSource;
        this.spawnDetail = spawnDetail;
        this.spawnWorldName = spawnWorldName;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnEpochMs = spawnEpochMs;
        this.spawnGameEpochDay = spawnGameEpochDay;
    }

    @Nonnull
    public String getSpawnSource() {
        return spawnSource;
    }

    @Nonnull
    public String getSpawnDetail() {
        return spawnDetail;
    }

    @Nonnull
    public String getSpawnWorldName() {
        return spawnWorldName;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public long getSpawnEpochMs() {
        return spawnEpochMs;
    }

    public long getSpawnGameEpochDay() {
        return spawnGameEpochDay;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new AetherhavenNpcSpawnOrigin(
            spawnSource,
            spawnDetail,
            spawnWorldName,
            spawnX,
            spawnY,
            spawnZ,
            spawnEpochMs,
            spawnGameEpochDay
        );
    }
}
