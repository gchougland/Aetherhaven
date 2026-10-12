package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Building Staff's use key must reach the block or entity in view before the staff's own action.
 *
 * <p>An item that declares {@code Use} replaces the default every other item inherits from
 * {@code Server/Item/Unarmed/Interactions/Empty.json} (use the block, else use the entity). The staff therefore has to
 * spell that chain out and put its own action last.
 */
class BuildingStaffUseOrderTest {
    private static final Path ITEMS = Path.of("src/main/resources/Server/Item/Items/Aetherhaven");
    private static final Path USE_ROOT =
        Path.of("src/main/resources/Server/Item/RootInteractions/Aetherhaven/Aetherhaven_BuildingStaff_Use_Root.json");
    private static final String USE_ROOT_ID = "Aetherhaven_BuildingStaff_Use_Root";
    private static final String STAFF_ACTION = "AetherhavenWorkplaceTool";

    @Test
    void everyStaffTierUsesTheSharedUseRoot() throws IOException {
        List<Path> staffs;
        try (Stream<Path> files = Files.list(ITEMS)) {
            staffs = files.filter(p -> p.getFileName().toString().startsWith("Aetherhaven_Building_Staff")).toList();
        }
        assertEquals(5, staffs.size(), "copper, iron, thorium, cobalt and adamantite staffs");
        for (Path staff : staffs) {
            JsonObject interactions = JsonParser.parseString(Files.readString(staff))
                .getAsJsonObject().getAsJsonObject("Interactions");
            JsonElement use = interactions.get("Use");
            assertTrue(use != null && use.isJsonPrimitive(), staff + " must name the shared use root");
            assertEquals(USE_ROOT_ID, use.getAsString(), staff.toString());
        }
        // The workplace tool inherits the staff, so it must not bring back its own use action.
        JsonObject workplaceTool = JsonParser.parseString(Files.readString(ITEMS.resolve("Aetherhaven_Workplace_Tool.json")))
            .getAsJsonObject();
        assertEquals("Aetherhaven_Building_Staff", workplaceTool.get("Parent").getAsString());
        JsonElement own = workplaceTool.get("Interactions");
        assertFalse(own != null && own.isJsonObject() && own.getAsJsonObject().has("Use"));
    }

    @Test
    void useTriesTheBlockThenTheEntityAndOnlyThenTheStaff() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(USE_ROOT)).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("Interactions").size());
        JsonObject first = root.getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("Simple", first.get("Type").getAsString());
        JsonObject useBlock = first.getAsJsonObject("Next");
        assertEquals("UseBlock", useBlock.get("Type").getAsString());
        JsonObject useEntity = useBlock.getAsJsonObject("Failed");
        assertEquals("UseEntity", useEntity.get("Type").getAsString());
        JsonObject staff = useEntity.getAsJsonObject("Failed");
        assertEquals(STAFF_ACTION, staff.get("Type").getAsString());
        // Nothing runs the staff action when the block or entity was used.
        assertFalse(useBlock.has("Next"));
        assertFalse(useEntity.has("Next"));
    }
}
