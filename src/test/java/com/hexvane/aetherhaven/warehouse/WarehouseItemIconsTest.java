package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** An item whose icon file cannot be read shows as a white square in the inventory, recipes and reward lists. */
@Tag("town")
class WarehouseItemIconsTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static String problem(Path file) throws Exception {
        if (!Files.isRegularFile(file)) return "missing";
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 0; i < PNG.length; i++)
            if (bytes.length <= i || bytes[i] != PNG[i]) return "not a PNG (was it saved as text? line endings corrupt the header)";
        var image = ImageIO.read(file.toFile());
        if (image == null) return "cannot be decoded";
        return image.getWidth() == image.getHeight() ? null : "not square";
    }

    @Test void theSupplyLinkAndLedgerDeskShipReadableIconsAtTheExactPathTheirItemsName() throws Exception {
        for (String id : List.of("Aetherhaven_Warehouse_Supply_Link", "Aetherhaven_Warehouse_Ledger_Desk")) {
            var item = JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Items/Aetherhaven/" + id + ".json"))).getAsJsonObject();
            String icon = item.get("Icon").getAsString();
            assertEquals("Icons/ItemsGenerated/" + id + ".png", icon);
            Path file = RES.resolve("Common").resolve(icon);
            // Windows forgives a wrong case. The jar and other systems do not.
            assertEquals(file.getFileName().toString(), file.toRealPath().getFileName().toString());
            assertNull(problem(file), id + " icon");
        }
    }

    /** Every icon the main pack ships for its own items must be a real picture, not only the two above. */
    @Test void everyShippedItemIconIsAReadablePicture() throws Exception {
        List<String> broken = new ArrayList<>();
        try (var files = Files.walk(RES.resolve("Common/Icons/ItemsGenerated"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                String problem = problem(file);
                if (problem != null && !"not square".equals(problem)) broken.add(RES.relativize(file) + ": " + problem);
            }
        }
        assertTrue(broken.isEmpty(), "Unreadable item icons:\n" + String.join("\n", broken));
    }
}
