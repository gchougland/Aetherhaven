package com.hexvane.aetherhaven.bard;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Snapshot of active bard performances, rebuilt once per world tick for proximity music. */
public final class BardActivePerformancesResource implements Resource<EntityStore> {
    /** Distance at which a player starts hearing a performance. */
    public static final double MUSIC_RADIUS = 40.0;
    /** Distance at which a listener stops hearing, larger so edge standing does not flicker. */
    public static final double MUSIC_LEAVE_RADIUS = 44.0;
    private static final double MUSIC_RADIUS_SQ = MUSIC_RADIUS * MUSIC_RADIUS;
    private static final double MUSIC_LEAVE_RADIUS_SQ = MUSIC_LEAVE_RADIUS * MUSIC_LEAVE_RADIUS;

    @Nullable
    private static volatile ResourceType<EntityStore, BardActivePerformancesResource> resourceType;

    private long builtForTick = -1L;
    private final List<Snapshot> active = new ArrayList<>();

    public record NearestMusic(int musicContainerIndex, double x, double y, double z) {
        public static final NearestMusic NONE = new NearestMusic(0, 0.0, 0.0, 0.0);
    }

    public static void register(
        @Nonnull com.hypixel.hytale.component.ComponentRegistryProxy<EntityStore> registry
    ) {
        resourceType = registry.registerResource(BardActivePerformancesResource.class, BardActivePerformancesResource::new);
    }

    @Nonnull
    public static ResourceType<EntityStore, BardActivePerformancesResource> getResourceType() {
        ResourceType<EntityStore, BardActivePerformancesResource> t = resourceType;
        if (t == null) {
            throw new IllegalStateException("BardActivePerformancesResource not registered");
        }
        return t;
    }

    public void rebuildForTick(@Nonnull Store<EntityStore> store, long worldTick) {
        if (builtForTick == worldTick) {
            return;
        }
        builtForTick = worldTick;
        active.clear();
        store.forEachChunk(
            BardPerformanceComponent.getComponentType(),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    BardPerformanceComponent perf =
                        archetypeChunk.getComponent(i, BardPerformanceComponent.getComponentType());
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (perf == null || tc == null || perf.getMusicContainerIndex() == 0) {
                        continue;
                    }
                    // Hearing follows the bard, not the spot where the song started.
                    active.add(new Snapshot(tc.getPosition(), perf.getMusicContainerIndex()));
                }
            }
        );
    }

    @Nonnull
    public NearestMusic nearestMusic(double px, double py, double pz) {
        return nearestMusic(px, py, pz, false);
    }

    @Nonnull
    public NearestMusic nearestMusic(double px, double py, double pz, boolean alreadyListening) {
        int bestIndex = 0;
        double bestX = 0.0;
        double bestY = 0.0;
        double bestZ = 0.0;
        double radiusSq = alreadyListening ? MUSIC_LEAVE_RADIUS_SQ : MUSIC_RADIUS_SQ;
        double bestDistSq = radiusSq + 1.0;
        for (Snapshot snapshot : active) {
            double dx = px - snapshot.x;
            double dy = py - snapshot.y;
            double dz = pz - snapshot.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= radiusSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIndex = snapshot.musicContainerIndex;
                bestX = snapshot.x;
                bestY = snapshot.y;
                bestZ = snapshot.z;
            }
        }
        if (bestIndex == 0) {
            return NearestMusic.NONE;
        }
        return new NearestMusic(bestIndex, bestX, bestY, bestZ);
    }

    @Override
    public Resource<EntityStore> clone() {
        BardActivePerformancesResource copy = new BardActivePerformancesResource();
        copy.builtForTick = builtForTick;
        copy.active.addAll(active);
        return copy;
    }

    private record Snapshot(double x, double y, double z, int musicContainerIndex) {
        Snapshot(@Nonnull Vector3d pos, int musicContainerIndex) {
            this(pos.x, pos.y, pos.z, musicContainerIndex);
        }
    }

    void putSnapshot(double x, double y, double z, int musicContainerIndex) {
        active.add(new Snapshot(x, y, z, musicContainerIndex));
    }
}
