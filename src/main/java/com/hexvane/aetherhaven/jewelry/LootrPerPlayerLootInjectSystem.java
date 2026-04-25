package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Injects Aetherhaven loot into Lootr's per-player chest containers once per player per chest. */
public final class LootrPerPlayerLootInjectSystem extends EntityTickingSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> bsiType = BlockModule.BlockStateInfo.getComponentType();
    @Nonnull
    private final ComponentType<ChunkStore, ? extends Component<ChunkStore>> lootrType;
    @Nonnull
    private final Query<ChunkStore> query;
    @Nonnull
    private final Field playerContainersField;

    private LootrPerPlayerLootInjectSystem(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ComponentType<ChunkStore, ? extends Component<ChunkStore>> lootrType,
        @Nonnull Field playerContainersField
    ) {
        this.plugin = plugin;
        this.lootrType = lootrType;
        this.playerContainersField = playerContainersField;
        this.query = Query.and(this.bsiType, this.lootrType);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static LootrPerPlayerLootInjectSystem createIfAvailable(@Nonnull AetherhavenPlugin plugin) {
        try {
            Class<?> lootrBlockClass = Class.forName("noobanidus.mods.lootr.block.ItemLootContainerBlock");
            Object lootrTypeObj = lootrBlockClass.getMethod("getLootComponentType").invoke(null);
            if (!(lootrTypeObj instanceof ComponentType<?, ?>)) {
                LOGGER.atWarning().log("Lootr compatibility disabled: Lootr loot component type not resolved.");
                return null;
            }
            Field playerContainers = lootrBlockClass.getDeclaredField("playerContainers");
            playerContainers.setAccessible(true);
            return new LootrPerPlayerLootInjectSystem(
                plugin,
                (ComponentType<ChunkStore, ? extends Component<ChunkStore>>) lootrTypeObj,
                playerContainers
            );
        } catch (Throwable t) {
            LOGGER.atInfo().log("Lootr compatibility disabled: Lootr classes unavailable or incompatible.");
            return null;
        }
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        AetherhavenPluginConfig cfg = this.plugin.getConfig().get();
        if (!cfg.isLootChestLootrPerPlayerCompatibilityEnabled()) {
            return;
        }
        Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(index);
        BlockModule.BlockStateInfo state = archetypeChunk.getComponent(index, this.bsiType);
        Component<ChunkStore> lootrBlock = archetypeChunk.getComponent(index, this.lootrType);
        if (state == null || lootrBlock == null) {
            return;
        }
        String blockTypeId = LootChestBonusInjectSystem.resolveBlockTypeIdForState(commandBuffer, state);
        if (!LootChestBonusApplier.isEligibleForBlockId(blockTypeId, cfg)) {
            return;
        }
        Object mapObj;
        try {
            mapObj = this.playerContainersField.get(lootrBlock);
        } catch (IllegalAccessException e) {
            return;
        }
        if (!(mapObj instanceof Map<?, ?> playerMap) || playerMap.isEmpty()) {
            return;
        }
        LootrChestProcessedPlayers processed = store.getComponent(ref, LootrChestProcessedPlayers.getComponentType());
        if (processed == null) {
            processed = new LootrChestProcessedPlayers();
            commandBuffer.putComponent(ref, LootrChestProcessedPlayers.getComponentType(), processed);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean injectedAny = false;
        for (Map.Entry<?, ?> e : playerMap.entrySet()) {
            if (!(e.getKey() instanceof UUID uuid)) {
                continue;
            }
            if (!(e.getValue() instanceof SimpleItemContainer inv)) {
                continue;
            }
            if (processed.contains(uuid)) {
                continue;
            }
            if (LootChestBonusApplier.applyAllToContainer(inv, cfg, rnd, false, false, false)) {
                injectedAny = true;
            }
            processed.add(uuid);
        }
        if (injectedAny) {
            state.markNeedsSaving(store);
        }
    }
}
