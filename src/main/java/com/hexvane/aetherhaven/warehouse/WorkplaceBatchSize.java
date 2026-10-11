package com.hexvane.aetherhaven.warehouse;

/**
 * How many crafts one delivery to a workplace covers.
 *
 * <p>A delivery used to hold the materials for a single craft, so every item made cost a courier a round trip. One
 * delivery now covers several crafts of the same task. It is still bounded: a bench is held by one delivery at a time,
 * so a very large one would keep the tasks below it waiting, and a task with no end must not empty the warehouse.
 */
public final class WorkplaceBatchSize {
    /** Most crafts one delivery to an ordinary bench may cover. */
    public static final int MAX_CRAFTS = 16;
    /** A "keep crafting" task has no end, so each delivery supplies only this many crafts before stock is looked at again. */
    public static final int FOREVER_CRAFTS = 8;
    /** Most crafts one furnace load may cover. A furnace takes a whole stack of ore. */
    public static final int MAX_FURNACE_CRAFTS = 100;

    private WorkplaceBatchSize() {}

    /**
     * @param need crafts the task still wants, counting what is already stored, on its way or queued
     * @param forever true for a task that never finishes
     */
    public static int crafts(int need, boolean forever) {
        return Math.max(1, Math.min(need, forever ? FOREVER_CRAFTS : MAX_CRAFTS));
    }

    /** As {@link #crafts} for a furnace, which is also limited by what one input and one output slot can hold. */
    public static int furnaceCrafts(int need, boolean forever, int stackLimit) {
        int wanted = forever ? Math.min(need, Math.max(FOREVER_CRAFTS, stackLimit)) : need;
        return Math.max(1, Math.min(Math.min(wanted, stackLimit), MAX_FURNACE_CRAFTS));
    }

    /** The next smaller size to try when a delivery of this size could not be put together. Zero means give up. */
    public static int smaller(int crafts) {
        return crafts <= 1 ? 0 : crafts / 2;
    }

    /** Stacks needed to carry a quantity of one item. */
    public static int stacks(int quantity, int maxStack) {
        return quantity <= 0 ? 0 : (quantity + Math.max(1, maxStack) - 1) / Math.max(1, maxStack);
    }
}
