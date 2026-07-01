package com.hexvane.aetherhaven.poi.marker;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Collapses duplicate {@link PoiMarkerEntity} instances that share the same block cell inside a plot footprint. */
public final class PoiMarkerDedupUtil {
    private PoiMarkerDedupUtil() {}

    /**
     * Removes extra POI marker entities in {@code plot}'s footprint when more than one occupies the same block cell.
     * Prefers the marker with a non-null {@link PoiMarkerDataComponent#getPoiRegistryId()}, else lowest entity UUID.
     */
    public static void dedupeInPlot(@Nonnull Store<EntityStore> store, @Nonnull PlotInstance plot) {
        dedupeInPlot(store, null, plot);
    }

    public static void dedupeInPlot(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotInstance plot
    ) {
        PlotFootprintRecord fp = plot.toFootprint();
        Map<Long, List<MarkerAtCell>> byCell = new HashMap<>();
        store.forEachChunk(
            Query.and(PoiMarkerEntity.getComponentType(), TransformComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (!footprintContains(fp, p.x, p.y, p.z)) {
                        continue;
                    }
                    int bx = (int) Math.floor(p.x);
                    int by = (int) Math.floor(p.y);
                    int bz = (int) Math.floor(p.z);
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    PoiMarkerDataComponent data = chunk.getComponent(i, PoiMarkerDataComponent.getComponentType());
                    UUIDComponent uuidComp = chunk.getComponent(i, UUIDComponent.getComponentType());
                    UUID entityUuid = uuidComp != null ? uuidComp.getUuid() : null;
                    boolean hasRegistryId = data != null && data.getPoiRegistryId() != null;
                    long cellKey = cellKey(bx, by, bz);
                    byCell.computeIfAbsent(cellKey, k -> new ArrayList<>())
                        .add(new MarkerAtCell(ref, entityUuid, hasRegistryId));
                }
            }
        );

        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        for (List<MarkerAtCell> group : byCell.values()) {
            if (group.size() <= 1) {
                continue;
            }
            MarkerAtCell keeper = pickKeeper(group);
            for (MarkerAtCell marker : group) {
                if (marker.ref != keeper.ref && marker.ref.isValid()) {
                    toRemove.add(marker.ref);
                }
            }
        }
        for (Ref<EntityStore> ref : toRemove) {
            if (!ref.isValid()) {
                continue;
            }
            if (commandBuffer != null) {
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            } else {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }

    @Nonnull
    private static MarkerAtCell pickKeeper(@Nonnull List<MarkerAtCell> group) {
        MarkerAtCell best = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            MarkerAtCell candidate = group.get(i);
            if (candidate.hasRegistryId && !best.hasRegistryId) {
                best = candidate;
                continue;
            }
            if (candidate.hasRegistryId == best.hasRegistryId
                && candidate.entityUuid != null
                && best.entityUuid != null
                && candidate.entityUuid.compareTo(best.entityUuid) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static long cellKey(int bx, int by, int bz) {
        return ((long) bx & 0x1FFFFF) | (((long) by & 0xFFF) << 21) | (((long) bz & 0x1FFFFF) << 33);
    }

    private static boolean footprintContains(@Nonnull PlotFootprintRecord fp, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY()
            && by <= fp.getMaxY()
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }

    private record MarkerAtCell(@Nonnull Ref<EntityStore> ref, @Nullable UUID entityUuid, boolean hasRegistryId) {}
}
