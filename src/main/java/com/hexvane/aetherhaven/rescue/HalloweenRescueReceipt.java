package com.hexvane.aetherhaven.rescue;

import java.util.Objects;
import java.util.UUID;

/** One authoritative Halloween encounter. Immutable snapshots are safe to save while another world observes Jack. */
public final class HalloweenRescueReceipt {
    public record Encounter(UUID token, UUID entity, String world, int x, int y, int z) {}
    public enum Observation { CURRENT, UPDATED, ADOPTED, REJECTED }
    private volatile Encounter encounter;
    // Retain this fence after retirement so an older saved entity cannot become a new encounter on load.
    private volatile boolean managed;
    private volatile boolean completed;
    private transient UUID spawning;

    public synchronized Encounter current() { return encounter; }
    public synchronized boolean authorizes(UUID entity,String world) {
        return entity!=null&&world!=null&&((encounter==null&&!managed)
            ||encounter!=null&&entity.equals(encounter.entity())&&world.equals(encounter.world()));
    }
    public synchronized boolean inFlight(Encounter expected) {
        return expected!=null&&expected.equals(encounter)&&expected.token().equals(spawning);
    }
    public synchronized Encounter reserve(String world,int x,int y,int z) {
        if(completed||encounter!=null||world==null||world.isBlank())return null;
        managed=true;spawning=UUID.randomUUID();
        return encounter=new Encounter(spawning,null,world,x,y,z);
    }
    public synchronized boolean bind(UUID token,UUID entity) {
        if(entity==null||encounter==null||!Objects.equals(token,spawning)||!token.equals(encounter.token()))return false;
        encounter=new Encounter(token,entity,encounter.world(),encounter.x(),encounter.y(),encounter.z());
        spawning=null;return true;
    }
    /** Also rolls back a partially initialized spawn, but never a newer encounter. */
    public synchronized boolean rollback(UUID token) {
        if(encounter==null||!Objects.equals(token,encounter.token()))return false;
        encounter=null;spawning=null;return true;
    }
    public synchronized boolean retire(Encounter expected) {
        if(expected==null||!expected.equals(encounter)||spawning!=null)return false;
        encounter=null;managed=true;return true;
    }
    public synchronized Observation observe(UUID entity,String world,int x,int y,int z) {
        if(entity==null||world==null||world.isBlank())return Observation.REJECTED;
        if(encounter==null) {
            if(managed)return Observation.REJECTED;
            managed=true;encounter=new Encounter(UUID.randomUUID(),entity,world,x,y,z);
            return Observation.ADOPTED;
        }
        if(!entity.equals(encounter.entity())||!world.equals(encounter.world()))return Observation.REJECTED;
        if(x==encounter.x()&&y==encounter.y()&&z==encounter.z())return Observation.CURRENT;
        encounter=new Encounter(encounter.token(),entity,world,x,y,z);return Observation.UPDATED;
    }
    /** Unloading records the last position; only a confirmed permanent removal releases ownership. */
    public synchronized boolean removed(UUID entity,String world,boolean unload,int x,int y,int z) {
        if(encounter==null||!Objects.equals(entity,encounter.entity())||!Objects.equals(world,encounter.world()))return false;
        if(unload)return observe(entity,world,x,y,z)==Observation.UPDATED;
        encounter=null;spawning=null;managed=true;return true;
    }
    public synchronized void complete() { encounter=null;spawning=null;managed=true;completed=true; }
}
