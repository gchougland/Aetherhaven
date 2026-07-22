package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Set;
import javax.annotation.Nonnull;

/** Marks block entities that still have a droplist (world loot chests), before Stash runs; sets {@link LootChestWorldGenerated}. */
public final class LootChestWorldLootMarkSystem extends RefSystem<ChunkStore> {
    private static final Class<? extends ISystem<ChunkStore>> STASH_SYSTEM_CLASS = loadStashSystemClass();

    @SuppressWarnings("unchecked")
    private static Class<? extends ISystem<ChunkStore>> loadStashSystemClass() {
        try {
            return (Class<? extends ISystem<ChunkStore>>) Class.forName("com.hypixel.hytale.builtin.adventure.stash.StashPlugin$StashSystem");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Nonnull
    private final Set<Dependency<ChunkStore>> dependencies = Set.of(new SystemDependency<>(Order.BEFORE, STASH_SYSTEM_CLASS));
    @Nonnull
    private final ComponentType<ChunkStore, ItemContainerBlock> itemType = ItemContainerBlock.getComponentType();
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> bsiType = BlockModule.BlockStateInfo.getComponentType();
    @Nonnull
    private final Query<ChunkStore> query = Query.and(this.itemType, this.bsiType);

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return this.dependencies;
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
        @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        ItemContainerBlock c = store.getComponent(ref, this.itemType);
        if (c == null) {
            return;
        }
        if (c.getDroplist() == null) {
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
        @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}
}
