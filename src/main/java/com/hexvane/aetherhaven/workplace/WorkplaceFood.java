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
    public WorkplaceFood() {}
    WorkplaceFood(RanchCare herd) { ranch=herd; }
    public RanchCare ranch() { if(ranch==null)ranch=new RanchCare();return ranch; }
    public String careIssue() { return careIssue; }
    public void careFailed(String issue) { careIssue=issue; }
    public String lastCare() { return lastCare==null?"No recorded care action yet.":lastCare; }

    public static boolean supports(TownsfolkWorkType type) {
        return type == TownsfolkWorkType.FARMING || type == TownsfolkWorkType.RANCHING;
    }
    public int foodPoints() { return tended && equipmentReady && minutesSinceTending < NEGLECT_MINUTES ? FOOD_POINTS : 0; }
    public long minutesSinceTending() { return Math.max(0, minutesSinceTending); }
    public long minutesUntilNeglected() { return tended ? Math.max(0, NEGLECT_MINUTES - minutesSinceTending()) : 0; }
    public boolean needsTending() { return !tended || minutesSinceTending() >= TENDING_INTERVAL_MINUTES; }
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
        if (!present || !ready || !needsTending() || !Double.isFinite(seconds) || seconds <= 0) return false;
        if (!Double.isFinite(multiplier)) multiplier = 1;
        equipmentReady = true;
        workSeconds = Math.min(WORK_SECONDS, workSeconds() + Math.min(.25, seconds) * Math.clamp(multiplier, 1, 2));
        return workSeconds >= WORK_SECONDS;
    }

    /** Commit support only after the actual world action succeeds, outside entity iteration. */
    public boolean completeCare(java.util.function.BooleanSupplier action) {
        return completeCare(action,null);
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
        if (!equipmentReady) return "Needs its workbench and a tended growing area.";
        if (!tended) return "Waiting for its first tending visit. No food support yet.";
        if (foodPoints() == 0) return "Untended for a full day. Food support paused.";
        long remaining = minutesUntilNeglected();
        return "Supports 5 residents | " + remaining / 60 + "h " + remaining % 60 + "m before neglect";
    }
}
