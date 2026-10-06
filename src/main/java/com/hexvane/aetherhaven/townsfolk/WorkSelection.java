package com.hexvane.aetherhaven.townsfolk;

/** Compare available jobs, not disabled professions or work still waiting for supplies. */
public final class WorkSelection {
    private WorkSelection() {}
    public static boolean chooseDelivery(int logisticsPriority,int workplacePriority,
            boolean carrying,boolean travellingDelivery,boolean workingAtSite) {
        // Finish carrying existing supplies before changing professions. Guard, wage and life gates apply outside this rule.
        if(carrying)return true;
        if(logisticsPriority<=0)return false;
        if(workingAtSite)return false;
        if(travellingDelivery)return true;
        // Equal priorities follow column order: workplace work precedes Logistics.
        return workplacePriority<=0||logisticsPriority<workplacePriority;
    }
}
