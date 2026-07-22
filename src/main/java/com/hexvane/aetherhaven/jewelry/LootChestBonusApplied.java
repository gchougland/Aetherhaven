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

/** Persistent tag: Aetherhaven world-chest bonus rolls already ran for this block entity. */
public final class LootChestBonusApplied implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<LootChestBonusApplied> CODEC =
        BuilderCodec.builder(LootChestBonusApplied.class, LootChestBonusApplied::new)
            .append(new KeyedCodec<>("_", Codec.BYTE), (t, v) -> {}, t -> (byte) 0)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, LootChestBonusApplied> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType =
            registry.registerComponent(LootChestBonusApplied.class, "AetherhavenLootChestBonusApplied", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, LootChestBonusApplied> getComponentType() {
        ComponentType<ChunkStore, LootChestBonusApplied> t = componentType;
        if (t == null) {
            throw new IllegalStateException("LootChestBonusApplied not registered");
        }
        return t;
    }

    public LootChestBonusApplied() {}

    @Override
    public Component<ChunkStore> clone() {
        return new LootChestBonusApplied();
    }
}
