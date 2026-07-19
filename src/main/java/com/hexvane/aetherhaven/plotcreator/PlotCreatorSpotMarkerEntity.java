package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-player plot creator important-spot preview marker; only visible to the session owner. */
public final class PlotCreatorSpotMarkerEntity extends Entity {
    @Nonnull
    public static final BuilderCodec<PlotCreatorSpotMarkerEntity> CODEC =
        BuilderCodec.builder(PlotCreatorSpotMarkerEntity.class, PlotCreatorSpotMarkerEntity::new, Entity.CODEC)
            .append(
                new KeyedCodec<>("OwnerPlayerUuid", Codec.UUID_BINARY),
                (e, u) -> e.ownerPlayerUuid = u,
                e -> e.ownerPlayerUuid
            )
            .add()
            .build();

    @Nullable
    private UUID ownerPlayerUuid;

    @Nullable
    public static com.hypixel.hytale.component.ComponentType<EntityStore, PlotCreatorSpotMarkerEntity> getComponentType() {
        return EntityModule.get().getComponentType(PlotCreatorSpotMarkerEntity.class);
    }

    public PlotCreatorSpotMarkerEntity() {}

    public void setOwnerPlayerUuid(@Nonnull UUID ownerPlayerUuid) {
        this.ownerPlayerUuid = ownerPlayerUuid;
    }

    @Nullable
    public UUID getOwnerPlayerUuid() {
        return ownerPlayerUuid;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isHiddenFromLivingEntity(
        @Nonnull Ref<EntityStore> markerRef,
        @Nonnull Ref<EntityStore> viewerRef,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        if (ownerPlayerUuid == null) {
            return false;
        }
        UUIDComponent viewerUuid = componentAccessor.getComponent(viewerRef, UUIDComponent.getComponentType());
        return viewerUuid == null || !ownerPlayerUuid.equals(viewerUuid.getUuid());
    }
}
