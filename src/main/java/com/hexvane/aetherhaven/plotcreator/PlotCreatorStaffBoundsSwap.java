package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Swaps the plot creator staff to an internal bounds variant with the Selection builder tool. */
public final class PlotCreatorStaffBoundsSwap {
    private PlotCreatorStaffBoundsSwap() {}

    public static boolean isMainStaff(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID.equals(stack.getItemId());
    }

    public static boolean isBoundsStaff(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID.equals(stack.getItemId());
    }

    public static boolean isAnyStaff(@Nullable ItemStack stack) {
        return isMainStaff(stack) || isBoundsStaff(stack);
    }

    public static boolean enterBoundsMode(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        byte slot = hotbar.getActiveSlot();
        if (slot < 0) {
            return false;
        }
        ItemContainer container = hotbar.getInventory();
        ItemStack held = container.getItemStack(slot);
        if (isBoundsStaff(held)) {
            return true;
        }
        if (!isMainStaff(held)) {
            return false;
        }
        ItemStack boundsStaff = new ItemStack(AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID, held.getQuantity());
        return container.replaceItemStackInSlot(slot, held, boundsStaff).succeeded();
    }

    public static boolean exitBoundsMode(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        byte slot = hotbar.getActiveSlot();
        if (slot < 0) {
            return false;
        }
        ItemContainer container = hotbar.getInventory();
        ItemStack held = container.getItemStack(slot);
        if (isMainStaff(held)) {
            return true;
        }
        if (!isBoundsStaff(held)) {
            return false;
        }
        ItemStack mainStaff = new ItemStack(AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID, held.getQuantity());
        return container.replaceItemStackInSlot(slot, held, mainStaff).succeeded();
    }
}
