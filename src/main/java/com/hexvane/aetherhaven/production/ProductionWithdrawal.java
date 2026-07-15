package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Grants production storage outputs to a player, including barn milk bucket exchange. */
public final class ProductionWithdrawal {
    public static final String EMPTY_BUCKET_ITEM_ID = "Container_Bucket";
    public static final String MILK_BUCKET_ITEM_ID = "*Container_Bucket_State_Filled_Milk";
    private static final String LEGACY_MILK_BUCKET_ITEM_ID = "Container_Bucket_State_Filled_Milk";

    private ProductionWithdrawal() {}

    public static boolean isMilkBucketItem(@Nonnull String itemId) {
        return MILK_BUCKET_ITEM_ID.equals(itemId) || LEGACY_MILK_BUCKET_ITEM_ID.equals(itemId);
    }

    /**
     * Removes up to {@code want} of {@code itemId} from {@code state} and grants it to {@code player}.
     *
     * @return how much was actually withdrawn from storage (may be less than {@code want} for milk when buckets are short)
     */
    public static long withdrawToPlayer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        @Nonnull String itemId,
        long want,
        @Nonnull ResultSink result
    ) {
        if (itemId.isBlank() || want <= 0L) {
            return 0L;
        }
        long have = state.getAmount(itemId);
        long take = Math.min(have, want);
        if (take <= 0L) {
            return 0L;
        }
        if (isMilkBucketItem(itemId)) {
            return withdrawMilk(ref, store, player, state, entry, itemId, take, result);
        }
        return withdrawPlain(ref, store, player, state, entry, itemId, take, result);
    }

    private static long withdrawPlain(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        @Nonnull String itemId,
        long take,
        @Nonnull ResultSink result
    ) {
        long maxCap = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, itemId);
        state.removeAmountUpTo(itemId, take);
        ItemStack grant = new ItemStack(itemId, (int) Math.min(take, Integer.MAX_VALUE));
        ItemStackTransaction giveTx = player.giveItem(grant, ref, store);
        if (!giveTx.succeeded()) {
            state.addAmount(itemId, take, maxCap);
            result.inventoryFull = true;
            return 0L;
        }
        ItemStack remainder = giveTx.getRemainder();
        long notAdded = ItemStack.isEmpty(remainder) ? 0L : Math.min(take, remainder.getQuantity());
        if (notAdded > 0L) {
            state.addAmount(itemId, notAdded, maxCap);
            result.inventoryPartial = true;
            if (notAdded == take) {
                result.inventoryFull = true;
            }
            return take - notAdded;
        }
        return take;
    }

    private static long withdrawMilk(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        @Nonnull String itemId,
        long take,
        @Nonnull ResultSink result
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) {
            result.inventoryFull = true;
            return 0L;
        }
        int emptyBuckets = InventoryMaterials.count(inv, EMPTY_BUCKET_ITEM_ID);
        if (emptyBuckets <= 0) {
            result.needEmptyBuckets = true;
            result.emptyBucketsRequired = (int) Math.min(take, Integer.MAX_VALUE);
            result.emptyBucketsHeld = 0;
            return 0L;
        }
        take = Math.min(take, emptyBuckets);
        long maxCap = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, itemId);
        state.removeAmountUpTo(itemId, take);
        ItemStackTransaction bucketTx = inv.removeItemStack(new ItemStack(EMPTY_BUCKET_ITEM_ID, (int) take));
        if (!bucketTx.succeeded()) {
            state.addAmount(itemId, take, maxCap);
            result.needEmptyBuckets = true;
            result.emptyBucketsRequired = (int) take;
            result.emptyBucketsHeld = emptyBuckets;
            return 0L;
        }
        ItemStack grant = new ItemStack(MILK_BUCKET_ITEM_ID, (int) take);
        ItemStackTransaction giveTx = player.giveItem(grant, ref, store);
        if (!giveTx.succeeded()) {
            state.addAmount(itemId, take, maxCap);
            player.giveItem(new ItemStack(EMPTY_BUCKET_ITEM_ID, (int) take), ref, store);
            result.inventoryFull = true;
            return 0L;
        }
        ItemStack remainder = giveTx.getRemainder();
        long notAdded = ItemStack.isEmpty(remainder) ? 0L : Math.min(take, remainder.getQuantity());
        if (notAdded > 0L) {
            state.addAmount(itemId, notAdded, maxCap);
            player.giveItem(new ItemStack(EMPTY_BUCKET_ITEM_ID, (int) notAdded), ref, store);
            result.inventoryPartial = true;
            if (notAdded == take) {
                result.inventoryFull = true;
            }
            return take - notAdded;
        }
        return take;
    }

    /** Mutable outcome flags for {@link #withdrawToPlayer}. */
    public static final class ResultSink {
        public boolean inventoryFull;
        public boolean inventoryPartial;
        public boolean needEmptyBuckets;
        public int emptyBucketsRequired;
        public int emptyBucketsHeld;
    }
}
