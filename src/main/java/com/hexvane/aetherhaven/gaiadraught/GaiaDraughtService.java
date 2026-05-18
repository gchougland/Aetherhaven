package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GaiaDraughtService {
    private static final ThreadLocal<Integer> INVENTORY_SYNC_DEPTH = ThreadLocal.withInitial(() -> 0);

    private GaiaDraughtService() {}

    /** True while {@link #syncDraughtStacksInInventory} is running (avoids infinite inventory event loops). */
    public static boolean isSyncingInventory() {
        return INVENTORY_SYNC_DEPTH.get() > 0;
    }

    @Nonnull
    public static GaiaDraughtState getOrCreate(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        return town.getOrCreateGaiaDraughtState(playerUuid);
    }

    @Nonnull
    public static ItemStack canonicalStack(@Nonnull GaiaDraughtState state) {
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        double cap = Math.max(1.0, (double) state.getCapacity());
        double dur = Math.min(cap, Math.max(0.0, (double) state.getCharges()));
        return new ItemStack(id, 1, dur, cap, null);
    }

    /**
     * Normalizes Gaia's Draught stacks: always quantity 1 per stack, {@code durability}/{@code maxDurability} on the
     * stack mirror town {@link GaiaDraughtState#getCharges()} / {@link GaiaDraughtState#getCapacity()}. Extra duplicate
     * stacks are removed (only one physical flask).
     */
    public static void syncDraughtStacksInInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull GaiaDraughtState state
    ) {
        INVENTORY_SYNC_DEPTH.set(INVENTORY_SYNC_DEPTH.get() + 1);
        try {
            CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
            if (inv == null) {
                return;
            }
            String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
            ItemStack canonical = canonicalStack(state);

            List<Short> slots = new ArrayList<>();
            for (short slot = 0; slot < inv.getCapacity(); slot++) {
                ItemStack st = inv.getItemStack(slot);
                if (st != null && !st.isEmpty() && id.equals(st.getItemId())) {
                    slots.add(slot);
                }
            }
            if (slots.isEmpty()) {
                return;
            }
            short keep = slots.get(0);
            for (int i = 0; i < slots.size(); i++) {
                short slot = slots.get(i);
                ItemStack prev = inv.getItemStack(slot);
                if (prev == null || prev.isEmpty()) {
                    continue;
                }
                if (slot == keep) {
                    if (stacksMatchCanonical(prev, canonical)) {
                        continue;
                    }
                    ItemStackSlotTransaction tx = inv.replaceItemStackInSlot(slot, prev, canonical);
                    if (!tx.succeeded()) {
                        return;
                    }
                } else {
                    ItemStackSlotTransaction tx = inv.removeItemStackFromSlot(slot, prev, prev.getQuantity());
                    if (!tx.succeeded()) {
                        return;
                    }
                }
            }
        } finally {
            int depth = INVENTORY_SYNC_DEPTH.get() - 1;
            if (depth <= 0) {
                INVENTORY_SYNC_DEPTH.remove();
            } else {
                INVENTORY_SYNC_DEPTH.set(depth);
            }
        }
    }

    private static boolean stacksMatchCanonical(@Nonnull ItemStack stack, @Nonnull ItemStack canonical) {
        return stack.getQuantity() == canonical.getQuantity()
            && AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(stack.getItemId())
            && Math.round(stack.getDurability()) == Math.round(canonical.getDurability())
            && Math.round(stack.getMaxDurability()) == Math.round(canonical.getMaxDurability());
    }

    public static void unlockAndFill(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        GaiaDraughtState s = getOrCreate(town, playerUuid);
        s.setUnlocked(true);
        s.clampChargesToCapacity();
        if (s.getCharges() < s.getCapacity()) {
            s.setCharges(s.getCapacity());
        }
    }

    public static boolean tryConsumeOneCharge(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        GaiaDraughtState s = getOrCreate(town, playerUuid);
        if (!s.isUnlocked() || s.getCharges() <= 0) {
            return false;
        }
        s.setCharges(s.getCharges() - 1);
        return true;
    }

    public static void refillToCapacity(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        GaiaDraughtState s = getOrCreate(town, playerUuid);
        if (!s.isUnlocked()) {
            return;
        }
        s.setCharges(s.getCapacity());
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

    private static boolean hasAnyDraughtStack(@Nonnull CombinedItemContainer inv, @Nonnull String id) {
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st != null && !st.isEmpty() && id.equals(st.getItemId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean playerHasDraughtStack(@Nullable CombinedItemContainer inv) {
        return inv != null && hasAnyDraughtStack(inv, AetherhavenConstants.ITEM_GAIAS_DRAUGHT);
    }

    /**
     * After unlocking, grants a first flask if the player has none, then normalizes draught stacks to town charges
     * (durability mirror).
     */
    public static void ensureDraughtStacksOrGrantFirst(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid
    ) {
        GaiaDraughtState s = getOrCreate(town, playerUuid);
        if (!s.isUnlocked()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        if (!hasAnyDraughtStack(inv, id) && s.getCharges() > 0) {
            Player p = store.getComponent(playerRef, Player.getComponentType());
            if (p != null) {
                p.giveItem(canonicalStack(s), playerRef, store);
            }
        }
        syncDraughtStacksInInventory(playerRef, store, s);
    }

    /**
     * After crafting Gaia's Draught: mirror town charges on the flask, remove duplicate stacks, and fill a replacement
     * bottle when the player had none or was empty (same as the priestess quest reward).
     */
    public static void onDraughtCrafted(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager townManager,
        @Nonnull UUID playerUuid,
        boolean hadDraughtInInventoryBeforeCraft
    ) {
        GaiaDraughtState s = getOrCreate(town, playerUuid);
        if (!s.isUnlocked()) {
            syncDraughtStacksInInventory(playerRef, store, s);
            return;
        }
        if (!hadDraughtInInventoryBeforeCraft || s.getCharges() <= 0) {
            refillToCapacity(town, playerUuid);
            s = getOrCreate(town, playerUuid);
        }
        syncDraughtStacksInInventory(playerRef, store, s);
        townManager.updateTown(town);
    }

    /**
     * Applies instant heal for the player's draught tier. Uses the vanilla potion effect when possible and always
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

    /**
     * Pulls Gaia's Draught out of open chest-like windows back into the player inventory.
     *
     * @return true if at least one flask was moved back to the player
     */
    public static boolean returnDraughtFromOpenExternalContainers(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (isSyncingInventory()) {
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CombinedItemContainer playerInv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (playerInv == null) {
            return false;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        boolean movedAny = false;
        for (com.hypixel.hytale.server.core.entity.entities.player.windows.Window window : player.getWindowManager().getWindows()) {
            if (!(window instanceof com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow itemWindow)) {
                continue;
            }
            ItemContainer external = itemWindow.getItemContainer();
            for (short slot = 0; slot < external.getCapacity(); slot++) {
                ItemStack st = external.getItemStack(slot);
                if (st == null || st.isEmpty() || !id.equals(st.getItemId())) {
                    continue;
                }
                ItemStackSlotTransaction removed = external.removeItemStackFromSlot(slot, st, st.getQuantity());
                if (!removed.succeeded()) {
                    continue;
                }
                ItemStack moved = removed.getOutput();
                if (moved == null || moved.isEmpty()) {
                    moved = st;
                }
                ItemStackTransaction added = playerInv.addItemStack(moved);
                if (added != null && !added.succeeded() && added.getRemainder() != null && !added.getRemainder().isEmpty()) {
                    external.addItemStackToSlot(slot, moved);
                } else {
                    movedAny = true;
                }
            }
        }
        return movedAny;
    }

    public static boolean tryApplyShardCapacityUpgrade(@Nonnull GaiaDraughtState s) {
        return s.tryApplyShardCapacityUpgrade();
    }

    public static boolean tryApplyCatalystHealTierUpgrade(@Nonnull GaiaDraughtState s) {
        return s.tryApplyCatalystHealTierUpgrade();
    }
}
