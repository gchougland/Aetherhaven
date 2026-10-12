package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.MineshaftSelection.Direction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code tests/hytale/mineshaft.test.json} equal to what the Mineshaft Planner sends, so the pictures drawn
 * from that file show the page a player gets.
 *
 * <p>The map is the page's own {@link MineshaftPlannerPage#drawMap} output, command for command. The lines of text
 * around it are computed with the same functions the page calls. Item names are sent as plain text here, because the
 * picture renderer does not resolve translations; the page sends the game's own translation for each.
 *
 * <p>After an intended change run the tests once with the environment variable AETHERHAVEN_WRITE_UI_CASES set, run the
 * render cases, and look at the new pictures before keeping them.
 */
@Tag("town")
class MineshaftPlannerRenderCasesTest {
    private static final Path CASES = Path.of("tests/hytale/mineshaft.test.json");
    private static final String PAGE = "Aetherhaven/src/main/resources/Common/UI/Custom/Aetherhaven/MineshaftPlannerPage.ui";
    private static final Map<String, String> NAMES = Map.of(
        ExcavationFittings.BEAM, "Hardwood - Beam",
        ExcavationFittings.TORCH, "Crude Torch",
        ExcavationFittings.LANTERN, "Lantern",
        ExcavationFittings.RAIL, "Rail"
    );
    private static final Map<String, Long> STOCK = Map.of(
        ExcavationFittings.BEAM, 6L,
        ExcavationFittings.TORCH, 20L,
        ExcavationFittings.LANTERN, 0L,
        ExcavationFittings.RAIL, 4L
    );

    private record Scene(String name, String golden, MineFixture mine, int centerX, int centerZ, int floor, Point start,
                         Direction direction, int length, String tunnel, String activity) {}

    private static void set(JsonArray out, String selector, Object value) {
        var command = new JsonObject();
        command.addProperty("type", "Set");
        command.addProperty("selector", selector);
        if (value instanceof Boolean flag) {
            command.addProperty("data", flag);
        } else {
            command.addProperty("data", String.valueOf(value));
        }
        out.add(command);
    }

    /** The commands of one scene, in the order the page's build sends them. */
    private static JsonArray commands(Scene scene) {
        var mine = scene.mine();
        var sites = mine.sites;
        var layout = ExcavationPlan.Layout.STRAIGHT;
        var start = scene.start();
        var out = new JsonArray();
        set(out, "#Name.Text", mine.base.name());
        set(out, "#Floor.Text", "Height " + scene.floor());
        set(out, "#StartPoint.Text", "Start: " + start.x() + ", " + start.y() + ", " + start.z() + " | Dig " + scene.direction().name().toLowerCase(Locale.ROOT));
        set(out, "#Length.Text", scene.length() + " blocks long");
        set(out, "#Shape.Text", layout.label + ": " + layout.description);
        var area = MineshaftSelection.area(start.x(), start.y() - 1, start.z(), scene.direction(), scene.length(), layout);
        var entrance = MineshaftSelection.entrance(scene.direction());
        var needs = ExcavationFittings.needs(area, entrance, layout, ExcavationFittings.Mode.ALL_RAILS, MineshaftNetwork.openSpace(sites, mine.base, null));
        String[] controls = {"Beams", "Torches", "Lanterns", "Rails"};
        boolean[] chosen = {true, true, false, false};
        for (int i = 0; i < controls.length; i++) {
            String item = ExcavationFittings.ITEMS.get(i);
            set(out, "#" + controls[i] + "Icon.ItemId", item);
            set(out, "#" + controls[i] + "Name.Text", NAMES.get(item));
            set(out, "#" + controls[i] + "Count.Text", ExcavationFittings.countLine(chosen[i], needs.get(item), STOCK.get(item)));
            set(out, "#" + controls[i] + ".Text", chosen[i] ? "On" : "Off");
        }
        set(out, "#Rails.Disabled", false);
        var advice = MineshaftNetwork.advice(sites, mine.base, start, scene.direction(), scene.length(), layout);
        var route = mine.route(start, scene.direction(), layout, scene.length());
        assertEquals(advice == null, route.ready(), scene.name());
        set(out, "#Review.Text", advice != null ? advice.message()
            : route.cells().size() + " blocks to excavate. Requires Mining 1. Adding authorizes permanent excavation by workers.");
        set(out, "#Add.Disabled", !route.ready());
        var fix = advice == null ? null : advice.fix();
        set(out, "#Fix.Visible", fix != null);
        if (fix != null) {
            set(out, "#Fix.Text", fix.button());
        }
        set(out, "#Tunnel.Text", scene.tunnel());
        set(out, "#Pause.Text", "Pause tunnel");
        set(out, "#Remove.Text", "Remove tunnel");
        set(out, "#ActivityIcon.Visible", false);
        set(out, "#Activity.Text", scene.activity());
        set(out, "#Buffer.Text", "Stored output: 0 items | Hover for contents");
        var draft = new MineshaftMap.Draft(start, scene.direction(), scene.length(), layout, advice == null ? List.of() : advice.clashes());
        var builder = new UICommandBuilder();
        MineshaftPlannerPage.drawMap(builder, MineshaftMap.cells(sites, mine.base, scene.centerX(), scene.centerZ(), scene.floor(), draft));
        for (var sent : builder.getCommands()) {
            var command = new JsonObject();
            command.addProperty("type", sent.type.name());
            if (sent.selector != null) {
                command.addProperty("selector", sent.selector);
            }
            if (sent.text != null) {
                command.addProperty("text", sent.text);
            }
            if (sent.data != null) {
                command.add("data", JsonParser.parseString(sent.data));
            }
            out.add(command);
        }
        set(out, "#Status.Text", "Choose direction and length, then add the reviewed tunnel.");
        return out;
    }

