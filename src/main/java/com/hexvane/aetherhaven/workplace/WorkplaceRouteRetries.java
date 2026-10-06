package com.hexvane.aetherhaven.workplace;

import java.util.*;

/** Per-worker, unsaved route failures. A blocked site must not monopolize all other enabled work. */
public final class WorkplaceRouteRetries {
    private static final long RETRY_STEP=15_000, MAX_RETRY=120_000, FORGET_AFTER=300_000;
    private record Failure(int attempts,long until) {}
    private final Map<UUID,Failure> failures=new LinkedHashMap<>();

    public boolean canTry(UUID site,long now) {
        var failure=failures.get(site);
        return failure==null||now>=failure.until();
    }
    public int approachAttempt(UUID site,long now) {
        var failure=failures.get(site);
        return failure==null||now-failure.until()>FORGET_AFTER?0:failure.attempts();
    }
    public void failed(UUID site,long now) {
        failures.values().removeIf(f->now-f.until()>FORGET_AFTER);
        int attempts=Math.min(1024,approachAttempt(site,now)+1);
        failures.remove(site);
        failures.put(site,new Failure(attempts,now+Math.min(MAX_RETRY,RETRY_STEP*attempts)));
        if(failures.size()>64)failures.remove(failures.keySet().iterator().next());
    }
    public void succeeded(UUID site) { failures.remove(site); }
    public WorkplaceRouteRetries copy() {
        var copy=new WorkplaceRouteRetries();copy.failures.putAll(failures);return copy;
    }
}
