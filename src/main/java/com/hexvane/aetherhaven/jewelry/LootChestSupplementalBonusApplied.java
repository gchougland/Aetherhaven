package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plot blueprint + prop + block palette + Gaia draught rolls at the current {@link #PIPELINE_VERSION}. */
public final class LootChestSupplementalBonusApplied implements Component<ChunkStore> {
    /** Bump when supplemental injection logic changes so older chest markers can re-roll once. */
    public static final int PIPELINE_VERSION = 2;

    @Nonnull
    public static final BuilderCodec<LootChestSupplementalBonusApplied> CODEC =
        BuilderCodec.builder(LootChestSupplementalBonusApplied.class, LootChestSupplementalBonusApplied::new)
            .append(
                new KeyedCodec<>("PipelineVersion", Codec.INTEGER),
                (t, v) -> t.pipelineVersion = v != null ? v : 0,
                t -> t.pipelineVersion
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, LootChestSupplementalBonusApplied> componentType;

    private int pipelineVersion;

    public LootChestSupplementalBonusApplied() {
        this.pipelineVersion = PIPELINE_VERSION;
    }

    public LootChestSupplementalBonusApplied(int pipelineVersion) {
        this.pipelineVersion = pipelineVersion;
    }

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType =
            registry.registerComponent(
                LootChestSupplementalBonusApplied.class,
                "AetherhavenLootChestSupplementalBonusApplied",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<ChunkStore, LootChestSupplementalBonusApplied> getComponentType() {
        ComponentType<ChunkStore, LootChestSupplementalBonusApplied> t = componentType;
        if (t == null) {
            throw new IllegalStateException("LootChestSupplementalBonusApplied not registered");
        }
        return t;
    }

    public boolean isCurrentPipeline() {
        return pipelineVersion >= PIPELINE_VERSION;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new LootChestSupplementalBonusApplied(pipelineVersion);
    }
}