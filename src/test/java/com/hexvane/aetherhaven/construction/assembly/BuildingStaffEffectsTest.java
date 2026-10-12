package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Nothing removes a particle effect that the staff spawns at a place in the world, so each one has to end by itself.
 * The mist at the staff tip once streamed for ever: a new copy every 72 ms while secondary was held, none ever gone.
 * It is part of the held conduit now and no longer spawned into the world at all.
 */
@Tag("construction")
class BuildingStaffEffectsTest {
    private static final Path PARTICLES = Path.of("src/main/resources/Server/Particles/Aetherhaven");
    private static final Path ASSEMBLY_SOURCES = Path.of("src/main/java/com/hexvane/aetherhaven/construction/assembly");
    private static final Path SECONDARY_INTERACTION =
        Path.of("src/main/resources/Server/Item/Interactions/Aetherhaven/Aetherhaven_BuildingStaff_Secondary_Op.json");
    private static final String CONDUIT_ID = BuildingStaffEffects.HELD_SYSTEM_ID;

    @Test
    void everyFreeStandingStaffEffectEndsByItself() throws IOException {
        assertEquals(4, BuildingStaffEffects.FREE_STANDING_SYSTEM_IDS.size());
        for (String id : BuildingStaffEffects.FREE_STANDING_SYSTEM_IDS) {
            JsonObject system = read(PARTICLES.resolve(id + ".particlesystem"));
            float systemLifeSpan = number(system, "LifeSpan", 0.0F);
            List<BuildingStaffEffects.Spawner> spawners = spawners(system);
            float seconds = BuildingStaffEffects.secondsUntilGone(systemLifeSpan, spawners);
            assertFalse(Float.isInfinite(seconds), id + " never ends by itself: give its spawners a TotalParticles");
            assertTrue(BuildingStaffEffects.endsInTime(systemLifeSpan, spawners), id + " stays for " + seconds + " seconds");
            for (BuildingStaffEffects.Spawner spawner : spawners) {
                assertTrue(spawner.totalParticlesMax() > 0, spawner.id() + " needs a TotalParticles above zero");
            }
        }
    }

    @Test
    void theTipMistIsNoLongerSpawnedIntoTheWorld() throws IOException {
        // Its stars are a spawner of the held conduit; the free-standing system and the server's spawn are gone.
        assertFalse(Files.exists(PARTICLES.resolve("Aetherhaven_Building_Staff_Stream.particlesystem")));
        assertFalse(Files.exists(PARTICLES.resolve("Aetherhaven_Building_Staff_ForwardMist.particlespawner")));
        String secondary = Files.readString(ASSEMBLY_SOURCES.resolve("BuildingStaffSecondaryInteraction.java"));
        assertFalse(secondary.contains("spawnParticleEffect"), "the held secondary must not spawn particles from the server");
        assertFalse(Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/AetherhavenConstants.java"))
            .contains("Aetherhaven_Building_Staff_Stream"));
    }

