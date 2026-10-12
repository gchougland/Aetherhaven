package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tuning for the Spirit Garden, read from {@code Server/Aetherhaven/Production/spirit_garden.json}. */
public final class SpiritGardenRules {
    public static final String RESOURCE = "Server/Aetherhaven/Production/spirit_garden.json";
    public record Plant(String plant, String seed, String block, String petal, String plantDrops, String petalDrops,
                        int tier, int mastery) {}
    public record Level(double value, Map<String, Integer> cost) {}
    public record Track(String icon, double base, int petalRollsPerPlant, List<Level> levels) {}
    public record Blessing(String item, int cost) {}
    public record Work(double plantSeconds, double riteSeconds, double harvestSeconds) {}
    public record Xp(int plant, int petal) {}

    private Blessing blessing;
    private Work work;
    private Xp xp;
    private List<Plant> plants;
    private Map<String, Track> tracks;

    private static volatile SpiritGardenRules current;

    public static SpiritGardenRules get() {
        var rules = current;
        return rules != null ? rules : reload(SpiritGardenRules.class.getClassLoader());
    }

    public static synchronized SpiritGardenRules reload(ClassLoader loader) {
        try (var in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
            var rules = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SpiritGardenRules.class);
            rules.check();
            current = rules;
            return rules;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not read " + RESOURCE, failure);
        }
    }

    private void check() {
        if (blessing == null || blessing.item() == null || blessing.cost() < 1 || work == null || xp == null
            || plants == null || plants.isEmpty() || tracks == null) throw new IllegalStateException(RESOURCE + " is incomplete");
        for (var plant : plants)
            if (plant.plant() == null || plant.seed() == null || plant.block() == null || plant.petal() == null
                || plant.plantDrops() == null || plant.petalDrops() == null) throw new IllegalStateException(RESOURCE + " has an incomplete plant");
        for (var track : GardenUpgrades.Track.values()) {
            var saved = tracks.get(track.name());
            if (saved == null || saved.levels() == null || saved.levels().isEmpty())
                throw new IllegalStateException(RESOURCE + " has no levels for " + track);
            for (var level : saved.levels())
                if (level.cost() == null || level.cost().isEmpty() || level.cost().values().stream().anyMatch(n -> n == null || n < 1))
                    throw new IllegalStateException(RESOURCE + " has a free level for " + track);
        }
    }

    public Blessing blessing() { return blessing; }
    public Work work() { return work; }
    public Xp xp() { return xp; }
    public List<Plant> plants() { return plants; }
    public Track track(GardenUpgrades.Track track) { return tracks.get(track.name()); }

    public Plant plant(String item) {
        if (item != null) for (var plant : plants) if (plant.plant().equals(item)) return plant;
        return null;
    }
    public Plant bySeed(String seed) {
        if (seed != null) for (var plant : plants) if (plant.seed().equals(seed)) return plant;
        return null;
    }
    /** Every item a garden can put in the Conservatory's stores, in catalog order. */
    public Map<String, Boolean> outputs() {
        var result = new LinkedHashMap<String, Boolean>();
        for (var plant : plants) result.put(plant.plant(), true);
        for (var plant : plants) result.putIfAbsent(plant.petal(), false);
        return result;
    }
    /** XP for one delivered item: a plant teaches more than a petal. */
    public int xpFor(String item) {
        return plant(item) != null ? xp.plant() : xp.petal();
    }
}
