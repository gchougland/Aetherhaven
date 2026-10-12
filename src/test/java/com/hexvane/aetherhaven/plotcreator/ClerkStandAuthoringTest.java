package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.shop.ShopClerkStand;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P15: a shop building can mark where its clerk stands, and a placed copy of that building yields the stand. */
@Tag("crossmod")
class ClerkStandAuthoringTest {
    @TempDir Path dir;
    private final Gson gson = new Gson();

    private PlotCreatorDraft shop() {
        var d = new PlotCreatorDraft();
        d.setConstructionId("market_stall");
        d.setDisplayName("Market Stall");
        d.setPrefabPath("Market_Stall.prefab.json");
        d.setPrefabFileName("Market_Stall.prefab.json");
        d.setRotationYaw("None");
        d.setPlotAnchor(new Vector3i(0, 100, 0));
        d.setCornerFirst(new Vector3i(0, 99, 0));
        d.setCornerSecond(new Vector3i(15, 110, 15));
        d.setPlotAnchorOffset(new int[] {0, 1, 0});
        d.setSelfBuildGameDays(1);
        d.setKinds(List.of(PlotBuildingKind.PLAYER_SHOP));
        return d;
    }

    private PlotInstance placed(String construction, int x, int y, int z, Rotation yaw) {
        return gson.fromJson("{\"plotId\":\"" + UUID.randomUUID() + "\",\"constructionId\":\"" + construction
            + "\",\"state\":\"COMPLETE\",\"prefabAnchorX\":" + x + ",\"prefabAnchorY\":" + y + ",\"prefabAnchorZ\":" + z
            + ",\"prefabYaw\":\"" + yaw.name() + "\"}", PlotInstance.class);
    }

    @Test
    void theMarkerIsOfferedOnlyToShopsThatTownsfolkCanStaff() {
        var d = shop();
        assertTrue(PlotCreatorClerkStand.offered(d, null));
        d.setKinds(List.of(PlotBuildingKind.SHOP));
        assertFalse(PlotCreatorClerkStand.offered(d, null), "A merchant's own shop has no townsfolk clerk");
        d.setCountsAsConstructionIds(List.of("plot_player_shop"));
        assertTrue(PlotCreatorClerkStand.offered(d, null), "A variant of the player shop is one too");
        d.setCountsAsConstructionIds(List.of());
        d.setKinds(List.of(PlotBuildingKind.HOME));
        assertFalse(PlotCreatorClerkStand.offered(d, null));
        d.setKinds(List.of(PlotBuildingKind.DECORATION));
        assertFalse(PlotCreatorClerkStand.offered(d, null));
    }

    @Test
    void aShopHasOneClerkStandAndItReadsAsThatMarker() {
        var d = shop();
        var requirement = new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.CLERK_STAND, 1);
        assertEquals(0, PlotCreatorValidator.countForRequirement(d, requirement));

        assertFalse(PlotCreatorClerkStand.place(d, new int[] {7, 1, 10}));
        assertTrue(PlotCreatorClerkStand.place(d, new int[] {8, 1, 11}), "Marking again moves the stand");
        assertEquals(1, d.getPois().size());
        var stand = d.getPois().getFirst();
        assertEquals(List.of(ShopClerkStand.TAG), stand.getTags());
        assertArrayEquals(new int[] {8, 1, 11}, new int[] {stand.getLocalX(), stand.getLocalY(), stand.getLocalZ()});

