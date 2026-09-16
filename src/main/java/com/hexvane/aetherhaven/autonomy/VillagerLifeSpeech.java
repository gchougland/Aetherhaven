package com.hexvane.aetherhaven.autonomy;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One selection binds the recording and facial timeline, avoiding independent random picks. */
public final class VillagerLifeSpeech {
    record Face(String id, long durationMs, String actionId) {}
    record MouthCue(long timeMs, String shape) {}
    record Clip(String clip, long audioMs, Map<String, Face> faces, float pitch, String actionsId, List<MouthCue> mouthCues) {
        Clip(String clip, long audioMs, Map<String, Face> faces, float pitch, String actionsId) {
            this(clip, audioMs, faces, pitch, actionsId, List.of());
        }
    }
    private static volatile Map<String, List<Clip>> CLIPS = load();
    private VillagerLifeSpeech() {}

    static String recordingName(String requested) {
        for (String key : CLIPS.keySet()) {
            int split = key.lastIndexOf('_');
            if (split > 0 && key.substring(0, split).equalsIgnoreCase(requested)) return key.substring(0, split);
        }
        return null;
    }

    /** Add-on packs supply the same clip/timeline schema as the bundled playback manifest. */
    public static void reloadFromAssetPacks() {
        reloadFromFiles(com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner
            .listJsonFilesUnderAllPacks("Server/Aetherhaven/VoiceClips").stream()
            .map(com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile::absolutePath).toList());
    }

    static void reloadFromFiles(List<java.nio.file.Path> files) {
        Map<String, List<Clip>> merged = new HashMap<>(load());
        for (var file : files) {
            try (var reader = java.nio.file.Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                // Parse and validate a whole file before applying any of its categories.
                merged.putAll(parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atWarning().withCause(e)
                    .log("Skipping invalid villager voice clips in %s", file);
            }
        }
        CLIPS = Map.copyOf(merged);
    }

    static Clip select(String profile, String mood, int choice) {
        return selectExcept(profile, mood, choice, null);
    }

    static Clip selectExcept(String profile, String mood, int choice, String previous) {
        var voice = VillagerVoiceProfile.resolve(profile, null, null);
        List<Clip> options = CLIPS.get(voice.recording() + "_" + mood);
        if (options == null || options.isEmpty()) return null;
        var eligible = options.stream().filter(c -> !c.clip().equals(previous)).toList();
        // A one-recording category stays visually expressive but silent on immediate repetition.
        if (eligible.isEmpty()) return null;
        Clip clip = eligible.get(Math.floorMod(choice, eligible.size()));
        if (voice.variant().isEmpty() || mood.equals("Stomach")) return clip;
        if (!clip.mouthCues().isEmpty()) {
            long audioMs = (long)Math.ceil(clip.audioMs()/voice.pitch());
            Map<String, Face> faces = new HashMap<>();
            clip.faces().forEach((gesture, face) -> faces.put(gesture,
                new Face(face.id(), Math.max(audioMs, VillagerLifeTiming.durationMs(gesture)), face.actionId())));
            return new Clip(clip.clip(), audioMs, Map.copyOf(faces), voice.pitch(), clip.actionsId(),
                clip.mouthCues().stream().map(c -> new MouthCue(Math.round(c.timeMs()/voice.pitch()), c.shape())).toList());
        }
        Map<String, Face> faces = new HashMap<>();
        clip.faces().forEach((gesture, face) -> faces.put(gesture, new Face(face.id() + "_" + voice.variant(),
            (long)Math.ceil(face.durationMs()/voice.pitch()), face.actionId())));
        return new Clip(clip.clip(), (long)Math.ceil(clip.audioMs()/voice.pitch()), Map.copyOf(faces), voice.pitch(),
            clip.actionsId() + "_" + voice.variant());
    }

    private static Map<String, List<Clip>> load() {
        try (var input = VillagerLifeSpeech.class.getResourceAsStream("/defaults/villager_life_playback.json")) {
            if (input == null) throw new IllegalStateException("Missing villager speech playback assets");
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(json);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    static Map<String, List<Clip>> parse(com.google.gson.JsonObject json) {
        Map<String, List<Clip>> result = new HashMap<>();
        for (var entry : json.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]*_(Talk|Question|Laugh|Gasp|Grumble|Groan|Yawn|Sigh|Stomach|Idle|Work|Thinking)"))
                throw new IllegalArgumentException("Invalid voice category: " + entry.getKey());
            List<Clip> clips = new ArrayList<>();
            for (var value : entry.getValue().getAsJsonArray()) {
                var clip = value.getAsJsonObject();
                Map<String, Face> faces = new HashMap<>();
                var faceJson = clip.has("faces") ? clip.getAsJsonObject("faces") : new com.google.gson.JsonObject();
                for (var f : faceJson.entrySet()) {
                    var face = f.getValue().getAsJsonObject();
                    faces.put(f.getKey(), new Face(face.get("id").getAsString(), face.get("durationMs").getAsLong(),
                        face.get("actionId").getAsString()));
                }
                long audioMs = clip.get("audioMs").getAsLong();
                List<MouthCue> cues = new ArrayList<>();
                if (clip.has("mouthCues")) for (var valueCue : clip.getAsJsonArray("mouthCues")) {
                    var cue = valueCue.getAsJsonArray();
                    long time = cue.get(0).getAsLong();
                    String shape = cue.get(1).getAsString();
                    if (time < 0 || time > audioMs || (!cues.isEmpty() && time <= cues.getLast().timeMs())
                        || !shape.matches("[A-F]")) throw new IllegalArgumentException("Invalid mouth cue");
                    cues.add(new MouthCue(time, shape));
                }
                if (!cues.isEmpty() && (cues.getFirst().timeMs()!=0
                        || !cues.getLast().shape().equals("A"))) throw new IllegalArgumentException("Mouth cues must start at zero and end closed");
                clips.add(new Clip(clip.get("clip").getAsString(), audioMs, Map.copyOf(faces), 1f,
                    clip.has("actionsId") ? clip.get("actionsId").getAsString() : VillagerLifeVisuals.BODY_ANIMATIONS, List.copyOf(cues)));
                Clip parsed = clips.getLast();
                if (parsed.clip().isBlank() || parsed.audioMs() <= 0 || parsed.faces().values().stream()
                        .anyMatch(f -> f.id().isBlank() || f.actionId().isBlank() || f.durationMs() <= 0))
                    throw new IllegalArgumentException("Invalid clip timing or identifier: " + entry.getKey());
            }
            if (clips.isEmpty()) throw new IllegalArgumentException("Empty voice category: " + entry.getKey());
            result.put(entry.getKey(), List.copyOf(clips));
        }
        return Map.copyOf(result);
    }
}
