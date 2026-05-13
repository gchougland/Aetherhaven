package com.hexvane.aetherhaven.scaffold;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Logs mining hits on scaffold blocks (health / cancellation before {@link com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent}). */
public final class ScaffoldDamageBlockDebugSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public ScaffoldDamageBlockDebugSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull DamageBlockEvent event
    ) {
        if (!ScaffoldDebug.ENABLED) {
            return;
        }
        BlockType bt = event.getBlockType();
        if (bt == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(bt.getId())) {
            return;
        }
        ItemStack hand = event.getItemInHand();
        String handId = hand != null && hand.getItem() != null ? hand.getItem().getId() : "(null)";
        ScaffoldDebug.breaking(
            "DamageBlockEvent pos=%s,%s,%s cancelled=%s dmg=%s curHp=%s hand=%s",
            event.getTargetBlock().getX(),
            event.getTargetBlock().getY(),
            event.getTargetBlock().getZ(),
            event.isCancelled(),
            event.getDamage(),
            event.getCurrentDamage(),
            handId
        );
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
