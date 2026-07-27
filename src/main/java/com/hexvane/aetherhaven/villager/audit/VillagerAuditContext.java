package com.hexvane.aetherhaven.villager.audit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Thread-local source tag for villager entity removal audit lines. */
public final class VillagerAuditContext {
    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();

    private VillagerAuditContext() {}

    public static void runWithSource(@Nonnull String source, @Nonnull Runnable action) {
        String previous = SOURCE.get();
        SOURCE.set(source);
        try {
            action.run();
        } finally {
            if (previous != null) {
                SOURCE.set(previous);
            } else {
                SOURCE.remove();
            }
        }
    }

    public static void removeEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String source
    ) {
        runWithSource(source, () -> store.removeEntity(ref, RemoveReason.REMOVE));
    }

    @Nullable
    public static String currentSource() {
        return SOURCE.get();
    }
}
