package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.joml.Vector3i;

/**
 * Festival prefabs omit empty air and rely on a fixed reserved box for swaps. Solid content must stay inside that box.
 */
@Tag("town")
final class FestivalPrefabSizeTest {
    private static final Path FESTIVAL_PREFABS = Path.of("src", "main", "resources", "Server", "Prefabs", "Festivals");

    @Test
    void everyShippedFestivalPrefabFitsInsideTheReservedBoxAndOmitsEmptyAir() throws IOException {
        var prefabs = listPrefabs();
        assertFalse(prefabs.isEmpty(), "no festival prefabs found under " + FESTIVAL_PREFABS);
        for (Path prefab : prefabs) {
            Bounds bounds = boundsOf(prefab);
            assertNull(
                FestivalPrefabSize.contentOutsideReservedReason(
                    bounds.minX,
                    bounds.minY,
                    bounds.minZ,
                    bounds.maxX,
                    bounds.maxY,
                    bounds.maxZ
                ),
                prefab.getFileName() + " content outside reserved box"
            );
            assertEquals(0, bounds.emptyCount, prefab.getFileName() + " should not store Empty air cells");
            assertEquals(0, bounds.emptyFluidCount, prefab.getFileName() + " should not store Empty fluid cells");
            assertTrue(bounds.solidCount > 0, prefab.getFileName() + " has no solid blocks");
        }
    }

    @Test
    void everyShippedFestivalPrefabAnchorsAtTheMiddleOfTheSquareOnTheGround() throws IOException {
        for (Path prefab : listPrefabs()) {
            JsonObject root = read(prefab);
            assertEquals(0, root.get("anchorX").getAsInt(), prefab.getFileName() + " anchorX");
            assertEquals(0, root.get("anchorY").getAsInt(), prefab.getFileName() + " anchorY");
            assertEquals(0, root.get("anchorZ").getAsInt(), prefab.getFileName() + " anchorZ");
        }
    }

    @Test
    void sizeMismatchIsReportedWithBothSizes() {
        assertNull(FestivalPrefabSize.mismatchReason(30, 55, 30));
        String reason = FestivalPrefabSize.mismatchReason(16, 20, 16);
        assertNotNull(reason);
        assertTrue(reason.contains("30 x 55 x 30"));
        assertTrue(reason.contains("16 x 20 x 16"));
    }

    @Test
    void contentOutsideReservedIsReported() {
        assertNull(FestivalPrefabSize.contentOutsideReservedReason(-14, 0, -14, 15, 54, 15));
        assertNull(FestivalPrefabSize.contentOutsideReservedReason(-10, 2, -8, 10, 20, 8));
        assertNotNull(FestivalPrefabSize.contentOutsideReservedReason(-15, 0, -14, 15, 54, 15));
        assertNotNull(FestivalPrefabSize.contentOutsideReservedReason(-14, 0, -14, 15, 55, 15));
    }

    @Test
    void reservedBoxCellCountMatchesWidthHeightDepth() {
        assertEquals(
            FestivalPrefabSize.WIDTH_X * FestivalPrefabSize.HEIGHT_Y * FestivalPrefabSize.DEPTH_Z,
            (FestivalPrefabSize.LOCAL_MAX_X - FestivalPrefabSize.LOCAL_MIN_X + 1)
                * (FestivalPrefabSize.LOCAL_MAX_Y - FestivalPrefabSize.LOCAL_MIN_Y + 1)
                * (FestivalPrefabSize.LOCAL_MAX_Z - FestivalPrefabSize.LOCAL_MIN_Z + 1)
        );
    }

    private static java.util.List<Path> listPrefabs() throws IOException {
        try (Stream<Path> walk = Files.list(FESTIVAL_PREFABS)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".prefab.json")).sorted().toList();
        }
    }

    private static Bounds boundsOf(Path prefab) throws IOException {
        JsonObject root = read(prefab);
        JsonArray blocks = root.getAsJsonArray("blocks");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int emptyCount = 0;
        int solidCount = 0;
        for (JsonElement element : blocks) {
            JsonObject row = element.getAsJsonObject();
            String name = row.has("name") && !row.get("name").isJsonNull() ? row.get("name").getAsString() : "";
            if ("Empty".equals(name)) {
                emptyCount++;
            } else {
                solidCount++;
            }
            int x = row.get("x").getAsInt();
            int y = row.get("y").getAsInt();
            int z = row.get("z").getAsInt();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        int emptyFluidCount = 0;
        JsonArray fluids = root.has("fluids") && root.get("fluids").isJsonArray() ? root.getAsJsonArray("fluids") : null;
        if (fluids != null) {
            for (JsonElement element : fluids) {
                JsonObject row = element.getAsJsonObject();
                String name = row.has("name") && !row.get("name").isJsonNull() ? row.get("name").getAsString() : "";
                if ("Empty".equals(name)) {
                    emptyFluidCount++;
                }
            }
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, emptyCount, emptyFluidCount, solidCount);
    }

    private static JsonObject read(Path prefab) throws IOException {
        try (Reader reader = Files.newBufferedReader(prefab, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private record Bounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        int emptyCount,
        int emptyFluidCount,
        int solidCount
    ) {}
}
