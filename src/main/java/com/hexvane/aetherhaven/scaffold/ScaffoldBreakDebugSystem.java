package com.hexvane.aetherhaven.scaffold;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Logs {@link BreakBlockEvent} data for wood scaffold breaks (drops / gathering / cancellation trail).
 */
public final class ScaffoldBreakDebugSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public ScaffoldBreakDebugSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (!ScaffoldDebug.ENABLED) {
            return;
        }
        BlockType bt = event.getBlockType();
        if (bt == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(bt.getId())) {
            return;
        }

        Vector3i pos = event.getTargetBlock();
        ItemStack hand = event.getItemInHand();
        String handId = hand != null && hand.getItem() != null ? hand.getItem().getId() : "(null)";
        BlockGathering g = bt.getGathering();

        boolean cancelled = event.isCancelled();
        ScaffoldDebug.breaking(
            "BreakBlockEvent pos=%s,%s,%s cancelled=%s handItem=%s block=%s canDeco=%s gathering=%s",
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            cancelled,
            handId,
            bt.getId(),
            bt.canBePlacedAsDeco(),
            gatheringSummary(g)
        );

        World world = store.getExternalData().getWorld();
        BlockType atPos = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        String atId = atPos != null ? atPos.getId() : "(null)";
        ScaffoldDebug.breaking("  world block at target (same tick, may be pre-remove): %s", atId);
    }

    private static String gatheringSummary(@Nonnull BlockGathering g) {
        StringBuilder sb = new StringBuilder();
        sb.append("useDefaultDropPlaced=").append(g.shouldUseDefaultDropWhenPlaced());
        if (g.getBreaking() != null) {
            sb.append(" Breaking(item=").append(g.getBreaking().getItemId()).append(",gather=").append(g.getBreaking().getGatherType()).append(")");
        }
        if (g.getSoft() != null) {
            sb.append(" Soft(item=").append(g.getSoft().getItemId()).append(")");
        }
        if (g.getPhysics() != null) {
            sb.append(" Physics(item=").append(g.getPhysics().getItemId()).append(")");
        }
        return sb.toString();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
