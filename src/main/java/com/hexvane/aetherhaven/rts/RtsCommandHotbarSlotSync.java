package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies explicit client hotbar slot requests during RTS command mode (scroll / number keys). */
public final class RtsCommandHotbarSlotSync {
    private RtsCommandHotbarSlotSync() {}

    /**
     * Handles hotbar-only {@code SyncInteractionChains} during RTS: updates the server slot and echoes
     * finished chains back to the client (without this ack the client reverts hotbar selection).
     *
     * @return {@code true} when the batch was consumed (caller should block vanilla handling)
     */
    static boolean tryHandleHotbarSlotChains(@Nonnull PlayerRef playerRef, @Nonnull SyncInteractionChain[] updates) {
        if (updates.length == 0 || !isHotbarOnlyBatch(updates)) {
            return false;
        }
        runOnWorld(playerRef, () -> applyAndAckChains(playerRef, updates));
        return true;
    }

    /** Primary/secondary tool chains (box drag, orders) must still reach vanilla interaction handling. */
    private static boolean isHotbarOnlyBatch(@Nonnull SyncInteractionChain[] updates) {
        for (SyncInteractionChain update : updates) {
            if (isToolInteractionChain(update)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isToolInteractionChain(@Nonnull SyncInteractionChain update) {
        return switch (update.interactionType) {
            case Primary, Secondary, Use, Ability1, Ability2, Ability3, Ability4, Pick, Pickup, Held, HeldOffhand -> true;
            default -> false;
        };
    }

    static void handleSetActiveSlot(@Nonnull PlayerRef playerRef, byte requestedSlot) {
        runOnWorld(playerRef, () -> {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            adoptIfCommanding(ref, store, requestedSlot);
            confirmSlotToClient(playerRef, store, ref);
        });
    }

    private static void applyAndAckChains(@Nonnull PlayerRef playerRef, @Nonnull SyncInteractionChain[] updates) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        RtsCommandPlayerComponent session = store.getComponent(ref, RtsCommandPlayerComponent.getComponentType());
        if (session == null || !session.isActive()) {
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return;
        }

        byte serverSlot = hotbar.getActiveSlot();
        for (SyncInteractionChain update : updates) {
            if (!isHotbarSlotChange(update)) {
                continue;
            }
            byte requestedSlot = resolveRequestedSlot(update, serverSlot);
            if (requestedSlot >= 0 && requestedSlot < hotbar.getInventory().getCapacity()) {
                if (hotbar.getActiveSlot() != requestedSlot) {
                    hotbar.setActiveSlot(requestedSlot, ref, store);
                }
                serverSlot = hotbar.getActiveSlot();
            }
        }

        ackChains(playerRef, store, ref, updates);
        confirmSlotToClient(playerRef, store, ref);
    }

    static byte resolveRequestedSlot(@Nonnull SyncInteractionChain update, byte serverSlot) {
        if (update.data != null
            && update.data.targetSlot != Integer.MIN_VALUE
            && update.activeHotbarSlot == serverSlot) {
            return (byte) update.data.targetSlot;
        }
        return (byte) update.activeHotbarSlot;
    }

    static boolean isHotbarSlotChange(@Nonnull SyncInteractionChain update) {
        return update.initial && update.forkedId == null && update.interactionType == InteractionType.SwapFrom;
    }

    private static void ackChains(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull SyncInteractionChain[] updates
    ) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        int hotbarSlot = hotbar != null ? hotbar.getActiveSlot() : -1;
        int utilitySlot = utility != null ? utility.getActiveSlot() : -1;
        String itemInHandId = itemId(hotbar != null ? hotbar.getActiveItem() : null);
        String utilityItemId = itemId(utility != null ? utility.getActiveItem() : null);

        SyncInteractionChain[] acks = new SyncInteractionChain[updates.length];
        for (int i = 0; i < updates.length; i++) {
            SyncInteractionChain inbound = updates[i];
            acks[i] = new SyncInteractionChain(
                hotbarSlot,
                utilitySlot,
                inbound.activeToolsSlot,
                itemInHandId,
                utilityItemId,
                inbound.toolsItemId,
                false,
                false,
                Integer.MIN_VALUE,
                inbound.interactionType,
                inbound.equipSlot,
                inbound.chainId,
                inbound.forkedId,
                inbound.data,
                InteractionState.Finished,
                null,
                inbound.operationBaseIndex,
                null
            );
        }
        playerRef.getPacketHandler().writeNoCache(new SyncInteractionChains(acks));
    }

    private static void confirmSlotToClient(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return;
        }
        byte slot = hotbar.getActiveSlot();
        RtsCommandHotbarSlotInboundAdapter.runWithOutboundHotbarSlotSync(
            () -> playerRef.getPacketHandler().writeNoCache(new SetActiveSlot(-1, slot))
        );
    }

    @Nullable
    private static String itemId(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() ? stack.getItemId() : null;
    }

    private static void runOnWorld(@Nonnull PlayerRef playerRef, @Nonnull Runnable action) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        ref.getStore().getExternalData().getWorld().execute(action);
    }

    static void adoptIfCommanding(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        byte requestedSlot
    ) {
        if (!playerRef.isValid()) {
            return;
        }
        RtsCommandPlayerComponent session = store.getComponent(playerRef, RtsCommandPlayerComponent.getComponentType());
        if (session == null || !session.isActive()) {
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return;
        }
        if (requestedSlot < 0 || requestedSlot >= hotbar.getInventory().getCapacity()) {
            return;
        }
        if (hotbar.getActiveSlot() == requestedSlot) {
            return;
        }
        hotbar.setActiveSlot(requestedSlot, playerRef, store);
        RtsCommandFeedback.playToolEquip(playerRef, store);
    }
}
