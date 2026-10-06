package com.hexvane.aetherhaven.workplace;

import java.util.UUID;

/** Saved, bounded herd cycle. Only the workplace's own offspring may be harvested. */
public final class RanchCare {
    public static final long GROW_MINUTES = 24 * 60;
    public static final int FEEDS_PER_BIRTH = 4;
    private UUID offspring;
    private String adultRole;
    private boolean grown;
    private long ageMinutes;
    private int feeds;
    public UUID offspring() { return offspring; }
    public String adultRole() { return adultRole; }
    public boolean grown() { return grown; }
    public long ageMinutes() { return Math.max(0,ageMinutes); }
    public boolean ready() { return offspring != null && ageMinutes() >= GROW_MINUTES; }
    public boolean canBreed() { return offspring == null && feeds >= FEEDS_PER_BIRTH; }
    public void fed() { feeds=Math.min(FEEDS_PER_BIRTH,feeds+1); }
    public void advance(long minutes) {
        if (offspring != null && minutes > 0) ageMinutes=Math.min(GROW_MINUTES,ageMinutes()+Math.min(minutes,GROW_MINUTES));
    }
    public void born(UUID id,String adult) { offspring=id;adultRole=adult;grown=false;ageMinutes=0;feeds=0; }
    public void matured(UUID replacement) { offspring=replacement;grown=true;ageMinutes=0; }
    public void harvested() { offspring=null;adultRole=null;grown=false;ageMinutes=0;feeds=0; }
    /** A late death/removal notification must never clear a newer offspring. */
    public boolean lost(UUID id) {
        if(id==null || !id.equals(offspring))return false;
        harvested();return true;
    }
    public String status() {
        if(offspring==null)return canBreed()?"Ready to breed. Keep two animals of the same kind near the trough and leave room for their young."
            :"Feeding breeding animals: "+feeds+" / "+FEEDS_PER_BIRTH+" visits before breeding.";
        if(ready())return grown?"Livestock ready for town food. Waiting for the next tending visit.":"Young livestock ready to mature. Waiting for the next tending visit.";
        long remaining=Math.max(0,GROW_MINUTES-ageMinutes());
        return (grown?"Livestock":"Young livestock")+": "+remaining/60+"h "+remaining%60+"m of tended growth remaining. No item output.";
    }
}
