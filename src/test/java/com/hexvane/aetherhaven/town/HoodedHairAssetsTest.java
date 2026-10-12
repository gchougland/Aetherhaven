package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A model asset has no way to hide hair under headgear. The game does it only for player skins: the character
 * creator marks some head accessories {@code FullyCovering} and draws no haircut under them. A resident model is a
 * fixed attachment list, so it must already be the list that rule would give. Hexvane's wizard hood is fully
 * covering, and his haircut stood through the top of it.
 */
@Tag("town")
class HoodedHairAssetsTest {
    private static final Path MODELS = Path.of("src/main/resources/Server/Models");
    /**
     * The models of every {@code "HeadAccessoryType": "FullyCovering"} entry in the installed game's
     * {@code Cosmetics/CharacterCreator/HeadAccessory.json} (Hoodie, LeatherCap, Hoodie_Feran, Hoodie_Ornated,
     * Hood_Blazen_Wizard, Battleworn_Helm). The registry is not part of this pack, so the list is kept here.
     */
    private static final Set<String> FULLY_COVERING = Set.of(
        "Cosmetics/Head/Hoodie.blockymodel",
        "Cosmetics/Head/LeatherCap.blockymodel",
        "Cosmetics/Head/Hoodie_Feran.blockymodel",
        "Cosmetics/Head/Hoodie_Ornated.blockymodel",
        "Cosmetics/Head/Hoodie_LongEnd.blockymodel",
        "Cosmetics/Head/Battleworn_Helm.blockymodel");
    private static final String HAIRCUTS = "Characters/Haircuts/";

    @Test void noResidentWearsAHaircutUnderFullyCoveringHeadgear() throws Exception {
        List<String> clipping = new ArrayList<>();
        int covered = 0;
        try (Stream<Path> files = Files.walk(MODELS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                JsonElement root = JsonParser.parseString(Files.readString(file));
                if (!root.isJsonObject() || !root.getAsJsonObject().has("DefaultAttachments")) continue;
                String headgear = null;
                List<String> haircuts = new ArrayList<>();
                for (JsonElement element : root.getAsJsonObject().getAsJsonArray("DefaultAttachments")) {
                    JsonObject attachment = element.getAsJsonObject();
                    if (!attachment.has("Model") || attachment.get("Model").isJsonNull()) continue;
                    String model = attachment.get("Model").getAsString();
                    if (FULLY_COVERING.contains(model)) headgear = model;
                    if (model.startsWith(HAIRCUTS)) haircuts.add(model);
                }
                if (headgear == null) continue;
                covered++;
                if (!haircuts.isEmpty()) clipping.add(file + " wears " + haircuts + " under " + headgear);
            }
        }
        // Hexvane's hood and the Alchemist's leather cap at the least.
        assertTrue(covered >= 2, "expected the hooded residents to be audited, saw " + covered);
        assertEquals(List.of(), clipping);
    }

    @Test void hexvaneKeepsHisHoodBeardAndBrows() throws Exception {
        List<String> models = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(Files.readString(MODELS.resolve("Townsfolk/Hexvane.json")))
            .getAsJsonObject().getAsJsonArray("DefaultAttachments")) {
            models.add(element.getAsJsonObject().get("Model").getAsString());
        }
        assertTrue(models.contains("Cosmetics/Head/Hoodie_LongEnd.blockymodel"));
        // Only the haircut goes. The face stays his.
        assertTrue(models.contains("Characters/Body_Attachments/Beards/Full.blockymodel"));
        assertTrue(models.contains("Characters/Body_Attachments/Eyebrows/Eyebrows_Medium.blockymodel"));
        assertEquals(13, models.size());
    }
}
