package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotSignBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<PlotSignBlock> CODEC = BuilderCodec.builder(PlotSignBlock.class, PlotSignBlock::new)
        .append(new KeyedCodec<>("ConstructionId", Codec.STRING), (s, v) -> s.constructionId = v != null ? v : "", s -> s.constructionId)
        .add()
        .append(new KeyedCodec<>("PlotId", Codec.STRING), (s, v) -> s.plotId = v != null ? v : "", s -> s.plotId)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, PlotSignBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(PlotSignBlock.class, "AetherhavenPlotSign", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, PlotSignBlock> getComponentType() {
        ComponentType<ChunkStore, PlotSignBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlotSignBlock not registered");
        }
        return t;
    }

    private String constructionId = "";
    private String plotId = "";

    public PlotSignBlock() {}

    public PlotSignBlock(@Nullable String constructionId) {
        this.constructionId = constructionId != null ? constructionId : "";
    }

    public PlotSignBlock(@Nullable String constructionId, @Nullable String plotId) {
        this.constructionId = constructionId != null ? constructionId : "";
        this.plotId = plotId != null ? plotId : "";
    }

    public String getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(String constructionId) {
        this.constructionId = constructionId != null ? constructionId : "";
    }

    @Nonnull
    public String getPlotId() {
        return plotId;
    }

    public void setPlotId(@Nonnull String plotId) {
        this.plotId = plotId != null ? plotId : "";
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new PlotSignBlock(constructionId, plotId);
    }
}
