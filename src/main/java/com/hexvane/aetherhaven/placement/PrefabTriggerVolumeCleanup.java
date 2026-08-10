package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Clears trigger volumes that came in with a prefab. Pasting a prefab does not leave a trigger volume entity behind:
 * the paste hands the volume to the trigger volume manager and throws the entity away, so clearing a footprint by
 * removing entities leaves music and weather volumes standing forever.
 *
 * <p>Volumes are switched off first so anyone standing in one gets the normal exit treatment (their music and weather
 * go back to normal), then marked for removal, which the trigger volume system finishes on its next tick.
 */
public final class PrefabTriggerVolumeCleanup {
    private PrefabTriggerVolumeCleanup() {}

    /**
     * Removes every trigger volume centred inside {@code fp}.
     *
     * @return how many volumes were marked for removal
     */
    public static int removeVolumesInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp
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
            if (!footprintContains(fp, entry.getPosition())) {
                continue;
            }
            // Disable first so the next trigger-volume tick can run EXIT (music/weather) before destroy.
            entry.setEnabled(false);
            entry.markPendingDestroy();
            removed++;
        }
        if (removed > 0) {
            manager.markSpatialDirty();
        }
        return removed;
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
        TriggerVolumeManager manager = resolveManager(store);
        if (manager == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (VolumeEntry entry : new ArrayList<>(manager.getVolumes())) {
            if (footprintContains(fp, entry.getPosition())) {
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
