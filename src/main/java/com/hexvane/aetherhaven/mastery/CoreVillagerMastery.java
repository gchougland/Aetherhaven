package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

/** One learned production level and one owned tool, independent of Townsfolk professions. */
public final class CoreVillagerMastery {
    public static final int MAX_LEVEL = 10;
    private int level = 1;
    private long xp;
    private String toolData;
    /** Work that has not yet added up to one XP, for villagers whose single pieces of work are worth less than one. */
    private int workTowardXp;
    private transient ItemStack tool;

    public int level() { return Math.clamp(level, 1, MAX_LEVEL); }
    public long xpToNextLevel() { return level() == MAX_LEVEL ? 0 : 100L + 50L * (level() - 1); }
    public long xpInLevel() { return level() == MAX_LEVEL ? 0 : Math.clamp(xp, 0L, xpToNextLevel() - 1); }
    /** Award alongside successful output delivery or bottling, before saving the town. */
    public long earnXp(long amount) {
        if (amount <= 0 || level() == MAX_LEVEL) return 0;
        long remaining = amount;
        xp = xpInLevel();
        while (remaining > 0 && level() < MAX_LEVEL) {
            long accepted = Math.min(remaining, xpToNextLevel() - xp);
            xp += accepted; remaining -= accepted;
            if (xp == xpToNextLevel()) { level = level() + 1; xp = 0; }
        }
        return amount - remaining;
    }
    /**
     * Award for work worth a fraction of an XP each, such as one block the Builder places. Whole points are paid at
     * once and the rest is kept for the next call, so nothing is lost between two steps of the same job.
     *
     * @return the XP paid now
     */
    public long earnWork(long units, int unitsPerXp) {
        if (units <= 0 || unitsPerXp <= 0 || level() == MAX_LEVEL) return 0;
        long total = workTowardXp() % unitsPerXp + units;
        long earned = earnXp(total / unitsPerXp);
        workTowardXp = level() == MAX_LEVEL ? 0 : (int) (total % unitsPerXp);
        return earned;
    }
    public int workTowardXp() { return Math.max(0, workTowardXp); }
    public boolean train() {
        if (level() == MAX_LEVEL) return false;
        xp = xpInLevel();
        level = level() + 1;
        if (level() == MAX_LEVEL) xp = 0;
        return true;
    }
    public ItemStack tool() {
        if (tool == null && toolData != null) tool = ItemStack.CODEC.decode(BsonDocument.parse(toolData), new ExtraInfo());
        return tool;
    }
    public void setTool(ItemStack stack) {
        String encoded = TownsfolkProgress.encodeProfessionKit(stack);
        toolData = encoded;
        tool = stack;
    }
}
