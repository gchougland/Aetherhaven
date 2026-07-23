package com.hexvane.aetherhaven.townsfolk.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterAvailability;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownsfolkCharacterCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, TownsfolkCharacterDefinition> byId;

    private TownsfolkCharacterCatalog(@Nonnull Map<String, TownsfolkCharacterDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static TownsfolkCharacterCatalog empty() {
        return new TownsfolkCharacterCatalog(Map.of());
    }

    @Nonnull
    public static TownsfolkCharacterCatalog loadFromAssetPacksOrClasspath(
        @Nonnull ClassLoader classLoader,
        @Nonnull TownsfolkPersonalityCatalog personalities
    ) {
        Gson gson = new GsonBuilder().create();
        Map<String, TownsfolkCharacterDefinition> byId = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.TOWNSFOLK);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadFromStream(gson, in, f.packName() + ":" + f.absolutePath(), byId, personalities);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load townsfolk character %s", f.absolutePath());
                }
            }
            LOGGER.atInfo().log(
                "Loaded %s townsfolk character definition(s) from %s asset pack file(s)",
                byId.size(),
                packFiles.size()
            );
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.townsfolkPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in != null) {
                        loadFromStream(gson, in, path, byId, personalities);
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load townsfolk character %s", path);
                }
            }
            LOGGER.atInfo().log("Loaded %s townsfolk character definition(s) from classpath", byId.size());
        }
        return new TownsfolkCharacterCatalog(Collections.unmodifiableMap(byId));
    }

    private static void loadFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, TownsfolkCharacterDefinition> byId,
        @Nonnull TownsfolkPersonalityCatalog personalities
    ) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull() || !root.isJsonObject()) {
                return;
            }
            TownsfolkCharacterDefinition def = gson.fromJson(root.getAsJsonObject(), TownsfolkCharacterDefinition.class);
            putDefinition(def, label, byId, personalities);
        }
    }

    private static void putDefinition(
        @Nullable TownsfolkCharacterDefinition def,
        @Nonnull String label,
        @Nonnull Map<String, TownsfolkCharacterDefinition> byId,
        @Nonnull TownsfolkPersonalityCatalog personalities
    ) {
        if (def == null) {
            return;
        }
        String id = def.getId();
        if (id.isEmpty()) {
            LOGGER.atWarning().log("Skipping townsfolk with empty id (%s)", label);
            return;
        }
        if (def.getPersonalityIds().isEmpty()) {
            LOGGER.atWarning().log("Skipping townsfolk %s: personalityIds is empty (%s)", id, label);
            return;
        }
        if (def.getAllowedAssignmentKinds().isEmpty()) {
            LOGGER.atWarning().log("Skipping townsfolk %s: allowedAssignmentKinds is empty (%s)", id, label);
            return;
        }
        for (String pid : def.getPersonalityIds()) {
            if (personalities.byId(pid) == null) {
                LOGGER.atWarning().log("Skipping townsfolk %s: unknown personality %s (%s)", id, pid, label);
                return;
            }
        }
        if (def.getModelAssetId().isEmpty()) {
            LOGGER.atWarning().log("Skipping townsfolk %s: modelAssetId is empty (%s)", id, label);
            return;
        }
        if (def.supportsAssignment("tourist") && def.getMoveInRequirements().isEmpty()) {
            LOGGER.atWarning().log("Townsfolk %s allows tourist but has no moveInRequirements (%s)", id, label);
        }
        for (var req : def.getMoveInRequirements()) {
            String itemId = req.getItemId();
            if (itemId == null || itemId.isBlank()) {
                LOGGER.atWarning().log("Townsfolk %s moveInRequirements entry missing itemId (%s)", id, label);
            }
        }
        var pluginReq = def.getRequiresOptionalPlugin();
        if (pluginReq != null && pluginReq.isComplete() && !TownsfolkCharacterAvailability.isOptionalPluginLoaded(pluginReq)) {
            LOGGER.atInfo().log(
                "Townsfolk %s requires plugin %s (not loaded; hidden from new pool draws)",
                id,
                pluginReq.displayId()
            );
        }
        byId.put(id, def);
    }

    public boolean isTownsfolkRole(@Nonnull String npcRoleId) {
        return AetherhavenConstants.NPC_TOWNSFOLK.equals(npcRoleId.trim());
    }

    @Nullable
    public TownsfolkCharacterDefinition byId(@Nonnull String characterId) {
        return byId.get(characterId.trim());
    }

    @Nonnull
    public List<String> allIds() {
        return new ArrayList<>(byId.keySet());
    }

    @Nonnull
    public Map<String, TownsfolkCharacterDefinition> allById() {
        return byId;
    }
}
