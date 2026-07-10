package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves whether a tracked entity uuid is live in loaded chunks, confirmed gone, or unknown because its chunk is
 * unloaded. Never treat {@link EntityPresence#UNKNOWN_UNLOADED} as a confirmed absence.
 */
public final class EntityPresenceUtil {
    public enum EntityPresence {
        /** Ref exists and is valid — entity is in the loaded simulation. */
        LOADED_LIVE,
        /** Ref exists but is invalid — safe to treat as despawned/dead. */
        LOADED_GONE,
        /** Ref is null — entity may still exist in chunk storage (unloaded). */
        UNKNOWN_UNLOADED
    }

    private EntityPresenceUtil() {}

    @Nonnull
    public static EntityPresence resolve(@Nonnull Store<EntityStore> store, @Nullable UUID entityUuid) {
        if (entityUuid == null) {
            return EntityPresence.UNKNOWN_UNLOADED;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null) {
            return EntityPresence.UNKNOWN_UNLOADED;
        }
        return ref.isValid() ? EntityPresence.LOADED_LIVE : EntityPresence.LOADED_GONE;
    }

    public static boolean isLoadedLive(@Nonnull EntityPresence presence) {
        return presence == EntityPresence.LOADED_LIVE;
    }

    public static boolean isConfirmedAbsent(@Nonnull EntityPresence presence) {
        return presence == EntityPresence.LOADED_GONE;
    }

    public static boolean isUnknownUnloaded(@Nonnull EntityPresence presence) {
        return presence == EntityPresence.UNKNOWN_UNLOADED;
    }

    /**
     * True when a tourist save row may be released during reconcile ({@code releaseMissing}) — only when the entity is
     * confirmed absent and town NPC territory chunks are loaded.
     */
    public static boolean shouldReleaseMissingTouristRecord(
        @Nonnull EntityPresence presence,
        boolean releaseMissing,
        boolean townNpcChunksLoaded
    ) {
        if (!releaseMissing || !townNpcChunksLoaded) {
            return false;
        }
        return isConfirmedAbsent(presence);
    }

    /**
     * True when night leave may finalize a tourist whose entity uuid cannot be resolved — only when confirmed absent,
     * not when the entity is merely unloaded.
     */
    public static boolean shouldFinalizeTouristLeaveForMissingEntity(@Nonnull EntityPresence presence) {
        return isConfirmedAbsent(presence);
    }
}
