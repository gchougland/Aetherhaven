package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.marker.EntityHeadMarker;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

/** Spawns model-attached selection markers that follow an entity as it moves. */
public final class RtsModelMarkerUtil {
    private static final float HEAD_OFFSET_Y = 2.75f;

    private RtsModelMarkerUtil() {}

    public static boolean clearAttachedMarker(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return EntityHeadMarker.clear(entityRef, audience, accessor);
    }

    /** Clears model-attached particles on an entity (used when retargeting or switching marker kinds). */
    public static boolean clearMarkerNode(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull String targetNodeName,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return clearAttachedMarker(entityRef, audience, accessor);
    }

    public static boolean spawnAttachedMarker(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull String particleSystemId,
        @Nonnull String targetNodeName,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return EntityHeadMarker.spawn(
            entityRef,
            particleSystemId,
            targetNodeName,
            HEAD_OFFSET_Y,
            audience,
            accessor
        );
    }
}
