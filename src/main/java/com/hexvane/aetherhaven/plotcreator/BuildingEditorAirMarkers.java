package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.prefab.EditorMarkerBlocks;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Random;
import org.joml.Vector3i;

/** Restores authored clear-space cells after a building editor paste. */
final class BuildingEditorAirMarkers {
    private BuildingEditorAirMarkers() {}

    static void restore(World world, IPrefabBuffer buffer, Vector3i origin, Rotation yaw) {
        var call = new PrefabBufferCall(new Random(ConstructionPasteOps.PREFAB_BUFFER_ITERATION_SEED),
            PrefabRotation.fromRotation(yaw));
        buffer.forEach(IPrefabBuffer.iterateAllColumns(),
            (x, y, z, blockId, holder, support, rotation, filler, context, fluidId, fluidLevel) -> {
                if (EditorMarkerBlocks.normalizePrefabBlockId(blockId) != 0 || filler != 0 || fluidId != 0) return;
                int bx = origin.x + x, by = origin.y + y, bz = origin.z + z;
                // Multi-block furniture and retained water may occupy an authored air cell.
                if (ChunkSectionBlockUtil.blockId(world, bx, by, bz) != 0
                    || ConstructionPasteOps.worldHasFluidAt(world, bx, by, bz)) return;
                ChunkSectionBlockUtil.setBlockByKey(world, bx, by, bz, "Editor_Empty",
                    ConstructionPasteOps.SET_BLOCK_SETTINGS_CLEAR);
            }, (x, z, entities, context) -> {}, null, call);
    }
}
