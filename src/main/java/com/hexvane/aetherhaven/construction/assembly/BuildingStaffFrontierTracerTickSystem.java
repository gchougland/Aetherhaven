package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.collision.TangiableEntitySpatialSystem;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/**
 * Moves a core-style projectile holder ({@link Projectile} + motion component) in a straight line toward the target cell
 * then removes it with a small particle burst (no block collision).
 */
public final class BuildingStaffFrontierTracerTickSystem extends EntityTickingSystem<EntityStore> {
    private static final double ARRIVAL_EPS = 0.22;
    /** Avoid instant “pop” when the aim cell was almost coincident with the staff tip. */
    private static final int MIN_TICKS_BEFORE_ARRIVAL = 4;
    /** Safety cap for very long paths at low speed (~18s at 20 TPS). */
    private static final int MAX_TICKS = 380;
    /** Clamp dt so a hitch cannot skip the whole path in one tick. */
    private static final float MAX_DT_SEC = 0.1F;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, TangiableEntitySpatialSystem.class, OrderPriority.CLOSEST),
        new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class)
    );

    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public BuildingStaffFrontierTracerTickSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            Projectile.getComponentType(),
            TransformComponent.getComponentType(),
            BuildingStaffFrontierTracerComponent.getComponentType(),
            HeadRotation.getComponentType(),
            Velocity.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        BuildingStaffFrontierTracerComponent tracer = archetypeChunk.getComponent(index, BuildingStaffFrontierTracerComponent.getComponentType());
        TransformComponent tc = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        HeadRotation head = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        if (tracer == null || tc == null || head == null || velocity == null) {
            return;
        }
        tracer.incrementTicksAlive();
        if (tracer.getTicksAlive() > MAX_TICKS) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        Vector3d pos = tc.getPosition();
        double tx = tracer.getTargetX();
        double ty = tracer.getTargetY();
        double tz = tracer.getTargetZ();
        double dx = tx - pos.x;
        double dy = ty - pos.y;
        double dz = tz - pos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-5 && tracer.getTicksAlive() >= MIN_TICKS_BEFORE_ARRIVAL) {
            velocity.setZero();
            Vector3d hit = new Vector3d(tx, ty, tz);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID, hit, store);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID, hit, store);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID, hit, store);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        if (dist < 1.0e-5) {
            velocity.setZero();
            return;
        }
        boolean mayFinish = tracer.getTicksAlive() >= MIN_TICKS_BEFORE_ARRIVAL && dist <= ARRIVAL_EPS;
        if (mayFinish) {
            velocity.setZero();
            Vector3d hit = new Vector3d(tx, ty, tz);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID, hit, store);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID, hit, store);
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID, hit, store);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        double nx = dx / dist;
        double ny = dy / dist;
        double nz = dz / dist;
        float speed = tracer.getSpeedBlocksPerSec();
        velocity.set(nx * speed, ny * speed, nz * speed);
        float dtSec = Math.min(Math.max(dt, 1.0f / 240.0f), MAX_DT_SEC);
        double step = Math.min(dist, speed * dtSec);
        Vector3d moved = new Vector3d(pos.x + nx * step, pos.y + ny * step, pos.z + nz * step);
        tc.setPosition(moved);
        double pitch = Math.asin(Math.max(-1.0, Math.min(1.0, ny)));
        double yaw = Math.atan2(-nx, -nz);
        head.teleportRotation(new Vector3f((float) pitch, (float) yaw, 0.0F));
        ParticleUtil.spawnParticleEffect(
            AetherhavenConstants.BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID,
            moved.clone(),
            store
        );
        if (tracer.getTicksAlive() % 5 == 0) {
            ParticleUtil.spawnParticleEffect(
                AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID,
                moved.clone(),
                store
            );
        }
    }
}
