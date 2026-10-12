package com.hexvane.aetherhaven.testtown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.testsupport.GameItems;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The bundled layout file: it must load, cover every building and workplace type, and name only things that exist. */
@Tag("town")
class TestTownSpecTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static TestTownSpec bundled() throws IOException {
        TestTownSpec spec = TestTownSpec.loadFromClasspath(TestTownSpecTest.class.getClassLoader());
        assertNotNull(spec, "the bundled layout was not found on the classpath");
        return spec;
    }

    private static TestTownSpec parse(String json) {
        return TestTownSpec.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theBundledLayoutLoadsAndIsUsable() throws IOException {
        TestTownSpec spec = bundled();
        assertNull(spec.problem());
        assertEquals("aetherhaven_test_town", spec.world().name());
        // The ground ends where the flat generator's own spawn point expects it.
        assertEquals(80, spec.world().surfaceY());
        assertTrue(spec.town().treasuryGold() >= 10_000);
        // One unfinished building, so the Building yard has a project of its own.
        assertEquals(1, spec.buildingSites().size());
        assertTrue(spec.kit().creative());
    }

    @Test
    void everyWorkTypeThatTakesAWorkplaceHasExactlyOne() throws IOException {
        TestTownSpec spec = bundled();
        Set<String> wanted = new TreeSet<>(), listed = new TreeSet<>();
        for (TownsfolkWorkType type : TownsfolkWorkType.values())
            if (type.requiresWorkplace() && type.availableForNewWork()) wanted.add(type.id);
        for (TestTownSpec.WorkplaceSpec workplace : spec.workplaces()) {
            assertNotNull(TownsfolkWorkType.byId(workplace.type()), "unknown work type " + workplace.type());
            assertTrue(listed.add(workplace.type()), workplace.type() + " is listed twice");
            assertTrue(workplace.skill() >= 1 && workplace.skill() <= 10, workplace.type() + " skill");
        }
        // A work type added to the game must be added to the layout too, or the test town silently lacks it.
        assertEquals(wanted, listed);
        assertEquals(TestTownCheck.workplaceTypes(), listed.size());
    }

    @Test
    void workplacesHoldWhatTheirTypeNeeds() throws IOException {
        for (TestTownSpec.WorkplaceSpec workplace : bundled().workplaces()) {
            TownsfolkWorkType type = TownsfolkWorkType.byId(workplace.type());
            if (type == TownsfolkWorkType.MINING) {
                assertTrue(workplace.width() >= 5 && workplace.depth() >= 9, "a mineshaft needs room for its approach and tunnel");
                continue;
            }
            assertFalse(workplace.blocks().isEmpty(), workplace.type() + " has no bench or beds");
            boolean ordersScreen = WorkplaceBenchAccess.screen(type) == WorkplaceBenchAccess.Screen.ORDERS;
            assertEquals(ordersScreen, !workplace.orders().isEmpty(),
                workplace.type() + (ordersScreen ? " takes orders but has none" : " does not take orders"));
            // Benches stand clear of the edge so a bench wider than one block stays inside its area.
            var bench = workplace.blocks().getFirst();
            assertTrue(bench.x() >= 1 && bench.z() >= 1 && bench.x() < workplace.width() - 1 && bench.z() < workplace.depth() - 1,
                workplace.type() + " bench is on the edge of its area");
        }
    }

    @Test
    void thereIsAHomeForEveryoneWhoIsHired() throws IOException {
        TestTownSpec spec = bundled();
        int hired = spec.staff().couriers() + spec.staff().guards() + spec.staff().clerks();
        for (TestTownSpec.WorkplaceSpec workplace : spec.workplaces()) hired += Math.max(1, workplace.workers());
        int houses = spec.buildings().stream().filter(b -> "plot_house".equals(b.id())).mapToInt(TestTownSpec.BuildingSpec::copies).sum();
        assertTrue(houses >= hired, hired + " townsfolk are hired but there are only " + houses + " houses, one bed each");
        assertTrue(spec.staff().couriers() >= 2, "a couple of couriers, so deliveries run");
        assertTrue(spec.staff().guards() >= 2);
        assertEquals(1, spec.staff().clerks());
    }

    @Test
    void everyCoreBuildingOfTheModIsInTheLayout() throws IOException {
        Set<String> listed = new TreeSet<>();
        for (TestTownSpec.BuildingSpec building : bundled().buildings()) listed.add(building.id());
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(RESOURCES.resolve("Server/Aetherhaven/Buildings"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject building = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String id = building.get("id").getAsString();
                boolean core = building.has("styleId") && "core".equals(building.get("styleId").getAsString());
                boolean wall = building.has("wallSegment") && building.get("wallSegment").getAsBoolean();
                boolean decoration = building.has("decorationPlot") && building.get("decorationPlot").getAsBoolean();
                boolean variant = building.has("countsAsConstructionId");
                if (core && !wall && !decoration && !variant && !listed.contains(id)) missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "core buildings missing from the test town layout: " + missing);
        // The villager buildings that ship in the mod's bundled packs rather than its main folder.
        for (String id : List.of("plot_guild_hall", "plot_tourist_portal", "plot_clown_tent"))
            assertTrue(listed.contains(id), id + " is missing from the test town layout");
    }

    @Test
    void namedOrdersAreRecipesOfThatWorkplaceWithinTheWorkersSkill() throws IOException {
        for (TestTownSpec.WorkplaceSpec workplace : bundled().workplaces()) {
            for (TestTownSpec.OrderSpec order : workplace.orders()) {
                if ("auto".equalsIgnoreCase(order.recipe())) continue;
                Path file = RESOURCES.resolve("Server/Aetherhaven/WorkplaceRecipes/" + order.recipe() + ".json");
                assertTrue(Files.isRegularFile(file), "no bundled recipe " + order.recipe());
                JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                assertEquals(workplace.type(), recipe.get("workplace").getAsString(), order.recipe());
                assertTrue(recipe.get("skillLevel").getAsInt() <= workplace.skill(),
                    order.recipe() + " needs more skill than the " + workplace.type() + " worker is given");
                assertFalse(recipe.has("requiresKnowledge") && recipe.get("requiresKnowledge").getAsBoolean(),
                    order.recipe() + " needs a town crafting unlock the test town does not have");
            }
        }
    }

    @Test
    void everyItemAndBlockTheLayoutNamesExists() throws IOException {
        GameItems items = GameItems.loadOrNull();
        assumeTrue(items != null, "Installed Hytale assets needed to check item ids");
        TestTownSpec spec = bundled();
        Set<String> ids = new TreeSet<>();
        for (TestTownSpec.Layer layer : spec.world().layers()) ids.add(layer.block());
        for (TestTownSpec.StackSpec stack : spec.stock()) ids.add(stack.item());
        for (TestTownSpec.StackSpec stack : spec.kit().items()) ids.add(stack.item());
        for (TestTownSpec.WorkplaceSpec workplace : spec.workplaces()) {
            if (workplace.floor() != null) ids.add(workplace.floor());
            for (TestTownSpec.BlockSpec block : workplace.blocks()) {
                ids.add(block.block());
                if (block.below() != null) ids.add(block.below());
            }
        }
        ids.addAll(spec.production().farmOutputs());
        ids.addAll(spec.production().mineOutputs());
        ids.addAll(spec.production().saplings());
        ids.addAll(spec.production().gardenPlants());
        List<String> unknown = new ArrayList<>();
        for (String id : ids) if (!items.exists(id) && !inBundledPack(id)) unknown.add(id);
        assertTrue(unknown.isEmpty(), "the layout names items that do not exist: " + unknown);
        assertTrue(ids.size() > 60, "the layout's ids were not read: " + ids.size());
    }

    /** Items that ship in one of the mod's bundled packs, which the item index of the main folder does not read. */
    private static boolean inBundledPack(String id) throws IOException {
        Path packs = Path.of("subplugin-assets");
        if (!Files.isDirectory(packs)) return false;
        try (Stream<Path> files = Files.walk(packs)) {
            return files.anyMatch(p -> p.getFileName().toString().equals(id + ".json"));
        }
    }

    @Test
    void aLayoutThatCannotBeBuiltSaysWhy() {
        String world = "\"world\":{\"name\":\"good_name\",\"environment\":\"Env\",\"layers\":[{\"block\":\"Rock_Stone\",\"thickness\":40}]}";
        String rest = ",\"town\":{},\"grid\":{\"rowWidth\":100,\"street\":8,\"plaza\":10},\"buildings\":[{\"id\":\"plot_house\"}]";
        assertNull(parse("{" + world + rest + "}").problem());
        assertTrue(parse("{" + world.replace("good_name", "Bad Name") + rest + "}").problem().contains("world.name"));
        assertTrue(parse("{" + world.replace("40", "4") + rest + "}").problem().contains("ground"));
        assertTrue(parse("{" + world + rest.replace("\"street\":8", "\"street\":1") + "}").problem().contains("grid"));
        assertTrue(parse("{" + world + rest.replace("[{\"id\":\"plot_house\"}]", "[]") + "}").problem().contains("buildings"));
        String twice = ",\"workplaces\":[{\"type\":\"cooking\",\"width\":9,\"depth\":9},{\"type\":\"cooking\",\"width\":9,\"depth\":9}]";
        assertTrue(parse("{" + world + rest + twice + "}").problem().contains("twice"));
        String outside = ",\"workplaces\":[{\"type\":\"cooking\",\"width\":9,\"depth\":9,\"blocks\":[{\"block\":\"Bench_Cooking\",\"x\":9,\"z\":2}]}]";
        assertTrue(parse("{" + world + rest + outside + "}").problem().contains("outside"));
    }

    @Test
    void missingListsBecomeEmptyOnes() {
        TestTownSpec spec = parse("{\"world\":{\"name\":\"good_name\",\"environment\":\"Env\",\"layers\":[{\"block\":\"Rock_Stone\",\"thickness\":40}]},"
            + "\"town\":{},\"grid\":{\"rowWidth\":100,\"street\":8,\"plaza\":10},\"buildings\":[{\"id\":\"plot_house\"}]}");
        assertTrue(spec.workplaces().isEmpty() && spec.stock().isEmpty() && spec.kit().items().isEmpty());
        assertTrue(spec.buildingSites().isEmpty());
        assertTrue(spec.production().animals().isEmpty() && spec.town().completeQuests().isEmpty());
        assertEquals(1, spec.buildings().getFirst().copies());
        assertEquals(1, spec.town().coreMastery());
        assertEquals(39, spec.world().surfaceY());
    }
}
