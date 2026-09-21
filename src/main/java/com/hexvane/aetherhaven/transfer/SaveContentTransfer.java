package com.hexvane.aetherhaven.transfer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.prop.PropPaths;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Copies reusable mod content only. Towns, players, installation identities and caches are never selected. */
public final class SaveContentTransfer {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    private static final Set<String> CONFIG_FILES = Set.of("config.json", "server_difficulty.json", "shop_prices.json",
        "floating_gift_loot.json", "geode_loot.json", "prop_loot_exclusions.json", "quest_board.json");
    public enum Category { PROPS, BUILDINGS, CONFIG }
    public record Bundle(Category category, String name, List<Path> files) {}
    public record Target(String name, Path save) {}
    public record Result(int copied, int skipped, int failed) {}
    private SaveContentTransfer() {}

    public static List<Target> discoverTargets(Path sourceData) throws IOException {
        Path currentSave = sourceData.toAbsolutePath().normalize().getParent().getParent();
        Set<Path> roots = new LinkedHashSet<>();
        if (currentSave.getParent() != null && currentSave.getParent().getFileName().toString().equalsIgnoreCase("Saves"))
            roots.add(currentSave.getParent());
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) roots.add(Path.of(appData, "Hytale", "UserData", "Saves"));
        Path home = Path.of(System.getProperty("user.home"));
        roots.add(home.resolve(".local/share/Hytale/UserData/Saves"));
        roots.add(home.resolve("Library/Application Support/Hytale/UserData/Saves"));
        return discoverTargets(currentSave, roots);
    }

    static List<Target> discoverTargets(Path currentSave, Collection<Path> roots) throws IOException {
        Map<Path, Target> found = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var children = Files.list(root)) {
                for (Path child : children.sorted().toList()) {
                    if (!isSave(child) || Files.isSameFile(child, currentSave)) continue;
                    Path real = child.toRealPath();
                    String name = child.getFileName().toString();
                    Path config = child.resolve("config.json");
                    if (Files.isRegularFile(config)) {
                        try {
                            JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
                            String display = string(json, "DisplayName");
                            if (!display.isBlank()) name = display + " (" + name + ")";
                        } catch (RuntimeException ignored) { }
                    }
                    found.put(real, new Target(name, real));
                }
            }
        }
        return List.copyOf(found.values());
    }

    private static boolean isSave(Path save) {
        return Files.isDirectory(save, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(save)
            && Files.isDirectory(save.resolve("universe"), LinkOption.NOFOLLOW_LINKS)
            && Files.isRegularFile(save.resolve("config.json"), LinkOption.NOFOLLOW_LINKS);
    }

    public static List<Bundle> inventory(Path data) throws IOException {
        data = data.toAbsolutePath().normalize();
        List<Bundle> result = new ArrayList<>();
        for (String prefix : List.of("", "Community/")) {
            Path root = data.resolve(prefix);
            collectDefinitions(data, root, "Server/Aetherhaven/Props", Category.PROPS, result);
            collectDefinitions(data, root, "Server/Aetherhaven/Buildings", Category.BUILDINGS, result);
            collectDefinitions(data, root, "Server/Aetherhaven/Festivals", Category.BUILDINGS, result);
        }
        for (String config : new TreeSet<>(CONFIG_FILES)) {
            Path file = checked(data, data.resolve(config));
            if (Files.isRegularFile(file)) result.add(new Bundle(Category.CONFIG, config, List.of(Path.of(config))));
        }
        Path loot = checked(data, data.resolve("shop_loot"));
        if (Files.isDirectory(loot)) try (var walk = Files.walk(loot)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                checked(data, file);
                if (Files.isRegularFile(file)) result.add(new Bundle(Category.CONFIG, file.getFileName().toString(), List.of(data.relativize(file))));
            }
        }
        return List.copyOf(result);
    }

    private static void collectDefinitions(Path data, Path root, String folder, Category category, List<Bundle> out) throws IOException {
        Path dir = checked(data, root.resolve(folder));
        if (!Files.isDirectory(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path definition : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                if (definition.startsWith(dir.resolve("PrefabMaterials"))) continue;
                checked(data, definition);
                if (!Files.isRegularFile(definition)) continue;
                JsonObject json;
                try { json = JsonParser.parseString(Files.readString(definition)).getAsJsonObject(); }
                catch (RuntimeException e) { throw new IOException("Cannot read content: " + definition.getFileName(), e); }
                String id = string(json, "id");
                if (id.isBlank()) continue;
                if (!id.matches("[A-Za-z0-9_-]+")) throw new IOException("Invalid content id: " + id);
                Set<Path> files = new LinkedHashSet<>();
                files.add(data.relativize(definition));
                collectReferences(data, root, json, files);
                for (Path assetRoot : List.of(root, data.resolve("Community"), data)) {
                    addIfPresent(data, category == Category.PROPS ? PropPaths.iconFile(assetRoot, id)
                        : CustomBuildingsPaths.iconFile(assetRoot, id), files);
                    addIfPresent(data, assetRoot.resolve("Server/Aetherhaven/Buildings/PrefabMaterials/" + id + ".json"), files);
                }
                addIfPresent(data, data.resolve("Community/.install-meta/" + id + ".json"), files);
                out.add(new Bundle(category, id, List.copyOf(files)));
            }
        }
    }

    private static void collectReferences(Path data, Path root, JsonElement node, Set<Path> files) throws IOException {
        if (node.isJsonObject()) {
            for (var field : node.getAsJsonObject().entrySet()) {
                if (field.getValue().isJsonPrimitive() && field.getValue().getAsJsonPrimitive().isString()) {
                    String key = field.getKey().toLowerCase(Locale.ROOT);
                    String value = field.getValue().getAsString().replace('\\', '/');
                    if (key.contains("prefab") && value.endsWith(".prefab.json")) {
                        if (value.startsWith("Server/Prefabs/")) value = value.substring("Server/Prefabs/".length());
                        addReference(data, root, "Server/Prefabs/", value, files);
                    } else if (key.contains("icon") && value.endsWith(".png")) {
                        if (value.startsWith("Common/")) value = value.substring("Common/".length());
                        addReference(data, root, "Common/", value, files);
                    }
                } else collectReferences(data, root, field.getValue(), files);
            }
        } else if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) collectReferences(data, root, child, files);
        }
    }

    private static void addReference(Path data, Path root, String folder, String key, Set<Path> files) throws IOException {
        Path relative = Path.of(key);
        if (relative.isAbsolute() || key.contains(":") || key.contains("..")) throw new IOException("Invalid asset path");
        // Only files in this mod's data folder are copied. Base game and external mod assets remain external dependencies.
        for (Path candidateRoot : List.of(root, data, data.resolve("Community"))) {
            Path file = checked(data, candidateRoot.resolve(folder).resolve(relative));
            if (Files.isRegularFile(file)) { files.add(data.relativize(file)); return; }
        }
    }

    private static void addIfPresent(Path data, Path file, Set<Path> files) throws IOException {
        checked(data, file);
        if (Files.isRegularFile(file)) files.add(data.relativize(file));
    }

    /** Reject links and path escapes, including an existing destination directory that is a junction. */
    static Path checked(Path root, Path file) throws IOException {
        root = root.toAbsolutePath().normalize();
        file = file.toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IOException("Path leaves the mod data folder");
        Path cursor = root;
        for (Path part : root.relativize(file)) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Linked files cannot be transferred");
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && !cursor.toRealPath().startsWith(root.toRealPath()))
                throw new IOException("Linked folders cannot be transferred");
        }
        return file;
    }

    public static synchronized Result transfer(Path source, Target target, Set<Category> categories, boolean overwrite) throws IOException {
        source = source.toAbsolutePath().normalize();
        Path sourceSave = source.getParent().getParent();
        if (!isSave(target.save()) || Files.isSameFile(sourceSave, target.save())) throw new IOException("Choose another local save");
        Path destination = checked(target.save(), target.save().resolve("mods").resolve(source.getFileName()));
        // Build and validate the full inventory before writing anything.
        List<Bundle> selected = inventory(source).stream().filter(b -> categories.contains(b.category())).toList();
        Files.createDirectories(destination);
        int copied = 0, skipped = 0, failed = 0;
        for (Bundle bundle : selected) {
            boolean conflict = false;
            for (Path relative : bundle.files()) {
                Path file = checked(destination, destination.resolve(relative));
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(file) || Files.mismatch(checked(source, source.resolve(relative)), file) != -1)) conflict = true;
            }
            if (conflict && !overwrite) { skipped++; continue; }
            try { copyBundle(source, destination, bundle, overwrite); copied++; }
            catch (IOException e) {
                failed++;
                LOGGER.atWarning().withCause(e).log("Could not transfer %s to %s", bundle.name(), destination);
            }
        }
        return new Result(copied, skipped, failed);
    }

    private static void copyBundle(Path source, Path destination, Bundle bundle, boolean overwrite) throws IOException {
        Path staging = Files.createTempDirectory(destination, ".transfer-");
        List<Integer> committed = new ArrayList<>();
        boolean cleanup = true;
        try {
            int index = 0;
            for (Path relative : bundle.files()) {
                Path src = checked(source, source.resolve(relative));
                Path dest = checked(destination, destination.resolve(relative));
                Files.copy(src, staging.resolve("new" + index));
                if (Files.exists(dest)) Files.copy(dest, staging.resolve("old" + index));
                index++;
            }
            index = 0;
            for (Path relative : bundle.files()) {
                Path dest = checked(destination, destination.resolve(relative));
                Files.createDirectories(dest.getParent());
                int fileIndex = index++;
                Path staged = staging.resolve("new" + fileIndex);
                if (!overwrite && Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(dest) || Files.mismatch(staged, dest) != -1)
                        throw new IOException("A destination file changed while copying");
                    continue;
                }
                if (overwrite) Files.move(staged, dest, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(staged, dest);
                committed.add(fileIndex);
            }
        } catch (IOException error) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                try {
                    Path dest = checked(destination, destination.resolve(bundle.files().get(committed.get(i))));
                    Path old = staging.resolve("old" + committed.get(i));
                    if (Files.exists(old)) Files.move(old, dest, StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(dest);
                } catch (IOException rollbackError) {
                    cleanup = false; // Preserve backups for manual recovery if the disk becomes unavailable.
                    error.addSuppressed(rollbackError);
                    LOGGER.atWarning().withCause(rollbackError).log("Transfer recovery files retained in %s", staging);
                }
            }
            throw error;
        } finally {
            if (cleanup) {
                try (var files = Files.list(staging)) {
                    for (Path file : files.toList()) Files.deleteIfExists(file);
                }
                Files.deleteIfExists(staging);
            }
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
