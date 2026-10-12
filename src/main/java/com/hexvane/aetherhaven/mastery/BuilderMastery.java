package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.town.TownRecord;

/**
 * What the core Builder's Mastery does at a building site. A site that nobody attends places one block every slot of
 * its own (the building's self build time divided by its blocks). While the Builder stands at it, a step comes
 * {@link #STEP_DIVISOR} times as often and places as many blocks as his Mastery level.
 *
 * <p>Townsfolk builders of a Builder's Yard do not come through here. They place single pieces with their own effort
 * and earn townsfolk Mastery.
 */
public final class BuilderMastery {
    private BuilderMastery() {}

    /** The Builder's binding kind, and his key in the town's saved Mastery. */
    public static final String KIND = "builder";
    /**
     * Steps come this many times as often while he is at the site. Unchanged from before he had Mastery, when he also
     * placed four blocks a step whatever his skill.
     */
    public static final int STEP_DIVISOR = 4;
    /**
     * Blocks he places or clears for one Mastery XP. A typical building is 350 to 600 blocks, so one building a game
     * day is 70 to 120 XP, the pace of the Farmer and the Miner (about 100 a day).
     */
    public static final int BLOCKS_PER_XP = 5;

    /** Blocks placed, or obstructions cleared, in one step: his Mastery level. */
    public static int blocksPerStep(int level) {
        return Math.clamp(level, 1, CoreVillagerMastery.MAX_LEVEL);
    }

    public static int blocksPerStep(TownRecord town) {
        return blocksPerStep(CoreVillagerMasteryService.level(town, KIND));
    }

    /**
     * Mastery for blocks the Builder placed or cleared himself in one step. Never call it for blocks a player, a
     * townsfolk builder or the unattended site placed.
     *
     * @return the XP paid now; the rest of the blocks count toward the next point
     */
    public static long earn(TownRecord town, int blocks) {
        if (town == null || blocks <= 0) return 0;
        return CoreVillagerMasteryService.progress(town, KIND).earnWork(blocks, BLOCKS_PER_XP);
    }

    /** Steps the Builder needs for this many blocks at one level. */
    public static long steps(long blocks, int level) {
        int each = blocksPerStep(level);
        return blocks <= 0 ? 0 : (blocks + each - 1) / each;
    }

    /** Time between two of his steps at a site whose unattended slot is {@code unattendedSlotMs}. */
    public static long stepMs(long unattendedSlotMs) {
        return Math.max(1L, unattendedSlotMs / STEP_DIVISOR);
    }

    /** Time he needs for this many blocks when he stays at the site, in the site's own clock. */
    public static long buildMs(long blocks, long unattendedSlotMs, int level) {
        return steps(blocks, level) * stepMs(unattendedSlotMs);
    }
}
