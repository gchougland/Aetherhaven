package com.hexvane.aetherhaven.guild.marker;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Resolves guild hall adventurer spawn positions from building JSON, with prefab marker fallback. */
public final class AdventurerSpawnMarkerLocator {
    private AdventurerSpawnMarkerLocator() {}

    /**
     * World spawn positions for guild hall adventurers. Prefers
     * {@link ConstructionDefinition#getAdventurerSpawnLocals()} when present; falls back to
     * {@link AdventurerSpawnMarkerEntity} markers in the plot footprint for older prefabs that never
     * wrote JSON locals.
     */
    @Nonnull
    public static List<Vector3d> resolveSpawnPositions(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull ConstructionDefinition hallDef
    ) {
        List<AdventurerSpawnSlot> slots = resolveSpawnSlots(store, hallPlot, hallDef);
        List<Vector3d> out = new ArrayList<>(slots.size());
        for (AdventurerSpawnSlot slot : slots) {
            out.add(new Vector3d(slot.position()));
        }
        return out;
    }

    @Nonnull
    public static List<AdventurerSpawnSlot> resolveSpawnSlots(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance hallPlot,
        @Nonnull ConstructionDefinition hallDef
    ) {
        Vector3i anchor = hallPlot.resolvePrefabAnchorWorld(hallDef);
        Rotation yaw = hallPlot.resolvePrefabYaw();
        List<AdventurerSpawnSlot> fromJson = slotsFromJsonLocals(hallDef, anchor, yaw);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }
        List<MarkerSortRow> markers = collectMarkersInFootprint(store, hallPlot.toFootprint(), anchor, yaw);
        if (markers.isEmpty()) {
            return List.of();
        }
        markers.sort(
            Comparator.comparingInt((MarkerSortRow r) -> r.localX)
                .thenComparingInt(r -> r.localZ)
                .thenComparingInt(r -> r.localY)
        );
        List<AdventurerSpawnSlot> out = new ArrayList<>(markers.size());
        for (MarkerSortRow row : markers) {
            out.add(new AdventurerSpawnSlot(new Vector3d(row.position), row.yawRadians));
        }
        return out;
    }

    @Nonnull
    private static List<AdventurerSpawnSlot> slotsFromJsonLocals(
        @Nonnull ConstructionDefinition hallDef,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        int[][] locals = hallDef.getAdventurerSpawnLocals();
        if (locals == null || locals.length == 0) {
            return List.of();
        }
        float[] yaws = hallDef.getAdventurerSpawnYaws();
        List<AdventurerSpawnSlot> out = new ArrayList<>(locals.length);
        for (int i = 0; i < locals.length; i++) {
            int[] local = locals[i];
            if (local == null || local.length != 3) {
                continue;
            }
            float prefabYaw = yaws != null && i < yaws.length ? yaws[i] : 0f;
            out.add(
                new AdventurerSpawnSlot(
                    GuildHallAdventurerSpawnPositions.fromPrefabLocalStandCell(
                        anchor,
                        yaw,
                        local[0],
                        local[1],
                        local[2]
                    ),
                    PrefabYaw.worldFromPrefabLocal(yaw, prefabYaw)
                )
            );
        }
        return out;
    }

    @Nonnull
    private static List<MarkerSortRow> collectMarkersInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        List<MarkerSortRow> rows = new ArrayList<>();
        store.forEachChunk(
            Query.and(AdventurerSpawnMarkerEntity.getComponentType(), TransformComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (!footprintContains(fp, p.x, p.y, p.z)) {
                        continue;
                    }
                    int dx = (int) Math.floor(p.x) - anchor.x;
                    int dy = (int) Math.floor(p.y) - anchor.y;
                    int dz = (int) Math.floor(p.z) - anchor.z;
                    Vector3i local = PrefabLocalOffset.inverseRotateWorldDelta(yaw, dx, dy, dz);
                    rows.add(new MarkerSortRow(local.x, local.y, local.z, new Vector3d(p), tc.getRotation().yaw()));
                }
            }
        );
        return rows;
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

    private record MarkerSortRow(int localX, int localY, int localZ, Vector3d position, float yawRadians) {}
}
