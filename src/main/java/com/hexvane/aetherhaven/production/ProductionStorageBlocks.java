package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Vector3i;

/** Repairs the exact native wardrobe footprint without clearing its origin or nearby furniture. */
public final class ProductionStorageBlocks {
    public enum Result { READY, REPAIRED, NEEDS_REPAIR, BLOCKED, UNLOADED, FAILED }
    record Cell(int x, int y, int z, int filler) {}
    record Voxel(int block, int rotation, int filler, boolean blockEntity) {}
    record Plan(Result result, List<Cell> missing) {}
    @FunctionalInterface interface Reader { Voxel read(int x, int y, int z); }
    @FunctionalInterface interface Writer { boolean write(Cell cell); }
    private ProductionStorageBlocks() {}

    /** Called on the owning world thread. Unavailable sections are never loaded for this repair. */
    public static Result repair(World world, Vector3i base, int rotation, boolean dryRun) {
        int id = BlockType.getAssetMap().getIndex(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        var type = id < 0 ? null : BlockType.getAssetMap().getAsset(id);
        var hitbox = type == null ? null : BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if (hitbox == null) return Result.FAILED;
        Plan plan = plan(hitbox.get(rotation), base, id, rotation, (x, y, z) -> {
            var section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
            if (section == null) return null;
            var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, x, y, z);
            int index = ChunkUtil.indexBlock(x, y, z);
            boolean occupied = components != null && (components.getBlockReference(index) != null || components.getBlockHolder(index) != null);
            return new Voxel(section.get(x, y, z), section.getRotationIndex(x, y, z), section.getFiller(x, y, z),
                occupied);
        });
        if (dryRun) return plan.result();
        // Every cell has been checked before any write. Native filler offsets also cross section boundaries.
        // Suppress automatic filler expansion because this plan writes each exact occupied cell once.
        int settings = SetBlockSettings.NO_UPDATE_STATE | SetBlockSettings.NO_SET_FILLER | SetBlockSettings.NO_BREAK_FILLER;
        return apply(plan, cell -> {
            if (ChunkSectionBlockUtil.setBlock(world, cell.x(), cell.y(), cell.z(), id, type, rotation, cell.filler(), settings)) return true;
            // Native unchanged writes return false. Accept only the complete expected state, never just its id.
            var section = ChunkSectionBlockUtil.blockSectionAt(world, cell.x(), cell.y(), cell.z());
            return section != null && section.get(cell.x(), cell.y(), cell.z()) == id
                && section.getRotationIndex(cell.x(), cell.y(), cell.z()) == rotation
                && section.getFiller(cell.x(), cell.y(), cell.z()) == cell.filler();
        });
    }

    static Plan plan(BlockBoundingBoxes.RotatedVariantBoxes boxes, Vector3i base, int id, int rotation, Reader reader) {
        var cells = new ArrayList<Cell>();
        FillerBlockUtil.forEachFillerBlock(boxes, (x, y, z) ->
            cells.add(new Cell(base.x + x, base.y + y, base.z + z, FillerBlockUtil.pack(x, y, z))));
        var missing = new ArrayList<Cell>();
        for (Cell cell : cells) {
            Voxel current = reader.read(cell.x(), cell.y(), cell.z());
            if (current == null) return new Plan(Result.UNLOADED, List.of());
            if (current.block() == id && current.rotation() == rotation && current.filler() == cell.filler()) continue;
            // A different wardrobe origin/filler is another object's cell, even when its item id is the same.
            if (current.block() != BlockType.EMPTY_ID || current.filler() != 0 || current.blockEntity())
                return new Plan(Result.BLOCKED, List.of());
            missing.add(cell);
        }
        missing.sort(Comparator.comparingInt(cell -> cell.filler() == 0 ? 0 : 1));
        return new Plan(missing.isEmpty() ? Result.READY : Result.NEEDS_REPAIR, List.copyOf(missing));
    }

    static Result apply(Plan plan, Writer writer) {
        if (plan.result() != Result.NEEDS_REPAIR) return plan.result();
        for (Cell cell : plan.missing()) if (!writer.write(cell)) return Result.FAILED;
        return Result.REPAIRED;
    }
}
