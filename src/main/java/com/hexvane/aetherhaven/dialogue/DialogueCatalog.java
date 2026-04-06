package com.hexvane.aetherhaven.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Dialogue trees from {@code Server/Aetherhaven/Dialogue/} under each asset pack (plus classpath fallback).
 */
public final class DialogueCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, DialogueTreeDefinition> byId;

    private DialogueCatalog(Map<String, DialogueTreeDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static DialogueCatalog empty() {
        return new DialogueCatalog(Collections.emptyMap());
    }

    @Nonnull
    public static DialogueCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, DialogueTreeDefinition> map = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.DIALOGUE);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadTreeFromStream(gson, in, f.packName() + ":" + f.absolutePath(), map);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load dialogue tree %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log("Loaded %s dialogue tree(s) from asset packs under %s", map.size(), AetherhavenAssetPaths.DIALOGUE);
        } else {
            List<String> paths = ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.dialoguePrefix());
            for (String path : paths) {
                loadTreeFromClasspath(classLoader, gson, path, map);
            }
            LOGGER.atInfo().log("Loaded %s dialogue tree(s) from classpath %s", map.size(), AetherhavenAssetPaths.dialoguePrefix());
        }
        return new DialogueCatalog(map);
    }

    private static void loadTreeFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, DialogueTreeDefinition> map
    ) {
        try (InputStreamReader tr = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            DialogueTreeDefinition tree = gson.fromJson(tr, DialogueTreeDefinition.class);
            putTree(tree, label, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load dialogue tree %s", label);
        }
    }

    private static void loadTreeFromClasspath(
        @Nonnull ClassLoader classLoader,
        @Nonnull Gson gson,
        @Nonnull String path,
        @Nonnull Map<String, DialogueTreeDefinition> map
    ) {
        try (InputStream tin = classLoader.getResourceAsStream(path)) {
            if (tin == null) {
                LOGGER.atWarning().log("Dialogue tree file not found: %s", path);
                return;
            }
            loadTreeFromStream(gson, tin, path, map);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to load dialogue tree %s", path);
        }
    }

    private static void putTree(
        @Nullable DialogueTreeDefinition tree,
        @Nonnull String path,
        @Nonnull Map<String, DialogueTreeDefinition> map
    ) {
        if (tree == null || tree.getId() == null || tree.getId().isBlank()) {
            LOGGER.atWarning().log("Skipping dialogue file with missing id: %s", path);
            return;
        }
        String id = tree.getId();
        if (map.containsKey(id)) {
            LOGGER.atInfo().log("Dialogue tree id %s overridden by later asset (%s)", id, path);
        }
        map.put(id, tree);
        LOGGER.atInfo().log("Loaded dialogue tree: %s (%s)", id, path);
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
