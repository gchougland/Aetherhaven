package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Saved tag: this block entity was spawned with a non-null {@code Droplist} (dungeon / world loot chest). Player-placed
 * storage chests never get this marker.
 */
public final class LootChestWorldGenerated implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<LootChestWorldGenerated> CODEC =
        BuilderCodec.builder(LootChestWorldGenerated.class, LootChestWorldGenerated::new)
            .append(new KeyedCodec<>("_", Codec.BYTE), (t, v) -> {}, t -> (byte) 0)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, LootChestWorldGenerated> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType =
            registry.registerComponent(
                LootChestWorldGenerated.class,
                "AetherhavenLootChestWorldGenerated",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<ChunkStore, LootChestWorldGenerated> getComponentType() {
        ComponentType<ChunkStore, LootChestWorldGenerated> t = componentType;
        if (t == null) {
            throw new IllegalStateException("LootChestWorldGenerated not registered");
        }
        return t;
    }

    public LootChestWorldGenerated() {}

    public static boolean isWorldLootChest(
        @Nonnull Store<ChunkStore> store,
        @Nonnull Ref<ChunkStore> blockEntityRef
    ) {
        return store.getComponent(blockEntityRef, getComponentType()) != null;
    }

    /** Ensures dungeon Lootr wrappers are tagged when they never had a droplist mark at spawn. */
    public static void ensureTagged(@Nonnull Store<ChunkStore> store, @Nonnull Ref<ChunkStore> blockEntityRef) {
        if (!isWorldLootChest(store, blockEntityRef)) {
            store.putComponent(blockEntityRef, getComponentType(), new LootChestWorldGenerated());
        }
    }

    @Override
    public Component<ChunkStore> clone() {
        return new LootChestWorldGenerated();
    }
}
