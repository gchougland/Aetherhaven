package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Clears trigger volumes that came in with a prefab. Pasting a prefab does not leave a trigger volume entity behind:
 * the paste hands the volume to the trigger volume manager and throws the entity away, so clearing a footprint by
 * removing entities leaves music and weather volumes standing forever.
 *
 * <p>Anyone still inside gets the volume's EXIT effects immediately (festival weather and music go back to normal).
 * Waiting for the next trigger-volume tick is racy: pending-destroy volumes can be unregistered in the same tick
 * without EXIT ever running.
 */
public final class PrefabTriggerVolumeCleanup {
    private PrefabTriggerVolumeCleanup() {}

    /**
     * Removes every trigger volume centred inside {@code fp} using exact block bounds (no Y padding). Prefer this for
     * prop packaging so neighboring props' volumes are not swept.
     *
     * @return how many volumes were marked for removal
     */
    public static int removeVolumesInExactFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp
    ) {
        return removeVolumesInFootprint(store, fp, true);
    }

    /**
     * Removes every trigger volume centred inside {@code fp}.
     *
     * @return how many volumes were marked for removal
     */
    public static int removeVolumesInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp
    ) {
        return removeVolumesInFootprint(store, fp, false);
    }

    private static int removeVolumesInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp,
        boolean exactBounds
    ) {
        TriggerVolumeManager manager = resolveManager(store);
        if (manager == null) {
            return 0;
        }
        World world = store.getExternalData().getWorld();
        // Paste stores world names lower-cased; compare the same way or every volume is skipped.
        String worldName = world != null ? world.getName().toLowerCase(Locale.ROOT) : null;
        int removed = 0;
        for (VolumeEntry entry : new ArrayList<>(manager.getVolumes())) {
            if (entry.isPendingDestroy()) {
                continue;
            }
            if (!sameWorld(worldName, entry.getWorldName())) {
                continue;
            }
            if (exactBounds
                ? !exactFootprintContains(fp, entry.getPosition())
                : !footprintContains(fp, entry.getPosition())) {
                continue;
            }
            markVolumePendingDestroy(store, entry);
            removed++;
        }
        if (removed > 0) {
            manager.markSpatialDirty();
        }
        return removed;
    }

    /**
     * Marks specific trigger volumes for removal by id (prop-linked volumes). Missing / already-destroying ids are
     * ignored.
     *
     * @return how many volumes were marked for removal
     */
    public static int removeVolumesByIds(
        @Nonnull Store<EntityStore> store,
        @Nonnull Iterable<String> volumeIds
    ) {
        TriggerVolumeManager manager = resolveManager(store);
        if (manager == null) {
            return 0;
        }
        int removed = 0;
        for (String id : volumeIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            VolumeEntry entry = manager.getVolume(id.trim());
            if (entry == null || entry.isPendingDestroy()) {
                continue;
            }
            markVolumePendingDestroy(store, entry);
            removed++;
        }
        if (removed > 0) {
            manager.markSpatialDirty();
        }
        return removed;
    }

    private static void markVolumePendingDestroy(
        @Nonnull Store<EntityStore> store,
        @Nonnull VolumeEntry entry
    ) {
        fireExitEffectsNow(store, entry);
        entry.setEnabled(false);
        entry.markPendingDestroy();
    }

    /**
     * Runs EXIT effects (weather reset, music clear, and anything else on the volume) for whoever is still inside,
     * then forgets them so a later trigger-volume tick does not fire EXIT twice.
     */
    private static void fireExitEffectsNow(
        @Nonnull Store<EntityStore> store,
        @Nonnull VolumeEntry entry
    ) {
        List<TriggerEffect> exits = exitEffects(entry.getEffects());
        for (Map.Entry<UUID, Ref<EntityStore>> tracked : new ArrayList<>(entry.getTrackedEntities().entrySet())) {
            UUID uuid = tracked.getKey();
            Ref<EntityStore> ref = tracked.getValue();
            if (ref != null && ref.isValid() && !exits.isEmpty()) {
                TriggerContext context = new TriggerContext(ref, store, TriggerEventType.EXIT, entry);
                for (TriggerEffect effect : exits) {
                    effect.execute(context);
                }
            }
            if (uuid != null) {
                entry.clearEntityRuntimeState(uuid);
            }
        }
        entry.getTrackedEntities().clear();
    }

    /** Effects that would run if the player walked out of the volume. */
    @Nonnull
    static List<TriggerEffect> exitEffects(@Nullable List<TriggerEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }
        List<TriggerEffect> out = new ArrayList<>();
        for (TriggerEffect effect : effects) {
            if (effect != null && effect.getEventType() == TriggerEventType.EXIT) {
                out.add(effect);
            }
        }
        return out;
    }

    /**
     * Removes every trigger volume centred inside the box a prefab reserves at {@code anchor}. The solid footprint of a
     * build stops at its tallest block, but a prefab can carry a volume that floats above the roof, so the clear has to
     * use the whole reserved box.
     *
     * @return how many volumes were marked for removal
     */
    public static int removeVolumesInPrefabBox(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null) {
            return 0;
        }
        return removeVolumesInFootprint(entityStore.getStore(), prefabBox(anchor, yaw, buffer));
    }

    /** Exact-bounds variant for prop packaging (no padded Y sweep). */
    public static int removeVolumesInExactPrefabBox(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null) {
            return 0;
        }
        return removeVolumesInExactFootprint(entityStore.getStore(), prefabBox(anchor, yaw, buffer));
    }

    /** World space box a prefab covers, empty cells included. */
    @Nonnull
    public static PlotFootprintRecord prefabBox(
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull IPrefabBuffer buffer
    ) {
        PrefabRotation rotation = PrefabRotation.fromRotation(yaw);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] { buffer.getMinX(), buffer.getMaxX() }) {
            for (int y : new int[] { buffer.getMinY(), buffer.getMaxY() }) {
                for (int z : new int[] { buffer.getMinZ(), buffer.getMaxZ() }) {
                    Vector3i corner = new Vector3i(x, y, z);
                    rotation.rotate(corner);
                    minX = Math.min(minX, anchor.x + corner.x);
                    minY = Math.min(minY, anchor.y + corner.y);
                    minZ = Math.min(minZ, anchor.z + corner.z);
                    maxX = Math.max(maxX, anchor.x + corner.x);
                    maxY = Math.max(maxY, anchor.y + corner.y);
                    maxZ = Math.max(maxZ, anchor.z + corner.z);
                }
            }
        }
        return new PlotFootprintRecord(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Ids of the trigger volumes centred inside {@code fp}, for tests and diagnostics. */
    @Nonnull
    public static List<String> listVolumeIdsInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp
    ) {
        return listVolumeIdsInFootprint(store, fp, false);
    }

    /** Exact-bounds volume ids (for diagnostics). Prefer {@link #listVolumeIdsInWorld} when linking a paste. */
    @Nonnull
    public static List<String> listVolumeIdsInExactFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp
    ) {
        return listVolumeIdsInFootprint(store, fp, true);
    }

    /**
     * Every non-pending trigger volume id in this world's manager. Used to diff before/after a prop paste so volumes
     * whose centres sit outside the block AABB are still linked to the prop.
     */
    @Nonnull
    public static List<String> listVolumeIdsInWorld(@Nonnull Store<EntityStore> store) {
        TriggerVolumeManager manager = resolveManager(store);
        if (manager == null) {
            return List.of();
        }
        World world = store.getExternalData().getWorld();
        String worldName = world != null ? world.getName().toLowerCase(Locale.ROOT) : null;
        List<String> ids = new ArrayList<>();
        for (VolumeEntry entry : new ArrayList<>(manager.getVolumes())) {
            if (entry.isPendingDestroy()) {
                continue;
            }
            if (!sameWorld(worldName, entry.getWorldName())) {
                continue;
            }
            ids.add(entry.getId());
        }
        return ids;
    }

    @Nonnull
    private static List<String> listVolumeIdsInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp,
        boolean exactBounds
    ) {
        TriggerVolumeManager manager = resolveManager(store);
        if (manager == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (VolumeEntry entry : new ArrayList<>(manager.getVolumes())) {
            if (entry.isPendingDestroy()) {
                continue;
            }
            boolean inside = exactBounds
                ? exactFootprintContains(fp, entry.getPosition())
                : footprintContains(fp, entry.getPosition());
            if (inside) {
                ids.add(entry.getId());
            }
        }
        return ids;
    }

    @Nullable
    private static TriggerVolumeManager resolveManager(@Nonnull Store<EntityStore> store) {
        TriggerVolumesPlugin plugin;
        try {
            plugin = TriggerVolumesPlugin.get();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return plugin != null ? store.getResource(plugin.getManagerResourceType()) : null;
    }

    static boolean footprintContains(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d position) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY() - 1
            && by <= fp.getMaxY() + 2
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }

    /** Block-inclusive AABB with no padding (prop package / link capture). */
    static boolean exactFootprintContains(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d position) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY()
            && by <= fp.getMaxY()
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }

    /** Paste writes {@code world.getName().toLowerCase}; blank means "already scoped to this store". */
    static boolean sameWorld(@Nullable String worldName, @Nullable String entryWorldName) {
        if (worldName == null || worldName.isBlank()) {
            return true;
        }
        if (entryWorldName == null || entryWorldName.isBlank()) {
            return true;
        }
        return worldName.toLowerCase(Locale.ROOT).equals(entryWorldName.toLowerCase(Locale.ROOT));
    }
}
