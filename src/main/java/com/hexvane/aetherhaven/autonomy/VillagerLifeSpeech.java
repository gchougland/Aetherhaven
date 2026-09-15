package com.hexvane.aetherhaven.autonomy;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One selection binds the recording and facial timeline, avoiding independent random picks. */
final class VillagerLifeSpeech {
    record Face(String id, long durationMs, String actionId) {}
    record Clip(String clip, long audioMs, Map<String, Face> faces) {}
    private static final Map<String, List<Clip>> CLIPS = load();
    private VillagerLifeSpeech() {}

    static Clip select(String profile, String mood, int choice) {
        List<Clip> options = CLIPS.get(profile + "_" + mood);
        if (options == null || options.isEmpty()) return null;
        return options.get(Math.floorMod(choice, options.size()));
    }

    private static Map<String, List<Clip>> load() {
        try (var input = VillagerLifeSpeech.class.getResourceAsStream("/defaults/villager_life_playback.json")) {
            if (input == null) throw new IllegalStateException("Missing villager speech playback assets");
            Map<String, List<Clip>> result = new HashMap<>();
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : json.entrySet()) {
                List<Clip> clips = new ArrayList<>();
                for (var value : entry.getValue().getAsJsonArray()) {
                    var clip = value.getAsJsonObject();
                    Map<String, Face> faces = new HashMap<>();
                    for (var f : clip.getAsJsonObject("faces").entrySet()) {
                        var face = f.getValue().getAsJsonObject();
                        faces.put(f.getKey(), new Face(face.get("id").getAsString(), face.get("durationMs").getAsLong(),
                            face.get("actionId").getAsString()));
                    }
                    clips.add(new Clip(clip.get("clip").getAsString(), clip.get("audioMs").getAsLong(), Map.copyOf(faces)));
                }
                result.put(entry.getKey(), List.copyOf(clips));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
