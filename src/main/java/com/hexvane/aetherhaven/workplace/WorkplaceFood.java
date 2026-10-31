package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;

/** Saved tending state. Only simulated, member-online game minutes age a food site. */
public final class WorkplaceFood {
    public static final int FOOD_POINTS = 10;
    public static final long NEGLECT_MINUTES = 24 * 60;
    public static final long TENDING_INTERVAL_MINUTES = 6 * 60;
    public static final double WORK_SECONDS = 12;
    private boolean tended, equipmentReady;
    private long minutesSinceTending;
    private double workSeconds;
    private int completedVisits;
    private RanchCare ranch;
    private String careIssue;
    private String lastCare;
    /** Null is a pre-crop-count save (or a ranch). First loaded farming inspection replaces it. */
    private Integer growingCrops;
    private int emptyGrowingSpaces;
    public WorkplaceFood() {}
    WorkplaceFood(RanchCare herd) { ranch=herd; }
    public RanchCare ranch() { if(ranch==null)ranch=new RanchCare();return ranch; }
    public String careIssue() { return careIssue; }
    public void careFailed(String issue) { careIssue=issue; }
    public String lastCare() { return lastCare==null?"No recorded care action yet.":lastCare; }

    public static boolean supports(TownsfolkWorkType type) {
        return type == TownsfolkWorkType.FARMING || type == TownsfolkWorkType.RANCHING;
    }
    public int foodCapacity() { return growingCrops == null ? FOOD_POINTS : Math.max(0, growingCrops); }
    public int growingCrops() { return growingCrops == null ? 0 : Math.max(0, growingCrops); }
    public int emptyGrowingSpaces() { return Math.max(0, emptyGrowingSpaces); }
    public void observeFarm(int crops, int emptySpaces, boolean ready) {
        growingCrops = Math.max(0, crops);
        emptyGrowingSpaces = Math.max(0, emptySpaces);
        equipmentReady = ready;
    }
    public int foodPoints() { return tended && equipmentReady && minutesSinceTending < NEGLECT_MINUTES ? foodCapacity() : 0; }
    public long minutesSinceTending() { return Math.max(0, minutesSinceTending); }
    public long minutesUntilNeglected() { return tended ? Math.max(0, NEGLECT_MINUTES - minutesSinceTending()) : 0; }
    public boolean needsTending() { return !tended || minutesSinceTending() >= TENDING_INTERVAL_MINUTES; }
    public boolean needsWork() { return needsTending() || emptyGrowingSpaces() > 0; }
    public double workSeconds() { return Math.max(0, workSeconds); }
    public int completedVisits() { return Math.max(0, completedVisits); }
    public boolean equipmentReady() { return equipmentReady; }

    public void advance(long minutes, boolean memberOnline, boolean fullySimulated, boolean ready) {
        advance(minutes,memberOnline,fullySimulated,ready,true);
    }
    public void advance(long minutes, boolean memberOnline, boolean fullySimulated, boolean ready, boolean offspringPresent) {
        if (!memberOnline || !fullySimulated || minutes <= 0) return;
        equipmentReady = ready;
        if(ready && tended && offspringPresent)ranch().advance(Math.min(minutes,Math.max(0,NEGLECT_MINUTES-minutesSinceTending())));
        minutesSinceTending = Math.min(NEGLECT_MINUTES, minutesSinceTending() + Math.min(minutes, NEGLECT_MINUTES));
    }

    /** Called only while the sole eligible worker is physically tending this site. */
    public boolean work(double seconds, boolean present, boolean ready) {
        return work(seconds, present, ready, 1);
    }

    public boolean work(double seconds, boolean present, boolean ready, double multiplier) {
        if (!present || !ready || !needsWork() || !Double.isFinite(seconds) || seconds <= 0) return false;
        if (!Double.isFinite(multiplier)) multiplier = 1;
        equipmentReady = true;
        workSeconds = Math.min(WORK_SECONDS, workSeconds() + Math.min(.25, seconds) * Math.clamp(multiplier, 1, 2));
        return workSeconds >= WORK_SECONDS;
    }

    /** Commit support only after the actual world action succeeds, outside entity iteration. */
    public boolean completeCare(java.util.function.BooleanSupplier action) {
        return completeCare(action,null);
    }

    /** Establishing wheat is real attended work, but food support begins only once the patch is ready. */
    public boolean completePlanting(java.util.function.BooleanSupplier action) {
        if (!needsWork() || workSeconds() < WORK_SECONDS || !action.getAsBoolean()) return false;
        workSeconds = 0;
        careIssue = null;
        lastCare = "Wheat planted. Filling the remaining clear soil spaces for town food.";
        return true;
    }

    public boolean completeCare(java.util.function.BooleanSupplier action,String description) {
        if(!needsTending() || workSeconds() < WORK_SECONDS || !action.getAsBoolean())return false;
        tended = true;
        careIssue = null;
        lastCare = description;
        minutesSinceTending = 0;
        workSeconds = 0;
        if (completedVisits < Integer.MAX_VALUE) completedVisits++;
        return true;
    }

    public String status() {
        if (!equipmentReady) return "Setup incomplete. Open Food support and tending to see the missing requirements.";
        if (!tended) return "Waiting for its first tending visit. No food support yet.";
        if (growingCrops != null && growingCrops() == 0) return "No edible crops growing. Food support paused.";
        if (foodPoints() == 0) return "Untended for a full day. Food support paused.";
        long remaining = minutesUntilNeglected();
        return "Supports " + foodPoints() / com.hexvane.aetherhaven.town.TownPopulation.FOOD_PER_RESIDENT
            + " residents | " + remaining / 60 + "h " + remaining % 60 + "m before neglect";
    }
}
