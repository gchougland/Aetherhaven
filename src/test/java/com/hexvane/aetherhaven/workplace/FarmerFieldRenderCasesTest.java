package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.FarmAlmanac;
import com.hexvane.aetherhaven.production.FarmFieldRules;
import com.hexvane.aetherhaven.production.FarmFieldWork;
import com.hexvane.aetherhaven.production.FarmFieldWorld.Ground;
import com.hexvane.aetherhaven.production.FarmFieldWorld.Spot;
import com.hexvane.aetherhaven.production.FarmFields;
import com.hexvane.aetherhaven.production.FarmFieldsState;
import com.hexvane.aetherhaven.production.FarmUpgrades;
import com.hexvane.aetherhaven.production.FarmUpgrades.Track;
import com.hexvane.aetherhaven.production.NativeFarmCrops;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps {@code tests/hytale/farmer.test.json} equal to what the Scarecrow Fields screen sends, so the pictures drawn
 * from that file show the page a player gets.
 *
 * <p>Every command is the page's own: each scene builds a real town, farm and crop plan, gives the page a field to look
 * at ({@link Ground}, where the game would read blocks) and runs {@link FarmerFieldPage#draw}. Nothing is written by
 * hand. Two things are added for the picture renderer only, and they change no selector:
 * <ul>
 * <li>after each {@code .TextSpans} the page sets, the same element's {@code .Text} with the English wording, because
 * the renderer does not look translations up;</li>
 * <li>an {@code .ItemId} whose picture file is not named after the item names the picture file instead.</li>
 * </ul>
 * Item names come out as plain English here; the page sends the game's own translation for each.
 *
 * <p>After an intended change run the tests once with the environment variable AETHERHAVEN_WRITE_UI_CASES set, run the
 * render cases, and look at the new pictures before keeping them.
 */
@Tag("town")
class FarmerFieldRenderCasesTest {
    private static final Path CASES = Path.of("tests/hytale/farmer.test.json");
    private static final String PAGE = "Aetherhaven/src/main/resources/Common/UI/Custom/Aetherhaven/FarmerFieldPage.ui";
    private static final String ESSENCE = NativeFarmCrops.LIFE_ESSENCE;
    private static final List<String> NAMES = List.of("Wheat", "Lettuce", "Carrot", "Corn", "Cauliflower", "Turnip", "Aubergine", "Pumpkin",
        "Tomato", "Chilli", "Cotton", "Rice", "Onion", "Potato");
    /** Items whose picture file is not named after them: the renderer finds a picture by file name only. */
    private static final Map<String, String> PICTURES = new HashMap<>(Map.of("Aetherhaven_Scarecrow", "Deco_Scarecrow"));
    private static final Map<String, String> ENGLISH = new HashMap<>(Map.of(ESSENCE, "Life Essence", "Aetherhaven_Golden_Harvest", "Golden Harvest"));
    private static final Map<String, String> LANG = new HashMap<>();
    private static Object previousItemStore;

    static {
        for (String name : NAMES) {
            ENGLISH.put(crop(name), name);
            ENGLISH.put("Plant_Seeds_" + name, name + " Seeds");
            PICTURES.put(crop(name), "Plant_Crop_" + name);
        }
    }

    private static String crop(String name) { return "Plant_Crop_" + name + "_Item"; }

    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    /** The page asks what a seed costs at the game's own bench. With no items loaded it takes the cost from the farm's rules. */
    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousItemStore = field.get(null); field.set(null, new ItemStore.Config().build());
        try (var files = Files.list(Path.of("src/main/resources/Server/Languages/en-US"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".lang")).toList()) {
                String prefix = file.getFileName().toString().replace(".lang", "") + ".";
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int equals = line.indexOf('=');
                    if (equals > 0 && !line.startsWith("#")) LANG.put(prefix + line.substring(0, equals).trim(), line.substring(equals + 1));
                }
            }
        }
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousItemStore);
    }

    // A farm and the ground the page looks at ------------------------------------------------------------------------

    /** What stands in the world, column by column. The game reads this from blocks. */
    private static final class Field implements FarmerFieldPage.Sight {
        final Map<Long, Spot> spots = new HashMap<>();
        final Set<Point> ripe = new HashSet<>();
        final Set<Point> scarecrows = new HashSet<>();
        LocalDateTime now;

        static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
        void soil(int x, int z, String standing, boolean ripeNow) {
            spots.put(key(x, z), new Spot(Ground.OK, 101, true, standing == null ? null : NativeFarmCrops.byOutput(standing)));
            if (ripeNow) ripe.add(new Point(x, 101, z));
        }
        void ground(int x, int z, Ground ground) { spots.put(key(x, z), new Spot(ground, 0, false, null)); }
        @Override public LocalDateTime now() { return now; }
        @Override public Spot inspect(TownRecord town, PlotInstance farm, boolean home, Point anchor, FarmFields.Bounds bounds, int x, int z) {
            return spots.getOrDefault(key(x, z), new Spot(Ground.NOT_SOIL, 0, false, null));
        }
        @Override public boolean ripe(Point cell, NativeFarmCrops.Crop crop) { return ripe.contains(cell); }
        @Override public double growth(Point cell, NativeFarmCrops.Crop crop) { return ripe.contains(cell) ? 1.0 : 0.5; }
        @Override public FarmFieldWork.View view(TownRecord town, FarmFieldsState.Cell cell) {
            var spot = spots.get(key(cell.x(), cell.z()));
            if (spot == null || !spot.ok()) return FarmFieldWork.View.FAR;
            return new FarmFieldWork.View(true, spot.crop() == null, spot.crop() == null ? null : spot.crop().output(), ripe.contains(cell.point()), false);
        }
        @Override public boolean scarecrowAt(Point block) { return scarecrows.contains(block); }
        @Override public boolean itemExists(String item) { return true; }
        @Override public Message itemName(String item) { return Message.raw(ENGLISH.getOrDefault(item, item)); }
    }

    /** One farm of a town, with the field around it. */
    private static final class Farm {
        final TownRecord town = new TownRecord(new UUID(1, 1), new UUID(1, 2), "world", 0, 100, 0, 1, 1, 0);
        final PlotInstance plot = new PlotInstance(new UUID(2, 1), FarmFields.CONSTRUCTION, PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0, 100, 0, 11, 104, 11), 0, 100, 0, 0L);
        final PlotProductionState production;
        final Field field = new Field();
        boolean manage = true;

        Farm(int season, int week, int daysLeft) {
            town.addPlotInstance(plot);
            production = FarmFields.production(town, plot);
            field.now = AetherhavenCalendar.EPOCH_DATE.plusDays((season * FarmAlmanac.WEEKS_PER_SEASON + week - 1) * 7L + 7 - daysLeft).atTime(12, 0);
        }
        FarmFieldsState fields() { return production.farm(); }
        /** Trains the Farmer and unlocks crops in the farm's storage, which is what makes a crop growable. */
        void grows(int mastery, String... names) {
            var progress = CoreVillagerMasteryService.progress(town, FarmFields.KIND);
            while (progress.level() < mastery) progress.earnXp(progress.xpToNextLevel() - progress.xpInLevel());
            for (String name : names) production.addWorkplaceOutputUnlock(crop(name));
        }
        void seeds(String name, int count) { fields().addSeeds("Plant_Seeds_" + name, count, 4096); }
        void stock(String item, int count) { production.addAmount(item, count, 100_000); }
        ProductionCatalog.Entry entry() {
            var loader = FarmerFieldRenderCasesTest.class.getClassLoader();
            return ProductionEffectiveCatalog.effective(ProductionCatalog.loadFromClasspath(loader), WorkplaceUnlockCatalog.loadFromClasspath(loader), FarmFields.CONSTRUCTION, production);
        }
        /** The farm building's own ground: three beds of four by four. */
        void beds(BedCell cell) {
            for (int z = 0; z < 12; z++) for (int x = 0; x < 12; x++) {
                String bed = z >= 1 && z <= 4 && x >= 7 && x <= 10 ? "north" : z >= 7 && z <= 10 && x >= 1 && x <= 4 ? "west" : z >= 7 && z <= 10 && x >= 7 && x <= 10 ? "east" : null;
                if (bed == null) { field.ground(x, z, Ground.FARMYARD); continue; }
                cell.at(bed, x, z, bed.equals("north") ? x - 7 : bed.equals("west") ? x - 1 : x - 7, bed.equals("north") ? z - 1 : z - 7);
            }
        }
        /** Wears a planned cell out: the same crop gathered again and again. What was gathered goes to compost. */
        void wear(int slot, int x, int z) {
            var cell = fields().cell(slot, x, z);
            for (int i = 0; i < FarmFieldRules.get().soil().wearEvery(); i++)
                fields().harvest(slot, x, z, cell.crop(), null, 1, 0, false, 0, 0.999, 0.999, FarmFieldRules.get());
            fields().deliverAll(production, item -> 0, item -> true, 1, 4096);
        }
        /** Lifts a planned cell's soil one step with compost made from spare crops. */
        void enrich(int slot, int x, int z) {
            var cell = fields().cell(slot, x, z);
            if (fields().compost() < 1) {
                fields().harvest(slot, x, z, cell.crop(), null, 1, 0, false, 0, 0.999, 0.999, FarmFieldRules.get());
                fields().deliverAll(production, item -> 0, item -> true, 1, 4096);
            }
            assertTrue(fields().spread(slot, x, z, true), "compost on " + x + ", " + z);
        }
        FarmerFieldPage.Context context() {
            return new FarmerFieldPage.Context(null, town, plot, production, entry(), null, field, manage);
        }
    }

    private interface BedCell { void at(String bed, int x, int z, int column, int row); }

    private record Scene(String name, String golden, Farm farm, Point scarecrow, String brush, boolean almanac) {}

    private static void set(Object target, String field, Object value) {
        try {
            var f = FarmerFieldPage.class.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("FarmerFieldPage no longer keeps " + field, e);
        }
    }

    // What the page sends, as a render case --------------------------------------------------------------------------

    /** The English wording of a message as the client would show it. */
    private static String english(JsonElement message) {
        if (message == null || message.isJsonNull()) return "";
        if (message.isJsonPrimitive()) return message.getAsString();
        var m = message.getAsJsonObject();
        var text = new StringBuilder();
        if (m.has("RawText") && !m.get("RawText").isJsonNull()) text.append(m.get("RawText").getAsString());
        else if (m.has("MessageId") && !m.get("MessageId").isJsonNull()) {
            String id = m.get("MessageId").getAsString();
            String wording = LANG.get(id);
            assertFalse(wording == null, "No English text for " + id);
            for (String group : List.of("Params", "MessageParams")) {
                if (!m.has(group) || !m.get(group).isJsonObject()) continue;
                for (var param : m.getAsJsonObject(group).entrySet()) wording = wording.replace("{" + param.getKey() + "}", parameter(param.getValue()));
            }
            text.append(wording);
        }
        if (m.has("Children") && m.get("Children").isJsonArray()) for (var child : m.getAsJsonArray("Children")) text.append(english(child));
        return text.toString();
    }

    /** A message parameter: another message, plain text, or a typed value. */
    private static String parameter(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsString();
        var o = value.getAsJsonObject();
        if (o.has("MessageId") || o.has("RawText") || o.has("Children")) return english(o);
        for (var entry : o.entrySet()) if (entry.getValue().isJsonPrimitive() && !entry.getKey().equalsIgnoreCase("Type")) return entry.getValue().getAsString();
        return o.toString();
    }

    private static JsonArray commands(Scene scene) {
        var page = new FarmerFieldPage(null, scene.farm().town.getTownId(), scene.farm().plot.getPlotId(), scene.scarecrow());
        if (scene.brush() != null) set(page, "brush", crop(scene.brush()));
        set(page, "almanac", scene.almanac());
        var builder = new UICommandBuilder();
        page.draw(builder, new UIEventBuilder(), scene.farm().context());
        var out = new JsonArray();
        for (var sent : builder.getCommands()) {
            var command = new JsonObject();
            command.addProperty("type", sent.type.name());
            if (sent.selector != null) command.addProperty("selector", sent.selector);
            if (sent.text != null) command.addProperty("text", sent.text);
            JsonElement data = sent.data == null ? null : JsonParser.parseString(sent.data);
            if (data != null && sent.type == CustomUICommandType.Set && sent.selector.endsWith(".ItemId")) {
                String item = data.getAsJsonObject().get("0").getAsString();
                if (PICTURES.containsKey(item)) data.getAsJsonObject().add("0", new JsonPrimitive(PICTURES.get(item)));
            }
            if (data != null) command.add("data", data);
            out.add(command);
            if (data != null && sent.type == CustomUICommandType.Set && sent.selector.endsWith(".TextSpans") && !sent.selector.endsWith(".TooltipTextSpans")) {
                var shown = new JsonObject();
                shown.addProperty("type", "Set");
                shown.addProperty("selector", sent.selector.substring(0, sent.selector.length() - "TextSpans".length()) + "Text");
                shown.addProperty("data", english(data.getAsJsonObject().get("0")));
                out.add(shown);
            }
        }
        return out;
    }

    private static JsonObject renderCase(Scene scene) {
        var arguments = new JsonObject();
        arguments.addProperty("root", "projects");
        arguments.addProperty("path", PAGE);
        var commands = commands(scene);
        assertTrue(commands.size() < 2000, scene.name() + " sends " + commands.size() + " commands, and the renderer takes two thousand");
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
        return step;
    }

    // The scenes ----------------------------------------------------------------------------------------------------

    private static final Point FAR_SCARECROW = new Point(40, 101, 40);

    /** A farm that was just built: the game's own crops stand in its three beds and nothing is planned yet. */
    private static Scene newFarm() {
        var farm = new Farm(0, 2, 5);
        farm.beds((bed, x, z, column, row) -> farm.field.soil(x, z, crop(bed.equals("north") ? "Carrot" : bed.equals("west") ? "Wheat" : "Lettuce"), false));
        // The seeds in its store are the ones every farm starts with.
        farm.stock(crop("Wheat"), 25); farm.stock(ESSENCE, 62);
        return new Scene("new farm, nothing planned yet", "farmer-fields-new-farm", farm, null, null, false);
    }

    /** A working farm: every bed planned, crops at every stage, all three kinds of soil, and a Scarecrow field beside it. */
    private static Farm working() {
        var farm = new Farm(0, 2, 5);
        farm.grows(3, "Carrot", "Corn");
        farm.beds((bed, x, z, column, row) -> {
            String plan = bed.equals("west") ? "Wheat" : bed.equals("east") ? "Lettuce" : column < 2 ? "Carrot" : "Corn";
            String standing = bed.equals("east") && column == 0 && row == 3 ? null : bed.equals("north") && row == 2 ? "Wheat" : plan;
            boolean ripe = bed.equals("west") ? (column + row) % 3 == 0 : bed.equals("east") && row == 0;
            farm.field.soil(x, z, standing == null ? null : crop(standing), ripe && standing != null);
            assertTrue(farm.fields().paint(FarmFieldsState.HOME, x, 101, z, crop(plan)));
        });
        farm.beds((bed, x, z, column, row) -> {
            if (bed.equals("east") && column == 3) farm.wear(FarmFieldsState.HOME, x, z);
        });
        farm.beds((bed, x, z, column, row) -> {
            if (bed.equals("west") && row < 2 || bed.equals("north") && row == 0) farm.enrich(FarmFieldsState.HOME, x, z);
        });
        farm.fields().setLevel(Track.SCARECROWS.name(), 1);
        assertNull(farm.fields().claim(1, FarmUpgrades.scarecrows(farm.production), FAR_SCARECROW));
        farm.field.scarecrows.add(FAR_SCARECROW);
        farm.seeds("Wheat", 9); farm.seeds("Lettuce", 14); farm.seeds("Carrot", 3);
        farm.stock(ESSENCE, 88); farm.stock(crop("Wheat"), 25); farm.stock(crop("Lettuce"), 21); farm.stock(crop("Corn"), 12);
        farm.stock(crop("Carrot"), 7); farm.stock("Aetherhaven_Golden_Harvest", 1);
        return farm;
    }

    private static Scene workingFarm() {
        var farm = working();
        // She is on her way home with a basket of what she gathered.
        farm.fields().harvest(FarmFieldsState.HOME, 1, 7, crop("Wheat"), null, 11, 0, false, 0, 0.999, 0.999, FarmFieldRules.get());
        return new Scene("working farm with every kind of soil", "farmer-fields-working", farm, null, "Corn", false);
    }

    /** A Scarecrow field planned with a crop there are no seeds for, and no Life Essence to make them. */
    private static Scene missingSeeds() {
        var farm = new Farm(0, 2, 5);
        farm.grows(3, "Carrot", "Corn");
        farm.fields().setLevel(Track.SCARECROWS.name(), 1);
        assertNull(farm.fields().claim(1, FarmUpgrades.scarecrows(farm.production), FAR_SCARECROW));
        farm.field.scarecrows.add(FAR_SCARECROW);
        int reach = FarmUpgrades.reach(farm.production);
        for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
            int x = FAR_SCARECROW.x() + dx, z = FAR_SCARECROW.z() + dz;
            if (dx == 0 && dz == 0) { farm.field.ground(x, z, Ground.SCARECROW); continue; }
            if (dx == reach && dz == reach) { farm.field.ground(x, z, Ground.BLOCKED); continue; }
            farm.field.soil(x, z, dz < 0 ? crop("Corn") : null, dz == -reach);
            assertTrue(farm.fields().paint(1, x, 101, z, crop("Corn")));
        }
        farm.seeds("Wheat", 9); farm.seeds("Lettuce", 14); farm.seeds("Carrot", 3);
        farm.stock(crop("Corn"), 6);
        return new Scene("Scarecrow field that has run out of seeds", "farmer-fields-missing-seeds", farm, FAR_SCARECROW, "Corn", false);
    }

    /** A Scarecrow that was just placed on open ground: nothing planned, the widest reach, every crop unlocked. */
    private static Scene newScarecrow() {
        var farm = new Farm(3, 4, 1);
        farm.grows(10, NAMES.toArray(String[]::new));
        farm.fields().setLevel(Track.SCARECROWS.name(), FarmUpgrades.maxLevel(Track.SCARECROWS));
        farm.fields().setLevel(Track.REACH.name(), FarmUpgrades.maxLevel(Track.REACH));
        farm.fields().setLevel(Track.ALMANAC.name(), FarmUpgrades.maxLevel(Track.ALMANAC));
        assertNull(farm.fields().claim(1, FarmUpgrades.scarecrows(farm.production), new Point(80, 101, 80)));
        farm.field.scarecrows.add(FAR_SCARECROW);
        int reach = FarmUpgrades.reach(farm.production);
        for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
            int x = FAR_SCARECROW.x() + dx, z = FAR_SCARECROW.z() + dz;
            if (dx == 0 && dz == 0) farm.field.ground(x, z, Ground.SCARECROW);
            else if (dx + dz <= 1 - 2 * reach || dx == reach && dz == reach) farm.field.ground(x, z, Ground.BLOCKED);
            else farm.field.soil(x, z, null, false);
        }
        for (String name : NAMES) farm.seeds(name, 64);
        return new Scene("new Scarecrow on open ground, every crop unlocked", "farmer-fields-new-scarecrow", farm, FAR_SCARECROW, null, false);
    }

    /** The Almanac, read as far ahead as it goes. */
    private static Scene almanac() {
        var farm = working();
        farm.grows(10, NAMES.toArray(String[]::new));
        farm.fields().setLevel(Track.ALMANAC.name(), FarmUpgrades.maxLevel(Track.ALMANAC));
        farm.field.now = AetherhavenCalendar.EPOCH_DATE.plusDays((FarmAlmanac.WEEKS_PER_SEASON + 2) * 7L + 5).atTime(12, 0);
        for (String name : NAMES) farm.seeds(name, 20);
        return new Scene("the Almanac a season ahead", "farmer-fields-almanac", farm, null, "Wheat", true);
    }

    private static List<Scene> scenes() { return List.of(newFarm(), workingFarm(), missingSeeds(), newScarecrow(), almanac()); }

    private static String data(JsonArray commands, String selector) {
        String found = null;
        for (var command : commands) {
            var sent = command.getAsJsonObject();
            if (sent.has("selector") && sent.get("selector").getAsString().equals(selector) && sent.has("data")) found = sent.get("data").toString();
        }
        return found;
    }

    @Test
    void theRenderCasesAreWhatTheFieldsScreenSends() throws IOException {
        var steps = new ArrayList<String>();
        var gson = new GsonBuilder().disableHtmlEscaping().create();
        for (var scene : scenes()) steps.add("  " + gson.toJson(renderCase(scene)));
        // One step to a line keeps a change to one screen readable.
        String json = "{\"cases\": [\n {\"name\": \"scarecrow fields\", \"steps\": [\n" + String.join(",\n", steps) + "\n ]}\n]}\n";
        if (System.getenv("AETHERHAVEN_WRITE_UI_CASES") != null) Files.writeString(CASES, json, StandardCharsets.UTF_8);
        assertTrue(Files.isRegularFile(CASES), CASES + " is missing. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set.");
        assertEquals(json, Files.readString(CASES, StandardCharsets.UTF_8).replace("\r\n", "\n"),
            CASES + " no longer matches what the Scarecrow Fields screen sends. Run the tests once with AETHERHAVEN_WRITE_UI_CASES set, run the render cases and look at the pictures.");
    }

    /** The command that disconnected a player on 2026-10-11 is in the cases, so the renderer and the audit both see it. */
    @Test
    void theToolsAreSwitchedByTheCommandsThatOnceLandedOnAPaletteRow() {
        var sent = commands(newFarm());
        for (String tool : List.of("Fill", "Clear", "Rotate", "Adopt", "Copy", "Seeds"))
            assertEquals("{\"0\":false}", data(sent, "#" + tool + ".Disabled"), tool);
        // A crop of the palette shows its seed count on its own label, through the palette's row.
        int wheat = newFarm().farm().fields().seeds("Plant_Seeds_Wheat");
        assertTrue(wheat > 0, "a new farm starts with seeds");
        assertEquals("{\"0\":\"" + wheat + "\"}", data(sent, "#Palette[0] #SeedCount.Text"));
        var readOnly = newFarm();
        readOnly.farm().manage = false;
        assertEquals("{\"0\":true}", data(commands(readOnly), "#Seeds.Disabled"));
    }

    @Test
    void eachSceneShowsWhatItsNameSays() {
        var fresh = commands(newFarm());
        assertEquals("{\"0\":true}", data(fresh, "#Adopt.Visible"), "crops stand in the beds and none is planned");
        assertEquals("{\"0\":false}", data(fresh, "#Tab1.Visible"));
        var busy = commands(workingFarm());
        assertEquals("{\"0\":true}", data(busy, "#Tab1.Visible"));
        assertTrue(busy.toString().contains("#PipRich.Visible") && busy.toString().contains("#PipWorn.Visible"), "rich and worn soil are both on the map");
        var seedless = commands(missingSeeds());
        assertTrue(seedless.toString().contains("#Need.Visible"), "cells waiting for a seed are marked");
        assertTrue(String.valueOf(data(seedless, "#Status.Text")).contains("Corn Seeds"), data(seedless, "#Status.Text"));
        var placed = commands(newScarecrow());
        assertEquals("{\"0\":true}", data(placed, "#TabNew.Visible"));
        var ahead = commands(almanac());
        assertEquals("{\"0\":true}", data(ahead, "#AlmanacPanel.Visible"));
        assertTrue(ahead.toString().contains("\"#Weeks[" + (FarmUpgrades.weeksShown(almanac().farm().production) - 1) + "] #When.TextSpans\""));
        var counts = new TreeMap<String, Integer>();
        for (var scene : scenes()) counts.put(scene.golden(), commands(scene).size());
        assertEquals(5, counts.size(), counts.toString());
    }
}
