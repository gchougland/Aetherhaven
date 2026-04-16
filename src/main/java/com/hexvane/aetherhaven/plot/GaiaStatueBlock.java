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

public final class GaiaStatueBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<GaiaStatueBlock> CODEC = BuilderCodec.builder(GaiaStatueBlock.class, GaiaStatueBlock::new)
        .append(new KeyedCodec<>("PlotId", Codec.STRING), (s, v) -> s.plotId = v != null ? v : "", s -> s.plotId)
        .add()
        .append(new KeyedCodec<>("TownId", Codec.STRING), (s, v) -> s.townId = v != null ? v : "", s -> s.townId)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, GaiaStatueBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(GaiaStatueBlock.class, "AetherhavenGaiaStatueBlock", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, GaiaStatueBlock> getComponentType() {
        ComponentType<ChunkStore, GaiaStatueBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("GaiaStatueBlock not registered");
        }
        return t;
    }

    private String plotId = "";
    private String townId = "";

    public GaiaStatueBlock() {}

    public GaiaStatueBlock(@Nonnull String plotId, @Nonnull String townId) {
        this.plotId = plotId != null ? plotId : "";
        this.townId = townId != null ? townId : "";
    }

    @Nonnull
    public String getPlotId() {
        return plotId;
    }

    @Nonnull
    public String getTownId() {
        return townId;
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new GaiaStatueBlock(plotId, townId);
    }
}
