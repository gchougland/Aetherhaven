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
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * Injects Aetherhaven bonus loot into Loot4Everyone per-player containers when a player has an open L4E chest window.
 */
public final class Loot4EveryonePerPlayerLootInjectPlayerSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public Loot4EveryonePerPlayerLootInjectPlayerSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (!cfg.isLootChestLoot4EveryonePerPlayerCompatibilityEnabled()) {
            return;
        }
        if (!Loot4EveryoneIntegration.isAvailable()) {
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

        ComponentType<ChunkStore, ?> lootrType = LootrIntegration.getLootComponentType();
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

            int x = containerWindow.getX();
            int y = containerWindow.getY();
            int z = containerWindow.getZ();
            if (!Loot4EveryoneReflection.hasTemplate(world, x, y, z)) {
                continue;
            }

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
            BlockModule.BlockStateInfo state =
                chunkComponentStore.getComponent(blockEntityRef, BlockModule.BlockStateInfo.getComponentType());
            if (state == null) {
                continue;
            }
            LootChestWorldGenerated.ensureTagged(chunkComponentStore, blockEntityRef);
            String blockTypeId = LootChestBonusInjectSystem.resolveBlockTypeIdForState(chunkComponentStore, state);
            if (!LootChestBonusApplier.isEligibleForBlockId(blockTypeId, cfg)) {
                continue;
            }

            Loot4EveryoneChestProcessedPlayers processed =
                chunkComponentStore.getComponent(blockEntityRef, Loot4EveryoneChestProcessedPlayers.getComponentType());
            if (processed == null) {
                processed = new Loot4EveryoneChestProcessedPlayers();
                chunkComponentStore.putComponent(
                    blockEntityRef,
                    Loot4EveryoneChestProcessedPlayers.getComponentType(),
                    processed
                );
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
