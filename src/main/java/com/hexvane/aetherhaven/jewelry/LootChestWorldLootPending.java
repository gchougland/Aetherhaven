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

/**
 * Ephemeral tag: a world {@link com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock} had a
 * non-null droplist on this add (before Stash has cleared it). The bonus inject system consumes it in the same
 * add cycle after Stash, so it should not be saved.
 */
public final class LootChestWorldLootPending implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<LootChestWorldLootPending> CODEC =
        BuilderCodec.builder(LootChestWorldLootPending.class, LootChestWorldLootPending::new)
            .append(
                new KeyedCodec<>("_", Codec.BYTE),
                (t, v) -> {},
                t -> (byte) 0
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, LootChestWorldLootPending> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType =
            registry.registerComponent(
                LootChestWorldLootPending.class,
                "AetherhavenLootChestWorldLootPending",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<ChunkStore, LootChestWorldLootPending> getComponentType() {
        ComponentType<ChunkStore, LootChestWorldLootPending> t = componentType;
        if (t == null) {
            throw new IllegalStateException("LootChestWorldLootPending not registered");
        }
        return t;
    }

    public LootChestWorldLootPending() {}

    @Override
    public Component<ChunkStore> clone() {
        return new LootChestWorldLootPending();
    }
}
