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

public final class CharterBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<CharterBlock> CODEC = BuilderCodec.builder(CharterBlock.class, CharterBlock::new)
        .append(new KeyedCodec<>("TownId", Codec.STRING), (s, v) -> s.townId = v != null ? v : "", s -> s.townId)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, CharterBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(CharterBlock.class, "AetherhavenCharter", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, CharterBlock> getComponentType() {
        ComponentType<ChunkStore, CharterBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("CharterBlock not registered");
        }
        return t;
    }

    private String townId = "";

    public CharterBlock() {}

    public CharterBlock(@Nullable String townId) {
        this.townId = townId != null ? townId : "";
    }

    public String getTownId() {
        return townId;
    }

    public void setTownId(String townId) {
        this.townId = townId != null ? townId : "";
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new CharterBlock(townId);
    }
}
