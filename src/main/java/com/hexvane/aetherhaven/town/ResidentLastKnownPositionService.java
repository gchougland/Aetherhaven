package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Persists and resolves last known positions for town residents. */
public final class ResidentLastKnownPositionService {
    private static final double MIN_MOVE_BLOCKS = 1.0;
    private static final double MIN_MOVE_BLOCKS_SQ = MIN_MOVE_BLOCKS * MIN_MOVE_BLOCKS;
    private static final long MIN_SAVE_INTERVAL_MS = 30_000L;

    private ResidentLastKnownPositionService() {}

    public static final class LocateTarget {
        @Nonnull
        private final Vector3d position;
        private final boolean lastKnown;
        private final boolean valid;

        public LocateTarget(@Nonnull Vector3d position, boolean lastKnown, boolean valid) {
            this.position = position;
            this.lastKnown = lastKnown;
            this.valid = valid;
        }

        @Nonnull
        public Vector3d getPosition() {
            return position;
        }

        public boolean isLastKnown() {
            return lastKnown;
        }

        public boolean isValid() {
            return valid;
        }

        @Nonnull
        public static LocateTarget invalid() {
            return new LocateTarget(new Vector3d(), false, false);
        }
    }

    public static void recordPosition(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid,
        double x,
        double y,
        double z,
        long nowEpochMs
    ) {
        ResidentLastKnownPosition existing = town.findLastKnownPosition(entityUuid);
        if (existing != null) {
            double dx = existing.getX() - x;
            double dy = existing.getY() - y;
            double dz = existing.getZ() - z;
            double distSq = dx * dx + dy * dy + dz * dz;
            long ageMs = nowEpochMs - existing.getUpdatedAtEpochMs();
            if (distSq < MIN_MOVE_BLOCKS_SQ && ageMs < MIN_SAVE_INTERVAL_MS) {
                return;
            }
            existing.setPosition(x, y, z, nowEpochMs);
            tm.updateTown(town);
            return;
        }
        town.getResidentLastKnownPositions().add(new ResidentLastKnownPosition(entityUuid, x, y, z, nowEpochMs));
        tm.updateTown(town);
    }

    @Nonnull
    public static LocateTarget resolveLocateTarget(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid
    ) {
        EntityPresenceUtil.EntityPresence presence = EntityPresenceUtil.resolve(store, entityUuid);
        if (EntityPresenceUtil.isLoadedLive(presence)) {
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(entityUuid);
            if (npcRef != null && npcRef.isValid()) {
                TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
                if (tc != null) {
                    Vector3d pos = tc.getPosition();
                    return new LocateTarget(new Vector3d(pos.x, pos.y, pos.z), false, true);
                }
            }
        }
        ResidentLastKnownPosition saved = town.findLastKnownPosition(entityUuid);
        if (saved != null) {
            return new LocateTarget(new Vector3d(saved.getX(), saved.getY(), saved.getZ()), true, true);
        }
        if (EntityPresenceUtil.isConfirmedAbsent(presence)) {
            return LocateTarget.invalid();
        }
        return LocateTarget.invalid();
    }

    public static void removePosition(@Nonnull TownRecord town, @Nonnull TownManager tm, @Nonnull UUID entityUuid) {
        Iterator<ResidentLastKnownPosition> it = town.getResidentLastKnownPositions().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            if (entityUuid.equals(it.next().getEntityUuid())) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            tm.updateTown(town);
        }
    }

    @Nullable
    public static ResidentLastKnownPosition find(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        return town.findLastKnownPosition(entityUuid);
    }
}
