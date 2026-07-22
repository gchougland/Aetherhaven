package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

/** Marks Lootr block entities spawned from world loot (droplist or spawner conversion). */
public final class LootChestLootrWorldLootMarkSystem extends RefSystem<ChunkStore> {
    @Nonnull
    private final ComponentType<ChunkStore, ?> lootType;
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> bsiType = BlockModule.BlockStateInfo.getComponentType();
    @Nonnull
    private final Query<ChunkStore> query;

    public LootChestLootrWorldLootMarkSystem(@Nonnull ComponentType<ChunkStore, ?> lootType) {
        this.lootType = lootType;
        this.query = Query.and(lootType, this.bsiType);
    }

    @Override
    @Nonnull
    public Query<ChunkStore> getQuery() {
        return this.query;
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<ChunkStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        Object lootBlock = store.getComponent(ref, this.lootType);
        if (lootBlock == null || !LootrReflection.isSpawnerConvertedLootChest(lootBlock)) {
            return;
        }
        ComponentType<ChunkStore, LootChestWorldGenerated> worldType = LootChestWorldGenerated.getComponentType();
        commandBuffer.putComponent(ref, worldType, new LootChestWorldGenerated());
        commandBuffer.putComponent(ref, LootChestWorldLootPending.getComponentType(), new LootChestWorldLootPending());
    }

    @Override
    public void onEntityRemove(
        @Nonnull Ref<ChunkStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {}
}
