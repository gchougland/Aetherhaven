package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Removes founder statue entity and clears town tax bonus when the monument block is broken. */
public final class FounderMonumentBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public FounderMonumentBreakSystem(@Nonnull AetherhavenPlugin plugin) {
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
        if (!AetherhavenConstants.FOUNDER_MONUMENT_BLOCK_TYPE_ID.equals(event.getBlockType().getId())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i pos = event.getTargetBlock();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ()));
        if (chunk == null) {
            return;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(pos.getX(), pos.getY(), pos.getZ());
        if (blockRef == null || !blockRef.isValid()) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        FounderMonumentBlock fb = cs.getComponent(blockRef, FounderMonumentBlock.getComponentType());
        if (fb == null || fb.getTownId().isBlank()) {
            return;
        }
        UUID townId;
        try {
            townId = UUID.fromString(fb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (fb.getStatueEntityUuid() != null && !fb.getStatueEntityUuid().isBlank()) {
            try {
                UUID statue = UUID.fromString(fb.getStatueEntityUuid().trim());
                FounderMonumentSpawnService.removeStatueEntity(world, store, statue);
            } catch (IllegalArgumentException ignored) {
            }
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town != null && town.getFounderMonumentCount() > 0) {
            town.decrementFounderMonumentPlaced();
            tm.updateTown(town);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