        assertEquals(PlotCreatorSubstepType.CLERK_STAND, PlotCreatorSpotMarkerCollector.resolvePoiTypePublic(stand));
        assertEquals(1, PlotCreatorValidator.countForRequirement(d, requirement));
        // It is not a work, browsing or visiting spot, so nobody else is sent to it.
        for (var other : new PlotCreatorSubstepType[] {PlotCreatorSubstepType.WORK_POI, PlotCreatorSubstepType.SHOP_POI,
                PlotCreatorSubstepType.TOURIST_VISIT_POI, PlotCreatorSubstepType.BARN_PEN, PlotCreatorSubstepType.FUN_POI})
            assertFalse(PlotCreatorValidator.matchesPoiRequirement(stand,
                new PlotBuildingKindRequirements.SubstepRequirement(other, 1)), other.name());
    }

    @Test
    void theMarkerLooksAndReadsLikeTheOtherPersonStands() throws Exception {
        assertEquals(PlotCreatorSpotMarkerVisuals.TEX_PURPLE, PlotCreatorSpotMarkerVisuals.textureFor(PlotCreatorSubstepType.CLERK_STAND));
        assertEquals(PlotCreatorSpotMarkerVisuals.textureFor(PlotCreatorSubstepType.INNKEEPER_SPAWN),
            PlotCreatorSpotMarkerVisuals.textureFor(PlotCreatorSubstepType.CLERK_STAND));
        assertEquals("Clerk stand", PlotCreatorSpotMarkerVisuals.nameplateText(PlotCreatorSubstepType.CLERK_STAND, null));
        assertTrue(Files.exists(Path.of("src/main/resources/Common", PlotCreatorSpotMarkerVisuals.TEX_PURPLE)));

        String lang = Files.readString(
            Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_plot_creator.lang"), StandardCharsets.UTF_8);
        for (String key : List.of("spot.CLERK_STAND", "substep.CLERK_STAND", "hint.clerkStandMarked",
                "hint.clerkStandMoved", "error.clerkStandBlocked")) {
            String line = lang.lines().filter(l -> l.startsWith("aetherhaven.plotcreator." + key + "=")).findFirst().orElse(null);
            assertNotNull(line, key);
            String text = line.substring(line.indexOf('=') + 1);
            assertFalse(text.isBlank(), key);
            assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), key);
        }
    }

    @Test
    void aBuildingSavedWithTheMarkerYieldsThatStandWhereverItIsPlaced() throws Exception {
        var d = shop();
        PlotCreatorClerkStand.place(d, new int[] {7, 1, 10});
        Path out = dir.resolve("market_stall.json");
        BuildingEditorJsonWriter.writeMerged(out, d, new LinkedHashMap<>());

        var saved = JsonParser.parseString(Files.readString(out)).getAsJsonObject().getAsJsonArray("pois");
        assertEquals(1, saved.size());
        assertEquals(ShopClerkStand.TAG, saved.get(0).getAsJsonObject().getAsJsonArray("tags").get(0).getAsString());

        var definition = gson.fromJson(Files.readString(out), ConstructionDefinition.class);
        assertNotNull(ShopClerkStand.marker(definition));

        // Placed as authored.
        assertEquals(new Vector3i(107, 65, 210),
            ShopClerkStand.cell(definition, placed("market_stall", 100, 64, 200, Rotation.None)));
        // Turned with the building, exactly as its blocks are.
        for (Rotation yaw : new Rotation[] {Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy}) {
            var turned = ShopClerkStand.cell(definition, placed("market_stall", 100, 64, 200, yaw));
            assertEquals(new Vector3i(100, 64, 200).add(PrefabLocalOffset.rotate(yaw, 7, 1, 10)), turned, yaw.name());
            assertEquals(65, turned.y);
            assertNotEquals(new Vector3i(107, 65, 210), turned, yaw.name());
        }
        // A plot that is not built yet has no stand, and the clerk keeps today's behaviour.
        var unbuilt = gson.fromJson("{\"plotId\":\"" + UUID.randomUUID()
            + "\",\"constructionId\":\"market_stall\",\"state\":\"BLUEPRINTING\"}", PlotInstance.class);
        assertNull(ShopClerkStand.cell(definition, unbuilt));
    }

    @Test
    void aShopWithoutTheMarkerHasNoStand() {
        var plain = gson.fromJson("{\"id\":\"plot_player_shop\",\"pois\":[{\"localX\":7,\"localY\":1,\"localZ\":-9,"
            + "\"tags\":[\"WORK\"]},{\"localX\":5,\"localY\":1,\"localZ\":-5,\"tags\":[\"SHOP\"]}]}", ConstructionDefinition.class);
        assertNull(ShopClerkStand.marker(plain));
        assertNull(ShopClerkStand.cell(plain, placed("plot_player_shop", 0, 64, 0, Rotation.None)));
        assertNull(ShopClerkStand.marker(null));
        assertNull(ShopClerkStand.cell(null, placed("plot_player_shop", 0, 64, 0, Rotation.None)));
    }
}
