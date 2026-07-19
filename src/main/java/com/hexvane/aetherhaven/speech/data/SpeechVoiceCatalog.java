package com.hexvane.aetherhaven.speech.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
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

public final class SpeechVoiceCatalog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, SpeechVoiceDefinition> byId;

    private SpeechVoiceCatalog(@Nonnull Map<String, SpeechVoiceDefinition> byId) {
        this.byId = byId;
    }

    @Nonnull
    public static SpeechVoiceCatalog empty() {
        return new SpeechVoiceCatalog(Map.of());
    }

    @Nonnull
    public static SpeechVoiceCatalog loadFromAssetPacksOrClasspath(@Nonnull ClassLoader classLoader) {
        Gson gson = new GsonBuilder().create();
        Map<String, SpeechVoiceDefinition> byId = new LinkedHashMap<>();
        List<PackJsonFile> packFiles = AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.SPEECH_VOICES);
        if (!packFiles.isEmpty()) {
            for (PackJsonFile f : packFiles) {
                try (InputStream in = Files.newInputStream(f.absolutePath())) {
                    loadFromStream(gson, in, f.packName() + ":" + f.absolutePath(), byId);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load speech voice %s", f.absolutePath());
                }
            }
        } else {
            for (String path : ClasspathResourceScanner.listJsonFiles(classLoader, AetherhavenAssetPaths.speechVoicesPrefix())) {
                try (InputStream in = classLoader.getResourceAsStream(path)) {
                    if (in != null) {
                        loadFromStream(gson, in, path, byId);
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load speech voice %s", path);
                }
            }
        }
        LOGGER.atInfo().log("Loaded %s speech voice profile(s)", byId.size());
        return new SpeechVoiceCatalog(Collections.unmodifiableMap(byId));
    }

    private static void loadFromStream(
        @Nonnull Gson gson,
        @Nonnull InputStream in,
        @Nonnull String label,
        @Nonnull Map<String, SpeechVoiceDefinition> byId
    ) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull() || !root.isJsonObject()) {
                return;
            }
            SpeechVoiceDefinition def = gson.fromJson(root.getAsJsonObject(), SpeechVoiceDefinition.class);
            if (def == null) {
                return;
            }
            String id = def.getId();
            if (id.isEmpty()) {
                LOGGER.atWarning().log("Skipping speech voice with empty id (%s)", label);
                return;
            }
            byId.put(id, def);
        }
    }

    @Nullable
    public SpeechVoiceDefinition byId(@Nonnull String voiceId) {
        return byId.get(voiceId.trim());
    }

    @Nonnull
    public SpeechVoiceDefinition requireOrDefault(@Nonnull String voiceId) {
        SpeechVoiceDefinition def = byId(voiceId);
        if (def != null) {
            return def;
        }
        def = byId(SpeechVoiceDefinition.DEFAULT_VOICE_ID);
        if (def != null) {
            return def;
        }
        return new SpeechVoiceDefinition();
    }

    @Nonnull
    public Map<String, SpeechVoiceDefinition> allById() {
        return byId;
    }
}
