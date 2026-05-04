package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GaiaDraughtService {
    private GaiaDraughtService() {}

    @Nonnull
    public static GaiaDraughtState getOrCreate(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        return town.getOrCreateGaiaDraughtState(playerUuid);
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
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        double cap = Math.max(1.0, (double) state.getCapacity());
        double dur = Math.min(cap, Math.max(0.0, (double) state.getCharges()));
        ItemStack canonical = new ItemStack(id, 1, dur, cap, null);

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
                double cap = Math.max(1.0, (double) s.getCapacity());
                double dur = Math.min(cap, Math.max(0.0, (double) s.getCharges()));
                p.giveItem(new ItemStack(id, 1, dur, cap, null), playerRef, store);
            }
        }
        syncDraughtStacksInInventory(playerRef, store, s);
    }

    public static boolean tryApplyShardCapacityUpgrade(@Nonnull GaiaDraughtState s) {
        return s.tryApplyShardCapacityUpgrade();
    }

    public static boolean tryApplyCatalystHealTierUpgrade(@Nonnull GaiaDraughtState s) {
        return s.tryApplyCatalystHealTierUpgrade();
    }
}
