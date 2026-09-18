package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Under an economy provider that is not the coin, coin items a player comes to hold are deposited into the player's
 * account and taken away, so that a server switching economy keeps what its players had (stock in inventories and
 * chests, picked up over time). Fires on every change of a player's inventory, on the world thread. All stacks are
 * taken first, then one deposit of the sum; if the provider refuses, the stacks go back where they were. Chests keep
 * their coins until someone takes them. Idle under the built-in economy.
 */
public final class CoinItemDepositSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public CoinItemDepositSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InventoryChangeEvent event
    ) {
        if (AetherhavenEconomy.usesCoinItem()) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        CombinedItemContainer inventory = InventoryComponent.getCombined(commandBuffer, ref, InventoryComponent.EVERYTHING);
        if (inventory == null) {
            return;
        }
        GoldAccount account = AetherhavenEconomy.account(ref, store);
        if (account == null) {
            return;
        }
        long coins = deposit(inventory, account);
        if (coins < 0L) {
            LOGGER.atWarning().log("Player %s: %d gold coins could not be deposited into %s, coins left in the inventory",
                ref, -coins, AetherhavenEconomy.provider().id());
        }
    }

    /**
     * Takes every coin stack out of {@code inventory} and deposits the sum into {@code account}.
     *
     * @return the coins deposited, 0 when there were none, negative (minus the coins) when the deposit was refused
     *     and the stacks put back
     */
    static long deposit(@Nonnull ItemContainer inventory, @Nonnull GoldAccount account) {
        String coinId = ItemCoinEconomy.coinItemId();
        List<Removed> removed = new ArrayList<>();
        long coins = 0L;
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (ItemStack.isEmpty(stack) || !coinId.equals(stack.getItemId())) {
                continue;
            }
            if (!inventory.removeItemStackFromSlot(slot, stack, stack.getQuantity()).succeeded()) {
                continue;
            }
            removed.add(new Removed(slot, stack));
            coins += stack.getQuantity();
        }
        if (removed.isEmpty()) {
            return 0L;
        }
        if (account.deposit(coins)) {
            return coins;
        }
        for (Removed r : removed) {
            inventory.addItemStackToSlot(r.slot(), r.stack());
        }
        return -coins;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    private record Removed(short slot, ItemStack stack) {}
}
