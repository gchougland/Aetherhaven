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
    private record PlaybackCatalog(Map<String, List<Clip>> clips, Map<String, String> recordings) {}
    private static volatile PlaybackCatalog catalog = prepareCatalog(load());
    private VillagerLifeSpeech() {}

    static String recordingName(String requested) {
        return requested == null ? null : catalog.recordings().get(requested.toLowerCase(java.util.Locale.ROOT));
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
        // Publish the recordings and all derived timelines together. Active speakers
        // retain their immutable clips; later selections immediately use the new pack.
        catalog = prepareCatalog(merged);
    }

    static Clip select(String profile, String mood, int choice) {
        return selectExcept(profile, mood, choice, null);
    }

    static Clip selectExcept(String profile, String mood, int choice, String previous) {
        var current = catalog;
        List<Clip> options = current.clips().get(profile + "_" + mood);
        if (options == null) {
            var voice = VillagerVoiceProfile.resolve(profile, null, null);
            options = current.clips().get(voice.id() + "_" + mood);
        }
        if (options == null || options.isEmpty()) return null;
        if (previous == null) return options.get(Math.floorMod(choice, options.size()));
        int count = 0;
        for (Clip clip : options) if (!clip.clip().equals(previous)) count++;
        // A one-recording category stays visually expressive but silent on immediate repetition.
        if (count == 0) return null;
        int selected = Math.floorMod(choice, count);
        for (Clip clip : options) {
            if (!clip.clip().equals(previous) && selected-- == 0) return clip;
        }
        return null;
    }

    private static PlaybackCatalog prepareCatalog(Map<String, List<Clip>> source) {
        Map<String, List<Clip>> prepared = new HashMap<>(source);
        Map<String, String> recordings = new HashMap<>();
        source.forEach((key, clips) -> {
            int split = key.lastIndexOf('_');
            String recording = key.substring(0, split);
            String mood = key.substring(split + 1);
            recordings.put(recording.toLowerCase(java.util.Locale.ROOT), recording);
            for (String variant : new String[]{"Lower", "Higher"}) {
                float pitch = (float) Math.pow(2, (variant.equals("Lower") ? -2.0 : 2.0) / 12);
                var voice = new VillagerVoiceProfile(recording + variant, recording, pitch, variant);
                prepared.put(voice.id() + "_" + mood, mood.equals("Stomach") ? clips
                    : clips.stream().map(clip -> pitched(clip, voice)).toList());
            }
        });
        return new PlaybackCatalog(Map.copyOf(prepared), Map.copyOf(recordings));
    }

    private static Clip pitched(Clip clip, VillagerVoiceProfile voice) {
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
