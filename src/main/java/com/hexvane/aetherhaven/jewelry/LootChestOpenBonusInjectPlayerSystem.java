package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * When a player opens a vanilla (non-Lootr) world loot chest, run bonus injection if chunk-load missed it, or retry
 * supplemental rolls when prerequisites were not ready at load.
 */
public final class LootChestOpenBonusInjectPlayerSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public LootChestOpenBonusInjectPlayerSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        AetherhavenPluginConfig cfg = this.plugin.getConfig().get();
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world.getWorldConfig().getGameMode() == GameMode.Creative && !cfg.isLootChestApplyInCreative()) {
            return;
        }

        ComponentType<ChunkStore, ?> lootrType = LootrIntegration.getLootComponentType();
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        boolean changedAny = false;

        for (Window window : player.getWindowManager().getWindows()) {
            if (!(window instanceof ContainerBlockWindow containerWindow)) {
                continue;
            }
            int x = containerWindow.getX();
            int y = containerWindow.getY();
            int z = containerWindow.getZ();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef == null || !sectionRef.isValid()) {
                continue;
            }
            BlockComponentSection blockComponentSection =
                chunkComponentStore.getComponent(sectionRef, BlockComponentSection.getComponentType());
            if (blockComponentSection == null) {
                continue;
            }
            Ref<ChunkStore> blockEntityRef = blockComponentSection.getBlockReference(ChunkUtil.indexBlock(x, y, z));
            if (blockEntityRef == null || !blockEntityRef.isValid()) {
                continue;
            }
            if (lootrType != null && chunkComponentStore.getComponent(blockEntityRef, lootrType) != null) {
                continue;
            }
            ItemContainerBlock container = chunkComponentStore.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
            BlockModule.BlockStateInfo state = chunkComponentStore.getComponent(blockEntityRef, BlockModule.BlockStateInfo.getComponentType());
            if (container == null || state == null) {
                continue;
            }
            String blockTypeId = LootChestBonusInjectSystem.resolveBlockTypeIdForState(chunkComponentStore, state);
            if (!LootChestBonusApplier.isEligibleForBlockId(blockTypeId, cfg)) {
                continue;
            }
            if (!LootChestWorldGenerated.isWorldLootChest(chunkComponentStore, blockEntityRef)) {
                continue;
            }
            ComponentType<ChunkStore, LootChestBonusApplied> coreAppliedType = LootChestBonusApplied.getComponentType();
            ComponentType<ChunkStore, LootChestSupplementalBonusApplied> supplementalAppliedType =
                LootChestSupplementalBonusApplied.getComponentType();
            boolean coreApplied = chunkComponentStore.getComponent(blockEntityRef, coreAppliedType) != null;
            boolean supplementalApplied =
                chunkComponentStore.getComponent(blockEntityRef, supplementalAppliedType) instanceof LootChestSupplementalBonusApplied s
                    && s.isCurrentPipeline();
            if (coreApplied && supplementalApplied) {
                continue;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            boolean changed = false;
            if (!supplementalApplied) {
                changed |= LootChestBonusApplier.applyWorldChestSupplementalBonusesOnce(
                    chunkComponentStore,
                    blockEntityRef,
                    state,
                    container,
                    this.plugin,
                    cfg,
                    rnd
                );
            }
            if (!coreApplied) {
                changed |= LootChestBonusApplier.applyWorldChestCoreBonusesOnce(
                    world,
                    chunkComponentStore,
                    blockEntityRef,
                    state,
                    container,
                    blockTypeId,
                    this.plugin,
                    cfg,
                    rnd
                );
            }
            if (changed) {
                changedAny = true;
                player.getWindowManager().markWindowChanged(window.getId());
            }
        }

        if (changedAny) {
            player.getWindowManager().updateWindows();
        }
    }
}
