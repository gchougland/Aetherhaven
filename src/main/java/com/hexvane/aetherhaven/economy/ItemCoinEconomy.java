package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The built-in economy: gold is the {@link AetherhavenConstants#ITEM_GOLD_COIN} item counted across the player's
 * whole inventory ({@link InventoryComponent#EVERYTHING}). Active when no economy mod registered a provider.
 */
public final class ItemCoinEconomy implements EconomyProvider {
    public static final ItemCoinEconomy INSTANCE = new ItemCoinEconomy();

    /** Translation key of the default amount text, "{count} gold". */
    public static final String AMOUNT_KEY = "aetherhaven_common.aetherhaven.common.goldAmount";
    private static final String AMOUNT_UI = "Aetherhaven/GoldAmount.ui";

    private ItemCoinEconomy() {}

    @Nonnull
    @Override
    public String id() {
        return "aetherhaven:coins";
    }

    @Nullable
    @Override
    public GoldAccount account(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inventory == null) {
            return null;
        }
        return new ItemCoinAccount(inventory, ref, store);
    }

    @Nonnull
    @Override
    public List<ItemStack> lootItems(@Nonnull String itemId, long amount) {
        if (amount <= 0L || Item.getAssetMap().getAsset(itemId) == null) {
            return List.of();
        }
        return split(itemId, amount);
    }

    @Nonnull
    @Override
    public Message amount(long amount) {
        return Message.translation(AMOUNT_KEY).param("count", amount);
    }

    @Override
    public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount) {
        builder.append(selector, AMOUNT_UI);
        builder.set(selector + " #Amount.Text", String.valueOf(amount));
    }

    @Nonnull
    public static String coinItemId() {
        return AetherhavenConstants.ITEM_GOLD_COIN;
    }

    /** {@code amount} coins of {@code itemId} as stacks no larger than the item's max stack. */
    @Nonnull
    static List<ItemStack> split(@Nonnull String itemId, long amount) {
        Item item = Item.getAssetMap().getAsset(itemId);
        int max = item == null ? Integer.MAX_VALUE : Math.max(1, item.getMaxStack());
        List<ItemStack> stacks = new ArrayList<>();
        long left = amount;
        while (left > 0L) {
            int chunk = (int) Math.min(left, max);
            stacks.add(new ItemStack(itemId, chunk));
            left -= chunk;
        }
        return stacks;
    }

    /**
     * Gold coins held in one inventory. Withdrawals remove coin stacks in chunks, deposits give coin stacks through
     * {@link Player#giveItem} when the account was opened for an entity, or straight into the container otherwise.
     */
    public static final class ItemCoinAccount implements GoldAccount {
        private final CombinedItemContainer inventory;
        @Nullable private final Ref<EntityStore> ref;
        @Nullable private final Store<EntityStore> store;

        /** An account on a bare container: deposits go straight in. Tests build one on a {@code SimpleItemContainer}. */
        public ItemCoinAccount(@Nonnull CombinedItemContainer inventory) {
            this(inventory, null, null);
        }

        private ItemCoinAccount(
            @Nonnull CombinedItemContainer inventory,
            @Nullable Ref<EntityStore> ref,
            @Nullable Store<EntityStore> store
        ) {
            this.inventory = inventory;
            this.ref = ref;
            this.store = store;
        }

        @Override
        public long balance() {
            return InventoryMaterials.count(inventory, coinItemId());
        }

        @Override
        public boolean withdraw(long amount) {
            if (amount <= 0L) {
                return true;
            }
            if (balance() < amount) {
                return false;
            }
            long left = amount;
            while (left > 0L) {
                int haveNow = InventoryMaterials.count(inventory, coinItemId());
                if (haveNow <= 0) {
                    return false;
                }
                int chunk = (int) Math.min(left, haveNow);
                ItemStackTransaction tx = inventory.removeItemStack(new ItemStack(coinItemId(), chunk));
                if (!tx.succeeded()) {
                    return false;
                }
                left -= chunk;
            }
            return true;
        }

        @Override
        public boolean deposit(long amount) {
            if (amount <= 0L) {
                return true;
            }
            List<ItemStack> stacks = split(coinItemId(), amount);
            if (!inventory.canAddItemStacks(stacks)) {
                return false;
            }
            for (ItemStack stack : stacks) {
                ItemStackTransaction tx = ref != null && store != null
                    ? Player.giveItem(stack, ref, store)
                    : inventory.addItemStack(stack);
                if (!tx.succeeded()) {
                    return false;
                }
            }
            return true;
        }
    }
}
