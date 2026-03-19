package com.hexvane.aetherhaven.inventory;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.List;
import javax.annotation.Nonnull;

public final class InventoryMaterials {
    private InventoryMaterials() {}

    public static int count(@Nonnull ItemContainer container, @Nonnull String itemId) {
        return container.countItemStacks(stack -> itemId.equals(stack.getItemId()));
    }

    public static boolean hasAll(@Nonnull ItemContainer container, @Nonnull List<MaterialRequirement> materials) {
        for (MaterialRequirement m : materials) {
            if (m.getItemId() == null || m.getCount() <= 0) {
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
            if (m.getItemId() == null || m.getCount() <= 0) {
                continue;
            }
            container.removeItemStack(new ItemStack(m.getItemId(), m.getCount()));
        }
    }
}
