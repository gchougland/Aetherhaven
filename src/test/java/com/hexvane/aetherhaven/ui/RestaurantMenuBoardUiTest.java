package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantDish;
import com.hexvane.aetherhaven.restaurant.RestaurantMenuCatalog;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.ui.RestaurantMenuBoardUi.Mode;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeConnections;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Node;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The menu board's commands, and the whole Restaurant tab as commands for offline pictures.
 *
 * <p>The board commands come from the real {@link RestaurantMenuBoardUi}. The rest of the tab (summary, tabs, tree,
 * details) mirrors {@code WorkplaceUpgradesPage.build}, which needs a running world and cannot be called here; keep
 * {@link #restaurantTab} in step with it. The picture cases in {@code tests/hytale/restaurant.test.json} are generated
 * from these commands into {@code build/restaurant-ui} and must match the committed file.
 */
@Tag("town")
class RestaurantMenuBoardUiTest {
    private static final RestaurantMenuCatalog MENU = RestaurantMenuCatalog.load(RestaurantMenuBoardUiTest.class.getClassLoader());
    private static final String LANG = "aetherhaven_ui_town.aetherhaven.ui.restaurantUpgrades.";
    private static Object previousItemStore;

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

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousItemStore = field.get(null); field.set(null, new ItemStore.Config().build());
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousItemStore);
    }

    private static PlotRestaurantState restaurant(int satiety, int service, int menu, int provisions) {
        var state = PlotRestaurantState.empty();
        state.setSatietyLevel(satiety); state.setServiceLevel(service); state.setMenuLevel(menu); state.setProvisionsLevel(provisions);
        return state;
    }

    private static Node node(String id) {
        return BuildingUpgradeTree.restaurant().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<CustomUICommand> board(Mode mode, Node node, PlotRestaurantState state, RestaurantMenuCatalog menu, int farmAndRanchFood) {
        var c = new UICommandBuilder();
        RestaurantMenuBoardUi.apply(c, mode, node, state, menu, farmAndRanchFood);
        return Arrays.asList(c.getCommands());
    }

    private static String data(List<CustomUICommand> commands, String selector) {
        return commands.stream().filter(c -> selector.equals(c.selector) && c.type == CustomUICommandType.Set).reduce((a, b) -> b).map(c -> c.data).orElse(null);
    }

    private static long appended(List<CustomUICommand> commands) {
        return commands.stream().filter(c -> c.type == CustomUICommandType.Append && "#MenuBoardRows".equals(c.selector)).count();
    }

    @Test void boardShowsOnlyOnTheRestaurantNodeAndOnChefsMenuNodes() {
        assertEquals(Mode.MENU, RestaurantMenuBoardUi.mode("Restaurant", node("home")));
        for (int tier = 1; tier <= 3; tier++) {
            assertEquals(Mode.LEVEL, RestaurantMenuBoardUi.mode("Restaurant", node("MENU:" + tier)));
            for (String other : List.of("SATIETY:", "SERVICE:", "PROVISIONS:")) assertEquals(Mode.NONE, RestaurantMenuBoardUi.mode("Restaurant", node(other + tier)));
        }
        assertEquals(Mode.NONE, RestaurantMenuBoardUi.mode("Restaurant", null));
        var cookHome = BuildingUpgradeTree.workplaces("chef").getFirst();
        assertEquals(Kind.FOUNDATION, cookHome.kind());
        assertEquals(Mode.NONE, RestaurantMenuBoardUi.mode("Workplaces", cookHome), "Other tabs keep their own details");
        var hidden = board(Mode.NONE, cookHome, null, MENU, 20);
        assertEquals("{\"0\": false}", data(hidden, "#MenuBoard.Visible"));
        assertEquals(0, appended(hidden));
        assertEquals(2, hidden.size(), "A hidden board only hides itself and clears its lines");
    }

    @Test void restaurantNodeListsTheCurrentMenuWithOnePerkSentencePerLine() {
        var plain = board(Mode.MENU, node("home"), restaurant(0, 0, 0, 0), MENU, 20);
        assertEquals("{\"0\": true}", data(plain, "#MenuBoard.Visible"));
        assertEquals(0, appended(plain));
        assertTrue(data(plain, "#MenuBoardHeading.TextSpans").contains(LANG + "board.menuHeading"));
        assertEquals("{\"0\": true}", data(plain, "#MenuBoardNote.Visible"));
        assertTrue(data(plain, "#MenuBoardNote.TextSpans").contains(LANG + "board.plain"));
        assertEquals("{\"0\": false}", data(plain, "#MenuBoardProvisions.Visible"));

        var some = board(Mode.MENU, node("home"), restaurant(1, 0, 2, 1), MENU, 20);
        assertEquals(2, appended(some));
        assertEquals("{\"0\": \"Food_Kebab_Mushroom\"}", data(some, "#MenuBoardRows[0] #Dish0.ItemId"));
        assertEquals("{\"0\": \"Food_Kebab_Vegetable\"}", data(some, "#MenuBoardRows[0] #Dish1.ItemId"));
        assertEquals("{\"0\": false}", data(some, "#MenuBoardRows[0] #Dish2.Visible"));
        assertTrue(data(some, "#MenuBoardRows[0] #Dishes.TextSpans").contains("server.items.Food_Kebab_Vegetable.name"));
        String work = data(some, "#MenuBoardRows[0] #Perk.TextSpans");
        assertTrue(work.contains(LANG + "perk.work") && work.contains("\"10\"") && work.contains("\"8\""), work);
        String energy = data(some, "#MenuBoardRows[1] #Perk.TextSpans");
        assertTrue(energy.contains(LANG + "perk.energy") && energy.contains("\"25\""), energy);
        assertEquals("{\"0\": false}", data(some, "#MenuBoardNote.Visible"));
        assertEquals("{\"0\": false}", data(some, "#MenuBoardMore.Visible"));
        assertEquals("{\"0\": true}", data(some, "#MenuBoardProvisions.Visible"));
        String provisions = data(some, "#MenuBoardProvisions.TextSpans");
        assertTrue(provisions.contains(LANG + "board.provisions\"") && provisions.contains("\"5\""), provisions);

        var maxed = board(Mode.MENU, node("home"), restaurant(3, 3, 3, 3), MENU, 20);
        assertEquals(3, appended(maxed));
        assertTrue(data(maxed, "#MenuBoardRows[2] #Perk.TextSpans").contains(LANG + "perk.cheer"));
        assertTrue(data(maxed, "#MenuBoardProvisions.TextSpans").contains("\"15\""));

        var starved = board(Mode.MENU, node("home"), restaurant(0, 0, 0, 3), MENU, 10);
        String shortfall = data(starved, "#MenuBoardProvisions.TextSpans");
        assertTrue(shortfall.contains(LANG + "board.provisionsShort") && shortfall.contains("\"10\"") && shortfall.contains("\"15\""), shortfall);
    }

    @Test void menuNodeShowsOnlyWhatThatLevelAddsAndCrowdedMenusNeverOverflowThePanel() {
        var level = board(Mode.LEVEL, node("MENU:2"), restaurant(0, 0, 0, 3), MENU, 20);
        assertEquals(1, appended(level));
        assertTrue(data(level, "#MenuBoardHeading.TextSpans").contains(LANG + "board.levelHeading"));
        assertEquals("{\"0\": \"Food_Salad_Berry\"}", data(level, "#MenuBoardRows[0] #Dish0.ItemId"));
        assertEquals("{\"0\": false}", data(level, "#MenuBoardNote.Visible"));
        assertEquals("{\"0\": false}", data(level, "#MenuBoardProvisions.Visible"), "Provisions belong to the Restaurant node");

        // A pack that adds many dishes: more lines than fit are summed up instead of drawn.
        var dishes = new ArrayList<RestaurantDish>(MENU.all());
        dishes.add(new RestaurantDish("Food_Bread", 1, "work", 0.15, 6));
        dishes.add(new RestaurantDish("Food_Cheese", 1, "cheer", 0.2, 4));
        dishes.add(new RestaurantDish("Food_Popcorn", 1, "work", 0.1, 8));
        dishes.add(new RestaurantDish("Food_Egg", 1, "work", 0.1, 8));
        var crowded = new RestaurantMenuCatalog(dishes);
        var busy = board(Mode.MENU, node("home"), restaurant(0, 0, 3, 0), crowded, 20);
        assertEquals(RestaurantMenuBoardUi.MENU_ROWS, appended(busy));
        assertTrue(data(busy, "#MenuBoardMore.TextSpans").contains(LANG + "board.more") && data(busy, "#MenuBoardMore.TextSpans").contains("\"2\""));
        assertEquals("{\"0\": false}", data(busy, "#MenuBoardNote.Visible"));
        // Lines follow the menu's order: bread, cheese, then the four dishes that share the skewers' perk.
        assertEquals("{\"0\": false}", data(busy, "#MenuBoardRows[0] #Dish1.Visible"));
        assertEquals("{\"0\": true}", data(busy, "#MenuBoardRows[2] #Dish2.Visible"), "At most three pictures in a line");
        assertNull(data(busy, "#MenuBoardRows[2] #Dish3.Visible"));
        assertTrue(data(busy, "#MenuBoardRows[2] #Dishes.TextSpans").contains("server.items.Food_Popcorn.name"), "Every dish is still named");
        var busyWithProvisions = board(Mode.MENU, node("home"), restaurant(0, 0, 3, 2), crowded, 20);
        assertEquals(RestaurantMenuBoardUi.MENU_ROWS - 1, appended(busyWithProvisions), "One line gives way to the provisions line");
        assertTrue(data(busyWithProvisions, "#MenuBoardMore.TextSpans").contains("\"3\""));
        var firstLevel = board(Mode.LEVEL, node("MENU:1"), restaurant(0, 0, 0, 0), crowded, 20);
        assertEquals(RestaurantMenuBoardUi.LEVEL_ROWS, appended(firstLevel));
        assertEquals("{\"0\": true}", data(firstLevel, "#MenuBoardMore.Visible"));
        var nothing = board(Mode.LEVEL, node("MENU:3"), restaurant(0, 0, 0, 0), RestaurantMenuCatalog.empty(), 20);
        assertTrue(data(nothing, "#MenuBoardNote.TextSpans").contains(LANG + "board.empty"));
    }

    @Test void everyBoardSentenceExistsInTheEnglishLanguageFileWithoutDashPauses() throws Exception {
        var lines = Files.readAllLines(Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_ui_town.lang"), StandardCharsets.UTF_8);
        for (String key : List.of("board.menuHeading", "board.levelHeading", "board.plain", "board.empty", "board.more", "board.provisions",
                "board.provisionsShort", "perk.work", "perk.energy", "perk.cheer")) {
            String prefix = "aetherhaven.ui.restaurantUpgrades." + key + "=";
            String text = lines.stream().filter(l -> l.startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Missing " + key)).substring(prefix.length());
            assertFalse(text.isBlank());
            assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), key);
            if (key.startsWith("perk.")) {
                assertTrue(text.contains("{percent}") && text.contains("{hours}"), key);
                assertEquals(1, text.chars().filter(ch -> ch == '.').count(), "One plain sentence: " + key);
            }
        }
        for (var perk : com.hexvane.aetherhaven.restaurant.MealPerk.values())
            assertTrue(lines.stream().anyMatch(l -> l.startsWith("aetherhaven.ui.restaurantUpgrades.perk." + perk.id + "=")), "No sentence for " + perk);
    }

    // --- whole tab, for pictures ---------------------------------------------------------------------------------------

    private static void anchor(UICommandBuilder c, String selector, int x, int y, int width, int height) {
        var a = new Anchor(); a.setLeft(Value.of(x)); a.setTop(Value.of(y)); a.setWidth(Value.of(width)); a.setHeight(Value.of(height));
        c.setObject(selector + ".Anchor", a);
    }

    private static void line(UICommandBuilder c, int x, int y, int w, int h, String color) {
        c.appendInline("#Canvas", "Group { Anchor: (Left: " + x + ", Top: " + y + ", Width: " + Math.max(2, w) + ", Height: " + Math.max(2, h) + "); Background: " + color + "; }");
    }

    /** Mirrors WorkplaceUpgradesPage.build for the Chef's Restaurant tab with one restaurant. */
    private static List<CustomUICommand> restaurantTab(PlotRestaurantState state, int townLevel, String selected, long gold, int held, int farmAndRanchFood) {
        var c = new UICommandBuilder();
        c.append("Aetherhaven/WorkplaceUpgradesPage.ui");
        var all = BuildingUpgradeTree.restaurant();
        c.set("#Summary.Text", "Chef  |  Town Level " + townLevel + "  |  0/1 workplaces  |  Available: " + gold + " gold");
        for (String t : List.of("Workplaces", "Production", "Restaurant", "Alchemy", "Storage", "Inn")) {
            c.set("#" + t + ".Visible", t.equals("Workplaces") || t.equals("Restaurant"));
            c.set("#" + t + ".Disabled", t.equals("Restaurant"));
        }
        c.set("#Previous.Visible", false); c.set("#Next.Visible", false); c.set("#Plot.Visible", true);
        c.set("#Plot.Text", "Building 1 / 1");
        c.clear("#Canvas"); anchor(c, "#Canvas", 0, 0, 640, all.stream().mapToInt(n -> n.y() + 140).max().orElse(240));
        java.util.function.Predicate<Node> owned = n -> n.kind() == Kind.FOUNDATION
            || RestaurantUpgrades.currentLevel(state, RestaurantUpgrades.Branch.valueOf(n.branch())) >= n.tier();
        var connections = BuildingUpgradeConnections.build(all, n -> owned.test(n) ? 2
            : n.parents().stream().anyMatch(parent -> all.stream().anyMatch(o -> o.id().equals(parent) && owned.test(o))) ? 1 : 0);
        c.appendInline("#Canvas", "Group #Bands { Anchor: (Full: 0); }");
        int bi = 0;
        for (int l = 1; l <= 6; l++) {
            final int band = l; if (all.stream().noneMatch(n -> n.level() == band)) continue;
            c.append("#Bands", "Aetherhaven/BuildingUpgradeBand.ui"); String s = "#Bands[" + bi++ + "]";
            int top = BuildingUpgradeTree.bandTop(l, true);
            anchor(c, s, 24, top, 592, 30); c.set(s + " #Label.Text", "TOWN LEVEL " + l + "\n" + (townLevel >= l ? "REACHED" : "LOCKED"));
            int y = top + 15, start = 132;
            var crossing = connections.stream().filter(path -> path.y() <= y + 1 && path.y() + path.height() > y)
                .sorted(Comparator.comparingInt(BuildingUpgradeConnections.Stroke::x)).toList();
            for (var path : crossing) {
                int end = Math.min(616, path.x() - 8);
                if (end > start) line(c, start, y, end - start, 1, "#48576a");
                start = Math.max(start, path.x() + path.width() + 8);
            }
            if (start < 616) line(c, start, y, 616 - start, 1, "#48576a");
        }
        for (var path : connections) line(c, path.x(), path.y(), path.width(), path.height(), path.state() == 2 ? "#7bad99" : path.state() == 1 ? "#ab9060" : "#48576a");
        c.appendInline("#Canvas", "Group #Nodes { Anchor: (Full: 0); }");
        java.util.function.Function<Node, String> blocked = n -> {
            if (owned.test(n)) return "Already unlocked";
            if (townLevel < n.level()) return "Requires Town Level " + n.level();
            for (String parent : n.parents()) if (all.stream().filter(p -> p.id().equals(parent)).noneMatch(owned)) return "Unlock the connected nodes first";
            return null;
        };
        for (int i = 0; i < all.size(); i++) {
            var n = all.get(i); c.append("#Nodes", "Aetherhaven/BuildingUpgradeNode.ui"); String s = "#Nodes[" + i + "]";
            anchor(c, s, n.x(), n.y(), 174, 112); c.set(s + " #Title.Text", n.title());
            c.set(s + " #Selected.Visible", selected.equals(n.id()));
            c.set(s + " #Icon.Visible", n.icon() != null); if (n.icon() != null) c.set(s + " #Icon.AssetPath", "UI/Custom/" + n.icon());
            c.set(s + " #Item.Visible", n.item() != null); if (n.item() != null) c.set(s + " #Item.ItemId", n.item());
            boolean bought = owned.test(n); String reason = blocked.apply(n);
            boolean prerequisitesMet = n.parents().stream().allMatch(parent -> all.stream().anyMatch(other -> other.id().equals(parent) && owned.test(other)));
            c.set(s + " #LockedBackground.Visible", !bought);
            c.setObject(s + " #LockedBackground.Background", new PatchStyle().setColor(Value.of(prerequisitesMet ? "#3e392d" : "#2b3038")));
            c.set(s + " #Lock.Visible", !bought && !prerequisitesMet);
            c.set(s + " #Title.Style.TextColor", bought ? "#c5cdd5" : prerequisitesMet ? "#e8c984" : "#98a3ae");
            c.set(s + " #State.Text", bought ? "UNLOCKED" : reason == null ? "AVAILABLE" : townLevel < n.level() ? "TOWN LEVEL " + n.level() : "LOCKED");
            c.set(s + " #State.Style.TextColor", bought ? "#96d4b4" : reason == null ? "#e8c984" : "#889bad");
        }
        var n = all.stream().filter(o -> o.id().equals(selected)).findFirst().orElseThrow();
        c.clear("#Costs"); c.set("#Hint.Text", "Choose a node to inspect its benefits and requirements. Scroll to see later upgrades.");
        var board = RestaurantMenuBoardUi.mode("Restaurant", n);
        RestaurantMenuBoardUi.apply(c, board, n, state, MENU, farmAndRanchFood);
        boolean menuOnly = board == Mode.MENU;
        c.set("#Description.Visible", board == Mode.NONE);
        c.set("#Requires.Visible", !menuOnly); c.set("#Costs.Visible", !menuOnly); c.set("#Gold.Visible", !menuOnly);
        String reason = blocked.apply(n); boolean bought = owned.test(n);
        c.set("#DetailLock.Visible", !bought);
        c.set("#DetailTitle.Style.TextColor", bought ? "#e8c984" : "#acb5bf");
        c.set("#DetailTitle.Text", n.title()); c.set("#Description.Text", n.description()); c.set("#DetailState.Text", bought ? "UNLOCKED" : reason == null ? "READY TO UNLOCK" : reason);
        c.set("#DetailState.Style.TextColor", bought ? "#96d4b4" : reason == null ? "#e8c984" : "#b4becb");
        c.set("#DetailIcon.Visible", n.icon() != null); if (n.icon() != null) c.set("#DetailIcon.AssetPath", "UI/Custom/" + n.icon());
        c.set("#DetailItem.Visible", n.item() != null); if (n.item() != null) c.set("#DetailItem.ItemId", n.item());
        var parentNames = all.stream().filter(o -> n.parents().contains(o.id())).map(Node::title).toList();
        c.set("#Requires.Text", "Town Level " + n.level() + (parentNames.isEmpty() ? "" : "\nRequires: " + String.join(", ", parentNames)));
        var costs = n.kind() == Kind.RESTAURANT ? RestaurantUpgrades.ingredientCosts(RestaurantUpgrades.Branch.valueOf(n.branch()), n.tier()) : List.<RestaurantUpgrades.IngredientCost>of();
        long price = n.kind() == Kind.RESTAURANT ? RestaurantUpgrades.goldCost(RestaurantUpgrades.Branch.valueOf(n.branch()), n.tier()) : 0;
        int ci = 0;
        for (var cost : costs) {
            c.append("#Costs", "Aetherhaven/BuildingUpgradeCost.ui"); String s = "#Costs[" + ci++ + "]";
            c.set(s + " #Item.ItemId", cost.itemId()); c.set(s + " #Count.Text", held + " / " + cost.count());
        }
        boolean affordable = gold >= price && costs.stream().allMatch(cost -> held >= cost.count());
        c.set("#Gold.Text", bought ? "Purchased" : price == 0 ? "No gold required" : price + " gold\nFrom inventory or treasury");
        if (reason == null && !affordable) c.set("#DetailState.Text", "You need more gold or materials");
        c.set("#Purchase.Text", bought ? "Unlocked" : "Unlock upgrade"); c.set("#Purchase.Disabled", reason != null || n.kind() == Kind.FOUNDATION || !affordable);
        return Arrays.asList(c.getCommands());
    }

    // --- tests/hytale/restaurant.test.json ----------------------------------------------------------------------------

    /** Where the Hytale MCP finds this mod from the Hytale-Modding folder. */
    private static final String PROJECT = "Aetherhaven";
    private static final String UI = PROJECT + "/src/main/resources/Common/UI/Custom/Aetherhaven/";
    private static final Path CASES = Path.of("tests", "hytale", "restaurant.test.json");

    /** One command per line, in the shape ui_render and ui_inspect take. The opening append of the page is left out. */
    private static String commandLines(List<CustomUICommand> commands, String onlyUnder) {
        var gson = new GsonBuilder().disableHtmlEscaping().create();
        var lines = new ArrayList<String>();
        for (var command : commands) {
            if (command.type == CustomUICommandType.Append && command.selector == null) continue;
            String selector = command.selector;
            if (onlyUnder != null) {
                if (selector == null || !selector.startsWith(onlyUnder)) continue;
                selector = selector.substring(onlyUnder.length());
            }
            var row = new JsonObject();
            row.addProperty("type", command.type.name());
            if (selector != null) row.addProperty("selector", selector);
            if (command.text != null) row.addProperty("text", command.text);
            if (command.data != null) row.add("data", JsonParser.parseString(command.data));
            lines.add("      " + gson.toJson(row));
        }
        return "[\n" + String.join(",\n", lines) + "\n     ]";
    }

    private static String render(String label, String document, String extra, String commands, String golden) {
        return "   {\"tool\": \"ui_render\", \"name\": \"" + label + "\",\n    \"arguments\": {\"root\": \"projects\", \"path\": \"" + UI + document + "\"" + extra
            + ",\n     \"commands\": " + commands + "},\n    \"expect\": {\"valid\": true}, \"golden\": \"tests/hytale/golden/" + golden + "\"}";
    }

    /** The purchase button sits where the panel puts it; a board that grew too tall would push it out of the panel. */
    private static String buttonInPlace(String label, String commands) {
        return "   {\"tool\": \"ui_inspect\", \"name\": \"" + label + "\",\n    \"arguments\": {\"root\": \"projects\", \"path\": \"" + UI
            + "WorkplaceUpgradesPage.ui\", \"selector\": \"#Purchase\", \"depth\": 1,\n     \"commands\": " + commands
            + "},\n    \"expect\": {\"valid\": true, \"tree\": [{\"id\": \"Purchase\", \"rect\": [1146, 799, 316, 44]}]}}";
    }

    private static String testCase(String name, String... steps) {
        return " {\"name\": \"" + name + "\",\n  \"steps\": [\n" + String.join(",\n", steps) + "\n  ]}";
    }

    @Test void pictureCasesAreBuiltFromTheCommandsTheJavaSends() throws Exception {
        var none = restaurantTab(restaurant(0, 0, 0, 0), 2, "home", 40, 0, 20);
        var some = restaurantTab(restaurant(2, 1, 2, 1), 5, "home", 180, 12, 20);
        var maxed = restaurantTab(restaurant(3, 3, 3, 3), 6, "home", 950, 64, 30);
        var menuLevel = restaurantTab(restaurant(1, 1, 1, 0), 3, "MENU:2", 180, 5, 20);
        var provisionsLevel = restaurantTab(restaurant(3, 3, 3, 1), 6, "PROVISIONS:2", 20, 25, 20);
        for (var commands : List.of(none, some, maxed, menuLevel, provisionsLevel)) {
            assertEquals(13, commands.stream().filter(c -> c.type == CustomUICommandType.Append && "#Nodes".equals(c.selector)).count());
            assertEquals(6, commands.stream().filter(c -> c.type == CustomUICommandType.Append && "#Bands".equals(c.selector)).count(), "All six Town Level bands");
            for (var command : commands) if (command.selector != null) assertFalse(command.selector.contains("_") || command.selector.contains("-"), command.selector);
        }
        assertEquals("{\"0\": false}", data(none, "#Description.Visible"));
        assertEquals("{\"0\": false}", data(none, "#Costs.Visible"));
        assertEquals("{\"0\": true}", data(menuLevel, "#Costs.Visible"));
        assertEquals("{\"0\": false}", data(menuLevel, "#Description.Visible"));
        assertEquals("{\"0\": true}", data(provisionsLevel, "#Description.Visible"));
        assertEquals("{\"0\": false}", data(provisionsLevel, "#MenuBoard.Visible"));
        assertTrue(data(provisionsLevel, "#Description.Text").contains("Adds 5 Food Production"));

        String page = "WorkplaceUpgradesPage.ui", details = ", \"focus\": \"#UpgradeDetails\"";
        String text = "{\"cases\": [\n" + String.join(",\n",
            testCase("restaurant upgrades: documents load and the page names real ids",
                "   {\"tool\": \"asset_validate\", \"arguments\": {\"project\": \"" + PROJECT + "\"}, \"expect\": {\"schemaErrors\": 0}}",
                "   {\"tool\": \"ui_check_java\", \"arguments\": {\"project\": \"" + PROJECT + "\"}, \"expect\": {\"valid\": true, \"errors\": 0}}"),
            testCase("restaurant upgrades: new restaurant",
                render("Restaurant tab with no upgrades", page, "", commandLines(none, null), "restaurant-no-upgrades.png")),
            testCase("restaurant upgrades: some bought",
                render("Restaurant tab with some upgrades", page, "", commandLines(some, null), "restaurant-some-upgrades.png")),
            testCase("restaurant upgrades: all maxed",
                render("Restaurant tab with every branch maxed", page, "", commandLines(maxed, null), "restaurant-all-maxed.png"),
                buttonInPlace("a full menu and the provisions line still fit the details panel", commandLines(maxed, null))),
            testCase("restaurant upgrades: details of a menu level",
                render("Chef's menu 2 selected", page, details, commandLines(menuLevel, null), "restaurant-menu-level-details.png"),
                buttonInPlace("the level's dishes fit where the description was", commandLines(menuLevel, null))),
            testCase("restaurant upgrades: details of a provisions level",
                render("Town provisions 2 selected", page, details, commandLines(provisionsLevel, null), "restaurant-provisions-level-details.png")),
            testCase("restaurant upgrades: one menu line",
                render("a menu line in the details panel's width", "RestaurantMenuRow.ui", ", \"frame\": {\"width\": 316}",
                    commandLines(some, "#MenuBoardRows[0] "), "restaurant-menu-line.png"))
        ) + "\n]}\n";
        JsonParser.parseString(text);
        Path generated = Path.of("build", "restaurant-ui", "restaurant.test.json");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, text, StandardCharsets.UTF_8);
        assertTrue(Files.exists(CASES), "Copy " + generated + " to " + CASES);
        // Compared as a plain yes or no (a failed assertEquals would print both files), ignoring Windows line ends.
        String committed = Files.readString(CASES, StandardCharsets.UTF_8).replace(String.valueOf((char) 13), "");
        assertTrue(text.equals(committed),
            CASES + " no longer matches what the page sends. Copy " + generated + " over it, run the cases and look at the pictures that change.");
    }
}
