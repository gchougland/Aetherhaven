package com.hexvane.aetherhaven.festival.snowball;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Counts opposing snowball hits on living fighters. Session state lives outside Store; entity writes use CommandBuffer.
 */
public final class SnowballHitSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType(),
            BoundingBox.getComponentType()
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
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        BoundingBox boundingBox = archetypeChunk.getComponent(index, BoundingBox.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (uuidComponent == null || transform == null || boundingBox == null || ref == null) {
            return;
        }
        UUID victimUuid = uuidComponent.getUuid();
        SnowballSession session = SnowballSessionIndex.sessionForFighter(victimUuid);
        if (session == null || !session.isLivingFighter(victimUuid)) {
            return;
        }

        Box hitVolume = expandUniform(worldBounds(transform.getPosition(), boundingBox), SnowballIds.HIT_PAD_BLOCKS);
        double mx = (hitVolume.min.x + hitVolume.max.x) * 0.5;
        double my = (hitVolume.min.y + hitVolume.max.y) * 0.5;
        double mz = (hitVolume.min.z + hitVolume.max.z) * 0.5;
        double dx = hitVolume.max.x - hitVolume.min.x;
        double dy = hitVolume.max.y - hitVolume.min.y;
        double dz = hitVolume.max.z - hitVolume.min.z;
        double collectRadius = 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) + 8.0;

        SpatialResource<Ref<EntityStore>, EntityStore> tangible =
            store.getResource(CollisionModule.get().getTangibleEntitySpatialResourceType());
        SpatialResource<Ref<EntityStore>, EntityStore> networkSendable =
            store.getResource(EntityModule.get().getNetworkSendableSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        Set<Ref<EntityStore>> candidates = new HashSet<>();
        nearby.clear();
        tangible.getSpatialStructure().collect(new Vector3d(mx, my, mz), collectRadius, nearby);
        addProjectileCandidates(store, candidates, nearby);
        nearby.clear();
        networkSendable.getSpatialStructure().collect(new Vector3d(mx, my, mz), collectRadius, nearby);
        addProjectileCandidates(store, candidates, nearby);

        for (Ref<EntityStore> projectileRef : candidates) {
            if (projectileRef == null || !projectileRef.isValid() || projectileRef.equals(ref)) {
                continue;
            }
            UUID creator = snowballCreator(store, projectileRef);
            if (creator == null || !session.isLivingFighter(creator)) {
                continue;
            }
            TransformComponent pTransform = store.getComponent(projectileRef, TransformComponent.getComponentType());
            if (pTransform == null) {
                continue;
            }
            if (!aabbOverlap(hitVolume, projectileVolume(store, projectileRef, pTransform, dt))) {
                continue;
            }
            boolean scored = SnowballFightHits.tryScore(
                store,
                commandBuffer,
                ref,
                victimUuid,
                creator,
                SnowballFightHits.projectileToken(store, projectileRef)
            );
            if (scored && projectileRef.isValid()) {
                commandBuffer.removeEntity(projectileRef, RemoveReason.REMOVE);
                return;
            }
        }
    }

    @Nullable
    private static UUID snowballCreator(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef
    ) {
        if (!isSnowballProjectile(store, projectileRef)) {
            return null;
        }
        ProjectileComponent pc = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
        if (pc != null && pc.getCreatorUuid() != null) {
            return pc.getCreatorUuid();
        }
        StandardPhysicsProvider physics =
            store.getComponent(projectileRef, ProjectileModule.get().getStandardPhysicsProviderComponentType());
        if (physics != null && physics.getCreatorUuid() != null) {
            return physics.getCreatorUuid();
        }
        return null;
    }

    private static boolean isSnowballProjectile(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef
    ) {
        Archetype<EntityStore> arch = store.getArchetype(projectileRef);
        if (!isProjectileEntity(arch)) {
            return false;
        }
        ModelComponent model = store.getComponent(projectileRef, ModelComponent.getComponentType());
        if (model != null && model.getModel() != null) {
            String id = model.getModel().getModelAssetId();
            if (id != null && isSnowballModelId(id)) {
                return true;
            }
        }
        PersistentModel persistent = store.getComponent(projectileRef, PersistentModel.getComponentType());
        if (persistent != null && persistent.getModelReference() != null) {
            String id = persistent.getModelReference().getModelAssetId();
            if (id != null && isSnowballModelId(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSnowballModelId(@Nonnull String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("snowball") || lower.contains(SnowballIds.PROJECTILE_MODEL_ID.toLowerCase(Locale.ROOT));
    }

    private static void addProjectileCandidates(
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<Ref<EntityStore>> out,
        @Nonnull List<Ref<EntityStore>> spatialHits
    ) {
        for (int i = 0; i < spatialHits.size(); i++) {
            Ref<EntityStore> r = spatialHits.get(i);
            if (r == null || !r.isValid()) {
                continue;
            }
            if (isProjectileEntity(store.getArchetype(r))) {
                out.add(r);
            }
        }
    }

    private static boolean isProjectileEntity(@Nonnull Archetype<EntityStore> archetype) {
        return archetype.contains(Projectile.getComponentType())
            || archetype.contains(ProjectileComponent.getComponentType());
    }

    @Nonnull
    private static Box projectileVolume(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef,
        @Nonnull TransformComponent transform,
        float dt
    ) {
        Vector3d pos = transform.getPosition();
        BoundingBox boundingBox = store.getComponent(projectileRef, BoundingBox.getComponentType());
        Box current = worldBoundsOrRadius(pos, boundingBox);
        Velocity velocity = store.getComponent(projectileRef, Velocity.getComponentType());
        if (velocity == null || dt <= 0f) {
            return current;
        }
        Vector3d v = velocity.getVelocity();
        if (v == null || (v.x == 0.0 && v.y == 0.0 && v.z == 0.0)) {
            return current;
        }
        Vector3d previous = new Vector3d(pos.x - v.x * dt, pos.y - v.y * dt, pos.z - v.z * dt);
        return encapsulate(current, worldBoundsOrRadius(previous, boundingBox));
    }

    @Nonnull
    private static Box worldBoundsOrRadius(@Nonnull Vector3d origin, @Nullable BoundingBox boundingBox) {
        if (boundingBox != null) {
            return worldBounds(origin, boundingBox);
        }
        double r = SnowballIds.HIT_PROJECTILE_RADIUS;
        return new Box(origin.x - r, origin.y - r, origin.z - r, origin.x + r, origin.y + r, origin.z + r);
    }

    @Nonnull
    private static Box worldBounds(@Nonnull Vector3d origin, @Nonnull BoundingBox bb) {
        Box local = bb.getBoundingBox();
        return new Box(
            origin.x + local.min.x,
            origin.y + local.min.y,
            origin.z + local.min.z,
            origin.x + local.max.x,
            origin.y + local.max.y,
            origin.z + local.max.z
        );
    }

    @Nonnull
    private static Box encapsulate(@Nonnull Box a, @Nonnull Box b) {
        return new Box(
            Math.min(a.min.x, b.min.x),
            Math.min(a.min.y, b.min.y),
            Math.min(a.min.z, b.min.z),
            Math.max(a.max.x, b.max.x),
            Math.max(a.max.y, b.max.y),
            Math.max(a.max.z, b.max.z)
        );
    }

    @Nonnull
    private static Box expandUniform(@Nonnull Box b, double pad) {
        return new Box(
            b.min.x - pad,
            b.min.y - pad,
            b.min.z - pad,
            b.max.x + pad,
            b.max.y + pad,
            b.max.z + pad
        );
    }

    private static boolean aabbOverlap(@Nonnull Box a, @Nonnull Box b) {
        return a.min.x <= b.max.x
            && a.max.x >= b.min.x
            && a.min.y <= b.max.y
            && a.max.y >= b.min.y
            && a.min.z <= b.max.z
            && a.max.z >= b.min.z;
    }
}
