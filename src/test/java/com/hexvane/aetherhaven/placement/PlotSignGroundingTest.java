package com.hexvane.aetherhaven.placement;

import com.google.gson.JsonParser;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class PlotSignGroundingTest {
    private record Column(int x, int z) {}
    private static final class SignBounds extends BlockBoundingBoxes {
        SignBounds() throws Exception {
            var json = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/Server/Item/Block/Hitboxes/Aetherhaven/Aetherhaven_Plot_Sign.json"))).getAsJsonObject();
            var boxes = json.getAsJsonArray("Boxes");
            baseDetailBoxes = new Box[boxes.size()];
            for (int i = 0; i < boxes.size(); i++) {
                var min = boxes.get(i).getAsJsonObject().getAsJsonObject("Min");
                var max = boxes.get(i).getAsJsonObject().getAsJsonObject("Max");
                baseDetailBoxes[i] = new Box(min.get("X").getAsDouble(), min.get("Y").getAsDouble(), min.get("Z").getAsDouble(),
                    max.get("X").getAsDouble(), max.get("Y").getAsDouble(), max.get("Z").getAsDouble());
            }
            processConfig();
        }
    }
    private static HashSet<Column> columns(BlockBoundingBoxes.RotatedVariantBoxes boxes, int x, int z) {
        var columns = new HashSet<Column>();
        FillerBlockUtil.forEachFillerBlock(boxes, (dx, dy, dz) -> columns.add(new Column(x + dx, z + dz)));
        return columns;
    }

    @Test void signClearsTheHigherHalfOfASlopeAtEveryRotationAndAcrossChunkEdges() throws Exception {
        var bounds = new SignBounds();
        for (var rotation : Rotation.values()) {
            var boxes = bounds.get(RotationTuple.index(rotation, Rotation.None, Rotation.None));
            for (int origin : new int[]{0, 31, 32, -32}) {
                var covered = columns(boxes, origin, origin);
                assertTrue(covered.size() > 1, "The actual sign extends beyond its anchor column");
                for (var raised : covered) {
                    if (raised.equals(new Column(origin, origin))) continue;
                    Map<Column, Integer> floor = new HashMap<>();
                    for (var column : covered) floor.put(column, column.equals(raised) ? 32 : 31);
                    int signY = PlotSignGrounding.resolveFootprintY(boxes, origin, origin,
                        (x, z) -> floor.get(new Column(x, z)) + 1);
                    assertEquals(33, signY);
                    assertFalse(FillerBlockUtil.testFillerBlocks(boxes,
                        (dx, dy, dz) -> 32 + dy > floor.get(new Column(origin + dx, origin + dz))), "Old single-column height intersects the slope");
                    assertTrue(FillerBlockUtil.testFillerBlocks(boxes,
                        (dx, dy, dz) -> signY + dy > floor.get(new Column(origin + dx, origin + dz))), "Raised sign must clear every native filler cell");
                }
            }
        }
    }

    @Test void flatGroundDoesNotRaiseTheSignAndEachCoveredColumnIsReadOnlyOnce() throws Exception {
        var boxes = new SignBounds().get(RotationTuple.NONE_INDEX);
        var visited = new HashSet<Column>();
        var covered = columns(boxes, 10, 20);
        int y = PlotSignGrounding.resolveFootprintY(boxes, 10, 20, (x, z) -> {
            assertTrue(covered.contains(new Column(x, z)), "Do not use terrain outside the sign footprint");
            assertTrue(visited.add(new Column(x, z)), "Do not repeat the downward scan for each vertical filler");
            return 40;
        });
        assertEquals(40, y);
        assertEquals(covered, visited);
    }

    @Test void theHighestCoveredSurfaceWinsRegardlessOfWhichSideIsHigher() throws Exception {
        var bounds = new SignBounds();
        for (var rotation : Rotation.values()) {
            var boxes = bounds.get(RotationTuple.index(rotation, Rotation.None, Rotation.None));
            var heights = new HashMap<Column, Integer>();
            int height = 75;
            for (var column : columns(boxes, 0, 0)) heights.put(column, height++);
            assertEquals(height - 1, PlotSignGrounding.resolveFootprintY(boxes, 0, 0,
                (x, z) -> heights.get(new Column(x, z))));
        }
    }
}