    @Test
    void aStreamWithNoCountAndNoLifeSpanNeverEnds() {
        // The mist as it shipped: 52 to 78 a second, at most 10 at once, and nothing to stop it.
        var endless = new BuildingStaffEffects.Spawner("mist", 0.0F, -1, 52.0F, false, 10, 0.0F, 0.0F, 0.42F);
        assertTrue(Float.isInfinite(BuildingStaffEffects.secondsUntilGone(0.0F, List.of(endless))));
        assertFalse(BuildingStaffEffects.endsInTime(0.0F, List.of(endless)));
        // A system life span stops emission; particles alive then finish their lives.
        assertEquals(0.25F + 0.42F, BuildingStaffEffects.secondsUntilGone(0.25F, List.of(endless)), 1.0e-4F);
        // So does a life span on the spawner or on its entry in the system, counted from its start delay.
        var spawnerLife = new BuildingStaffEffects.Spawner("mist", 0.1F, -1, 52.0F, false, 10, 0.3F, 0.0F, 0.42F);
        assertEquals(0.1F + 0.3F + 0.42F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(spawnerLife)), 1.0e-4F);
        var groupLife = new BuildingStaffEffects.Spawner("mist", 0.1F, -1, 52.0F, false, 10, 0.0F, 0.2F, 0.42F);
        assertEquals(0.1F + 0.2F + 0.42F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(groupLife)), 1.0e-4F);
        // One endless spawner makes the whole system endless.
        var burst = new BuildingStaffEffects.Spawner("star", 0.0F, 1, 1.0F, true, 1, 0.0F, 0.0F, 0.78F);
        assertTrue(Float.isInfinite(BuildingStaffEffects.secondsUntilGone(0.0F, List.of(burst, endless))));
    }

    @Test
    void aParticleCountEndsStreamsAndBursts() {
        // A stream makes its particles at its rate per second.
        var stream = new BuildingStaffEffects.Spawner("mist", 0.0F, 3, 52.0F, false, 10, 0.0F, 0.0F, 0.42F);
        assertEquals(3 / 52.0F + 0.42F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(stream)), 1.0e-4F);
        // A burst makes its rate every update, sixty updates a second.
        var burst = new BuildingStaffEffects.Spawner("embers", 0.0F, 26, 56.0F, true, 28, 0.0F, 0.0F, 0.58F);
        assertEquals(1 / 60.0F + 0.58F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(burst)), 1.0e-4F);
        // With fewer slots than particles, the rest wait for the first ones to die.
        var crowded = new BuildingStaffEffects.Spawner("embers", 0.0F, 6, 6.0F, true, 2, 0.0F, 0.0F, 0.5F);
        assertEquals(1 / 60.0F + 2 * 0.5F + 0.5F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(crowded)), 1.0e-4F);
        // A total of zero makes nothing; no total at all is no limit.
        var none = new BuildingStaffEffects.Spawner("none", 0.0F, 0, 52.0F, false, 10, 0.0F, 0.0F, 0.42F);
        assertEquals(0.42F, BuildingStaffEffects.secondsUntilGone(0.0F, List.of(none)), 1.0e-4F);
        assertFalse(BuildingStaffEffects.endsInTime(0.0F, List.of()), "a system with no spawners is a broken asset");
    }

    @Test
    void everyStaffEffectTheAssemblyCodeSpawnsIsHeldToTheRule() throws IOException {
        String listed = Files.readString(ASSEMBLY_SOURCES.resolve("BuildingStaffEffects.java"));
        Pattern constant = Pattern.compile("BUILDING_STAFF_[A-Z_]+_PARTICLE_SYSTEM_ID");
        int found = 0;
        try (Stream<Path> files = Files.list(ASSEMBLY_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("spawnParticleEffect")) {
                    continue;
                }
                Matcher matcher = constant.matcher(source);
                while (matcher.find()) {
                    found++;
                    assertTrue(listed.contains("AetherhavenConstants." + matcher.group()),
                        file.getFileName() + " spawns " + matcher.group() + ", which BuildingStaffEffects does not list");
                }
            }
        }
        assertTrue(found >= 5, "the staff's spawn calls were not found");
    }

    @Test
    void theEndlessConduitIsHeldByTheSecondaryInteraction() throws IOException {
        // The conduit streams for as long as it exists, so the held interaction must own it. Java must never spawn it.
        JsonObject effects = read(SECONDARY_INTERACTION).getAsJsonObject("Effects");
        assertEquals(CONDUIT_ID, effects.getAsJsonArray("Particles").get(0).getAsJsonObject().get("SystemId").getAsString());
        assertEquals(BuildingStaffEffects.HELD_INTERACTION_ID + ".json", SECONDARY_INTERACTION.getFileName().toString());
        assertFalse(BuildingStaffEffects.FREE_STANDING_SYSTEM_IDS.contains(CONDUIT_ID));
        assertTrue(Float.isInfinite(BuildingStaffEffects.secondsUntilGone(0.0F, spawners(read(PARTICLES.resolve(CONDUIT_ID + ".particlesystem"))))),
            "the conduit is the looping kind; if it ever becomes finite, hold it to the free-standing rule instead");
        try (Stream<Path> files = Files.list(ASSEMBLY_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("spawnParticleEffect")) {
                    assertFalse(source.contains("HELD_SYSTEM_ID") || source.contains("\"" + CONDUIT_ID + "\""), file.toString());
                }
            }
        }
        assertFalse(Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/AetherhavenConstants.java"))
            .contains("\"" + CONDUIT_ID + "\""));
    }

    private static List<BuildingStaffEffects.Spawner> spawners(JsonObject system) throws IOException {
        List<BuildingStaffEffects.Spawner> out = new ArrayList<>();
        for (JsonElement element : system.getAsJsonArray("Spawners")) {
            JsonObject entry = element.getAsJsonObject();
            String spawnerId = entry.get("SpawnerId").getAsString();
            JsonObject spawner = read(PARTICLES.resolve(spawnerId + ".particlespawner"));
            out.add(new BuildingStaffEffects.Spawner(
                spawnerId,
                number(entry, "StartDelay", 0.0F),
                spawner.has("TotalParticles") ? (int) rangeMax(spawner.getAsJsonObject("TotalParticles")) : -1,
                spawner.has("SpawnRate") ? rangeMin(spawner.getAsJsonObject("SpawnRate")) : BuildingStaffEffects.DEFAULT_SPAWN_RATE,
                spawner.has("SpawnBurst") && spawner.get("SpawnBurst").getAsBoolean(),
                (int) number(spawner, "MaxConcurrentParticles", 0.0F),
                number(spawner, "LifeSpan", 0.0F),
                entry.has("LifeSpan") ? rangeMax(entry.getAsJsonObject("LifeSpan")) : 0.0F,
                spawner.has("ParticleLifeSpan")
                    ? rangeMax(spawner.getAsJsonObject("ParticleLifeSpan"))
                    : BuildingStaffEffects.DEFAULT_PARTICLE_LIFE_SECONDS
            ));
        }
        return out;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    private static float rangeMin(JsonObject range) {
        return Math.min(number(range, "Min", 0.0F), number(range, "Max", 0.0F));
    }

    private static float rangeMax(JsonObject range) {
        return Math.max(number(range, "Min", 0.0F), number(range, "Max", 0.0F));
    }
}
