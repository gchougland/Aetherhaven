package com.hexvane.aetherhaven.halloween;

import com.google.gson.*;
import com.hexvane.aetherhaven.rescue.HalloweenCoffinUseSystem;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("entity")
class HalloweenResidentTest {
    private static final Path RES = Path.of("src/main/resources");
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(RES.resolve(path))).getAsJsonObject();
    }
    @Test void rescueUsesRealOctoberBoundariesAndServerCalendarZone() {
        assertFalse(season("2026-09-30T23:59:59Z", ZoneOffset.UTC));
        assertTrue(season("2026-10-01T00:00:00Z", ZoneOffset.UTC));
        assertTrue(season("2026-10-31T23:59:59Z", ZoneOffset.UTC));
        assertFalse(season("2026-11-01T00:00:00Z", ZoneOffset.UTC));
        assertFalse(season("2026-10-01T03:00:00Z", ZoneId.of("America/Los_Angeles")));
        assertTrue(season("2026-11-01T03:00:00Z", ZoneId.of("America/Los_Angeles")));
    }
    private static boolean season(String instant, ZoneId zone) {
        return HalloweenResident.rescueSeason(Clock.fixed(Instant.parse(instant), zone));
    }
    @Test void onlyClosedNativeCoffinsCanStartAnEncounter() {
        for (String family : List.of("Ancient", "Human_Ruins", "Temple_Dark", "Village")) {
            String id = "Furniture_" + family + "_Coffin";
            assertTrue(HalloweenCoffinUseSystem.isClosedCoffin(id));
            assertFalse(HalloweenCoffinUseSystem.isClosedCoffin("*" + id + "_State_Definitions_Open"));
        }
        assertFalse(HalloweenCoffinUseSystem.isClosedCoffin("Furniture_Village_Chest"));
    }
    @Test void headToggleRemovesTheWholeSkullButKeepsItsAnimationAnchorsAndBody() throws Exception {
        var model = json("Common/Characters/Aetherhaven/Halloween/Headless_Skeleton.blockymodel");
        Map<String, JsonObject> bones = new HashMap<>();
        collect(model.getAsJsonArray("nodes"), bones, false);
        assertTrue(bones.keySet().containsAll(List.of("Head", "Jaw", "Jaw-Top", "Pelvis", "Chest", "Neck")));
        assertTrue(bones.values().stream().anyMatch(n -> n.getAsJsonObject("shape").get("type").getAsString().equals("box")));
        var headed = json("Server/Models/Villager/" + HalloweenResident.modelForHead(true) + ".json");
        var headless = json("Server/Models/Villager/" + HalloweenResident.modelForHead(false) + ".json");
        assertEquals(headed.get("Model"), headless.get("Model"));
        assertEquals(headed.get("Texture"), headless.get("Texture"));
        assertEquals(1, headed.getAsJsonArray("DefaultAttachments").size());
        assertTrue(headless.getAsJsonArray("DefaultAttachments").isEmpty());
        assertTrue(headless.getAsJsonObject("RandomAttachmentSets").isEmpty());
        assertEquals("Aetherhaven_Life_Actions_Skeleton", NpcFaceVisuals.itemAnimationsForModel(headed.get("Model").getAsString(), "Aetherhaven_Life_Actions"));
        assertFalse(NpcFaceVisuals.supportsFaceModel(headed.get("Model").getAsString()));
        assertFalse(NpcFaceVisuals.supportsFaceModel(headless.get("Model").getAsString()));
        assertTrue(NpcFaceVisuals.supportsFaceModel("NPC/Undead/Skeleton/Models/Model.blockymodel"));
    }
    private static void collect(JsonArray nodes, Map<String, JsonObject> out, boolean hidden) {
        for (var value : nodes) {
            var node = value.getAsJsonObject(); String name = node.get("name").getAsString();
            out.put(name, node);
            boolean skull = hidden || name.equals("Head");
            if (skull) {
                assertEquals("none", node.getAsJsonObject("shape").get("type").getAsString(), name);
                assertFalse(node.getAsJsonObject("shape").get("visible").getAsBoolean(), name);
            }
            if (node.has("children")) collect(node.getAsJsonArray("children"), out, skull);
        }
    }
    @Test void residentAndRescueHaveNoMemoryAndSeasonDoesNotGateResidentLife() throws Exception {
        for (String suffix : List.of("", "_Rescue")) {
            var role = json("Server/NPC/Roles/Aetherhaven/Aetherhaven_Halloween" + suffix + ".json");
            assertFalse(role.get("IsMemory").getAsBoolean());
            assertEquals(HalloweenResident.MODEL, role.get("Appearance").getAsString());
        }
        var villager = json("Server/Aetherhaven/Villagers/Aetherhaven_Halloween.json");
        assertEquals("GravelyMaleLower", villager.get("speechVoiceId").getAsString());
        var rescue = json("Server/Aetherhaven/Dialogue/aetherhaven_halloween_rescue.json");
        assertEquals("halloween_rescue_available", rescue.getAsJsonObject("nodes").getAsJsonObject("root")
            .getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("condition").get("type").getAsString());
        var resident = json("Server/Aetherhaven/Dialogue/aetherhaven_halloween.json");
        assertFalse(resident.toString().contains("halloween_rescue_season"));
        assertFalse(resident.toString().contains("aetherhaven_dialogue_clown"));
        assertTrue(Files.exists(RES.resolve("Common/UI/Custom/Pages/Memories/npcs/" + villager.get("portraitIcon").getAsString())));
    }
    @Test void rescueAcceptanceRequiresOctoberAndQuestPermission() {
        var owner=UUID.randomUUID();var town=new com.hexvane.aetherhaven.town.TownRecord();town.setOwner(owner,"Owner");
        var october=Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"),ZoneOffset.UTC);
        var november=Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),ZoneOffset.UTC);
        assertTrue(HalloweenResident.canAcceptRescue(town,owner,october));
        assertFalse(HalloweenResident.canAcceptRescue(town,owner,november));
        var member=UUID.randomUUID();town.putMember(member,com.hexvane.aetherhaven.town.TownMemberRole.BUILD);
        assertFalse(HalloweenResident.canAcceptRescue(town,member,october));
        assertFalse(HalloweenResident.canAcceptRescue(town,UUID.randomUUID(),october));
        town.putMember(member,com.hexvane.aetherhaven.town.TownMemberRole.QUEST);
        assertTrue(HalloweenResident.canAcceptRescue(town,member,october));
    }
}
