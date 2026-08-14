package com.hexvane.aetherhaven.prop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

/** Plugin-data list of prop ids that must not appear in floating gifts or world loot chests. */
public final class PropLootExclusions {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String FILE_NAME = "prop_loot_exclusions.json";
    private static final String DEFAULT_RESOURCE = "/defaults/prop_loot_exclusions.json";

    private PropLootExclusions() {}

    @Nonnull
    public static Path path(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getDataDirectory().resolve(FILE_NAME);
    }

    @Nonnull
    public static String readDefaultJson() throws IOException {
        try (InputStream in = PropLootExclusions.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return "{\"excludedPropIds\":[]}";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Writes the bundled default next to {@code config.json} if the file is missing. Does not overwrite edits. */
    public static void ensureDefaultFile(@Nonnull AetherhavenPlugin plugin) {
        Path file = path(plugin);
        if (Files.isRegularFile(file)) {
            return;
        }
        try {
            Files.createDirectories(plugin.getDataDirectory());
            Files.writeString(file, readDefaultJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write default %s", FILE_NAME);
        }
    }

    @Nonnull
    public static Set<String> load(@Nonnull AetherhavenPlugin plugin) {
        ensureDefaultFile(plugin);
        try {
            Path file = path(plugin);
            if (Files.isRegularFile(file)) {
                return parseJson(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to read %s; using bundled default", FILE_NAME);
        }
        try {
            return parseJson(readDefaultJson());
        } catch (IOException e) {
            return Set.of();
        }
    }

    @Nonnull
    public static Set<String> parseJson(@Nonnull String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return Set.of();
        }
        if (!root.isJsonObject()) {
            return Set.of();
        }
        JsonObject obj = root.getAsJsonObject();
        JsonArray arr = obj.getAsJsonArray("excludedPropIds");
        if (arr == null) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                continue;
            }
            String id = el.getAsString();
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        return Collections.unmodifiableSet(ids);
    }
}
