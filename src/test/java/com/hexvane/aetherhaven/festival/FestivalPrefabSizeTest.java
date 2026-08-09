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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.joml.Vector3i;

/**
 * Every festival prefab must reserve the same box, otherwise swapping one in would leave blocks of the previous
 * festival behind or spill outside the plot footprint.
 */
@Tag("town")
final class FestivalPrefabSizeTest {
    private static final Path FESTIVAL_PREFABS = Path.of("src", "main", "resources", "Server", "Prefabs", "Festivals");

    @Test
    void everyShippedFestivalPrefabUsesTheSharedSize() throws IOException {
        List<Path> prefabs = listPrefabs();
        assertFalse(prefabs.isEmpty(), "no festival prefabs found under " + FESTIVAL_PREFABS);
        for (Path prefab : prefabs) {
            Vector3i size = extentsOf(prefab);
            assertTrue(
                FestivalPrefabSize.matches(size.x, size.y, size.z),
                prefab.getFileName() + " is " + size.x + " x " + size.y + " x " + size.z
            );
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
    void fixedSizeMaxCornerIsDerivedFromTheMinCorner() {
        Vector3i max = FestivalPrefabSize.maxFromMin(new Vector3i(100, 60, -40));
        assertEquals(129, max.x);
        assertEquals(114, max.y);
        assertEquals(-11, max.z);
    }

    private static List<Path> listPrefabs() throws IOException {
        try (Stream<Path> walk = Files.list(FESTIVAL_PREFABS)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".prefab.json")).sorted().toList();
        }
    }

    private static Vector3i extentsOf(Path prefab) throws IOException {
        JsonArray blocks = read(prefab).getAsJsonArray("blocks");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : blocks) {
            rows.add(element.getAsJsonObject());
        }
        for (JsonObject row : rows) {
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
        return new Vector3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    private static JsonObject read(Path prefab) throws IOException {
        try (Reader reader = Files.newBufferedReader(prefab, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }
}