    private static JsonObject renderCase(Scene scene) {
        var arguments = new JsonObject();
        arguments.addProperty("root", "projects");
        arguments.addProperty("path", PAGE);
        var commands = commands(scene);
        // The renderer takes two thousand commands at most, and so much less is sent that a whole map always fits.
        assertTrue(commands.size() < 2000, scene.name() + " sends " + commands.size() + " commands");
        arguments.add("commands", commands);
        var expect = new JsonObject();
        expect.addProperty("valid", true);
        expect.addProperty("errors", 0);
        var step = new JsonObject();
        step.addProperty("tool", "ui_render");
        step.addProperty("name", scene.name());
        step.add("arguments", arguments);
        step.add("expect", expect);
        step.addProperty("golden", "tests/hytale/golden/" + scene.golden() + ".png");
        var steps = new JsonArray();
        steps.add(step);
        var result = new JsonObject();
        result.addProperty("name", "mineshaft planner: " + scene.name());
        result.add("steps", steps);
        return result;
    }

    private static List<Scene> scenes() {
        // The owner's mine: stairs come down heading south to a landing at 511, 104, -370.
        var landing = new Point(511, 104, -370);
        var top = new Point(511, 111, -379);
        var owners = MineFixture.facing(top, Direction.SOUTH);
        owners.add("Stairs", top, Direction.SOUTH, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);

        // A gallery north from the base with stairs going down east from its sixth block.
        var anchor = new Point(520, 111, -370);
        var over = MineFixture.facing(anchor, Direction.NORTH);
        over.add("Gallery", anchor, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 12, ExcavationFittings.Mode.SUPPORTS);
        over.add("Stairs", new Point(520, 111, -376), Direction.EAST, ExcavationPlan.Layout.DEEP_STAIRS_DOWN, 9);

        // The same gallery, dug and fitted, with a side passage added exactly at its second frame.
        var junction = MineFixture.facing(anchor, Direction.NORTH);
        var gallery = junction.add("Gallery", anchor, Direction.NORTH, ExcavationPlan.Layout.STRAIGHT, 12, ExcavationFittings.Mode.SUPPORTS);
        junction.dig(gallery);
        junction.fit(gallery);
        junction.add("Side passage", new Point(520, 111, -378), Direction.EAST, ExcavationPlan.Layout.STRAIGHT, 9);
        var plan = gallery.excavation();
        String galleryLine = "1 / 2: Gallery | " + plan.completed() + " / " + plan.total() + " blocks | " + plan.fitted() + " / " + plan.fittingsTotal() + " fittings";

        return List.of(
            new Scene("stairs drawn on the floor they pass through", "mineshaft-stairwell-lower-floor", owners, 510, -370, 104, landing,
                Direction.SOUTH, 9, "1 / 1: Stairs | 0 / 102 blocks", "Ready for a Mining worker."),
            new Scene("a tunnel aimed under its own stairs", "mineshaft-clash-under-stairs", owners, 510, -370, 104, landing,
                Direction.NORTH, 9, "1 / 1: Stairs | 0 / 102 blocks", "Ready for a Mining worker."),
            new Scene("a route over a stairwell with a start to move to", "mineshaft-clash-move-start", over, 520, -377, 111, new Point(520, 111, -377),
                Direction.EAST, 9, "2 / 2: Stairs | 0 / 102 blocks", "Waiting for the connecting tunnel to be finished."),
            new Scene("a junction where a frame was taken down", "mineshaft-junction", junction, 520, -376, 111, new Point(520, 111, -374),
                Direction.WEST, 9, galleryLine, "Ready for a Mining worker.")
        );
    }

