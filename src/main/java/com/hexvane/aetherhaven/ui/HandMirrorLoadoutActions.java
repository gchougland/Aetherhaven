package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.PlayerJewelryLoadout;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Server-side jewelry loadout equip / unequip for the hand mirror: items leave the bag when equipped
 * and return on unequip, with swap and inventory-full safety.
 */
public final class HandMirrorLoadoutActions {
    private HandMirrorLoadoutActions() {}

    public enum EquipFromInventoryResult {
        SUCCESS,
        NOT_JEWELRY,
        INVALID_FOR_SLOT,
        SLOT_EMPTY,
        INVENTORY_UPDATE_FAILED,
        /** Could not place previous item back into the bag; inventory and loadout reverted. */
        COULD_NOT_RETURN_PREVIOUS
    }

    @Nonnull
    public static PlayerJewelryLoadout loadoutOrCreate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerJewelryLoadout lw = store.getComponent(ref, PlayerJewelryLoadout.getComponentType());
        if (lw == null) {
            lw = new PlayerJewelryLoadout();
            store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
        }
        return lw;
    }

    /**
     * Remove one piece from {@code invSlot} and place it in loadout index {@code target0To2} (0 ring1, 1
     * ring2, 2 neck). Pushes any previous equipped stack into the bag.
     */
    @Nonnull
    public static EquipFromInventoryResult equipFromInventory(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull CombinedItemContainer inv,
        short invSlot,
        int target0To2) {
        if (target0To2 < 0 || target0To2 > 2) {
            return EquipFromInventoryResult.INVALID_FOR_SLOT;
        }
        if (invSlot < 0 || invSlot >= inv.getCapacity()) {
            return EquipFromInventoryResult.SLOT_EMPTY;
        }
        ItemStack cur0 = inv.getItemStack(invSlot);
        if (cur0 == null || ItemStack.isEmpty(cur0)) {
            return EquipFromInventoryResult.SLOT_EMPTY;
        }
        if (!JewelryItemIds.isJewelry(cur0.getItemId())) {
            return EquipFromInventoryResult.NOT_JEWELRY;
        }
        if (target0To2 == 2 && !JewelryItemIds.isNecklace(cur0.getItemId())) {
            return EquipFromInventoryResult.INVALID_FOR_SLOT;
        }
        if (target0To2 != 2 && !JewelryItemIds.isRing(cur0.getItemId())) {
            return EquipFromInventoryResult.INVALID_FOR_SLOT;
        }
        ItemStack rolled = JewelryMetadata.ensureRolled(cur0);
        if (rolled != cur0) {
            if (!inv.replaceItemStackInSlot(invSlot, cur0, rolled).succeeded()) {
                return EquipFromInventoryResult.INVENTORY_UPDATE_FAILED;
            }
        }
        ItemStack cur = inv.getItemStack(invSlot);
        if (cur == null || ItemStack.isEmpty(cur) || !JewelryItemIds.isJewelry(cur.getItemId())) {
            return EquipFromInventoryResult.SLOT_EMPTY;
        }
        int q = cur.getQuantity();
        ItemStack one = cur.withQuantity(1);
        ItemStack newInv = q == 1 ? ItemStack.EMPTY : cur.withQuantity(q - 1);
        if (!inv.replaceItemStackInSlot(invSlot, cur, newInv).succeeded()) {
            return EquipFromInventoryResult.INVENTORY_UPDATE_FAILED;
        }

        PlayerJewelryLoadout lw = loadoutOrCreate(ref, store);
        ItemStack previous = lw.getSlot(target0To2);
        one = JewelryMetadata.ensureRolled(one);
        lw.setSlot(target0To2, one);
        store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);

        if (!ItemStack.isEmpty(previous)) {
            if (!player.giveItem(Objects.requireNonNull(previous), ref, store).succeeded()) {
                lw.setSlot(target0To2, previous);
                store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
                inv.replaceItemStackInSlot(invSlot, newInv, cur);
                return EquipFromInventoryResult.COULD_NOT_RETURN_PREVIOUS;
            }
        }
        return EquipFromInventoryResult.SUCCESS;
    }

    /**
     * @return true if the item was returned to the bag, or the slot was already empty. false if the
     *     player could not pick up the item.
     */
    public static boolean takeFromLoadout(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Player player, int target0To2) {
        if (target0To2 < 0 || target0To2 > 2) {
            return true;
        }
        PlayerJewelryLoadout lw = loadoutOrCreate(ref, store);
        ItemStack cur = lw.getSlot(target0To2);
        if (ItemStack.isEmpty(cur)) {
            return true;
        }
        ItemStack toReturn = Objects.requireNonNull(cur);
        if (toReturn.getItem() == null) {
            return false;
        }
        PlayerSettings settings = getPlayerSettings(ref, store);
        ItemStackTransaction t =
            player
                .getInventory()
                .getContainerForItemPickup(toReturn.getItem(), settings)
                .addItemStack(toReturn, true, false, true);
        if (!t.succeeded()) {
            return false;
        }
        lw.setSlot(target0To2, null);
        store.putComponent(ref, PlayerJewelryLoadout.getComponentType(), lw);
        return true;
    }

    @Nonnull
    private static PlayerSettings getPlayerSettings(
        @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> components) {
        PlayerSettings s = components.getComponent(ref, PlayerSettings.getComponentType());
        return s != null ? s : PlayerSettings.defaults();
    }
}
