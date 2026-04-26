package com.hexvane.aetherhaven.purification;

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

/**
 * Transient spin-preview for mob spawn points; only the holder of purification powder should see it
 * ({@link Entity#isHiddenFromLivingEntity}).
 */
public final class PurificationPreviewEntity extends Entity {
    @Nonnull
    public static final BuilderCodec<PurificationPreviewEntity> CODEC =
        BuilderCodec.builder(PurificationPreviewEntity.class, PurificationPreviewEntity::new, Entity.CODEC)
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
    public static com.hypixel.hytale.component.ComponentType<EntityStore, PurificationPreviewEntity> getComponentType() {
        return EntityModule.get().getComponentType(PurificationPreviewEntity.class);
    }

    public PurificationPreviewEntity() {}

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
        @Nonnull Ref<EntityStore> labelRef,
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
