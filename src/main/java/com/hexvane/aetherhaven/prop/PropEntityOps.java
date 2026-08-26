package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPrefabSequence;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.FromPrefab;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.event.PrefabPlaceEntityEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.prefab.selection.standard.RotateBlockMode;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Prop prefab entity paste / removal. Prefers {@link BlockSelection} (full entity list from the prefab file), tags each
 * spawned entity with {@link AetherhavenPlacedInstance} for that prop instance, and on package removes by linked UUIDs
 * and matching tags. An exact-footprint sweep only cleans untagged decorative leftovers (never a neighbor prop's
 * tagged entities, and never padded visual bounds).
 */
public final class PropEntityOps {
    private PropEntityOps() {}

    /**
     * Pastes every prefab entity for this prop, tagging each with {@code instanceId}.
     *
     * @return world entity UUIDs that were spawned (for persistence / targeted removal)
     */
    @Nonnull
    public static List<UUID> pasteEntities(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull String prefabPathKey,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull UUID instanceId
    ) {
        List<UUID> linked = new ArrayList<>();
        ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
        Path path = PrefabResolveUtil.resolvePrefabPath(prefabPathKey);
        BlockSelection selection = path != null ? PrefabStore.get().getPrefab(path) : null;
        if (selection != null && selection.getEntityCount() > 0) {
            BlockSelection rotated = rotateSelection(selection, yaw);
            int prefabId = PrefabUtil.getNextPrefabId();
            rotated.forEachEntity(
                source -> {
                    UUID spawned = spawnTaggedFromSelection(world, origin, accessor, source, instanceId, prefabId);
                    if (spawned != null) {
                        linked.add(spawned);
                    }
                }
            );
            return linked;
        }
        ConstructionPrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        int prefabId = PrefabUtil.getNextPrefabId();
        for (Holder<EntityStore> source : seq.prefabEntitiesInOrder()) {
            try {
                UUID spawned =
                    spawnTaggedPrefabEntity(world, origin, seq.prefabRotation(), accessor, source, instanceId, prefabId);
                if (spawned != null) {
                    linked.add(spawned);
                }
            } catch (RuntimeException e) {
                com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass()
                    .atWarning()
                    .withCause(e)
                    .log("Failed to spawn prop entity for instance %s", instanceId);
            }
        }
        return linked;
    }

    @Nullable
    private static UUID spawnTaggedFromSelection(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Holder<EntityStore> entityToAdd,
        @Nonnull UUID instanceId,
        int prefabId
    ) {
        Holder<EntityStore> clone = entityToAdd.clone();
        if (!isSpawnablePropEntity(clone)) {
            return null;
        }
        TransformComponent transformComp = clone.getComponent(TransformComponent.getComponentType());
        if (transformComp == null) {
            return null;
        }
        transformComp.getPosition().add(origin.x, origin.y, origin.z);
        return finishSpawn(world, entityAccessor, clone, instanceId, prefabId);
    }

    @Nullable
    private static UUID spawnTaggedPrefabEntity(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull PrefabRotation prefabRotation,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Holder<EntityStore> entityToAdd,
        @Nonnull UUID instanceId,
        int prefabId
    ) {
        Holder<EntityStore> clone = entityToAdd.clone();
        if (!isSpawnablePropEntity(clone)) {
            return null;
        }
        TransformComponent transformComp = clone.getComponent(TransformComponent.getComponentType());
        if (transformComp == null) {
            return null;
        }
        Vector3d w = new Vector3d(transformComp.getPosition());
        boolean blockEntity = clone.getComponent(BlockEntity.getComponentType()) != null;
        Vector3d centerOffset = blockEntity ? new Vector3d(0.5, 0.0, 0.5) : new Vector3d(0.5, 0.5, 0.5);
        w.sub(centerOffset);
        prefabRotation.rotate(w);
        w.add(centerOffset);
        w.add(origin.x, origin.y, origin.z);
        Vector3d pos = transformComp.getPosition();
        pos.x = w.x;
        pos.y = w.y;
        pos.z = w.z;
        float dyaw = prefabRotation.getYaw();
        if (prefabRotation == PrefabRotation.ROTATION_90 || prefabRotation == PrefabRotation.ROTATION_270) {
            dyaw += (float) Math.PI;
        }
        EntityRotationUtil.setBodyYaw(transformComp.getRotation(), transformComp.getRotation().yaw() + dyaw);
        HeadRotation headRotation = clone.getComponent(HeadRotation.getComponentType());
        if (headRotation != null) {
            EntityRotationUtil.setBodyYaw(headRotation.getRotation(), headRotation.getRotation().yaw() + dyaw);
        }
        return finishSpawn(world, entityAccessor, clone, instanceId, prefabId);
    }

