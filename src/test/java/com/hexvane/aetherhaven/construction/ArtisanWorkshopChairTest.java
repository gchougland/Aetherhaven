package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The tipped chair on the Artisan Workshop counter must rest on the counter.
 *
 * <p>A block prop's position is the center of its one block cell. The chair is full size, so its center belongs
 * about half a block above the counter top. It was saved one whole block above after its anchor was converted twice.
 */
@Tag("town")
class ArtisanWorkshopChairTest {
    private static final Path PREFAB = Path.of("src/main/resources/Server/Prefabs/Artisan_Workshop.prefab.json");

    @Test
    void theChairRestsOnTheCounterBeneathIt() throws Exception {
        JsonObject prefab = JsonParser.parseString(Files.readString(PREFAB)).getAsJsonObject();
        JsonObject chair = null;
        for (var element : prefab.getAsJsonArray("entities")) {
            JsonObject components = element.getAsJsonObject().getAsJsonObject("Components");
            JsonObject block = components.getAsJsonObject("BlockEntity");
            if (block != null && "Furniture_Village_Chair".equals(block.get("BlockTypeKey").getAsString())) {
                assertEquals(null, chair, "one chair prop expected");
                chair = components;
            }
        }
        assertNotNull(chair, "the workshop keeps its chair prop");
        assertEquals(1.0, chair.getAsJsonObject("EntityScale").get("Scale").getAsDouble(), "full size chair");
        JsonObject position = chair.getAsJsonObject("Transform").getAsJsonObject("Position");
        double x = position.get("X").getAsDouble();
        double y = position.get("Y").getAsDouble();
        double z = position.get("Z").getAsDouble();

        int column = (int) Math.floor(x);
        int row = (int) Math.floor(z);
        int supportY = Integer.MIN_VALUE;
        String support = null;
        for (var element : prefab.getAsJsonArray("blocks")) {
            JsonObject block = element.getAsJsonObject();
            if (block.get("x").getAsInt() != column || block.get("z").getAsInt() != row) {
                continue;
            }
            String name = block.get("name").getAsString();
            int blockY = block.get("y").getAsInt();
            if (!"Empty".equals(name) && blockY < y && blockY > supportY) {
                supportY = blockY;
                support = name;
            }
        }
        assertEquals("Furniture_Village_Counter", support, "the chair lies on the counter");
        double height = y - (supportY + 1);
        assertTrue(
            height > 0.40 && height < 0.56,
            "chair center is " + height + " blocks above the counter top; a full size prop at rest sits about half a block above"
        );
    }
}
