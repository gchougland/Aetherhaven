package com.hexvane.aetherhaven.town;

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

/** Active plot locate trail session on a player. */
public final class PlotLocatePlayerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlotLocatePlayerComponent> CODEC = BuilderCodec.builder(
            PlotLocatePlayerComponent.class,
            PlotLocatePlayerComponent::new
        )
        .append(
            new KeyedCodec<>("PlotId", Codec.UUID_BINARY),
            (c, u) -> c.plotId = u,
            c -> c.plotId
        )
        .add()
        .append(
            new KeyedCodec<>("TownId", Codec.UUID_BINARY),
            (c, u) -> c.townId = u,
            c -> c.townId
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
    private static volatile ComponentType<EntityStore, PlotLocatePlayerComponent> componentType;

    @Nullable
    private UUID plotId;
    @Nullable
    private UUID townId;
    @Nonnull
    private String targetLabel = "";

    @Nonnull
    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            PlotLocatePlayerComponent.class,
            "AetherhavenPlotLocate",
            PlotLocatePlayerComponent.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, PlotLocatePlayerComponent> getComponentType() {
        ComponentType<EntityStore, PlotLocatePlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlotLocatePlayerComponent not registered");
        }
        return t;
    }

    public boolean isActive() {
        return plotId != null && townId != null;
    }

    public boolean isActiveFor(@Nonnull UUID plotUuid) {
        return plotId != null && plotId.equals(plotUuid);
    }

    @Nullable
    public UUID getPlotId() {
        return plotId;
    }

    @Nullable
    public UUID getTownId() {
        return townId;
    }

    @Nonnull
    public String getTargetLabel() {
        return targetLabel != null ? targetLabel : "";
    }

    @Nonnull
    public static PlotLocatePlayerComponent getOrCreate(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        PlotLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        return c != null ? c : new PlotLocatePlayerComponent();
    }

    public static void start(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        @Nonnull String targetLabel
    ) {
        PlotLocatePlayerComponent c = getOrCreate(store, ref);
        c.plotId = plotId;
        c.townId = townId;
        c.targetLabel = targetLabel != null ? targetLabel : "";
        store.putComponent(ref, getComponentType(), c);
    }

    public static void clear(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        PlotLocatePlayerComponent c = store.getComponent(ref, getComponentType());
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
        PlotLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        if (c == null) {
            return;
        }
        putCleared(commandBuffer, ref, c);
    }

    private static void putCleared(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlotLocatePlayerComponent c
    ) {
        c.plotId = null;
        c.townId = null;
        c.targetLabel = "";
        store.putComponent(ref, getComponentType(), c);
    }

    private static void putCleared(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlotLocatePlayerComponent c
    ) {
        PlotLocatePlayerComponent cleared = (PlotLocatePlayerComponent) c.clone();
        cleared.plotId = null;
        cleared.townId = null;
        cleared.targetLabel = "";
        commandBuffer.putComponent(ref, getComponentType(), cleared);
    }

    @Nullable
    public static PlotLocatePlayerComponent get(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        PlotLocatePlayerComponent c = store.getComponent(ref, getComponentType());
        return c != null && c.isActive() ? c : null;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlotLocatePlayerComponent c = new PlotLocatePlayerComponent();
        c.plotId = this.plotId;
        c.townId = this.townId;
        c.targetLabel = this.targetLabel;
        return c;
    }
}
