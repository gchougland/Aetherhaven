package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.ProductionStorageBlocks.*;

@Tag("town")
class ProductionStorageBlocksTest {
    private static final int STORAGE = 42;
    private static final Vector3i BASE = new Vector3i(32, 31, 32);
    private static final class WardrobeBounds extends BlockBoundingBoxes {
        WardrobeBounds() {
            baseDetailBoxes = new Box[]{new Box(-1, 0, 0, 1, 2, 1)};
            processConfig();
        }
    }
    private static final class Blocks implements Reader {
        final Map<String, BlockSection> sections = new HashMap<>();
        BlockSection section(int x, int y, int z) {
            return sections.computeIfAbsent(ChunkUtil.chunkCoordinate(x) + "/" + ChunkUtil.chunkCoordinate(y) + "/"
                + ChunkUtil.chunkCoordinate(z), unused -> new BlockSection());
        }
        public Voxel read(int x, int y, int z) {
            var section = section(x, y, z);
            return new Voxel(section.get(x, y, z), section.getRotationIndex(x, y, z), section.getFiller(x, y, z), false);
        }
        boolean set(Cell cell, int id, int rotation) {
            return section(cell.x(), cell.y(), cell.z()).set(cell.x(), cell.y(), cell.z(), id, rotation, cell.filler());
        }
    }

    @Test void originOnlyWardrobesRecoverEveryNativeFillerAcrossSectionsAtAllFourRotations() {
        var bounds = new WardrobeBounds();
        for (var yaw : Rotation.values()) {
            int rotation = RotationTuple.index(yaw, Rotation.None, Rotation.None);
            var blocks = new Blocks();
            var origin = new Cell(BASE.x, BASE.y, BASE.z, 0);
            assertTrue(blocks.set(origin, STORAGE, rotation));
            var original = blocks.read(BASE.x, BASE.y, BASE.z);
            var plan = plan(bounds.get(rotation), BASE, STORAGE, rotation, blocks);
            assertEquals(Result.NEEDS_REPAIR, plan.result());
            assertEquals(3, plan.missing().size());
            assertFalse(plan.missing().contains(origin), "The surviving origin and its data must not be cleared");
            assertEquals(Result.REPAIRED, apply(plan, cell -> blocks.set(cell, STORAGE, rotation)));
            assertEquals(original, blocks.read(BASE.x, BASE.y, BASE.z));
            var occupied = new AtomicInteger();
            FillerBlockUtil.forEachFillerBlock(bounds.get(rotation), (x, y, z) -> {
                var cell = blocks.read(BASE.x + x, BASE.y + y, BASE.z + z);
                assertEquals(STORAGE, cell.block());
                assertEquals(rotation, cell.rotation());
                assertEquals(BASE, new Vector3i(BASE.x + x - FillerBlockUtil.unpackX(cell.filler()),
                    BASE.y + y - FillerBlockUtil.unpackY(cell.filler()), BASE.z + z - FillerBlockUtil.unpackZ(cell.filler())));
                occupied.incrementAndGet();
            });
            assertEquals(4, occupied.get());
            var repeated = plan(bounds.get(rotation), BASE, STORAGE, rotation, blocks);
            assertEquals(Result.READY, apply(repeated, cell -> fail("An intact wardrobe must not be re-stamped")));
        }
    }

    @Test void anyObstructionPreventsAllWritesIncludingAnotherWardrobesOriginOrFiller() {
        var boxes = new WardrobeBounds().get(RotationTuple.NONE_INDEX);
        for (var obstacle : new Voxel[]{new Voxel(87, 0, 0, false), new Voxel(STORAGE, 0, 0, false),
                new Voxel(STORAGE, 0, FillerBlockUtil.pack(1, 1, 0), false), new Voxel(0, 0, 0, true),
                new Voxel(0, 0, 1, false)}) {
            var plan = plan(boxes, BASE, STORAGE, 0, (x, y, z) ->
                x == BASE.x && y == BASE.y + 1 ? obstacle : new Voxel(0, 0, 0, false));
            assertEquals(Result.BLOCKED, plan.result());
            assertEquals(Result.BLOCKED, apply(plan, cell -> fail("Repair must not partially write before an obstruction")));
        }
    }

    @Test void unavailableNeighborSectionDoesNotLoadOrPartiallyPlaceTheWardrobe() {
        var plan = plan(new WardrobeBounds().get(0), BASE, STORAGE, 0,
            (x, y, z) -> y > BASE.y ? null : new Voxel(0, 0, 0, false));
        assertEquals(Result.UNLOADED, apply(plan, cell -> fail("Missing sections must defer the whole repair")));
    }

    @Test void missingOriginAndPartlyIntactStorageAreCompletedWithoutRewritingExistingCells() {
        var boxes = new WardrobeBounds().get(0);
        var blocks = new Blocks();
        var empty = plan(boxes, BASE, STORAGE, 0, blocks);
        assertEquals(4, empty.missing().size());
        var surviving = empty.missing().getFirst();
        assertTrue(blocks.set(surviving, STORAGE, 0));
        var partial = plan(boxes, BASE, STORAGE, 0, blocks);
        assertEquals(3, partial.missing().size());
        assertFalse(partial.missing().contains(surviving));
        assertEquals(Result.REPAIRED, apply(partial, cell -> blocks.set(cell, STORAGE, 0)));
        assertEquals(Result.READY, plan(boxes, BASE, STORAGE, 0, blocks).result());
    }
}
