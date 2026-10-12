package com.hexvane.aetherhaven.testsupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Every item id the game and this mod define, and where each item can come from. Read from the installed game's assets
 * and from this checkout, for data audits only.
 *
 * <p>An item counts as obtainable when the data shows at least one source: a crafting recipe, a drop list, a block that
 * gives it when gathered, a native trader, one of the mod's shops or loot tables, or town production.
 */
public final class GameItems {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern ITEM_ID = Pattern.compile("\"ItemId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MOD_ITEM_ID = Pattern.compile("\"itemId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GENERATED_RECIPE = Pattern.compile("^(.*)_Recipe_Generated_\\d+$");
    private static GameItems cached;
    private static boolean tried;

    private final Map<String, JsonObject> items = new HashMap<>();
    private final Set<String> modItems = new HashSet<>();
    private final Map<String, Set<String>> sources = new HashMap<>();

    private GameItems() {}

    /** The installed game this mod targets, or null when it is not on this machine. */
    public static Path installedAssets() {
        String appData = System.getenv("APPDATA");
        if (appData == null) return null;
        Path assets = Path.of(appData, "Hytale/install/pre-release/package/game/latest/Assets.zip");
        return Files.isRegularFile(assets) ? assets : null;
    }

    /** Null when the game is not installed here. Callers skip their check in that case. */
    public static synchronized GameItems loadOrNull() throws IOException {
        if (tried) return cached;
        tried = true;
        Path assets = installedAssets();
        if (assets == null) return null;
        var index = new GameItems();
        index.readGame(assets);
        index.readMod();
        index.findRecipes();
        cached = index;
        return cached;
    }

    public boolean exists(String id) { return items.containsKey(id); }
    public boolean fromThisMod(String id) { return modItems.contains(id); }
    /** Where the item can come from. Empty when the data shows no way to get it. */
    public Set<String> sources(String id) { return sources.getOrDefault(id, Set.of()); }
    public boolean obtainable(String id) { return exists(id) && !sources(id).isEmpty(); }

    private void source(String id, String kind) {
        if (id != null && !id.isBlank()) sources.computeIfAbsent(id, ignored -> new TreeSet<>()).add(kind);
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = JsonParser.parseString(text.startsWith("﻿") ? text.substring(1) : text);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException unreadable) {
            // The id still exists in the game even when this reader cannot follow the file.
            return new JsonObject();
        }
    }

    private static String stem(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - ".json".length());
    }

    private void readGame(Path assets) throws IOException {
        try (var zip = new ZipFile(assets.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".json")) continue;
                boolean item = name.startsWith("Server/Item/Items/");
                boolean drops = name.startsWith("Server/Drops/");
                boolean barter = name.startsWith("Server/BarterShops/");
                if (!item && !drops && !barter) continue;
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                if (item) {
                    addItem(stem(name), parse(text));
                } else {
                    var matcher = ITEM_ID.matcher(text);
                    while (matcher.find()) source(matcher.group(1), drops ? "drop" : "barter");
                }
            }
        }
    }

    private void addItem(String id, JsonObject data) {
        items.put(id, data);
        if (data.has("BlockType")) {
            var matcher = ITEM_ID.matcher(data.get("BlockType").toString());
            while (matcher.find()) source(matcher.group(1), "gather");
        }
    }

    private void readMod() throws IOException {
        var packs = new java.util.ArrayList<Path>();
        packs.add(RESOURCES);
        Path optional = Path.of("subplugin-assets");
        if (Files.isDirectory(optional)) try (var list = Files.list(optional)) { list.filter(Files::isDirectory).sorted().forEach(packs::add); }
        for (Path pack : packs) {
            Path itemRoot = pack.resolve("Server/Item/Items");
            if (Files.isDirectory(itemRoot)) try (var walk = Files.walk(itemRoot)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String id = stem(file.toString().replace('\\', '/'));
                    modItems.add(id);
                    addItem(id, parse(Files.readString(file)));
                }
            }
            // The mod's own traders and drop lists, such as the festival stalls.
            for (String[] folder : new String[][] {{"Server/BarterShops", "barter"}, {"Server/Drops", "drop"}}) {
                Path root = pack.resolve(folder[0]);
                if (Files.isDirectory(root)) try (var walk = Files.walk(root)) {
                    for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                        var matcher = ITEM_ID.matcher(Files.readString(file));
                        while (matcher.find()) source(matcher.group(1), folder[1]);
                    }
                }
            }
            Path recipes = pack.resolve("Server/Aetherhaven/WorkplaceRecipes");
            if (Files.isDirectory(recipes)) try (var list = Files.list(recipes)) {
                for (Path file : list.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var recipe = parse(Files.readString(file));
                    if (recipe.has("output") && !recipe.get("output").isJsonNull()) source(recipe.get("output").getAsString(), "production");
                    else if (recipe.has("sourceRecipe")) {
                        var generated = GENERATED_RECIPE.matcher(recipe.get("sourceRecipe").getAsString());
                        if (generated.matches()) source(generated.group(1), "production");
                    }
                }
            }
        }
        Path defaults = RESOURCES.resolve("defaults");
        try (var list = Files.list(defaults.resolve("shop_loot"))) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).toList()) {
                var matcher = MOD_ITEM_ID.matcher(Files.readString(file));
                while (matcher.find()) source(matcher.group(1), "shop");
            }
        }
        try (var list = Files.list(defaults)) {
            for (Path file : list.filter(p -> p.getFileName().toString().endsWith("_loot.json")).toList()) {
                var matcher = MOD_ITEM_ID.matcher(Files.readString(file));
                while (matcher.find()) source(matcher.group(1), "loot");
            }
        }
        for (String name : new String[] {"production_catalog.json", "workplace_unlocks.json"}) {
            var matcher = MOD_ITEM_ID.matcher(Files.readString(RESOURCES.resolve("Server/Aetherhaven/Production/" + name)));
            while (matcher.find()) source(matcher.group(1), "production");
        }
    }

    private void findRecipes() {
        for (String id : items.keySet()) {
            String current = id;
            for (int depth = 0; depth < 8 && current != null; depth++) {
                JsonObject data = items.get(current);
                if (data == null) break;
                var recipe = data.get("Recipe");
                if (recipe != null && recipe.isJsonObject()) {
                    var input = recipe.getAsJsonObject().get("Input");
                    if (input != null && input.isJsonArray() && !input.getAsJsonArray().isEmpty()) source(id, "recipe");
                    break;
                }
                var parent = data.get("Parent");
                current = parent != null && parent.isJsonPrimitive() ? parent.getAsString() : null;
            }
        }
    }
}