    @Test
    void theRenderCasesAreWhatThePlannerSends() throws IOException {
        var cases = new JsonArray();
        for (var scene : scenes()) {
            cases.add(renderCase(scene));
        }
        var root = new JsonObject();
        root.add("cases", cases);
        String json = new GsonBuilder().disableHtmlEscaping().create().toJson(root) + "\n";
        if (System.getenv("AETHERHAVEN_WRITE_UI_CASES") != null) {
            Files.writeString(CASES, json, StandardCharsets.UTF_8);
        }
        assertTrue(Files.isRegularFile(CASES), CASES + " is missing. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set.");
        assertEquals(json, Files.readString(CASES, StandardCharsets.UTF_8).replace("\r\n", "\n"),
            CASES + " no longer matches what the planner sends. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set, run the render cases and look at the pictures.");
    }

    @Test
    void theSquareHeHoveredIsSentAsStairsAndThenAsAClash() {
        var scenes = scenes();
        // With the map centred on 510, -370 the square at 510, -373 is the tenth of the ninth row down.
        int hovered = 9 * MineshaftMap.SIZE + 10;
        String stairs = commands(scenes.get(0)).toString();
        assertTrue(stairs.contains("\"#Map[" + hovered + "] #Stairs.AssetPath\",\"data\":{\"0\":\"UI/Custom/Aetherhaven/MineshaftStairwellNorth.png\"}"), "no uphill arrows on his square");
        assertTrue(background(scenes.get(0), hovered).contains("#7d6aa8"), background(scenes.get(0), hovered));
        assertTrue(stairs.contains("510, 104, -373 | Stairs up to 111 pass here"));
        assertTrue(background(scenes.get(1), hovered).contains("#d9534f"), background(scenes.get(1), hovered));
        String clash = commands(scenes.get(1)).toString();
        assertTrue(clash.contains("Stairs up to 111 pass through the first 2 blocks north of here. Dig east, south or west from here."));
        assertTrue(clash.contains("\"selector\":\"#Fix.Visible\",\"data\":false"));
        assertTrue(commands(scenes.get(2)).toString().contains("\"selector\":\"#Fix.Text\",\"data\":\"Move start 2 blocks north\""));
    }

    private static String background(Scene scene, int square) {
        for (var command : commands(scene)) {
            var sent = command.getAsJsonObject();
            if (sent.has("selector") && sent.get("selector").getAsString().equals("#Map[" + square + "].Background")) {
                return sent.get("data").toString();
            }
        }
        return "";
    }
}
