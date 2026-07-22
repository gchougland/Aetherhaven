package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.world.WorldZoneIndex;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Ensures jewelry stacks carry rolled BSON and {@link ItemDisplayMetadata} before inventory packets are built. */
public final class JewelryInventoryTooltipSync {

    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private JewelryInventoryTooltipSync() {}

    public static boolean isSyncing() {
        return Boolean.TRUE.equals(SYNCING.get());
    }

    /**
     * Ensures rolled jewelry BSON and {@link JewelryMetadata#syncInstanceDescriptionForTooltip} are present on every
     * jewelry stack in the player's inventory so {@code ItemStack.toPacket().metadata} carries BSON and ItemDisplay text.
     */
    public static void syncPlayerInventory(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        if (isSyncing()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        SYNCING.set(true);
        try {
            for (short slot = 0; slot < inv.getCapacity(); slot++) {
                ItemStack current = inv.getItemStack(slot);
                if (ItemStack.isEmpty(current) || !JewelryItemIds.isJewelry(current.getItemId())) {
                    continue;
                }
                ItemStack synced = syncJewelryStackForPlayer(current, playerRef, store);
                if (synced.equals(current)) {
                    continue;
                }
                ItemStackSlotTransaction tx = inv.replaceItemStackInSlot(slot, current, synced);
                if (!tx.succeeded()) {
                    continue;
                }
            }
        } finally {
            SYNCING.set(false);
        }
    }

    /** Sync one stack after a slot change when the new stack is jewelry. */
    public static void syncSlotIfJewelry(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable ItemStack after
    ) {
        if (isSyncing() || ItemStack.isEmpty(after) || !JewelryItemIds.isJewelry(after.getItemId())) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        ItemStack synced = syncJewelryStackForPlayer(after, playerRef, store);
        if (synced.equals(after)) {
            return;
        }
        SYNCING.set(true);
        try {
            for (short slot = 0; slot < inv.getCapacity(); slot++) {
                ItemStack current = inv.getItemStack(slot);
                if (current != null && current.equals(after)) {
                    inv.replaceItemStackInSlot(slot, current, synced);
                    return;
                }
            }
        } finally {
            SYNCING.set(false);
        }
    }

    @Nonnull
    private static ItemStack syncJewelryStackForPlayer(
        @Nonnull ItemStack stack,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!JewelryPieceKind.isEnchanted(stack.getItemId())) {
            return JewelryMetadata.ensureRolled(stack);
        }
        int zone = resolveAdventureZoneForPlayer(playerRef, store);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        AetherhavenPluginConfig cfg = JewelryRolling.config();
        if (LootChestBonusApplier.needsLootChestJewelryReroll(stack, zone)) {
            return JewelryMetadata.rerollLootChestStack(stack, rnd, cfg, zone);
        }
        return JewelryMetadata.ensureRolledForLootChest(stack, rnd, cfg, zone);
    }

    private static int resolveAdventureZoneForPlayer(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return WorldZoneIndex.UNKNOWN_DEFAULT;
        }
        var pos = transform.getPosition();
        return WorldZoneIndex.resolveAtBlock(world, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
    }
}
