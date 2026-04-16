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

public final class SprinklerBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<SprinklerBlock> CODEC = BuilderCodec.builder(SprinklerBlock.class, SprinklerBlock::new)
        .append(new KeyedCodec<>("Tier", Codec.INTEGER), (s, v) -> s.tier = v != null ? v : 1, s -> s.tier)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, SprinklerBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(SprinklerBlock.class, "AetherhavenSprinklerBlock", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, SprinklerBlock> getComponentType() {
        ComponentType<ChunkStore, SprinklerBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("SprinklerBlock not registered");
        }
        return t;
    }

    /** Chebyshev radius: tier 1 => 1 (3x3), 2 => 2, 3 => 3, 4 => 4. */
    public static int radiusForTier(int tier) {
        int t = Math.max(1, Math.min(4, tier));
        return t;
    }

    private int tier = 1;

    public SprinklerBlock() {}

    public SprinklerBlock(int tier) {
        this.tier = Math.max(1, Math.min(4, tier));
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(4, tier));
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new SprinklerBlock(tier);
    }
}
