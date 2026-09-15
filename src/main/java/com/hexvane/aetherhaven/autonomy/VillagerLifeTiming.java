package com.hexvane.aetherhaven.autonomy;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Shared exported timing prevents holds, facial acting and turn changes cutting off poses. */
public final class VillagerLifeTiming {
    private static final Map<String, Long> DURATIONS = load("villager_life_timing.json");
    private static final Map<String, Long> VOICES = load("villager_life_voice_timing.json");
    private VillagerLifeTiming() {}
    public static long durationMs(String gesture) { return DURATIONS.getOrDefault(gesture, 3000L); }
    public static long voiceDurationMs(String voice, String mood) { return VOICES.getOrDefault(voice + "_" + mood, 4000L); }
    private static Map<String, Long> load(String file) {
        try (var input = VillagerLifeTiming.class.getResourceAsStream("/defaults/" + file)) {
            if (input == null) throw new IllegalStateException("Missing villager timing: " + file);
            Map<String, Long> result = new HashMap<>();
            JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsLong()));
            return Map.copyOf(result);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
