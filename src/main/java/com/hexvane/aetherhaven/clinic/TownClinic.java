package com.hexvane.aetherhaven.clinic;

/** Saved clinic supplies. Treatment progress belongs to the loaded doctor, not offline time. */
public final class TownClinic {
    public static final String SUPPLY_ITEM = "Potion_Health_Lesser";
    public static final int SUPPLY_VALUE = 80, CAPACITY = 800;
    public static final double TREATMENT_SECONDS = 12;
    public static final int TREATMENT_HEAL = 40;
    private int medicine;
    private boolean paused;
    private long revision;

    public int medicine() { return Math.clamp(medicine, 0, CAPACITY); }
    public boolean paused() { return paused; }
    public long revision() { return revision; }
    public boolean canSupply(int bottles) {
        return bottles > 0 && bottles <= CAPACITY / SUPPLY_VALUE
            && (long) medicine() + (long) bottles * SUPPLY_VALUE <= CAPACITY;
    }
    public boolean supply(int bottles) {
        if (!canSupply(bottles)) return false;
        medicine = medicine() + bottles * SUPPLY_VALUE; revision++; return true;
    }
    public void toggle() { paused = !paused; revision++; }
    /** Charge only for actual healing; never consume while paused or already healthy. */
    public float treat(float current, float max) {
        if (paused || !Float.isFinite(current) || !Float.isFinite(max) || current <= 0 || max <= current) return 0;
        float heal = Math.min(max - current, Math.min(TREATMENT_HEAL, medicine()));
        if (heal <= 0) return 0;
        medicine = medicine() - (int) Math.ceil(heal); revision++; return heal;
    }
}
