package com.hexvane.aetherhaven.backpack;

import com.hexvane.aetherhaven.warehouse.WarehouseCraftingContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lets a native crafting bench use what is inside the backpacks its player is carrying.
 *
 * <p>Only bags in the crafter's own inventory are opened. A bag lying in a chest or the warehouse stays shut, and
 * nothing is ever put into a bag from here: these are take-only views, like the warehouse's.
 */
public final class BackpackCraftingSupply {
    private BackpackCraftingSupply() {}

    /** A bag found in an inventory, with where it sits. */
    private record Found(short slot, ItemStack stack, Backpack pack) {}

    /**
     * One take-only slot for every filled slot of every carried backpack.
     *
     * @param inventory the crafting player's current inventory, or null when they are gone
     */
    public static List<WarehouseCraftingContainer.Slot> slots(Supplier<ItemContainer> inventory) {
        var slots = new ArrayList<WarehouseCraftingContainer.Slot>();
        ItemContainer carried = inventory.get();
        if (carried == null) {
            return slots;
        }
        for (short at = 0; at < carried.getCapacity(); at++) {
            ItemStack stack = carried.getItemStack(at);
            Backpack pack = Backpack.read(stack);
            if (pack == null || pack.used() == 0) {
                continue;
            }
            UUID bag = pack.id;
            for (int i = 0; i < pack.tier.slots; i++) {
                if (ItemStack.isEmpty(pack.items[i])) {
                    continue;
                }
                final int index = i;
                slots.add(new WarehouseCraftingContainer.Slot(
                    () -> {
                        Found found = find(inventory.get(), bag);
                        return found == null ? null : found.pack().items[index];
                    },
                    count -> take(inventory.get(), bag, index, count)));
            }
        }
        return slots;
    }

    /** True when the inventory holds a backpack with anything in it. */
    public static boolean carriesSupplies(ItemContainer inventory) {
        if (inventory == null) {
            return false;
        }
        for (short at = 0; at < inventory.getCapacity(); at++) {
            Backpack pack = Backpack.read(inventory.getItemStack(at));
            if (pack != null && pack.used() > 0) {
                return true;
            }
        }
        return false;
    }

    private static Found find(ItemContainer inventory, UUID bag) {
        if (inventory == null) {
            return null;
        }
        for (short at = 0; at < inventory.getCapacity(); at++) {
            ItemStack stack = inventory.getItemStack(at);
            if (!Backpack.isBackpack(stack)) {
                continue;
            }
            Backpack pack = Backpack.read(stack);
            if (pack != null && bag.equals(pack.id)) {
                return new Found(at, stack, pack);
            }
        }
        return null;
    }

    /** Removes items from one slot of a carried bag, as a single change to the bag item. */
    static boolean take(ItemContainer inventory, UUID bag, int index, int count) {
        Found found = find(inventory, bag);
        if (found == null || count <= 0) {
            return false;
        }
        return BackpackExchange.change(inventory, found.slot(), found.stack(), Map.of(), pack -> {
            ItemStack held = pack.items[index];
            if (ItemStack.isEmpty(held) || held.getQuantity() < count) {
                return false;
            }
            pack.items[index] = held.getQuantity() == count ? null : held.withQuantity(held.getQuantity() - count);
            return true;
        });
    }
}
