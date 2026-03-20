package com.hexvane.aetherhaven.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String MANIFEST_RESOURCE = "Server/Dialogue/dialogues.json";

    private final Map<String, DialogueTreeDefinition> byId;

    private DialogueCatalog(Map<String, DialogueTreeDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static DialogueCatalog loadFromClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, DialogueTreeDefinition> map = new LinkedHashMap<>();
        try (InputStream in = classLoader.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                LOGGER.atWarning().log("No %s on classpath; dialogue catalog empty", MANIFEST_RESOURCE);
                return new DialogueCatalog(Collections.emptyMap());
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                if (root == null) {
                    return new DialogueCatalog(Collections.emptyMap());
                }
                JsonArray trees = root.getAsJsonArray("trees");
                if (trees == null) {
                    LOGGER.atWarning().log("dialogues.json missing \"trees\" array");
                    return new DialogueCatalog(Collections.emptyMap());
                }
                for (JsonElement el : trees) {
                    if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                        continue;
                    }
                    String path = el.getAsString();
                    loadTreeAtPath(classLoader, gson, path, map);
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load dialogue catalog");
            return new DialogueCatalog(Collections.emptyMap());
        }
        return new DialogueCatalog(map);
    }

    private static void loadTreeAtPath(
        @Nonnull ClassLoader classLoader,
        @Nonnull Gson gson,
        @Nonnull String path,
        @Nonnull Map<String, DialogueTreeDefinition> map
    ) {
        try (InputStream tin = classLoader.getResourceAsStream(path.startsWith("Server/") ? path : "Server/" + path)) {
            if (tin == null) {
                LOGGER.atWarning().log("Dialogue tree file not found: %s", path);
                return;
            }
            try (InputStreamReader tr = new InputStreamReader(tin, StandardCharsets.UTF_8)) {
                DialogueTreeDefinition tree = gson.fromJson(tr, DialogueTreeDefinition.class);
                if (tree == null || tree.getId() == null || tree.getId().isBlank()) {
                    LOGGER.atWarning().log("Skipping dialogue file with missing id: %s", path);
                    return;
                }
                map.put(tree.getId(), tree);
                LOGGER.atInfo().log("Loaded dialogue tree: %s (%s)", tree.getId(), path);
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load dialogue tree %s", path);
        }
    }

    @Nullable
    public DialogueTreeDefinition get(@Nonnull String id) {
        return byId.get(id);
    }

    @Nonnull
    public Map<String, DialogueTreeDefinition> all() {
        return Collections.unmodifiableMap(byId);
    }
}
