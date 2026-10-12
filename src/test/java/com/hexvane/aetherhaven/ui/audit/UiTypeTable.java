package com.hexvane.aetherhaven.ui.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.Library;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.Node;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.RawDocument;
import com.hexvane.aetherhaven.ui.audit.UiMarkup.RawElement;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Which properties each Custom UI element type has, and the game's own documents the mod's pages build on.
 *
 * <p>Both are read from two files kept beside the tests ({@code src/test/resources/customui}) so the audit runs on a
 * machine with no game installed:
 * <ul>
 * <li>{@code element-properties.json}: per element type, each property with where it is known from: the official
 * type documentation, a document shipped with the game that writes it, a page of the game's own server that sets it,
 * and whether the client program has a member of that name.</li>
 * <li>{@code vanilla-documents.json}: the element structure (types, ids, templates, no values) of the game documents
 * the mod imports or appends, {@code Common.ui} first of all.</li>
 * </ul>
 * {@link #generate} rebuilds both from an installed game. Run it after every game update.
 */
public final class UiTypeTable {
    public static final String FOLDER = "src/test/resources/customui";
    public static final String PROPERTIES = FOLDER + "/element-properties.json";
    public static final String DOCUMENTATION = FOLDER + "/official-type-documentation.json";
    public static final String VANILLA = FOLDER + "/vanilla-documents.json";

    public enum Answer { YES, NO, UNKNOWN }

    public record Verdict(Answer answer, String why) {}

    private final JsonObject root;
    private final JsonObject common, elements, dataTypes;

    private UiTypeTable(JsonObject root) {
        this.root = root;
        common = root.getAsJsonObject("common");
        elements = root.getAsJsonObject("elements");
        dataTypes = root.getAsJsonObject("dataTypes");
    }

    public static UiTypeTable load(Path project) throws IOException {
        return new UiTypeTable(JsonParser.parseString(Files.readString(project.resolve(PROPERTIES), StandardCharsets.UTF_8)).getAsJsonObject());
    }

    public String gameVersion() { return root.getAsJsonObject("game").get("version").getAsString(); }
    public String patchline() { return root.getAsJsonObject("game").get("patchline").getAsString(); }
    public JsonObject sources() { return root.getAsJsonObject("sources"); }

    /**
     * The installed game of a patchline ("release", "pre-release"): the folder with {@code Assets.zip}, {@code Client}
     * and {@code Server}, or null when this machine has none. The environment variable AETHERHAVEN_HYTALE_GAME names
     * another folder.
     */
    public static Path installedGame(String patchline) {
        String chosen = System.getenv("AETHERHAVEN_HYTALE_GAME"), data = System.getenv("APPDATA");
        Path game = chosen != null ? Path.of(chosen) : data == null ? null : Path.of(data, "Hytale", "install", patchline, "package", "game", "latest");
        return game != null && Files.isRegularFile(game.resolve("Assets.zip")) && Files.isRegularFile(game.resolve("Server/HytaleServer.jar"))
            && Files.isRegularFile(game.resolve("Client/HytaleClient.exe")) ? game : null;
    }

    /** The version the installed game's server says it is. */
    public static String installedVersion(Path game) throws IOException {
        try (var jar = new JarFile(game.resolve("Server/HytaleServer.jar").toFile())) {
            return String.valueOf(jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
    }

    /** Rebuilds both files for this mod: the game documents kept are the ones its documents import and its Java names. */
    public static String regenerate(Path game, Path project) throws IOException {
        var mod = new Library();
        mod.addFolder(project.resolve("src/main/resources/Common/UI/Custom"));
        var packs = project.resolve("subplugin-assets");
        if (Files.isDirectory(packs)) {
            try (var list = Files.list(packs)) {
                for (var pack : list.sorted().toList()) mod.addFolder(pack.resolve("Common/UI/Custom"));
            }
        }
        var wanted = new TreeSet<String>();
        for (String path : mod.paths()) wanted.addAll(mod.get(path).imports.values());
        wanted.addAll(JavaUiScanner.read(project.resolve("src/main/java")).documentsNamed());
        wanted.removeIf(mod::has);
        return generate(game, project, wanted);
    }
    public boolean knowsElement(String type) { return elements.has(type); }

    /** "yes", "no" or "?": whether documents may be appended below an element of this type. */
    public String children(String type) {
        var element = elements.getAsJsonObject(type);
        return element == null ? "?" : element.get("children").getAsString();
    }

    private JsonObject dataType(String typeName) {
        if (typeName == null) return null;
        for (String word : typeName.split("[^A-Za-z0-9]+")) if (dataTypes.has(word)) return dataTypes.getAsJsonObject(word);
        return null;
    }

    private static String names(JsonObject object) { return String.join(", ", new TreeSet<>(object.keySet())); }

    /** Whether an element of {@code type} has the property a selector names after its dot. */
    public Verdict property(String type, List<String> path) {
        String first = path.getFirst();
        var element = elements.getAsJsonObject(type);
        JsonObject info = common.getAsJsonObject(first);
        if (info == null && element != null) info = element.getAsJsonObject("properties").getAsJsonObject(first);
        if (info == null) {
            if (element == null) return new Verdict(Answer.UNKNOWN, "the element type " + type + " is not in the table");
            if (!element.get("documented").getAsBoolean())
                return new Verdict(Answer.UNKNOWN, type + " is not in the official documentation, and no game document or game page uses " + type + "." + first);
            // A property the documentation lists nowhere is only known where the game happens to use it.
            for (var other : elements.entrySet()) {
                var same = other.getValue().getAsJsonObject().getAsJsonObject("properties").getAsJsonObject(first);
                if (same != null && !same.get("docs").getAsBoolean())
                    return new Verdict(Answer.UNKNOWN, first + " is not in the official documentation; the game uses it on a " + other.getKey() + ", and nothing shows whether a " + type + " has it");
            }
            return new Verdict(Answer.NO, ("AEIOU".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type + " has no property " + first + " (it has: " + names(element.getAsJsonObject("properties")) + ", and what every element has)");
        }
        if (path.size() == 1) return new Verdict(Answer.YES, "");
        if (first.equals("Anchor")) return new Verdict(Answer.NO, "Anchor cannot be addressed by field; send the whole Anchor");
        String typeName = info.get("type").getAsString(), walked = type + "." + first;
        for (String field : path.subList(1, path.size())) {
            var data = dataType(typeName);
            if (data == null) return new Verdict(Answer.UNKNOWN, "the fields of " + walked + " (" + typeName + ") are not in the table");
            var next = data.getAsJsonObject(field);
            if (next == null) return new Verdict(Answer.NO, walked + " (" + typeName + ") has no field " + field + " (it has: " + names(data) + ")");
            typeName = next.get("type").getAsString();
            walked += "." + field;
        }
        return new Verdict(Answer.YES, "");
    }

    // The game's own documents -------------------------------------------------------------------------------------

    private static JsonObject write(RawElement e) {
        var o = new JsonObject();
        if (e.type != null) o.addProperty("type", e.type);
        if (e.alias != null) o.addProperty("alias", e.alias);
        if (e.template != null) o.addProperty("template", e.template);
        if (e.id != null) o.addProperty("id", e.id);
        if (e.override) o.addProperty("override", true);
        o.addProperty("line", e.line);
        if (!e.templates.isEmpty()) {
            var t = new JsonObject();
            e.templates.forEach((name, element) -> t.add(name, write(element)));
            o.add("templates", t);
        }
        if (!e.references.isEmpty()) {
            var r = new JsonObject();
            e.references.forEach((name, to) -> r.addProperty(name, (to[0] == null ? "" : to[0]) + "." + to[1]));
            o.add("references", r);
        }
        if (!e.children.isEmpty()) {
            var c = new JsonArray();
            for (var child : e.children) c.add(write(child));
            o.add("children", c);
        }
        return o;
    }

    private static RawElement read(JsonObject o) {
        var e = new RawElement();
        if (o.has("type")) e.type = o.get("type").getAsString();
        if (o.has("alias")) e.alias = o.get("alias").getAsString();
        if (o.has("template")) e.template = o.get("template").getAsString();
        if (o.has("id")) e.id = o.get("id").getAsString();
        e.override = o.has("override");
        e.line = o.get("line").getAsInt();
        if (o.has("templates")) for (var t : o.getAsJsonObject("templates").entrySet()) e.templates.put(t.getKey(), read(t.getValue().getAsJsonObject()));
        if (o.has("references")) for (var r : o.getAsJsonObject("references").entrySet()) {
            String[] to = r.getValue().getAsString().split("\\.", 2);
            e.references.put(r.getKey(), new String[] {to[0].isEmpty() ? null : to[0], to[1]});
        }
        if (o.has("children")) for (var c : o.getAsJsonArray("children")) e.children.add(read(c.getAsJsonObject()));
        return e;
    }

    /** Adds the game's documents kept in {@code vanilla-documents.json} to a library that already holds the mod's. */
    public static void addVanilla(Library library, Path project) throws IOException {
        var file = JsonParser.parseString(Files.readString(project.resolve(VANILLA), StandardCharsets.UTF_8)).getAsJsonObject();
        for (var entry : file.getAsJsonObject("documents").entrySet()) {
            var o = entry.getValue().getAsJsonObject();
            var document = new RawDocument();
            document.path = entry.getKey();
            document.source = "game:" + entry.getKey();
            for (var i : o.getAsJsonObject("imports").entrySet()) document.imports.put(i.getKey(), i.getValue().getAsString());
            var top = read(o.getAsJsonObject("top"));
            document.top.templates.putAll(top.templates);
            document.top.references.putAll(top.references);
            document.top.children.addAll(top.children);
            library.add(document);
        }
    }

    // Rebuilding both files from an installed game ------------------------------------------------------------------

    /** {@code #Id.Property} with the id straight before the dot: the property is that element's, not a row's ({@code #List[2].Text}). */
    private static final Pattern SET = Pattern.compile("#([A-Za-z][A-Za-z0-9]*)\\.([A-Z][A-Za-z]+)");

    private static void collect(Node node, Map<String, Map<String, Integer>> written, Set<String> parents, Map<String, Set<String>> ids) {
        if (!node.type.equals("?")) {
            var mine = written.computeIfAbsent(node.type, k -> new TreeMap<>());
            for (String property : node.written) mine.merge(property, 1, Integer::sum);
            if (!node.children.isEmpty()) parents.add(node.type);
            if (node.id != null && ids != null) ids.computeIfAbsent(node.id, k -> new TreeSet<>()).add(node.type);
        }
        for (var child : node.children) collect(child, written, parents, ids);
    }

    private static Library read(Path folder) throws IOException {
        var library = new Library();
        library.addFolder(folder);
        return library;
    }

    private static JsonObject evidence(JsonObject properties, String name, String type) {
        var info = properties.getAsJsonObject(name);
        if (info == null) {
            info = new JsonObject();
            info.addProperty("type", type);
            info.addProperty("docs", false);
            properties.add(name, info);
        }
        return info;
    }

    /**
     * Rebuilds {@code element-properties.json} and {@code vanilla-documents.json}.
     *
     * @param game the installed game: the folder that holds {@code Assets.zip}, {@code Client} and {@code Server}
     * @param project the mod's root
     * @param wanted game documents the mod's Java names, beside those its documents import
     * @return a few lines saying what was read
     */
    public static String generate(Path game, Path project, Set<String> wanted) throws IOException {
        var documentation = JsonParser.parseString(Files.readString(project.resolve(DOCUMENTATION), StandardCharsets.UTF_8)).getAsJsonObject();
        var common = documentation.getAsJsonObject("common").deepCopy();
        var elements = documentation.getAsJsonObject("elements").deepCopy();
        for (var e : common.entrySet()) e.getValue().getAsJsonObject().addProperty("docs", true);
        for (var e : elements.entrySet()) {
            var properties = e.getValue().getAsJsonObject().getAsJsonObject("properties");
            for (var p : properties.entrySet()) {
                var info = p.getValue().getAsJsonObject();
                info.addProperty("docs", info.remove("documented").getAsBoolean());
            }
        }
        for (var e : common.entrySet()) e.getValue().getAsJsonObject().remove("documented");

        // 1. Every document the game ships: what is written on which element type.
        var custom = new Library();
        int customCount = 0;
        try (var zip = new ZipFile(game.resolve("Assets.zip").toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("Common/UI/Custom/") || !name.endsWith(".ui")) continue;
                String path = name.substring("Common/UI/Custom/".length());
                custom.add(UiMarkup.parse(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8), path, "Assets.zip!" + name));
                customCount++;
            }
        }
        var problems = new ArrayList<String>();
        var game1 = read(game.resolve("Client/Data/Game/Interface"));
        var editor = read(game.resolve("Client/Data/Editor/Interface"));
        Map<String, Map<String, Integer>> written = new TreeMap<>();
        Set<String> parents = new TreeSet<>();
        int documents = 0;
        for (var library : List.of(custom, game1, editor)) {
            for (String path : library.paths()) {
                documents++;
                for (var node : library.roots(path)) collect(node, written, parents, null);
                for (var node : library.templates(path).values()) collect(node, written, parents, null);
            }
            problems.addAll(library.problems);
        }

        // 2. The game's own server pages: which property they set on which element type.
        Map<String, Map<String, Integer>> sets = new TreeMap<>();
        int classes = 0;
        String version = "?", revision = "?", patchline = "?";
        try (var jar = new JarFile(game.resolve("Server/HytaleServer.jar").toFile())) {
            var manifest = jar.getManifest().getMainAttributes();
            version = String.valueOf(manifest.getValue("Implementation-Version"));
            revision = String.valueOf(manifest.getValue("Implementation-Revision-Id"));
            patchline = String.valueOf(manifest.getValue("Implementation-Patchline"));
            Map<String, Set<String>> strings = new TreeMap<>();
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("com/hypixel/hytale/") || !name.endsWith(".class")) continue;
                String outer = name.substring(0, name.length() - 6);
                int dollar = outer.indexOf('$');
                if (dollar > 0) outer = outer.substring(0, dollar);
                var mine = strings.computeIfAbsent(outer, k -> new HashSet<>());
                for (var constant : ClassFile.of().parse(jar.getInputStream(entry).readAllBytes()).constantPool()) {
                    if (constant instanceof Utf8Entry text && (text.stringValue().contains("#") || text.stringValue().endsWith(".ui"))) mine.add(text.stringValue());
                }
            }
            for (var group : strings.entrySet()) {
                Map<String, Set<String>> ids = new TreeMap<>();
                boolean any = false;
                for (String constant : group.getValue()) {
                    if (!constant.endsWith(".ui") || !custom.has(constant)) continue;
                    any = true;
                    for (var node : custom.roots(constant)) collect(node, new TreeMap<>(), new TreeSet<>(), ids);
                }
                if (!any) continue;
                classes++;
                for (String constant : group.getValue()) {
                    var match = SET.matcher(constant.replace('\u0001', '0').replace('\u0002', '0'));
                    while (match.find()) {
                        var types = ids.get(match.group(1));
                        if (types == null || types.size() != 1) continue;
                        sets.computeIfAbsent(types.iterator().next(), k -> new TreeMap<>()).merge(match.group(2), 1, Integer::sum);
                    }
                }
            }
        }

        // 3. Merge the game's evidence into the documented lists.
        for (var type : written.entrySet()) {
            if (!elements.has(type.getKey())) {
                var fresh = new JsonObject();
                fresh.addProperty("documented", false);
                fresh.addProperty("children", "?");
                fresh.add("properties", new JsonObject());
                fresh.add("events", new JsonArray());
                elements.add(type.getKey(), fresh);
            }
        }
        // A property the documentation does not list for a type, written on three or more types, belongs to every element.
        Map<String, Set<String>> undocumentedOn = new TreeMap<>();
        for (var type : written.entrySet()) for (String property : type.getValue().keySet()) {
            if (common.has(property) || elements.getAsJsonObject(type.getKey()).getAsJsonObject("properties").has(property)) continue;
            undocumentedOn.computeIfAbsent(property, k -> new TreeSet<>()).add(type.getKey());
        }
        for (var e : undocumentedOn.entrySet()) if (e.getValue().size() >= 3) evidence(common, e.getKey(), "?").addProperty("onTypes", String.join(", ", e.getValue()));
        for (var type : written.entrySet()) {
            var properties = elements.getAsJsonObject(type.getKey()).getAsJsonObject("properties");
            for (var p : type.getValue().entrySet()) {
                var info = common.has(p.getKey()) ? common.getAsJsonObject(p.getKey()) : evidence(properties, p.getKey(), "?");
                info.addProperty("files", (info.has("files") ? info.get("files").getAsInt() : 0) + p.getValue());
            }
        }
        for (var type : sets.entrySet()) {
            if (!elements.has(type.getKey())) continue;
            var properties = elements.getAsJsonObject(type.getKey()).getAsJsonObject("properties");
            for (var p : type.getValue().entrySet()) {
                var info = common.has(p.getKey()) ? common.getAsJsonObject(p.getKey()) : evidence(properties, p.getKey(), "?");
                info.addProperty("server", (info.has("server") ? info.get("server").getAsInt() : 0) + p.getValue());
            }
        }
        for (String type : parents) {
            var element = elements.getAsJsonObject(type);
            if (!element.get("children").getAsString().equals("yes")) {
                element.addProperty("childrenDocumented", element.get("children").getAsString());
                element.addProperty("children", "yes");
            }
        }

        // 4. Does the client program have a member of each name?
        Set<String> names = new HashSet<>(common.keySet());
        for (var e : elements.entrySet()) { names.add(e.getKey()); names.addAll(e.getValue().getAsJsonObject().getAsJsonObject("properties").keySet()); }
        Set<String> inClient = new HashSet<>();
        byte[] client = Files.readAllBytes(game.resolve("Client/HytaleClient.exe"));
        for (int i = 0; i + 1 < client.length; i++) {
            int prefix = client[i] & 0xff;
            if (prefix < 4 || prefix > 126 || (prefix & 1) != 0) continue;
            int length = prefix >> 1;
            if (i + 1 + length > client.length) continue;
            int first = client[i + 1];
            if (first < 'A' || first > 'Z') continue;
            boolean word = true;
            for (int k = 1; k <= length && word; k++) {
                int c = client[i + k];
                word = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
            }
            if (!word) continue;
            String name = new String(client, i + 1, length, StandardCharsets.US_ASCII);
            if (names.contains(name)) inClient.add(name);
        }
        for (var e : common.entrySet()) e.getValue().getAsJsonObject().addProperty("client", inClient.contains(e.getKey()));
        var missing = new ArrayList<String>();
        for (var e : elements.entrySet()) {
            var element = e.getValue().getAsJsonObject();
            element.addProperty("client", inClient.contains(e.getKey()));
            for (var p : element.getAsJsonObject("properties").entrySet()) {
                p.getValue().getAsJsonObject().addProperty("client", inClient.contains(p.getKey()));
                if (!inClient.contains(p.getKey())) missing.add(e.getKey() + "." + p.getKey());
            }
        }

        var out = new JsonObject();
        out.addProperty("about", "Which properties each Custom UI element type has. Written by UiTypeTable.generate; do not edit by hand. "
            + "Run the tests once with AETHERHAVEN_WRITE_UI_TABLE set after a game update.");
        var gameInfo = new JsonObject();
        gameInfo.addProperty("version", version);
        gameInfo.addProperty("patchline", patchline);
        gameInfo.addProperty("revision", revision);
        out.add("game", gameInfo);
        var source = new JsonObject();
        source.addProperty("docs", documentation.get("about").getAsString());
        source.addProperty("files", documents + " documents shipped with the game: " + customCount + " in Assets.zip Common/UI/Custom, "
            + game1.paths().size() + " in Client/Data/Game/Interface, " + editor.paths().size() + " in Client/Data/Editor/Interface. "
            + "A number is how many elements of that type have the property written on them, templates resolved. The client refuses a document that writes an unknown property, so each is proof.");
        source.addProperty("server", classes + " classes of Server/HytaleServer.jar that name a game document: each text constant shaped like #Id.Property, "
            + "counted for the element type #Id has in the documents that class names, when that is one type only.");
        source.addProperty("client", "Client/HytaleClient.exe: true when the program's member names include exactly this name. It says the name exists, not on which type.");
        out.add("sources", source);
        out.add("common", common);
        out.add("elements", elements);
        out.add("dataTypes", documentation.getAsJsonObject("dataTypes"));
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(project.resolve(PROPERTIES), gson.toJson(out).replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);

        // 5. The game documents the mod builds on, with everything they import.
        var keep = new TreeSet<String>();
        var queue = new ArrayList<>(wanted);
        while (!queue.isEmpty()) {
            String path = queue.removeLast();
            var document = custom.get(path);
            if (document == null || !keep.add(path)) continue;
            queue.addAll(document.imports.values());
        }
        var kept = new JsonObject();
        for (String path : keep) {
            var document = custom.get(path);
            var o = new JsonObject();
            var imports = new JsonObject();
            document.imports.forEach(imports::addProperty);
            o.add("imports", imports);
            o.add("top", write(document.top));
            kept.add(path, o);
        }
        var vanilla = new JsonObject();
        vanilla.addProperty("about", "The element structure (types, ids, templates; no values) of the game documents the mod imports or appends. "
            + "Written by UiTypeTable.generate from Assets.zip of game " + version + "; do not edit by hand.");
        vanilla.add("documents", kept);
        Files.writeString(project.resolve(VANILLA), new GsonBuilder().disableHtmlEscaping().create().toJson(vanilla) + "\n", StandardCharsets.UTF_8);

        var summary = new StringBuilder();
        summary.append("game ").append(version).append(" (").append(patchline).append(")\n");
        summary.append(documents).append(" game documents read, ").append(written.size()).append(" element types written in them\n");
        summary.append(classes).append(" server classes with pages; properties they set: ").append(sets).append("\n");
        summary.append("kept game documents: ").append(keep).append("\n");
        summary.append("undocumented, written on three or more types (taken as common): ");
        for (var e : undocumentedOn.entrySet()) if (e.getValue().size() >= 3) summary.append(e.getKey()).append(' ');
        summary.append("\nproperties whose name the client program does not have: ").append(missing).append("\n");
        summary.append("template problems in game documents: ").append(problems.size()).append("\n");
        for (String problem : problems) summary.append("  ").append(problem).append("\n");
        return summary.toString();
    }

    /** For messages: every element type with the properties known for it. */
    public Map<String, Set<String>> listing() {
        var out = new TreeMap<String, Set<String>>();
        for (var e : elements.entrySet()) out.put(e.getKey(), new TreeSet<>(e.getValue().getAsJsonObject().getAsJsonObject("properties").keySet()));
        return out;
    }

    public Set<String> commonProperties() { return new TreeSet<>(common.keySet()); }
}
