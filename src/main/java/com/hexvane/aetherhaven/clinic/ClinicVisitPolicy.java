package com.hexvane.aetherhaven.clinic;

/** A scratch does not interrupt work; an admitted patient stays until recovered. */
public final class ClinicVisitPolicy {
    public static final double QUIET_SECONDS=30, TRAVEL_SECONDS=90;
    private ClinicVisitPolicy() {}
    public static double advanceQuiet(double quiet,double dt,boolean interrupted) {
        if(interrupted||!Double.isFinite(quiet)||!Double.isFinite(dt))return 0;
        return Math.min(QUIET_SECONDS,Math.max(0,quiet)+Math.clamp(dt,0,.25));
    }
    public static boolean needsVisit(float health,float max,boolean alreadyVisiting,double quietSeconds) {
        if(!Float.isFinite(health)||!Float.isFinite(max)||!Double.isFinite(quietSeconds)||health<=0||max<=0||quietSeconds<QUIET_SECONDS) return false;
        return max-health >= (alreadyVisiting?1:Math.max(10,max*.25));
    }
}
