package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.FastRandom;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Preview by spawning transient {@link BlockEntity} markers (same idea as the NPC spawn page block preview).
 */
public final class PlotPreviewSpawner {
    private static final int MAX_BLOCKS = 400;

    private PlotPreviewSpawner() {}

    public static void clear(@Nonnull Store<EntityStore> store, @Nonnull List<Ref<EntityStore>> refs) {
        for (Ref<EntityStore> r : refs) {
            if (r != null && r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
        refs.clear();
    }

    public static void rebuild(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation prefabYaw,
        @Nonnull IPrefabBuffer bufferAccess,
        @Nonnull List<Ref<EntityStore>> outRefs
    ) {
        clear(store, outRefs);
        Random random = new FastRandom();
        PrefabBufferCall call = new PrefabBufferCall(random, PrefabRotation.fromRotation(prefabYaw));
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        AtomicInteger spawned = new AtomicInteger();
        bufferAccess.forEach(
            IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, supportValue, blockRotation, filler, t, fluidId, fluidLevel) -> {
                if (filler != 0 || blockId == 0 || spawned.get() >= MAX_BLOCKS) {
                    return;
                }
                BlockType bt = blockTypeMap.getAsset(blockId);
                if (bt == null) {
                    return;
                }
                // x,y,z and blockRotation are already transformed by PrefabBufferCall (placement yaw + per-block spin).
                int wx = anchor.x + x;
                int wy = anchor.y + y;
                int wz = anchor.z + z;
                spawnOne(store, outRefs, bt.getId(), wx, wy, wz, blockRotation, spawned);
            },
            null,
            null,
            call
        );
    }

    private static void spawnOne(
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Ref<EntityStore>> outRefs,
        @Nonnull String blockTypeKey,
        int wx,
        int wy,
        int wz,
        int blockRotationIndex,
        @Nonnull AtomicInteger spawned
    ) {
        if (spawned.get() >= MAX_BLOCKS) {
            return;
        }
        spawned.incrementAndGet();
        RotationTuple rt = RotationTuple.get(blockRotationIndex);
        // BlockEntity meshes use a -Z forward; block RotationTuple yaw is aligned to chunk space. Add π so preview matches world block facing.
        float yawRad = (float) (rt.yaw().getRadians() + Math.PI);
        Vector3f euler = new Vector3f(
            (float) rt.pitch().getRadians(),
            yawRad,
            (float) rt.roll().getRadians()
        );
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(BlockEntity.getComponentType(), new BlockEntity(blockTypeKey));
        // Match vanilla BlockEntity offset: (0.5, 0, 0.5) from block min corner (not +0.5 on Y).
        Vector3d pos = new Vector3d(wx + 0.5, wy, wz + 0.5);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, euler));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(euler));
        holder.ensureComponent(UUIDComponent.getComponentType());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref != null) {
            outRefs.add(ref);
        }
    }
}
