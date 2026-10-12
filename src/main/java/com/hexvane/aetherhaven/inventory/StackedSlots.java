package com.hexvane.aetherhaven.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Slots that may hold several native stacks of one item. The backpack and the town warehouse share these rules, so a
 * slot fills, reports its room and gives items back the same way in both.
 */
public final class StackedSlots {
    private StackedSlots() {}

    /** Most of this item one slot holds when it stores {@code stacks} native stacks. */
    public static int limit(ItemStack stack, int stacks) {
        return Math.max(1, stack.getItem().getMaxStack()) * Math.max(1, stacks);
    }

    /** What a single click takes out of a slot: never more than one native stack, however much the slot holds. */
    public static int oneStack(ItemStack held) {
        return Math.min(held.getQuantity(), Math.max(1, held.getItem().getMaxStack()));
    }

    /** True when one of the first {@code size} slots already holds a stack this one merges with. */
    public static boolean holds(ItemStack[] slots, int size, ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return false;
        for (int i = 0; i < Math.min(size, slots.length); i++)
            if (!ItemStack.isEmpty(slots[i]) && slots[i].isStackableWith(stack)) return true;
        return false;
    }

    /**
     * Tops up matching slots first, then fills empty ones, changing only the given array.
     *
     * @param size   how many of the slots are in use
     * @param stacks native stacks one slot may hold
     * @return the number accepted. Native stack metadata is retained.
     */
    public static int insert(ItemStack[] slots, int size, ItemStack stack, int stacks) {
        if (ItemStack.isEmpty(stack)) return 0;
        int left = stack.getQuantity(), limit = limit(stack, stacks);
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < Math.min(size, slots.length) && left > 0; i++) {
            var current = slots[i];
            boolean empty = ItemStack.isEmpty(current);
            if (pass == 0 ? empty || !current.isStackableWith(stack) : !empty) continue;
            int held = empty ? 0 : current.getQuantity(), take = Math.min(left, Math.max(0, limit - held));
            if (take > 0) { slots[i] = stack.withQuantity(held + take); left -= take; }
        }
        return stack.getQuantity() - left;
    }
}
