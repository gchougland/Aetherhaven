package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Spawns and removes building staff assembly preview marker entities on the world thread. */
public final class AssemblyMarkerSpawner {
    private AssemblyMarkerSpawner() {}

    public static void spawnMarker(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        long cellKey,
        int x,
        int y,
        int z,
        @Nonnull AssemblyMarkerKind kind,
        @Nullable String blockTypeOrTexture,
        float scale
    ) {
        try {
            Ref<EntityStore> ownerRef = world.getEntityRef(ownerPlayerEntityUuid);
            if (ownerRef == null || !ownerRef.isValid()) {
                return;
            }
            Store<EntityStore> store = ownerRef.getStore();
            BuildingStaffPreviewPlayerComponent st =
                store.getComponent(ownerRef, BuildingStaffPreviewPlayerComponent.getComponentType());
            if (st == null) {
                st = new BuildingStaffPreviewPlayerComponent();
                store.addComponent(ownerRef, BuildingStaffPreviewPlayerComponent.getComponentType(), st);
            }
            BuildingStaffMarkerEntity ent = new BuildingStaffMarkerEntity();
            if (!EntityModule.get().isKnown(ent)) {
                return;
            }
            ent.loadIntoWorld(world);
            ent.setOwnerPlayerUuid(ownerPlayerEntityUuid);
            Rotation3f rot = new Rotation3f(0.0F, 0.0F, 0.0F);
            ent.unloadFromWorld();
            Holder<EntityStore> holder = ent.toHolder();
            Vector3d pos = cellCenter(x, y, z);
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
            holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);

            if (kind == AssemblyMarkerKind.PLACING) {
                String blockTypeKey =
                    blockTypeOrTexture != null && !blockTypeOrTexture.isBlank()
                        ? blockTypeOrTexture.trim()
                        : AssemblyMarkerTextureResolver.blockTypeKeyForPlacingBlockId(Integer.MIN_VALUE);
                holder.addComponent(BlockEntity.getComponentType(), new BlockEntity(blockTypeKey));
                holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
            } else if (AssemblyMarkerModels.modelFor(kind, blockTypeOrTexture, scale) == null) {
                return;
            }

            Store<EntityStore> wstore = world.getEntityStore().getStore();
            wstore.addEntity(holder, AddReason.SPAWN);
            Ref<EntityStore> markerRef = ent.getReference();
            if (markerRef == null || !markerRef.isValid()) {
                return;
            }
            if (kind == AssemblyMarkerKind.PLACING) {
                // Legacy Entity types get Velocity auto-added; BlockEntity physics then ticks and NPE-removes us.
                // Preview markers are static visuals — drop Velocity so BlockEntitySystems.Ticking never matches.
                if (wstore.getArchetype(markerRef).contains(Velocity.getComponentType())) {
                    wstore.removeComponent(markerRef, Velocity.getComponentType());
                }
            } else {
                Model model = AssemblyMarkerModels.modelFor(kind, blockTypeOrTexture, scale);
                if (model == null) {
                    wstore.removeEntity(markerRef, RemoveReason.REMOVE);
                    return;
                }
                wstore.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(model));
                wstore.putComponent(markerRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            }
            UUIDComponent markerUuid = wstore.getComponent(markerRef, UUIDComponent.getComponentType());
            if (markerUuid == null) {
                wstore.removeEntity(markerRef, RemoveReason.REMOVE);
                return;
            }
            st.getCellKeyToMarkerUuid().put(cellKey, markerUuid.getUuid());
            st.getCellKeyToKind().put(cellKey, kind);
            st.getCellKeyToLastScale().put(cellKey, scale);
            if (kind == AssemblyMarkerKind.PLACING && blockTypeOrTexture != null) {
                st.getCellKeyToLastBlockKey().put(cellKey, blockTypeOrTexture);
            }
        } finally {
            Ref<EntityStore> ownerRef2 = world.getEntityRef(ownerPlayerEntityUuid);
            if (ownerRef2 != null && ownerRef2.isValid()) {
                BuildingStaffPreviewPlayerComponent st2 =
                    ownerRef2.getStore().getComponent(ownerRef2, BuildingStaffPreviewPlayerComponent.getComponentType());
                if (st2 != null) {
                    st2.getPendingSpawnCellKeys().remove(cellKey);
                }
            }
        }
    }

    public static void removeMarkerByUuid(@Nonnull World world, @Nullable UUID markerUuid) {
        removeMarkerByUuid(world, markerUuid, null);
    }

    public static void removeMarkerByUuid(
        @Nonnull World world,
        @Nullable UUID markerUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (markerUuid == null) {
            return;
        }
        Ref<EntityStore> ref = world.getEntityRef(markerUuid);
        removeMarkerRef(ref, commandBuffer, world.getEntityStore().getStore());
    }

    /** Removes every preview marker owned by {@code ownerPlayerEntityUuid}, including untracked orphans. */
    public static void removeAllForOwner(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(BuildingStaffMarkerEntity.getComponentType()),
            (chunk, chunkCommandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    BuildingStaffMarkerEntity marker = chunk.getComponent(i, BuildingStaffMarkerEntity.getComponentType());
                    if (marker == null || !ownerPlayerEntityUuid.equals(marker.getOwnerPlayerUuid())) {
                        continue;
                    }
                    Ref<EntityStore> markerRef = chunk.getReferenceTo(i);
                    if (markerRef.isValid()) {
                        toRemove.add(markerRef);
                    }
                }
            }
        );
        removeMarkerRefs(toRemove, commandBuffer, store);
    }

    /** World shutdown / load hygiene: drop every transient assembly preview marker in this world. */
    public static void purgeAllInWorld(@Nonnull World world) {
        if (!world.isAlive()) {
            return;
        }
        // RemoveWorldEvent (e.g. prefab-editor exit) can run off the world thread; Store requires it.
        if (!world.isInThread()) {
            CompletableFuture.runAsync(() -> purgeAllInWorld(world), world).join();
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(BuildingStaffMarkerEntity.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> markerRef = chunk.getReferenceTo(i);
                    if (markerRef.isValid()) {
                        toRemove.add(markerRef);
                    }
                }
            }
        );
        removeMarkerRefs(toRemove, null, store);
    }

    private static void removeMarkerRefs(
        @Nonnull Iterable<Ref<EntityStore>> refs,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store
    ) {
        Set<Ref<EntityStore>> seen = new HashSet<>();
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid() || !seen.add(ref)) {
                continue;
            }
            removeMarkerRef(ref, commandBuffer, store);
        }
    }

    private static void removeMarkerRef(
        @Nullable Ref<EntityStore> ref,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store
    ) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        if (commandBuffer != null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        } else {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    public static void applyModelUpdate(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> markerRef,
        @Nonnull Model model
    ) {
        commandBuffer.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(model));
        commandBuffer.putComponent(markerRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
    }

    public static void applyPlacingScale(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> markerRef,
        float scale
    ) {
        EntityScaleComponent existing = commandBuffer.getComponent(markerRef, EntityScaleComponent.getComponentType());
        if (existing != null) {
            existing.setScale(scale);
            return;
        }
        commandBuffer.addComponent(markerRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
    }

    public static void applyPlacingScaleImmediate(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> markerRef,
        float scale
    ) {
        EntityScaleComponent existing = store.getComponent(markerRef, EntityScaleComponent.getComponentType());
        if (existing != null) {
            existing.setScale(scale);
            return;
        }
        store.addComponent(markerRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
    }

    public static void applyPlacingBlockType(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> markerRef,
        @Nonnull String blockTypeKey
    ) {
        BlockEntity existing = commandBuffer.getComponent(markerRef, BlockEntity.getComponentType());
        if (existing != null) {
            existing.setBlockTypeKey(blockTypeKey, markerRef, commandBuffer);
            return;
        }
        commandBuffer.addComponent(markerRef, BlockEntity.getComponentType(), new BlockEntity(blockTypeKey));
    }

    public static void applyPlacingBlockTypeImmediate(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> markerRef,
        @Nonnull String blockTypeKey
    ) {
        BlockEntity existing = store.getComponent(markerRef, BlockEntity.getComponentType());
        if (existing != null) {
            existing.setBlockTypeKey(blockTypeKey, markerRef, store);
            return;
        }
        store.addComponent(markerRef, BlockEntity.getComponentType(), new BlockEntity(blockTypeKey));
    }

    @Nonnull
    public static Vector3d cellCenter(int x, int y, int z) {
        return new Vector3d(x + 0.5, y + 0.5, z + 0.5);
    }
}
