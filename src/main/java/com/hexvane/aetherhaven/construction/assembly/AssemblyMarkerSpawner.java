package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.UUID;
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
        @Nullable String texturePath,
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
            Model model = AssemblyMarkerModels.modelFor(kind, texturePath, scale);
            if (model == null) {
                return;
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
            holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
            Store<EntityStore> wstore = world.getEntityStore().getStore();
            wstore.addEntity(holder, AddReason.SPAWN);
            Ref<EntityStore> markerRef = ent.getReference();
            if (markerRef == null || !markerRef.isValid()) {
                return;
            }
            wstore.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(model));
            wstore.putComponent(markerRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            UUIDComponent markerUuid = wstore.getComponent(markerRef, UUIDComponent.getComponentType());
            if (markerUuid == null) {
                wstore.removeEntity(markerRef, RemoveReason.REMOVE);
                return;
            }
            st.getCellKeyToMarkerUuid().put(cellKey, markerUuid.getUuid());
            st.getCellKeyToKind().put(cellKey, kind);
            st.getCellKeyToLastScale().put(cellKey, scale);
            if (kind == AssemblyMarkerKind.PLACING && texturePath != null) {
                st.getCellKeyToLastTexture().put(cellKey, texturePath);
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
        if (markerUuid == null) {
            return;
        }
        Ref<EntityStore> ref = world.getEntityRef(markerUuid);
        if (ref != null && ref.isValid()) {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
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

    @Nonnull
    public static Vector3d cellCenter(int x, int y, int z) {
        return new Vector3d(x + 0.5, y + 0.5, z + 0.5);
    }
}
