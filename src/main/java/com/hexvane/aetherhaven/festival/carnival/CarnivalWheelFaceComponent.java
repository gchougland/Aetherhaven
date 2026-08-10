package com.hexvane.aetherhaven.festival.carnival;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Marks the spinning carnival wheel face prop for a town. */
public final class CarnivalWheelFaceComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<CarnivalWheelFaceComponent> CODEC =
        BuilderCodec
            .builder(CarnivalWheelFaceComponent.class, CarnivalWheelFaceComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .append(new KeyedCodec<>("BaseYaw", Codec.FLOAT), (o, v) -> o.baseYaw = v, o -> o.baseYaw)
            .add()
            .append(new KeyedCodec<>("Roll", Codec.FLOAT), (o, v) -> o.roll = v, o -> o.roll)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, CarnivalWheelFaceComponent> componentType;

    @Nullable
    private String townId;
    private float baseYaw;
    private float roll = CarnivalIds.WHEEL_IDLE_OFFSET_RAD;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(CarnivalWheelFaceComponent.class, "AetherhavenCarnivalWheelFace", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, CarnivalWheelFaceComponent> getComponentType() {
        ComponentType<EntityStore, CarnivalWheelFaceComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("CarnivalWheelFaceComponent not registered");
        }
        return type;
    }

    @Nullable
    public UUID getTownId() {
        if (townId == null || townId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(townId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTownId(@Nullable UUID townId) {
        this.townId = townId != null ? townId.toString() : null;
    }

    public float getBaseYaw() {
        return baseYaw;
    }

    public void setBaseYaw(float baseYaw) {
        this.baseYaw = baseYaw;
    }

    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        CarnivalWheelFaceComponent copy = new CarnivalWheelFaceComponent();
        copy.townId = townId;
        copy.baseYaw = baseYaw;
        copy.roll = roll;
        return copy;
    }
}
