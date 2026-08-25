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
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Bursts a thrown snowball on whoever it lands on and, when both of them are in a fight, counts the hit. Driven by the
 * snowball rather than by the people it might land on, so a snowball never ends up half deleted by two fighters at once
 * and one that lands on a bystander still bursts instead of hanging in the air.
 *
 * <p>Fight hits are judged against a padded hit box, because a fast projectile and a coarse tick otherwise let clean
 * throws slip past. Everyone else uses the real hit box, so a bystander only swallows a snowball that actually reaches
 * them, and your own side is not in the way at all. Session state lives outside the Store; entity writes go through
 * the CommandBuffer.
 */
public final class SnowballHitSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TransformComponent.getComponentType(),
            Query.or(Projectile.getComponentType(), ProjectileComponent.getComponentType())
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
        Ref<EntityStore> projectileRef = archetypeChunk.getReferenceTo(index);
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (projectileRef == null || !projectileRef.isValid() || transform == null) {
            return;
        }
        if (!isSnowballProjectile(store, projectileRef)) {
            return;
        }
        UUID thrower = snowballCreator(store, projectileRef);
        Box swept = projectileVolume(store, projectileRef, transform, dt);
        Box padded = expandUniform(swept, SnowballIds.HIT_PAD_BLOCKS);

        for (Ref<EntityStore> victimRef : nearbyTargets(store, projectileRef, padded)) {
            UUIDComponent victimUuid = store.getComponent(victimRef, UUIDComponent.getComponentType());
            if (victimUuid == null || victimUuid.getUuid() == null || victimUuid.getUuid().equals(thrower)) {
                continue;
            }
            Box victimBounds = targetBounds(store, victimRef);
            if (victimBounds == null || !aabbOverlap(padded, victimBounds)) {
                continue;
            }
            if (isLivingTeamMate(victimUuid.getUuid(), thrower)) {
                continue;
            }
            if (thrower != null
                && SnowballFightHits.tryScore(
                    store, commandBuffer, victimRef, victimUuid.getUuid(), thrower, projectileToken(store, projectileRef)
                )) {
                // tryScore already plays the puff and the sound on the fighter it landed on.
                commandBuffer.removeEntity(projectileRef, RemoveReason.REMOVE);
                return;
            }
            if (aabbOverlap(swept, victimBounds)) {
                SnowballHitFeedback.burst(commandBuffer, centre(swept));
                commandBuffer.removeEntity(projectileRef, RemoveReason.REMOVE);
                return;
            }
        }
    }

    private static int projectileToken(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        return SnowballFightHits.projectileToken(store, projectileRef);
    }

    /**
     * True while both are still standing on the same side of one fight, in which case the snowball carries on past. A
     * team mate already knocked out is fair game and soaks it up like anybody else.
     */
    private static boolean isLivingTeamMate(@Nonnull UUID victimUuid, @Nullable UUID throwerUuid) {
        if (throwerUuid == null) {
            return false;
        }
        SnowballSession session = SnowballSessionIndex.sessionForFighter(throwerUuid);
        if (session == null || !session.isLivingFighter(throwerUuid) || !session.isLivingFighter(victimUuid)) {
            return false;
        }
        SnowballSession.Fighter victim = session.fighter(victimUuid);
        SnowballSession.Fighter thrower = session.fighter(throwerUuid);
        return victim != null && thrower != null && victim.team() == thrower.team();
    }

    /** Players and villagers close enough to be worth an overlap test. */
    @Nonnull
    private static List<Ref<EntityStore>> nearbyTargets(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef,
        @Nonnull Box around
    ) {
        Vector3d centre = centre(around);
        double radius = 0.5 * diagonal(around) + SnowballIds.HIT_TARGET_SEARCH_BLOCKS;
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        SpatialResource<Ref<EntityStore>, EntityStore> tangible =
            store.getResource(CollisionModule.get().getTangibleEntitySpatialResourceType());
        tangible.getSpatialStructure().collect(centre, radius, nearby);
        SpatialResource<Ref<EntityStore>, EntityStore> networkSendable =
            store.getResource(EntityModule.get().getNetworkSendableSpatialResourceType());
        networkSendable.getSpatialStructure().collect(centre, radius, nearby);
        nearby.removeIf(candidate -> !isTarget(store, projectileRef, candidate));
        return nearby;
    }

    private static boolean isTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef,
        @Nullable Ref<EntityStore> candidate
    ) {
        if (candidate == null || !candidate.isValid() || candidate.equals(projectileRef)) {
            return false;
        }
        Archetype<EntityStore> archetype = store.getArchetype(candidate);
        if (isProjectileEntity(archetype)) {
            return false;
        }
        return archetype.contains(Player.getComponentType()) || archetype.contains(NPCEntity.getComponentType());
    }

    @Nullable
    private static Box targetBounds(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        BoundingBox boundingBox = store.getComponent(ref, BoundingBox.getComponentType());
        if (transform == null || boundingBox == null) {
            return null;
        }
        return worldBounds(transform.getPosition(), boundingBox);
    }

    @Nullable
    private static UUID snowballCreator(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef
    ) {
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

    private static boolean isProjectileEntity(@Nonnull Archetype<EntityStore> archetype) {
        return archetype.contains(Projectile.getComponentType())
            || archetype.contains(ProjectileComponent.getComponentType());
    }

    /** The snowball's box grown backwards along this frame's travel, so a fast throw cannot skip past somebody. */
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

    @Nonnull
    private static Vector3d centre(@Nonnull Box b) {
        return new Vector3d(
            (b.min.x + b.max.x) * 0.5,
            (b.min.y + b.max.y) * 0.5,
            (b.min.z + b.max.z) * 0.5
        );
    }

    private static double diagonal(@Nonnull Box b) {
        double dx = b.max.x - b.min.x;
        double dy = b.max.y - b.min.y;
        double dz = b.max.z - b.min.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
