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
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * Injects Aetherhaven bonus loot into Lootr per-player containers when a player has an open Lootr chest window.
 */
public final class LootrPerPlayerLootInjectPlayerSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public LootrPerPlayerLootInjectPlayerSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (!cfg.isLootChestLootrPerPlayerCompatibilityEnabled()) {
            return;
        }
        ComponentType<ChunkStore, ?> lootrType = LootrIntegration.getLootComponentType();
        if (lootrType == null) {
            return;
        }

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return;
        }
        UUID playerUuid = playerRefComponent.getUuid();

        World world = store.getExternalData().getWorld();
        if (world.getWorldConfig().getGameMode() == GameMode.Creative && !cfg.isLootChestApplyInCreative()) {
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        boolean injectedAny = false;

        for (Window window : player.getWindowManager().getWindows()) {
            if (!(window instanceof ContainerBlockWindow containerWindow)) {
                continue;
            }
            if (!(containerWindow.getItemContainer() instanceof SimpleItemContainer inv)) {
                continue;
            }
            if (LootrReflection.isPlaceholderContainer(inv)) {
                continue;
            }

            int x = containerWindow.getX();
            int y = containerWindow.getY();
            int z = containerWindow.getZ();
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunkRef == null || !chunkRef.isValid()) {
                continue;
            }
            BlockComponentChunk blockComponentChunk =
                chunkComponentStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (blockComponentChunk == null) {
                continue;
            }
            Ref<ChunkStore> blockEntityRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(x, y, z));
            if (blockEntityRef == null || !blockEntityRef.isValid()) {
                continue;
            }
            Object lootBlock = chunkComponentStore.getComponent(blockEntityRef, lootrType);
            if (lootBlock == null) {
                continue;
            }
            BlockModule.BlockStateInfo state = chunkComponentStore.getComponent(blockEntityRef, BlockModule.BlockStateInfo.getComponentType());
            if (state == null) {
                continue;
            }
            if (!LootChestWorldGenerated.isWorldLootChest(chunkComponentStore, blockEntityRef)) {
                if (LootrReflection.isSpawnerConvertedLootChest(lootBlock)) {
                    chunkComponentStore.putComponent(
                        blockEntityRef,
                        LootChestWorldGenerated.getComponentType(),
                        new LootChestWorldGenerated()
                    );
                } else {
                    LootChestWorldGenerated.ensureTagged(chunkComponentStore, blockEntityRef);
                }
            }
            String blockTypeId = LootrReflection.resolveEligibleBlockTypeId(lootBlock, chunkComponentStore, state);
            if (!LootChestBonusApplier.isEligibleForBlockId(blockTypeId, cfg)) {
                continue;
            }

            LootrChestProcessedPlayers processed =
                chunkComponentStore.getComponent(blockEntityRef, LootrChestProcessedPlayers.getComponentType());
            if (processed == null) {
                processed = new LootrChestProcessedPlayers();
                chunkComponentStore.putComponent(blockEntityRef, LootrChestProcessedPlayers.getComponentType(), processed);
            }
            if (processed.contains(playerUuid)) {
                continue;
            }

            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            if (LootChestBonusApplier.applyOpenContainerBonuses(
                inv,
                world,
                chunkComponentStore,
                state,
                this.plugin,
                cfg,
                rnd
            )) {
                injectedAny = true;
                player.getWindowManager().markWindowChanged(window.getId());
                state.markNeedsSaving(chunkComponentStore);
            }
            processed.add(playerUuid);
        }

        if (injectedAny) {
            player.getWindowManager().updateWindows();
        }
    }
}
