package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GaiaDraughtService {
    private GaiaDraughtService() {}

    public static boolean replaceStackInCombinedSlot(
        @Nonnull CombinedItemContainer inv,
        short slot,
        @Nonnull ItemStack previous,
        @Nonnull ItemStack next
    ) {
        ItemStackSlotTransaction tx = inv.replaceItemStackInSlot(slot, previous, next);
        return tx.succeeded();
    }

    public static boolean replaceDraughtInMainHand(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull ItemStack previous,
        @Nonnull ItemStack next
    ) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        byte active = hotbar.getActiveSlot();
        if (active < 0) {
            return false;
        }
        ItemContainer container = hotbar.getInventory();
        ItemStackSlotTransaction tx = container.replaceItemStackInSlot(active, previous, next);
        return tx.succeeded();
    }

    /** After crafting: normalize any draught stacks that lack metadata or need a full charge bar. */
    public static void initializeCraftedDraughtStacksInInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st == null || st.isEmpty() || !id.equals(st.getItemId())) {
                continue;
            }
            ItemStack next = GaiaDraughtMetadata.asNewlyCraftedOrNormalized(st);
            if (stacksEqualForSync(st, next)) {
                continue;
            }
            replaceStackInCombinedSlot(inv, slot, st, next);
        }
    }

    private static boolean stacksEqualForSync(@Nonnull ItemStack a, @Nonnull ItemStack b) {
        return a.getQuantity() == b.getQuantity()
            && Math.round(a.getDurability()) == Math.round(b.getDurability())
            && Math.round(a.getMaxDurability()) == Math.round(b.getMaxDurability())
            && GaiaDraughtMetadata.getHealTier(a) == GaiaDraughtMetadata.getHealTier(b)
            && GaiaDraughtMetadata.getCapacity(a) == GaiaDraughtMetadata.getCapacity(b);
    }

    public static boolean removeOneItemFromInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String itemId
    ) {
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return false;
        }
        ItemStackTransaction tx = inv.removeItemStack(new ItemStack(itemId, 1));
        return tx != null && tx.succeeded();
    }

    /** @return false if stack missing */
    public static boolean hasItem(@Nullable CombinedItemContainer inv, @Nonnull String itemId, int count) {
        if (inv == null) {
            return false;
        }
        int have = 0;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st != null && !st.isEmpty() && itemId.equals(st.getItemId())) {
                have += st.getQuantity();
            }
        }
        return have >= count;
    }

    /**
     * Applies instant heal for the draught tier. Uses the vanilla potion effect when possible and always
     * applies a direct health restore so sips never fail silently.
     */
    public static void applyDraughtHeal(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        int healTier
    ) {
        String effectId = GaiaDraughtState.instantHealEffectId(healTier);
        EntityEffect asset = EntityEffect.getAssetMap().getAsset(effectId);
        if (asset != null) {
            EffectControllerComponent ecc = accessor.getComponent(playerRef, EffectControllerComponent.getComponentType());
            if (ecc != null) {
                ecc.addEffect(playerRef, asset, accessor);
            }
        }
        if (accessor instanceof Store<EntityStore> store) {
            PlayerHealUtil.healPercentOfMax(playerRef, store, PlayerHealUtil.healPercentForDraughtTier(healTier));
        }
    }
}
