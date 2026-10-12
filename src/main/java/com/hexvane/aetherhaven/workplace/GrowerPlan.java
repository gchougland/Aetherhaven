package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.List;

/**
 * What one kind of grower site grows and how fast, read from {@code Server/Aetherhaven/GrowerPlans}. The Mycologist
 * and the Berry Patch are the same machinery with two of these.
 *
 * @param bedBlocks     blocks that count as a bed, by their base id
 * @param bench         bench id the site needs inside it and that opens its page, or null when the beds do
 * @param logBeds       true when a townsfolk seeds the bed and its growth is shown by the bed's own block states;
 *                      false when the bed is a living plant that ripens by the game's own growth
 * @param tends         tending visits a bed needs between seeding (or the last pick) and the next pick
 * @param stageMinutes  town minutes a bed rests before its next tending visit is useful
 */
public record GrowerPlan(String workplace, List<String> bedBlocks, String bench, boolean logBeds, int maxBeds,
                         int storeCapacity, int tends, int stageMinutes, double seedSeconds, double tendSeconds,
                         double pickSeconds, List<Crop> crops) {
    /**
     * @param item       what a pick puts in the site's store
     * @param skill      Farming skill a townsfolk needs to grow it
     * @param grownState block state of a log bed carrying the ripe crop, unused for living plants
     * @param use        lang key suffix naming what the town uses it for
     */
    public record Crop(String id, String item, int skill, int yield, String grownState, String use) {
        boolean valid(boolean logBeds) {
            return filled(id) && filled(item) && filled(use) && skill >= 1 && skill <= 10 && yield >= 1 && yield <= 20
                && (!logBeds || filled(grownState));
        }
    }

    public static final String SEEDED_STATE = "Seeded";
    public static final String SPROUTING_STATE = "Sprouting";

    private static boolean filled(String text) { return text != null && !text.isBlank(); }

    /** The work types that are grower sites. Their plan decides everything else. */
    public static boolean supports(TownsfolkWorkType type) {
        return type == TownsfolkWorkType.MYCOLOGY || type == TownsfolkWorkType.BERRIES;
    }

    public TownsfolkWorkType type() { return TownsfolkWorkType.byId(workplace); }

    public boolean valid() {
        return supports(type()) && bedBlocks != null && !bedBlocks.isEmpty() && bedBlocks.stream().allMatch(GrowerPlan::filled)
            && maxBeds >= 1 && maxBeds <= 32 && storeCapacity >= 1 && storeCapacity <= 9999
            && tends >= 1 && tends <= 6 && stageMinutes >= 1 && stageMinutes <= 7 * 24 * 60
            && duration(seedSeconds, !logBeds) && duration(tendSeconds, false) && duration(pickSeconds, false)
            && crops != null && !crops.isEmpty() && crops.stream().allMatch(c -> c != null && c.valid(logBeds))
            && crops.stream().map(Crop::id).distinct().count() == crops.size()
            && crops.stream().allMatch(c -> c.yield() <= storeCapacity);
    }

    private static boolean duration(double seconds, boolean optional) {
        return optional && seconds == 0 || Double.isFinite(seconds) && seconds >= 1 && seconds <= 600;
    }

    public Crop crop(String id) {
        if (id != null) for (var crop : crops) if (crop.id().equals(id)) return crop;
        return null;
    }

    /** A site with one crop grows it without asking. */
    public Crop onlyCrop() { return crops.size() == 1 ? crops.getFirst() : null; }

    public boolean isBed(String baseBlock) { return baseBlock != null && bedBlocks.contains(baseBlock); }

    /** A bed is ripe one stage after its last tending visit. */
    public int ripeStage() { return tends + 1; }

    public double seconds(GrowerSite.Job job) {
        return switch (job) { case SEED -> seedSeconds; case TEND -> tendSeconds; case PICK -> pickSeconds; };
    }

    /** The block state a log bed shows at a stage: bare, seeded, sprouting, then the chosen crop fully grown. */
    public String logState(Crop crop, int stage) {
        if (stage <= 0 || crop == null) return "default";
        if (stage >= ripeStage()) return crop.grownState();
        return stage == 1 ? SEEDED_STATE : SPROUTING_STATE;
    }
}
