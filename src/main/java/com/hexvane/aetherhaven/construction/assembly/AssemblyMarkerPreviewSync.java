package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Syncs desired assembly preview cells to per-player marker entities. */
public final class AssemblyMarkerPreviewSync {
    public record DesiredMarker(
        int x,
        int y,
        int z,
        @Nonnull AssemblyMarkerKind kind,
        /** Placing: block type key. Clearing: unused (null). */
        @Nullable String blockTypeKey,
        double grow01
    ) {
        public long cellKey() {
            return BuildingStaffPreviewPlayerComponent.packCellKey(x, y, z);
        }
    }

    private AssemblyMarkerPreviewSync() {}

    public static void clearAllMarkers(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        clearAllMarkers(world, playerRef, store, null);
    }

    public static void clearAllMarkers(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        BuildingStaffPreviewPlayerComponent st =
            store.getComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType());
        UUIDComponent ownerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (ownerUuid != null) {
            AssemblyMarkerSpawner.removeAllForOwner(world, ownerUuid.getUuid(), commandBuffer);
        }
        if (st != null) {
            st.clearAllTracking();
        }
    }

    public static void syncMarkers(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<DesiredMarker> desired,
        boolean syncClearing,
        boolean syncPlacing,
        float dt
    ) {
        syncMarkersInternal(
            world,
            playerRef,
            store,
            commandBuffer,
            ownerPlayerEntityUuid,
            desired,
            syncClearing,
            syncPlacing,
            true,
            dt
        );
    }

    /** World-queue refresh path (outside entity tick); uses {@link Store} writes directly. */
    public static void syncMarkersImmediate(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<DesiredMarker> desired,
        boolean syncClearing,
        boolean syncPlacing
    ) {
        syncMarkersInternal(
            world,
            playerRef,
            store,
            null,
            ownerPlayerEntityUuid,
            desired,
            syncClearing,
            syncPlacing,
            false,
            0.0f
        );
    }

    private static void syncMarkersInternal(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<DesiredMarker> desired,
        boolean syncClearing,
        boolean syncPlacing,
        boolean useCommandBuffer,
        float dt
    ) {
        if (useCommandBuffer && commandBuffer != null) {
            ensurePreviewState(commandBuffer, playerRef);
        } else if (store.getComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType()) == null) {
            store.addComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType(), new BuildingStaffPreviewPlayerComponent());
        }
        BuildingStaffPreviewPlayerComponent st =
            useCommandBuffer && commandBuffer != null
                ? commandBuffer.getComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType())
                : store.getComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType());
        if (st == null) {
            return;
        }

        Map<Long, DesiredMarker> desiredByKey = new HashMap<>(desired.size());
        for (DesiredMarker d : desired) {
            if (d.kind() == AssemblyMarkerKind.CLEARING && !syncClearing) {
                continue;
            }
            if (d.kind() == AssemblyMarkerKind.PLACING && !syncPlacing) {
                continue;
            }
            desiredByKey.put(d.cellKey(), d);
        }

        Set<Long> desiredKeys = desiredByKey.keySet();
        Iterator<Map.Entry<Long, UUID>> it = st.getCellKeyToMarkerUuid().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, UUID> e = it.next();
            long key = e.getKey();
            if (desiredKeys.contains(key) || st.getPendingSpawnCellKeys().contains(key)) {
                continue;
            }
            UUID markerId = e.getValue();
            it.remove();
            st.getCellKeyToLastScale().remove(key);
            st.getCellKeyToLastBlockKey().remove(key);
            st.getCellKeyToKind().remove(key);
            removeMarkerEntity(world, markerId, commandBuffer, useCommandBuffer);
        }

        for (DesiredMarker d : desiredByKey.values()) {
            long key = d.cellKey();
            AssemblyMarkerKind kind = d.kind();
            float scale = AssemblyMarkerModels.scaleForGrow01(kind, d.grow01());
            String blockTypeKey = d.blockTypeKey();

            UUID existingId = st.getCellKeyToMarkerUuid().get(key);
            if (existingId == null) {
                queueSpawn(world, ownerPlayerEntityUuid, st, key, d, kind, blockTypeKey, scale);
                continue;
            }

            Ref<EntityStore> markerRef = world.getEntityRef(existingId);
            if (markerRef == null || !markerRef.isValid()) {
                st.getCellKeyToMarkerUuid().remove(key);
                st.getCellKeyToLastScale().remove(key);
                st.getCellKeyToLastBlockKey().remove(key);
                st.getCellKeyToKind().remove(key);
                queueSpawn(world, ownerPlayerEntityUuid, st, key, d, kind, blockTypeKey, scale);
                continue;
            }

            if (kind == AssemblyMarkerKind.PLACING) {
                updatePlacingMarker(
                    store,
                    commandBuffer,
                    useCommandBuffer,
                    st,
                    key,
                    markerRef,
                    blockTypeKey,
                    scale,
                    d.grow01(),
                    dt
                );
            } else {
                updateClearingMarker(store, commandBuffer, useCommandBuffer, st, key, markerRef, scale);
            }
            st.getCellKeyToKind().put(key, kind);
        }
    }

    private static void queueSpawn(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull BuildingStaffPreviewPlayerComponent st,
        long key,
        @Nonnull DesiredMarker d,
        @Nonnull AssemblyMarkerKind kind,
        @Nullable String blockTypeKey,
        float scale
    ) {
        if (!st.getPendingSpawnCellKeys().add(key)) {
            return;
        }
        final int sx = d.x();
        final int sy = d.y();
        final int sz = d.z();
        final String keyCopy = blockTypeKey;
        final float scaleCopy = scale;
        world.execute(
            () ->
                AssemblyMarkerSpawner.spawnMarker(
                    world,
                    ownerPlayerEntityUuid,
                    key,
                    sx,
                    sy,
                    sz,
                    kind,
                    keyCopy,
                    scaleCopy
                )
        );
    }

    private static void updatePlacingMarker(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        boolean useCommandBuffer,
        @Nonnull BuildingStaffPreviewPlayerComponent st,
        long key,
        @Nonnull Ref<EntityStore> markerRef,
        @Nullable String blockTypeKey,
        float scale,
        double grow01,
        float dt
    ) {
        Float prevScale = st.getCellKeyToLastScale().get(key);
        float prevScaleVal = prevScale == null ? -1.0F : prevScale.floatValue();
        String prevKey = st.getCellKeyToLastBlockKey().get(key);
        boolean scaleChanged = AssemblyMarkerModels.scaleChanged(prevScaleVal, scale);
        boolean blockChanged = blockTypeKey != null && !Objects.equals(prevKey, blockTypeKey);

        if (blockChanged && blockTypeKey != null) {
            if (useCommandBuffer && commandBuffer != null) {
                AssemblyMarkerSpawner.applyPlacingBlockType(commandBuffer, markerRef, blockTypeKey);
            } else {
                AssemblyMarkerSpawner.applyPlacingBlockTypeImmediate(store, markerRef, blockTypeKey);
            }
            st.getCellKeyToLastBlockKey().put(key, blockTypeKey);
        }
        if (scaleChanged) {
            if (useCommandBuffer && commandBuffer != null) {
                AssemblyMarkerSpawner.applyPlacingScale(commandBuffer, markerRef, scale);
            } else {
                AssemblyMarkerSpawner.applyPlacingScaleImmediate(store, markerRef, scale);
            }
            st.getCellKeyToLastScale().put(key, scale);
        }

        if (dt > 0.0f) {
            TransformComponent transform =
                useCommandBuffer && commandBuffer != null
                    ? commandBuffer.getComponent(markerRef, TransformComponent.getComponentType())
                    : store.getComponent(markerRef, TransformComponent.getComponentType());
            if (transform != null) {
                Rotation3f rot = transform.getRotation();
                rot.addYaw(AssemblyMarkerModels.spinYawRadiansPerSec(grow01) * dt);
            }
        }
    }

    private static void updateClearingMarker(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        boolean useCommandBuffer,
        @Nonnull BuildingStaffPreviewPlayerComponent st,
        long key,
        @Nonnull Ref<EntityStore> markerRef,
        float scale
    ) {
        Float prevScale = st.getCellKeyToLastScale().get(key);
        float prevScaleVal = prevScale == null ? -1.0F : prevScale.floatValue();
        if (!AssemblyMarkerModels.scaleChanged(prevScaleVal, scale)) {
            return;
        }
        Model model = AssemblyMarkerModels.modelFor(AssemblyMarkerKind.CLEARING, null, scale);
        if (model == null) {
            return;
        }
        if (useCommandBuffer && commandBuffer != null) {
            AssemblyMarkerSpawner.applyModelUpdate(commandBuffer, markerRef, model);
        } else {
            store.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(model));
            store.putComponent(markerRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        }
        st.getCellKeyToLastScale().put(key, scale);
    }

    @Nonnull
    public static List<DesiredMarker> buildDesiredFromCells(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull List<Vector3i> clearingCells,
        @Nonnull List<Vector3i> placingCells,
        boolean staffInHand,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs
    ) {
        ArrayList<DesiredMarker> out = new ArrayList<>(clearingCells.size() + placingCells.size());
        for (Vector3i cell : clearingCells) {
            double grow01 = grow01ForCell(cell, staffInHand, channel, nowNs);
            out.add(new DesiredMarker(cell.x, cell.y, cell.z, AssemblyMarkerKind.CLEARING, null, grow01));
        }
        for (Vector3i cell : placingCells) {
            double grow01 = grow01ForCell(cell, staffInHand, channel, nowNs);
            int blockId = AssemblyMarkerTextureResolver.resolvePlacingBlockId(world, plugin, cell);
            String blockTypeKey = AssemblyMarkerTextureResolver.blockTypeKeyForPlacingBlockId(blockId);
            out.add(new DesiredMarker(cell.x, cell.y, cell.z, AssemblyMarkerKind.PLACING, blockTypeKey, grow01));
        }
        return out;
    }

    /** Quantize marker grow steps to limit per-tick model rebuilds during the 0.5s channel. */
    private static final int GROW_DISPLAY_STEPS = 12;

    private static double grow01ForCell(
        @Nonnull Vector3i cell,
        boolean staffInHand,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs
    ) {
        if (staffInHand
            && channel != null
            && channel.cellMatchesBrush(cell.x, cell.y, cell.z)
            && channel.isFresh(nowNs)) {
            return quantizeGrow01(channel.channelGrow01(nowNs));
        }
        return 0.0;
    }

    private static double quantizeGrow01(double grow01) {
        if (grow01 <= 0.0) {
            return 0.0;
        }
        if (grow01 >= 1.0) {
            return 1.0;
        }
        double step = 1.0 / GROW_DISPLAY_STEPS;
        return Math.floor(grow01 / step) * step;
    }

    private static void removeMarkerEntity(
        @Nonnull World world,
        @Nullable UUID markerUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        boolean preferCommandBuffer
    ) {
        if (preferCommandBuffer && commandBuffer != null) {
            AssemblyMarkerSpawner.removeMarkerByUuid(world, markerUuid, commandBuffer);
        } else {
            AssemblyMarkerSpawner.removeMarkerByUuid(world, markerUuid, null);
        }
    }

    private static void ensurePreviewState(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        if (commandBuffer.getComponent(playerRef, BuildingStaffPreviewPlayerComponent.getComponentType()) == null) {
            commandBuffer.addComponent(
                playerRef,
                BuildingStaffPreviewPlayerComponent.getComponentType(),
                new BuildingStaffPreviewPlayerComponent()
            );
        }
    }

    @Nonnull
    public static UUID requireOwnerEntityUuid(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            throw new IllegalStateException("Player missing UUIDComponent");
        }
        return uc.getUuid();
    }
}
