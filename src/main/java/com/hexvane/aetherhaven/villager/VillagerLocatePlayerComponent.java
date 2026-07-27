package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Active villager locate trail session on a player. */
public final class VillagerLocatePlayerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<VillagerLocatePlayerComponent> CODEC = BuilderCodec.builder(
            VillagerLocatePlayerComponent.class,
            VillagerLocatePlayerComponent::new
        )
        .append(
            new KeyedCodec<>("TargetEntityUuid", Codec.UUID_BINARY),
            (c, u) -> c.targetEntityUuid = u,
            c -> c.targetEntityUuid
        )
        .add()
        .append(
            new KeyedCodec<>("TownId", Codec.UUID_BINARY),
            (c, u) -> c.townId = u,
            c -> c.townId
        )
        .add()
        .append(
            new KeyedCodec<>("UsingLastKnown", Codec.BOOLEAN),
            (c, v) -> c.usingLastKnown = v != null && v,
            c -> c.usingLastKnown
        )
        .add()
        .append(
            new KeyedCodec<>("TargetLabel", Codec.STRING),
            (c, s) -> c.targetLabel = s != null ? s : "",
            c -> c.targetLabel
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerLocatePlayerComponent> componentType;

    @Nullable
    private UUID targetEntityUuid;
    @Nullable
    private UUID townId;
    private boolean usingLastKnown;
    @Nonnull
    private String targetLabel = "";

    @Nonnull
    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            VillagerLocatePlayerComponent.class,
            "AetherhavenVillagerLocate",
            VillagerLocatePlayerComponent.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerLocatePlayerComponent> getComponentType() {
        ComponentType<EntityStore, VillagerLocatePlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerLocatePlayerComponent not registered");
        }
        return t;
    }

    public boolean isActive() {
        return targetEntityUuid != null && townId != null;
    }

    public boolean isActiveFor(@Nonnull UUID entityUuid) {
        return targetEntityUuid != null && targetEntityUuid.equals(entityUuid);
    }

    @Nullable
    public UUID getTargetEntityUuid() {
        return targetEntityUuid;
    }

    @Nullable
    public UUID getTownId() {
        return townId;
    }

    public boolean isUsingLastKnown() {
        return usingLastKnown;
    }

    @Nonnull
    public String getTargetLabel() {
        return targetLabel != null ? targetLabel : "";
    }

    public void setUsingLastKnown(boolean usingLastKnown) {
        this.usingLastKnown = usingLastKnown;
    }

    @Nonnull
    public static VillagerLocatePlayerComponent getOrCreate(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        VillagerLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        return c != null ? c : new VillagerLocatePlayerComponent();
    }

    public static void start(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID townId,
        @Nonnull UUID targetEntityUuid,
        @Nonnull String targetLabel,
        boolean usingLastKnown
    ) {
        VillagerLocatePlayerComponent c = getOrCreate(store, ref);
        c.targetEntityUuid = targetEntityUuid;
        c.townId = townId;
        c.targetLabel = targetLabel != null ? targetLabel : "";
        c.usingLastKnown = usingLastKnown;
        store.putComponent(ref, getComponentType(), c);
    }

    public static void clear(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        VillagerLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        if (c == null) {
            return;
        }
        putCleared(store, ref, c);
    }

    public static void clear(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        VillagerLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        if (c == null) {
            return;
        }
        putCleared(commandBuffer, ref, c);
    }

    private static void putCleared(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull VillagerLocatePlayerComponent c
    ) {
        c.targetEntityUuid = null;
        c.townId = null;
        c.targetLabel = "";
        c.usingLastKnown = false;
        store.putComponent(ref, getComponentType(), c);
    }

    private static void putCleared(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull VillagerLocatePlayerComponent c
    ) {
        VillagerLocatePlayerComponent cleared = (VillagerLocatePlayerComponent) c.clone();
        cleared.targetEntityUuid = null;
        cleared.townId = null;
        cleared.targetLabel = "";
        cleared.usingLastKnown = false;
        commandBuffer.putComponent(ref, getComponentType(), cleared);
    }

    @Nullable
    public static VillagerLocatePlayerComponent get(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        VillagerLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        return c != null && c.isActive() ? c : null;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        VillagerLocatePlayerComponent c = new VillagerLocatePlayerComponent();
        c.targetEntityUuid = this.targetEntityUuid;
        c.townId = this.townId;
        c.usingLastKnown = this.usingLastKnown;
        c.targetLabel = this.targetLabel;
        return c;
    }
}
