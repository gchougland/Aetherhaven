package com.hexvane.aetherhaven.plotcreator;

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

/** Marks a plot-creator important-spot villager preview NPC (owner-only, non-autonomous). */
public final class PlotCreatorSpotPreview implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlotCreatorSpotPreview> CODEC =
        BuilderCodec.builder(PlotCreatorSpotPreview.class, PlotCreatorSpotPreview::new)
            .append(new KeyedCodec<>("OwnerPlayerUuid", Codec.UUID_BINARY), (c, v) -> c.ownerPlayerUuid = v, c -> c.ownerPlayerUuid)
            .add()
            .append(new KeyedCodec<>("PreviewKey", Codec.LONG), (c, v) -> c.previewKey = v, c -> c.previewKey)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlotCreatorSpotPreview> componentType;

    @Nullable
    private UUID ownerPlayerUuid;
    private long previewKey;
    /** Not serialized; pose applied once after spawn. */
    private transient boolean poseApplied;
    /** Not serialized; last work/leisure beat epoch ms. */
    private transient long lastWorkBeatEpochMs;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(PlotCreatorSpotPreview.class, "AetherhavenPlotCreatorSpotPreview", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlotCreatorSpotPreview> getComponentType() {
        ComponentType<EntityStore, PlotCreatorSpotPreview> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlotCreatorSpotPreview not registered");
        }
        return t;
    }

    public PlotCreatorSpotPreview() {}

    public PlotCreatorSpotPreview(@Nonnull UUID ownerPlayerUuid, long previewKey) {
        this.ownerPlayerUuid = ownerPlayerUuid;
        this.previewKey = previewKey;
    }

    @Nullable
    public UUID getOwnerPlayerUuid() {
        return ownerPlayerUuid;
    }

    public long getPreviewKey() {
        return previewKey;
    }

    public boolean isPoseApplied() {
        return poseApplied;
    }

    public void setPoseApplied(boolean poseApplied) {
        this.poseApplied = poseApplied;
    }

    public long getLastWorkBeatEpochMs() {
        return lastWorkBeatEpochMs;
    }

    public void setLastWorkBeatEpochMs(long lastWorkBeatEpochMs) {
        this.lastWorkBeatEpochMs = lastWorkBeatEpochMs;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlotCreatorSpotPreview copy = new PlotCreatorSpotPreview();
        copy.ownerPlayerUuid = ownerPlayerUuid;
        copy.previewKey = previewKey;
        copy.poseApplied = poseApplied;
        copy.lastWorkBeatEpochMs = lastWorkBeatEpochMs;
        return copy;
    }
}
