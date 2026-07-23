package com.hexvane.aetherhaven.patrol;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class GuardCombatClock {
    private GuardCombatClock() {}

    public static long nowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod == null) {
            return System.currentTimeMillis();
        }
        TimeResource tr = store.getResource(mod.getTimeResourceType());
        if (tr == null) {
            return System.currentTimeMillis();
        }
        return tr.getNow().toEpochMilli();
    }
}
