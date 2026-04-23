package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.builtin.adventure.stash.StashGameplayConfig;
import com.hypixel.hytale.builtin.adventure.stash.StashPlugin;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runs after the built-in Stash system. Vanilla Stash clears {@link ItemContainerBlock#getDroplist} on the same add, so
 * {@link LootChestWorldLootMarkSystem} records a {@link LootChestWorldLootPending} tag when a droplist was present
 * before Stash. This system only applies bonus rolls when that tag is present (world loot), then removes the tag.
 */
public final class LootChestBonusInjectSystem extends RefSystem<ChunkStore> {
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
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<ChunkStore>> dependencies = Set.of(new SystemDependency<>(Order.AFTER, STASH_SYSTEM_CLASS));
    @Nonnull
    private final ComponentType<ChunkStore, ItemContainerBlock> itemType = ItemContainerBlock.getComponentType();
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> bsiType = BlockModule.BlockStateInfo.getComponentType();
    @Nonnull
    private final Query<ChunkStore> query = Query.and(this.itemType, this.bsiType);

    public LootChestBonusInjectSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

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
        ItemContainerBlock block = store.getComponent(ref, this.itemType);
        BlockModule.BlockStateInfo bsi = store.getComponent(ref, this.bsiType);
        if (block == null || bsi == null) {
            return;
        }
        String blockTypeId = resolveBlockTypeId(commandBuffer, bsi);
        commandBuffer.run(
            s -> {
                World world = s.getExternalData().getWorld();
                AetherhavenPluginConfig cfg = this.plugin.getConfig().get();
                if (world.getWorldConfig().getGameMode() == GameMode.Creative && !cfg.isLootChestApplyInCreative()) {
                    return;
                }
                ItemContainerBlock c = s.getComponent(ref, this.itemType);
                BlockModule.BlockStateInfo state = s.getComponent(ref, this.bsiType);
                if (c == null || state == null) {
                    return;
                }
                ComponentType<ChunkStore, LootChestWorldLootPending> pendingType = LootChestWorldLootPending.getComponentType();
                if (s.getComponent(ref, pendingType) == null) {
                    return;
                }
                if (world.getWorldConfig().getGameMode() == GameMode.Creative && !cfg.isLootChestApplyInCreative()) {
                    s.removeComponent(ref, pendingType);
                    return;
                }
                if (!LootChestBonusApplier.isEligibleForBlockId(blockTypeId, cfg)) {
                    s.removeComponent(ref, pendingType);
                    return;
                }
                if (c.getDroplist() != null) {
                    StashGameplayConfig sg = StashGameplayConfig.getOrDefault(world.getGameplayConfig());
                    StashPlugin.stash(state, c, sg.isClearContainerDropList());
                }
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                LootChestBonusApplier.tryInjectJewelry(s, state, c, cfg, rnd, false);
                LootChestBonusApplier.tryInjectGoldCoins(s, state, c, cfg, rnd, false);
                LootChestBonusApplier.tryInjectPlotToken(s, state, c, cfg, rnd, false);
                s.removeComponent(ref, pendingType);
            });
    }

    @Override
    public void onEntityRemove(
        @Nonnull Ref<ChunkStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

    @Nullable
    public static String resolveBlockTypeIdForState(@Nonnull CommandBuffer<ChunkStore> commandBuffer, @Nonnull BlockModule.BlockStateInfo bsi) {
        Ref<ChunkStore> chunkRef = bsi.getChunkRef();
        if (!chunkRef.isValid()) {
            return null;
        }
        int index = bsi.getIndex();
        int x = ChunkUtil.xFromBlockInColumn(index);
        int y = ChunkUtil.yFromBlockInColumn(index);
        int z = ChunkUtil.zFromBlockInColumn(index);
        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return null;
        }
        int blockId = blockChunk.getBlock(x, y, z);
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        return bt != null ? bt.getId() : null;
    }

    @Nullable
    private static String resolveBlockTypeId(@Nonnull CommandBuffer<ChunkStore> commandBuffer, @Nonnull BlockModule.BlockStateInfo bsi) {
        return resolveBlockTypeIdForState(commandBuffer, bsi);
    }
}
