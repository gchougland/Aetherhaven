package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Tags an entity spawned as part of a placed instance (prop, and future placeable kinds) so it can be found and
 * removed by instance id later (packaging, teardown).
 */
public final class AetherhavenPlacedInstance implements Component<EntityStore> {
    public enum Kind {
        PROP
    }

    @Nonnull
    public static final BuilderCodec<AetherhavenPlacedInstance> CODEC =
        BuilderCodec
            .builder(AetherhavenPlacedInstance.class, AetherhavenPlacedInstance::new)
            .append(new KeyedCodec<>("InstanceId", Codec.STRING), (o, v) -> o.instanceId = v, o -> o.instanceId)
            .add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (o, v) -> o.kind = v, o -> o.kind)
            .add()
            .build();

    private static volatile ComponentType<EntityStore, AetherhavenPlacedInstance> componentType;

    @Nonnull
    private String instanceId = "";

    @Nonnull
    private String kind = Kind.PROP.name();

    public AetherhavenPlacedInstance() {}

    public AetherhavenPlacedInstance(@Nonnull String instanceId, @Nonnull Kind kind) {
        this.instanceId = instanceId;
        this.kind = kind.name();
    }

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                AetherhavenPlacedInstance.class,
                "AetherhavenPlacedInstance",
                AetherhavenPlacedInstance.CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, AetherhavenPlacedInstance> getComponentType() {
        ComponentType<EntityStore, AetherhavenPlacedInstance> t = componentType;
        if (t == null) {
            throw new IllegalStateException("AetherhavenPlacedInstance not registered");
        }
        return t;
    }

    @Nonnull
    public String getInstanceId() {
        return instanceId;
    }

    @Nonnull
    public Kind getKind() {
        try {
            return Kind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return Kind.PROP;
        }
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        AetherhavenPlacedInstance c = new AetherhavenPlacedInstance();
        c.instanceId = this.instanceId;
        c.kind = this.kind;
        return c;
    }
}
