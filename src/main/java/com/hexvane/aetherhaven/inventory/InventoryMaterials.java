package com.hexvane.aetherhaven.inventory;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import java.util.List;
import javax.annotation.Nonnull;

public final class InventoryMaterials {
    private InventoryMaterials() {}

    public static int count(@Nonnull ItemContainer container, @Nonnull String itemId) {
        return container.countItemStacks(stack -> itemId.equals(stack.getItemId()));
    }

    public static int countResourceType(@Nonnull ItemContainer container, @Nonnull String resourceTypeId) {
        return container.countItemStacks(stack -> itemStackHasResourceType(stack, resourceTypeId));
    }

    public static int count(@Nonnull ItemContainer container, @Nonnull MaterialRequirement m) {
        if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
            return countResourceType(container, m.getResourceTypeId().trim());
        }
        String itemId = m.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        return count(container, itemId);
    }

    public static boolean hasAll(@Nonnull ItemContainer container, @Nonnull List<MaterialRequirement> materials) {
        for (MaterialRequirement m : materials) {
            if (m.getCount() <= 0) {
                continue;
            }
            if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
                if (countResourceType(container, m.getResourceTypeId().trim()) < m.getCount()) {
                    return false;
                }
                continue;
            }
            if (m.getItemId() == null || m.getItemId().isBlank()) {
                continue;
            }
            if (count(container, m.getItemId()) < m.getCount()) {
                return false;
            }
        }
        return true;
    }

    public static void removeAll(@Nonnull ItemContainer container, @Nonnull List<MaterialRequirement> materials) {
        for (MaterialRequirement m : materials) {
            if (m.getCount() <= 0) {
                continue;
            }
            if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
                removeResourceType(container, m.getResourceTypeId().trim(), m.getCount());
                continue;
            }
            if (m.getItemId() == null || m.getItemId().isBlank()) {
                continue;
            }
            container.removeItemStack(new ItemStack(m.getItemId(), m.getCount()));
        }
    }

    private static void removeResourceType(@Nonnull ItemContainer container, @Nonnull String resourceTypeId, int amount) {
        int remaining = amount;
        for (short i = 0; i < container.getCapacity() && remaining > 0; i++) {
            ItemStack stack = container.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !itemStackHasResourceType(stack, resourceTypeId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getQuantity());
            ItemStackSlotTransaction tx = container.removeItemStackFromSlot(i, take);
            if (tx.succeeded()) {
                remaining -= take;
            }
        }
    }

    public static boolean itemStackHasResourceType(@Nonnull ItemStack stack, @Nonnull String resourceTypeId) {
        Item item = Item.getAssetMap().getAsset(stack.getItemId());
        return item != null && itemHasResourceType(item, resourceTypeId);
    }

    public static boolean itemHasResourceType(@Nonnull Item item, @Nonnull String resourceTypeId) {
        ItemResourceType[] types = item.getResourceTypes();
        if (types == null) {
            return false;
        }
        for (ItemResourceType t : types) {
            if (t != null && t.id != null && resourceTypeId.equals(t.id)) {
                return true;
            }
        }
        return false;
    }
}
