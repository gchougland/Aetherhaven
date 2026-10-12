package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The desk's collision is one box per plank, with the open gaps a player can walk a hand through. Aiming at a gap
 * found nothing to use. A separate interaction shape covers the whole desk and leaves the collision alone.
 */
@Tag("town")
class LedgerDeskInteractionTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path HITBOXES = RES.resolve("Server/Item/Block/Hitboxes/Aetherhaven");

    private record Box(double[] min, double[] max) {
        boolean holds(double x, double y, double z) {
            double e = 1e-6;
            return x >= min[0] - e && x <= max[0] + e && y >= min[1] - e && y <= max[1] + e && z >= min[2] - e && z <= max[2] + e;
        }
    }

    private static double[] point(JsonObject corner) {
        return new double[] {corner.get("X").getAsDouble(), corner.get("Y").getAsDouble(), corner.get("Z").getAsDouble()};
    }

    private static List<Box> boxes(String id) throws Exception {
        var boxes = new ArrayList<Box>();
        for (var entry : JsonParser.parseString(Files.readString(HITBOXES.resolve(id + ".json"))).getAsJsonObject().getAsJsonArray("Boxes")) {
            var box = entry.getAsJsonObject();
            boxes.add(new Box(point(box.getAsJsonObject("Min")), point(box.getAsJsonObject("Max"))));
        }
        return boxes;
    }

    private static boolean inside(List<Box> boxes, double x, double y, double z) {
        return boxes.stream().anyMatch(box -> box.holds(x, y, z));
    }

    private static JsonObject desk() throws Exception {
        return JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Items/Aetherhaven/Aetherhaven_Warehouse_Ledger_Desk.json")))
            .getAsJsonObject().getAsJsonObject("BlockType");
    }

    @Test void theDeskUsesItsOwnInteractionShapeAndShowsAKeyPrompt() throws Exception {
        var block = desk();
        assertEquals("Aetherhaven_Warehouse_Ledger_Desk", block.get("HitboxType").getAsString(), "Collision is unchanged");
        String interaction = block.get("InteractionHitboxType").getAsString();
        assertTrue(Files.isRegularFile(HITBOXES.resolve(interaction + ".json")), "The interaction shape must exist under exactly that name");
        String hint = block.get("InteractionHint").getAsString();
        assertTrue(hint.startsWith("aetherhaven_warehouse."));
        String key = hint.substring("aetherhaven_warehouse.".length());
        String line = Files.readAllLines(RES.resolve("Server/Languages/en-US/aetherhaven_warehouse.lang")).stream()
            .filter(text -> text.startsWith(key + " =") || text.startsWith(key + "=")).findFirst().orElseThrow();
        assertTrue(line.contains("{key}"), "A custom hint must show the native key");
    }

    @Test void everyVisiblePartOfTheDeskCanBeAimedAt() throws Exception {
        var collision = boxes("Aetherhaven_Warehouse_Ledger_Desk");
        var interaction = boxes(desk().get("InteractionHitboxType").getAsString());
        for (var part : collision)
            for (int a = 0; a <= 4; a++) for (int b = 0; b <= 4; b++) for (int c = 0; c <= 4; c++) {
                double x = part.min[0] + (part.max[0] - part.min[0]) * a / 4, y = part.min[1] + (part.max[1] - part.min[1]) * b / 4,
                    z = part.min[2] + (part.max[2] - part.min[2]) * c / 4;
                assertTrue(inside(interaction, x, y, z), "A piece of the desk lies outside the interaction shape at " + x + ", " + y + ", " + z);
            }
    }

    @Test void theFrontOfTheDeskHasNoDeadGapsWhereCollisionAloneDid() throws Exception {
        var collision = boxes("Aetherhaven_Warehouse_Ledger_Desk");
        var interaction = boxes(desk().get("InteractionHitboxType").getAsString());
        double left = collision.stream().mapToDouble(box -> box.min[0]).min().orElseThrow(), right = collision.stream().mapToDouble(box -> box.max[0]).max().orElseThrow();
        int gaps = 0;
        // A ray through the middle of the desk, at every height up to the tabletop and every step across its width.
        for (int across = 1; across < 40; across++) for (int up = 1; up < 20; up++) {
            double x = left + (right - left) * across / 40, y = up / 20.0, z = 0.5;
            assertTrue(inside(interaction, x, y, z), "Dead gap in the interaction shape at " + x + ", " + y);
            if (!inside(collision, x, y, z)) gaps++;
        }
        assertTrue(gaps > 0, "The collision shape is expected to keep its open gaps; this is what made parts of the desk unresponsive");
    }

    /** Neighbouring cells of a wide block exist only where its collision shape reaches, so the interaction shape must not leave them. */
    @Test void theInteractionShapeStaysInsideTheCellsTheDeskOccupies() throws Exception {
        var collision = boxes("Aetherhaven_Warehouse_Ledger_Desk");
        for (var box : boxes(desk().get("InteractionHitboxType").getAsString()))
            for (int axis = 0; axis < 3; axis++) {
                final int a = axis;
                double low = Math.floor(collision.stream().mapToDouble(part -> part.min[a]).min().orElseThrow());
                double high = Math.ceil(collision.stream().mapToDouble(part -> part.max[a]).max().orElseThrow());
                assertTrue(box.min[axis] >= low && box.max[axis] <= high && box.min[axis] < box.max[axis]);
            }
    }
}
