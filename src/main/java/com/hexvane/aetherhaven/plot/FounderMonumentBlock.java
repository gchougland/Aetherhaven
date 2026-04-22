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

public final class FounderMonumentBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<FounderMonumentBlock> CODEC = BuilderCodec.builder(FounderMonumentBlock.class, FounderMonumentBlock::new)
        .append(new KeyedCodec<>("TownId", Codec.STRING), (s, v) -> s.townId = v != null ? v : "", s -> s.townId)
        .add()
        .append(new KeyedCodec<>("StatueEntityUuid", Codec.STRING), (s, v) -> s.statueEntityUuid = v, s -> s.statueEntityUuid)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, FounderMonumentBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(FounderMonumentBlock.class, "AetherhavenFounderMonumentBlock", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, FounderMonumentBlock> getComponentType() {
        ComponentType<ChunkStore, FounderMonumentBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("FounderMonumentBlock not registered");
        }
        return t;
    }

    private String townId = "";
    @Nullable
    private String statueEntityUuid;

    public FounderMonumentBlock() {}

    public FounderMonumentBlock(@Nonnull String townId, @Nullable String statueEntityUuid) {
        this.townId = townId != null ? townId : "";
        this.statueEntityUuid = statueEntityUuid;
    }

    @Nonnull
    public String getTownId() {
        return townId;
    }

    @Nullable
    public String getStatueEntityUuid() {
        return statueEntityUuid;
    }

    public void setStatueEntityUuid(@Nullable String statueEntityUuid) {
        this.statueEntityUuid = statueEntityUuid;
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new FounderMonumentBlock(townId, statueEntityUuid);
    }
}