    @Nullable
    private static UUID finishSpawn(
        @Nonnull World world,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Holder<EntityStore> clone,
        @Nonnull UUID instanceId,
        int prefabId
    ) {
        // Prefab JSON often embeds fixed UUIDs; refresh so repeated placements do not collide / skip adds.
        UUID entityUuid = UUID.randomUUID();
        clone.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));

        PrefabPlaceEntityEvent prefabPlaceEntityEvent = new PrefabPlaceEntityEvent(prefabId, clone);
        entityAccessor.invoke(prefabPlaceEntityEvent);
        if (prefabPlaceEntityEvent.isCancelled()) {
            return null;
        }
        clone.ensureComponent(FromPrefab.getComponentType());
        if (clone.getComponent(NPCEntity.getComponentType()) == null
            && clone.getComponent(Player.getComponentType()) == null
            && clone.getComponent(Invulnerable.getComponentType()) == null) {
            clone.ensureComponent(Invulnerable.getComponentType());
        }
        clone.putComponent(
            AetherhavenPlacedInstance.getComponentType(),
            new AetherhavenPlacedInstance(instanceId.toString(), AetherhavenPlacedInstance.Kind.PROP)
        );
        entityAccessor.addEntity(clone, AddReason.LOAD);
        return entityUuid;
    }

    /**
     * Prefabs sometimes keep empty Transform-only leftovers from editor / trigger-volume cleanup. Those markers sit
     * far from the real decoration and must not be spawned (they stretch the prefab buffer AABB used by packaging).
     */
    private static boolean isSpawnablePropEntity(@Nonnull Holder<EntityStore> holder) {
        return holder.getComponent(ItemComponent.getComponentType()) != null
            || holder.getComponent(BlockEntity.getComponentType()) != null
            || holder.getComponent(PropComponent.getComponentType()) != null
            || holder.getComponent(NPCEntity.getComponentType()) != null;
    }

    /**
     * Removes entities linked to this prop: known UUIDs, matching {@link AetherhavenPlacedInstance} tags, then an
     * exact-footprint sweep for untagged decorative leftovers only.
     */
    public static void removeLinkedEntities(
        @Nonnull World world,
        @Nonnull UUID instanceId,
        @Nonnull List<UUID> linkedEntityIds,
        @Nonnull Vector3i origin,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Set<Ref<EntityStore>> toRemove = new HashSet<>();

        for (UUID entityUuid : linkedEntityIds) {
            if (entityUuid == null) {
                continue;
            }
            Ref<EntityStore> ref = world.getEntityRef(entityUuid);
            if (ref != null && ref.isValid()) {
                toRemove.add(ref);
            }
        }

        String idString = instanceId.toString();
        store.forEachEntityParallel(
            AetherhavenPlacedInstance.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                AetherhavenPlacedInstance tag = archetypeChunk.getComponent(index, AetherhavenPlacedInstance.getComponentType());
                if (tag == null || !idString.equals(tag.getInstanceId())) {
                    return;
                }
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                if (ref != null && ref.isValid()) {
                    toRemove.add(ref);
                }
            }
        );

        // Exact reserved footprint (no visual padding). Skip anything already tagged to a prop instance.
        PlotFootprintRecord fp = PropPrefabOps.footprint(origin, yaw, buffer);
        collectUntaggedFootprintLeftovers(store, fp, toRemove);

        for (Ref<EntityStore> ref : toRemove) {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }

    /** Legacy overload: tag-only removal (no footprint context). Prefer the full overload. */
    public static void removeLinkedEntities(@Nonnull World world, @Nonnull UUID instanceId) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        String idString = instanceId.toString();
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachEntityParallel(
            AetherhavenPlacedInstance.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                AetherhavenPlacedInstance tag = archetypeChunk.getComponent(index, AetherhavenPlacedInstance.getComponentType());
                if (tag == null || !idString.equals(tag.getInstanceId())) {
                    return;
                }
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                if (ref != null && ref.isValid()) {
                    toRemove.add(ref);
                }
            }
        );
        for (Ref<EntityStore> ref : toRemove) {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }

    /**
     * Exact block-inclusive footprint (min … max+1), no padding. Only untagged decorative leftovers — never entities
     * already owned by any {@link AetherhavenPlacedInstance} (neighbors must stay).
     */
    private static void collectUntaggedFootprintLeftovers(
        @Nonnull Store<EntityStore> store, @Nonnull PlotFootprintRecord fp, @Nonnull Set<Ref<EntityStore>> toRemove
    ) {
        double minX = fp.getMinX();
        double minY = fp.getMinY();
        double minZ = fp.getMinZ();
        double maxX = fp.getMaxX() + 1.0;
        double maxY = fp.getMaxY() + 1.0;
        double maxZ = fp.getMaxZ() + 1.0;
        ComponentType<EntityStore, PropComponent> propType = null;
        try {
            EntityModule module = EntityModule.get();
            if (module != null) {
                propType = module.getPropComponentType();
            }
        } catch (RuntimeException ignored) {
            // Prop component unavailable.
        }
        final ComponentType<EntityStore, PropComponent> propComponentType = propType;
        store.forEachChunk(
            TransformComponent.getComponentType(),
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (p.x < minX || p.x >= maxX || p.y < minY || p.y >= maxY || p.z < minZ || p.z >= maxZ) {
                        continue;
                    }
                    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    if (archetypeChunk.getComponent(i, Player.getComponentType()) != null
                        || archetypeChunk.getComponent(i, NPCEntity.getComponentType()) != null) {
                        continue;
                    }
                    // Owned by a specific prop (this one already collected, or a neighbor) — do not footprint-sweep.
                    if (archetypeChunk.getComponent(i, AetherhavenPlacedInstance.getComponentType()) != null) {
                        continue;
                    }
                    boolean decorative =
                        archetypeChunk.getComponent(i, FromPrefab.getComponentType()) != null
                            || (propComponentType != null && archetypeChunk.getComponent(i, propComponentType) != null)
                            || (archetypeChunk.getComponent(i, ItemComponent.getComponentType()) != null
                                && archetypeChunk.getComponent(i, PreventPickup.getComponentType()) != null);
                    if (decorative) {
                        toRemove.add(ref);
                    }
                }
            }
        );
    }

    @Nonnull
    private static BlockSelection rotateSelection(@Nonnull BlockSelection selection, @Nonnull Rotation yaw) {
        int steps = (yaw.getDegrees() / 90) % 4;
        if (steps < 0) {
            steps += 4;
        }
        if (steps == 0) {
            return selection.cloneSelection();
        }
        return selection.cloneSelection().rotate(Axis.Y, 90 * steps, RotateBlockMode.ALL);
    }
}
