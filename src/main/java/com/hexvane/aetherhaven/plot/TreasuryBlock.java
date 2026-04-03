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

public final class TreasuryBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<TreasuryBlock> CODEC = BuilderCodec.builder(TreasuryBlock.class, TreasuryBlock::new)
        .append(new KeyedCodec<>("PlotId", Codec.STRING), (s, v) -> s.plotId = v != null ? v : "", s -> s.plotId)
        .add()
        .append(new KeyedCodec<>("TownId", Codec.STRING), (s, v) -> s.townId = v != null ? v : "", s -> s.townId)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, TreasuryBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(TreasuryBlock.class, "AetherhavenTreasuryBlock", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, TreasuryBlock> getComponentType() {
        ComponentType<ChunkStore, TreasuryBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("TreasuryBlock not registered");
        }
        return t;
    }

    private String plotId = "";
    private String townId = "";

    public TreasuryBlock() {}

    public TreasuryBlock(@Nonnull String plotId, @Nonnull String townId) {
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

    public void setPlotId(@Nonnull String plotId) {
        this.plotId = plotId != null ? plotId : "";
    }

    public void setTownId(@Nonnull String townId) {
        this.townId = townId != null ? townId : "";
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new TreasuryBlock(plotId, townId);
    }
}
