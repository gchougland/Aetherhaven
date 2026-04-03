package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Prevents breaking {@link AetherhavenConstants#TREASURY_BLOCK_TYPE_ID} while its town still exists in {@link TownManager}.
 */
public final class TreasuryBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public TreasuryBreakBlockSystem(@Nonnull AetherhavenPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }
        if (!AetherhavenConstants.TREASURY_BLOCK_TYPE_ID.equals(event.getBlockType().getId())) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        TreasuryBlock tb = cs.getComponent(blockRef, TreasuryBlock.getComponentType());
        if (tb == null || tb.getTownId().isBlank()) {
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(tb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        event.setCancelled(true);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
