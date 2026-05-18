package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Blocks storing Gaia's Draught in chests or other open external containers (bound to the player, not town storage). */
public final class GaiaDraughtInventoryChangeSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    public GaiaDraughtInventoryChangeSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryChangeEvent event
    ) {
        if (GaiaDraughtService.isSyncingInventory()) {
            return;
        }
        if (!transactionTouchesDraught(event.getTransaction())) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        boolean pulledFromChest = GaiaDraughtService.returnDraughtFromOpenExternalContainers(playerRef, store);
        if (pulledFromChest) {
            notifyCannotStore(playerRef, store);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    private static boolean transactionTouchesDraught(@Nullable Transaction transaction) {
        if (transaction == null) {
            return false;
        }
        if (transaction instanceof SlotTransaction slot) {
            return stackIsDraught(slot.getSlotBefore()) || stackIsDraught(slot.getSlotAfter());
        }
        if (transaction instanceof ListTransaction<?> list) {
            for (Transaction entry : list.getList()) {
                if (transactionTouchesDraught(entry)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean stackIsDraught(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(stack.getItemId());
    }

    private static void notifyCannotStore(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        PlayerRef pref = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pref != null) {
            pref.sendMessage(Message.translation("aetherhaven_jewelry_geode.aetherhaven.gaiadraught.cannotStore"));
        }
    }
}
