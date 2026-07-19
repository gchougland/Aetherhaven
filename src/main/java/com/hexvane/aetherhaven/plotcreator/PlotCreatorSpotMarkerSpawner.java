package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.assembly.AssemblyMarkerTextureResolver;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns / removes transient plot creator important-spot markers on the world thread. */
public final class PlotCreatorSpotMarkerSpawner {
    /** Visible prop scale for Building_Marker during plot creator viz. */
    public static final float MARKER_SCALE = 1.75f;

    private PlotCreatorSpotMarkerSpawner() {}

    @Nullable
    public static UUID spawnMarker(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull PlotCreatorSpotMarkerCollector.DesiredSpotMarker desired
    ) {
        Model model = modelForTexture(desired.texturePath(), MARKER_SCALE);
        if (model == null) {
            return null;
        }
        PlotCreatorSpotMarkerEntity ent = new PlotCreatorSpotMarkerEntity();
        if (!EntityModule.get().isKnown(ent)) {
            return null;
        }
        ent.loadIntoWorld(world);
        ent.setOwnerPlayerUuid(ownerPlayerEntityUuid);
        float yaw = desired.facingYawWorldRadians() != null ? desired.facingYawWorldRadians() : 0.0F;
        Rotation3f rot = new Rotation3f(0.0F, yaw, 0.0F);
        ent.unloadFromWorld();
        Holder<EntityStore> holder = ent.toHolder();
        Vector3d pos = new Vector3d(desired.x() + 0.5, desired.y() + 0.5, desired.z() + 0.5);
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        String plate = desired.nameplateText();
        if (plate != null && !plate.isBlank()) {
            holder.putComponent(Nameplate.getComponentType(), new Nameplate(plate));
            holder.putComponent(
                DisplayNameComponent.getComponentType(),
                new DisplayNameComponent(Message.raw(plate))
            );
        }
        Store<EntityStore> wstore = world.getEntityStore().getStore();
        wstore.addEntity(holder, AddReason.SPAWN);
        Ref<EntityStore> markerRef = ent.getReference();
        if (markerRef == null || !markerRef.isValid()) {
            return null;
        }
        wstore.putComponent(markerRef, ModelComponent.getComponentType(), new ModelComponent(model));
        wstore.putComponent(markerRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        UUIDComponent markerUuid = wstore.getComponent(markerRef, UUIDComponent.getComponentType());
        if (markerUuid == null) {
            wstore.removeEntity(markerRef, RemoveReason.REMOVE);
            return null;
        }
        return markerUuid.getUuid();
    }

    @Nullable
    public static Model modelForTexture(@Nullable String texturePath) {
        return modelForTexture(texturePath, MARKER_SCALE);
    }

    @Nullable
    public static Model modelForTexture(@Nullable String texturePath, float scale) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(AetherhavenConstants.MODEL_ASSET_BUILDING_MARKER);
        if (asset == null) {
            return null;
        }
        Model template = Model.createUnitScaleModel(asset);
        String tex =
            AssemblyMarkerTextureResolver.entitySafeTexture(
                texturePath != null && !texturePath.isBlank() ? texturePath.trim() : template.getTexture()
            );
        return copyWithTextureAndScale(template, tex, scale);
    }

    @Nonnull
    private static Model copyWithTextureAndScale(@Nonnull Model template, @Nullable String texture, float scale) {
        return new Model(
            template.getModelAssetId(),
            scale,
            template.getRandomAttachmentIds(),
            template.getAttachments(),
            template.getBoundingBox(),
            template.getModel(),
            texture,
            template.getGradientSet(),
            template.getGradientId(),
            template.getEyeHeight(),
            template.getCrouchOffset(),
            template.getSittingOffset(),
            template.getSleepingOffset(),
            template.getAnimationSetMap(),
            template.getCamera(),
            template.getLight(),
            template.getParticles(),
            template.getTrails(),
            template.getPhysicsValues(),
            template.getDetailBoxes(),
            template.getPhobia(),
            template.getPhobiaModelAssetId()
        );
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
        if (ref == null || !ref.isValid()) {
            return;
        }
        if (commandBuffer != null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        } else {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    public static void removeAllForOwner(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(PlotCreatorSpotMarkerEntity.getComponentType()),
            (chunk, chunkCommandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    PlotCreatorSpotMarkerEntity marker =
                        chunk.getComponent(i, PlotCreatorSpotMarkerEntity.getComponentType());
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

    /** World load hygiene: drop every transient plot creator spot marker in this world. */
    public static void purgeAllInWorld(@Nonnull World world) {
        if (!world.isAlive()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(PlotCreatorSpotMarkerEntity.getComponentType()),
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
            if (commandBuffer != null) {
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            } else {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }
}
