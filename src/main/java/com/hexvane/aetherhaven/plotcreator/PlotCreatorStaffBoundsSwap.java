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
    private static final String ORIGINAL_STAFF = "AetherhavenBoundsOriginalStaff";
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
        if (!isMainStaff(held) && !BuildingEditorInteractions.isBuildingEditorStaff(held)) {
            return false;
        }
        ItemStack boundsStaff = toBoundsStaff(held);
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
        ItemStack mainStaff = fromBoundsStaff(held);
        return container.replaceItemStackInSlot(slot, held, mainStaff).succeeded();
    }
    public static boolean isEditorBoundsStaff(@Nullable ItemStack stack) {
        if (!isBoundsStaff(stack)) return false;
        return BuildingEditorInteractions.isBuildingEditorStaff(stack.getFromMetadataOrNull(ORIGINAL_STAFF, ItemStack.CODEC));
    }

    static ItemStack toBoundsStaff(ItemStack held) {
        if (!isMainStaff(held) && !BuildingEditorInteractions.isBuildingEditorStaff(held))
            throw new IllegalArgumentException("A wizard staff is required");
        return new ItemStack(AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID, held.getQuantity())
            .withMetadata(ORIGINAL_STAFF, ItemStack.CODEC, held);
    }
    static ItemStack fromBoundsStaff(ItemStack held) {
        var restored = held.getFromMetadataOrNull(ORIGINAL_STAFF, ItemStack.CODEC);
        if (isMainStaff(restored) || BuildingEditorInteractions.isBuildingEditorStaff(restored)) return restored;
        // Old saves had a plain bounds staff with no original-item receipt.
        return new ItemStack(AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID, held.getQuantity());
    }
}
