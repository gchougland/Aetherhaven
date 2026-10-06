package com.hexvane.aetherhaven.townsfolk;

/** Provisional awards for completed physical work. No award for travel, polling or cancelled work. */
public final class TownsfolkMastery {
    private TownsfolkMastery() {}

    public enum Work {
        CRAFT(10), CULTIVATION_PLANT(2), CULTIVATION_HARVEST(10), FOOD_CARE(5),
        SMELTING_COLLECTION(2), DELIVERY(5), MINING_DELIVERY(2), BUILDING_BLOCK(1), SHOP_SALE(2);

        public final int xp;
        Work(int xp) { this.xp = xp; }
    }

    /** The caller must use the transaction's actual result and persist the same town afterward. */
    public static long completed(TownsfolkProgress progress, Work work, boolean committed) {
        return progress == null || work == null || !committed ? 0 : progress.earnMasteryXp(work.xp);
    }
}
