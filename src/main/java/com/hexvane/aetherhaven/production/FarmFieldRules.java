package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tuning for the Scarecrow Fields, read from {@code Server/Aetherhaven/Production/farm_fields.json}. */
public final class FarmFieldRules {
    public static final String RESOURCE = "Server/Aetherhaven/Production/farm_fields.json";

    /**
     * @param tilled the game's farmland block
     * @param tillable ground the game's own hoe turns into farmland (the {@code Hoe_Till} interaction of 0.7.0-pre.5)
     * @param wearEvery harvests of the same crop in a row that wear a cell one step
     * @param richYield how many times the crop a harvest on Rich soil gives
     * @param goldenChance chance of one Golden Harvest from a harvest on Rich soil
     */
    public record Soil(String tilled, List<String> tillable, int wearEvery, int richYield, double goldenChance) {}
    public record Golden(String item, int keep) {}
    public record Seeds(String essence, int fallbackEssenceCost, Map<String, Integer> starter, int keep) {}
    /**
     * @param keep compost the heap holds
     * @param enrichLevel the level of richer compost from which compost also lifts Good soil to Rich
     */
    public record Compost(int keep, int enrichLevel) {}
    public record Almanac(int extraYield, List<Integer> weeksShown) {}
    public record Work(double harvestSeconds, double plantSeconds, double waterSeconds, double compostSeconds,
                       double deliverSeconds, int basket, int waterHours, int rewaterHours) {}
    public record Level(double value, Map<String, Integer> cost) {}
    public record Track(String icon, double base, List<Level> levels) {}

    private Soil soil;
    private Golden golden;
    private Seeds seeds;
    private Compost compost;
    private Almanac almanac;
    private Work work;
    private int xp;
    private Map<String, Track> tracks;
    private transient Set<String> tillable;

    private static volatile FarmFieldRules current;

    public static FarmFieldRules get() {
        var rules = current;
        return rules != null ? rules : reload(FarmFieldRules.class.getClassLoader());
    }

    public static synchronized FarmFieldRules reload(ClassLoader loader) {
        try (var in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
            var rules = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), FarmFieldRules.class);
            rules.check();
            current = rules;
            return rules;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not read " + RESOURCE, failure);
        }
    }

    private void check() {
        if (soil == null || soil.tilled() == null || soil.tillable() == null || soil.wearEvery() < 1 || soil.richYield() < 1
            || soil.goldenChance() < 0 || soil.goldenChance() > 1 || golden == null || golden.item() == null || golden.keep() < 1
            || seeds == null || seeds.essence() == null || seeds.fallbackEssenceCost() < 1 || seeds.keep() < 1
            || compost == null || compost.keep() < 1 || compost.enrichLevel() < 0 || almanac == null || almanac.extraYield() < 0
            || almanac.weeksShown() == null || almanac.weeksShown().isEmpty() || work == null || work.basket() < 1
            || work.waterHours() < 1 || work.rewaterHours() < 0 || xp < 0 || tracks == null)
            throw new IllegalStateException(RESOURCE + " is incomplete");
        for (var track : FarmUpgrades.Track.values()) {
            var saved = tracks.get(track.name());
            if (saved == null || saved.levels() == null || saved.levels().isEmpty())
                throw new IllegalStateException(RESOURCE + " has no levels for " + track);
            for (var level : saved.levels())
                if (level.cost() == null || level.cost().isEmpty() || level.cost().values().stream().anyMatch(n -> n == null || n < 1))
                    throw new IllegalStateException(RESOURCE + " has a free level for " + track);
        }
        tillable = Set.copyOf(soil.tillable());
    }

    public Soil soil() { return soil; }
    public Golden golden() { return golden; }
    public Seeds seeds() { return seeds; }
    public Compost compost() { return compost; }
    public Almanac almanac() { return almanac; }
    public Work work() { return work; }
    /** Mastery experience for one item put into the farm's stores. */
    public int xp() { return xp; }
    public Track track(FarmUpgrades.Track track) { return tracks.get(track.name()); }

    public boolean tilled(String block) { return soil.tilled().equals(block); }
    public boolean tillable(String block) { return block != null && tillable.contains(block); }
    /** Weeks of the Almanac a farm can read at an insight level, this week included. */
    public int weeksShown(int level) {
        var weeks = almanac.weeksShown();
        return Math.max(1, weeks.get(Math.clamp(level, 0, weeks.size() - 1)));
    }
}
