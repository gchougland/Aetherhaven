package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
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
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.World;
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
 * Floats carnival balloons upward and pops them on dart hits. Session state lives outside Store; entity writes use
 * {@link CommandBuffer}.
 */
public final class CarnivalBalloonSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            CarnivalBalloonComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Velocity.getComponentType(),
            BoundingBox.getComponentType(),
            UUIDComponent.getComponentType()
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
        CarnivalBalloonComponent balloon =
            archetypeChunk.getComponent(index, CarnivalBalloonComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        BoundingBox boundingBox = archetypeChunk.getComponent(index, BoundingBox.getComponentType());
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (balloon == null || transform == null || velocity == null || boundingBox == null || uuidComponent == null
            || ref == null) {
            return;
        }
        UUID townId = balloon.getTownId();
        UUID balloonUuid = uuidComponent.getUuid();
        if (townId == null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        CarnivalBalloonSession session = CarnivalBalloonSessionIndex.get(townId);
        if (session == null || session.getPhase() != CarnivalBalloonSession.Phase.PLAYING) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        balloon.addLifeSeconds(dt);
        velocity.set(0.0, CarnivalIds.BALLOON_RISE_SPEED, 0.0);
        Vector3d pos = transform.getPosition();
        Vector3d next = new Vector3d(pos.x, pos.y + CarnivalIds.BALLOON_RISE_SPEED * dt, pos.z);
        World world = store.getExternalData().getWorld();
        if (!EntityChunkUtil.isPositionChunkInMemory(world, next)) {
            session.markMissed(balloonUuid);
            playFinishIfNeeded(store, session, townId);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        transform.setPosition(next);

        if (tryDartPop(store, commandBuffer, ref, transform, boundingBox, session, balloonUuid, townId)) {
            return;
        }

        if (balloon.getLifeSeconds() >= balloon.getMaxLifeSeconds()) {
            session.markMissed(balloonUuid);
            playFinishIfNeeded(store, session, townId);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static boolean tryDartPop(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> balloonRef,
        @Nonnull TransformComponent transform,
        @Nonnull BoundingBox boundingBox,
        @Nonnull CarnivalBalloonSession session,
        @Nonnull UUID balloonUuid,
        @Nonnull UUID townId
    ) {
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid == null) {
            return false;
        }
        Box hitVolume = expandUniform(worldBounds(transform.getPosition(), boundingBox), CarnivalIds.BALLOON_HIT_PAD);
        double mx = (hitVolume.min.x + hitVolume.max.x) * 0.5;
        double my = (hitVolume.min.y + hitVolume.max.y) * 0.5;
        double mz = (hitVolume.min.z + hitVolume.max.z) * 0.5;
        double dx = hitVolume.max.x - hitVolume.min.x;
        double dy = hitVolume.max.y - hitVolume.min.y;
        double dz = hitVolume.max.z - hitVolume.min.z;
        double collectRadius = 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) + 4.0;

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
            if (projectileRef == null || !projectileRef.isValid() || projectileRef.equals(balloonRef)) {
                continue;
            }
            if (!isDartProjectileFromPlayer(store, projectileRef, playerUuid)) {
                continue;
            }
            TransformComponent pTransform = store.getComponent(projectileRef, TransformComponent.getComponentType());
            BoundingBox pBb = store.getComponent(projectileRef, BoundingBox.getComponentType());
            if (pTransform == null || pBb == null) {
                continue;
            }
            if (!aabbOverlap(hitVolume, worldBounds(pTransform.getPosition(), pBb))) {
                continue;
            }
            CarnivalAudio.playBalloonPop(store, transform.getPosition());
            session.markPopped(balloonUuid);
            playFinishIfNeeded(store, session, townId);
            commandBuffer.removeEntity(balloonRef, RemoveReason.REMOVE);
            return true;
        }
        return false;
    }

    private static void playFinishIfNeeded(
        @Nonnull Store<EntityStore> store,
        @Nonnull CarnivalBalloonSession session,
        @Nonnull UUID townId
    ) {
        if (!session.consumeFinishSfxPending()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        TownRecord town =
            AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(townId);
        if (town == null) {
            return;
        }
        CarnivalAudio.playBalloonFinish(store, CarnivalAudio.squareCenter(plugin, town));
    }

    private static boolean isDartProjectileFromPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> projectileRef,
        @Nonnull UUID playerUuid
    ) {
        Archetype<EntityStore> arch = store.getArchetype(projectileRef);
        if (!isProjectileEntity(arch)) {
            return false;
        }
        ProjectileComponent pc = store.getComponent(projectileRef, ProjectileComponent.getComponentType());
        if (pc != null) {
            UUID creator = pc.getCreatorUuid();
            if (creator == null || !creator.equals(playerUuid)) {
                return false;
            }
            if (isDartModel(store, projectileRef) || looksLikeDartAppearance(pc.getAppearance())) {
                return true;
            }
            return !playerHoldingBow(store, playerUuid);
        }
        return isDartModel(store, projectileRef) && !playerHoldingBow(store, playerUuid);
    }

    private static boolean playerHoldingBow(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        ItemStack held = activeHotbarItem(store, playerUuid);
        if (held == null || held.isEmpty()) {
            return false;
        }
        String id = held.getItemId();
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("bow") || (lower.contains("arrow") && !lower.contains("dart"));
    }

    @Nullable
    private static ItemStack activeHotbarItem(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }
        if (store.getComponent(playerRef, Player.getComponentType()) == null) {
            return null;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        return hotbar != null ? hotbar.getActiveItem() : null;
    }

    private static boolean isDartModel(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> projectileRef) {
        ModelComponent model = store.getComponent(projectileRef, ModelComponent.getComponentType());
        if (model != null && model.getModel() != null) {
            String id = model.getModel().getModelAssetId();
            if (id != null) {
                String lower = id.toLowerCase(Locale.ROOT);
                if (lower.contains("dart") || lower.contains("tribal")) {
                    return true;
                }
            }
        }
        PersistentModel persistent = store.getComponent(projectileRef, PersistentModel.getComponentType());
        if (persistent != null && persistent.getModelReference() != null) {
            String id = persistent.getModelReference().getModelAssetId();
            if (id != null) {
                String lower = id.toLowerCase(Locale.ROOT);
                return lower.contains("dart") || lower.contains("tribal");
            }
        }
        return false;
    }

    private static boolean looksLikeDartAppearance(@Nullable String appearance) {
        if (appearance == null) {
            return false;
        }
        String lower = appearance.toLowerCase(Locale.ROOT);
        return lower.contains("dart") || lower.contains("tribal");
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
