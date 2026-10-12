package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Saved state of one grower site: the chosen crop, its beds and its store. This is the record of what has grown. The
 * blocks in the world only show it, so a bed that was changed while nobody watched is put right, never paid twice.
 *
 * <p>Nothing here advances by time alone. Resting time only decides when the next visit is useful. Every change of
 * stage and every item in the store comes from {@link #complete}, which a townsfolk standing at the bed calls.
 */
public final class GrowerSite {
    public enum Job { SEED, TEND, PICK }

    /** One bed. Stage 0 is bare, 1 to {@code tends} is growing, {@code tends + 1} has had all its care. */
    public static final class Bed {
        private WarehouseBatch.Point point;
        private int stage;
        private int minutes;
        private double work;
        private Bed() {}
        private Bed(WarehouseBatch.Point point, int stage) { this.point = point; this.stage = stage; }
        public WarehouseBatch.Point point() { return point; }
        public int stage() { return Math.max(0, stage); }
        public int minutes() { return Math.max(0, minutes); }
        public double work() { return Math.max(0, work); }
    }

    public record Task(Bed bed, Job job) {}

    private String crop;
    private List<Bed> beds = new ArrayList<>();
    private Map<String, Integer> output = new LinkedHashMap<>();
    private WarehouseBatch.Point pickup;
    private long revision;
    private long picks;
    private String lastCare;

    public GrowerSite() {}

    private List<Bed> bedList() { if (beds == null) beds = new ArrayList<>(); return beds; }
    private Map<String, Integer> store() { if (output == null) output = new LinkedHashMap<>(); return output; }

    public List<Bed> beds() { return Collections.unmodifiableList(bedList()); }
    public long revision() { return revision; }
    public long picks() { return Math.max(0, picks); }
    /** Lang key suffix of the last finished job, or null before the first one. */
    public String lastCare() { return lastCare; }
    /** Where couriers stand to collect from this site's store. Null until the site has a bed or a bench. */
    public WarehouseBatch.Point pickup() { return pickup; }

    public Bed bed(WarehouseBatch.Point point) {
        for (var bed : bedList()) if (bed.point.equals(point)) return bed;
        return null;
    }

    /** The crop this site grows: the player's choice, or the only one a single crop plan offers. */
    public GrowerPlan.Crop chosen(GrowerPlan plan) {
        var chosen = plan.crop(crop);
        return chosen != null ? chosen : plan.onlyCrop();
    }

    /**
     * Change what the site grows. Everything growing is cleared, because a half grown bed of the old crop cannot turn
     * into the new one. What is already in the store stays.
     *
     * @param bestSkill the best Farming skill among the townsfolk who can work here
     * @return null when the crop was chosen, otherwise the reason it was not
     */
    public String choose(GrowerPlan plan, String cropId, int bestSkill, long expectedRevision) {
        if (expectedRevision != revision) return "changed";
        var next = plan.crop(cropId);
        if (next == null) return "unknown";
        if (next.skill() > bestSkill) return "skill";
        if (next == chosen(plan)) return "same";
        crop = next.id();
        for (var bed : bedList()) clear(plan, bed);
        revision++;
        return null;
    }

    private static void clear(GrowerPlan plan, Bed bed) {
        bed.stage = plan.logBeds() ? 0 : 1;
        bed.minutes = 0;
        bed.work = 0;
    }

    /**
     * Match the saved beds to the beds found in the world. Beds already in use keep their place, so adding an extra
     * log never pushes a growing one out. New beds fill what room is left, in the order they were found.
     *
     * @return true when anything changed
     */
    public boolean sync(GrowerPlan plan, List<WarehouseBatch.Point> found, WarehouseBatch.Point collectAt) {
        boolean changed = false;
        var present = new LinkedHashSet<>(found);
        changed |= bedList().removeIf(bed -> bed.point == null || !present.contains(bed.point));
        while (bedList().size() > plan.maxBeds()) { bedList().removeLast(); changed = true; }
        for (var point : found) {
            if (bedList().size() >= plan.maxBeds()) break;
            if (bed(point) != null) continue;
            bedList().add(new Bed(point, plan.logBeds() ? 0 : 1));
            changed = true;
        }
        var stand = collectAt != null ? collectAt : bedList().isEmpty() ? null : bedList().getFirst().point;
        // Keep the old collection point while the site has nothing to stand at, so a waiting courier is not stranded.
        if (stand != null && !stand.equals(pickup)) { pickup = stand; changed = true; }
        if (changed) revision++;
        return changed;
    }

    public int stored() {
        long total = 0;
        for (int count : store().values()) total += Math.max(0, count);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** A full store pauses the whole site. Nothing is picked that could not be put away. */
    public boolean full(GrowerPlan plan, GrowerPlan.Crop crop) {
        return crop == null || stored() + crop.yield() > plan.storeCapacity();
    }

    /** What this bed needs right now, or null when it is resting or cannot be worked. */
    public Job job(GrowerPlan plan, Bed bed, boolean ripeInWorld) {
        int stage = bed.stage();
        if (stage == 0) return plan.logBeds() ? Job.SEED : null;
        if (stage <= plan.tends()) return bed.minutes() >= plan.stageMinutes() ? Job.TEND : null;
        return plan.logBeds() || ripeInWorld ? Job.PICK : null;
    }

    /**
     * The next bed to visit: ripe beds first, then beds that need tending, then bare ones, nearest first.
     *
     * @param ripeInWorld for living plants, whether the plant at this bed is ready to pick. Log beds ignore it.
     */
    public Task next(GrowerPlan plan, GrowerPlan.Crop crop, Predicate<Bed> ripeInWorld, ToDoubleFunction<Bed> distance) {
        if (crop == null || full(plan, crop)) return null;
        Task best = null;
        double bestDistance = 0;
        for (var bed : bedList()) {
            var job = job(plan, bed, !plan.logBeds() && bed.stage() > plan.tends() && ripeInWorld.test(bed));
            if (job == null) continue;
            double away = distance == null ? 0 : distance.applyAsDouble(bed);
            if (best == null || job.ordinal() > best.job().ordinal() || job == best.job() && away < bestDistance) {
                best = new Task(bed, job);
                bestDistance = away;
            }
        }
        return best;
    }

    /**
     * Called only while a townsfolk stands at the bed doing this job.
     *
     * @return true when the job has had all its labour and may be completed
     */
    public boolean work(GrowerPlan plan, Bed bed, Job job, double seconds, boolean present, double multiplier) {
        if (!present || bed == null || job == null || !Double.isFinite(seconds) || seconds <= 0) return false;
        if (!Double.isFinite(multiplier)) multiplier = 1;
        double needed = plan.seconds(job);
        bed.work = Math.min(needed, bed.work() + Math.min(.25, seconds) * Math.clamp(multiplier, 1, 2));
        return bed.work >= needed;
    }

    /**
     * Finish a job. The saved state changes only after the change in the world succeeds, and both happen in this one
     * call on the world thread, so a harvest reaches the store exactly once.
     */
    public boolean complete(GrowerPlan plan, GrowerPlan.Crop crop, Bed bed, Job job, boolean ripeInWorld, BooleanSupplier world) {
        if (crop == null || bed == null || bed(bed.point) != bed || job != job(plan, bed, ripeInWorld)) return false;
        if (bed.work() < plan.seconds(job) || full(plan, crop) || !world.getAsBoolean()) return false;
        switch (job) {
            case SEED -> bed.stage = 1;
            case TEND -> bed.stage++;
            case PICK -> {
                store().merge(crop.item(), crop.yield(), Integer::sum);
                if (picks < Long.MAX_VALUE) picks++;
                bed.stage = plan.logBeds() ? 0 : 1;
            }
        }
        bed.minutes = 0;
        bed.work = 0;
        lastCare = job.name().toLowerCase(Locale.ROOT);
        revision++;
        return true;
    }

    /** Resting time passes only while a town member is online and the site is being simulated. */
    public boolean advance(GrowerPlan plan, long minutes, boolean memberOnline, boolean simulated) {
        if (!memberOnline || !simulated || minutes <= 0) return false;
        boolean changed = false;
        for (var bed : bedList()) {
            if (bed.stage() < 1 || bed.stage() > plan.tends() || bed.minutes() >= plan.stageMinutes()) continue;
            bed.minutes = (int) Math.min(plan.stageMinutes(), bed.minutes() + Math.min(minutes, plan.stageMinutes()));
            changed = true;
        }
        return changed;
    }

    /** Growth shown on the site's page, from 0 for a bare bed to 1 for one ready to pick. */
    public double growth(GrowerPlan plan, Bed bed, boolean ripeInWorld) {
        int stage = bed.stage();
        if (stage == 0) return 0;
        double rested = Math.min(1, bed.minutes() / (double) plan.stageMinutes());
        if (plan.logBeds()) return stage > plan.tends() ? 1 : (stage - 1 + rested) / plan.tends();
        // A living plant finishes ripening by itself once it has had its care.
        if (stage > plan.tends()) return ripeInWorld ? 1 : (plan.tends() + .5) / (plan.tends() + 1);
        return (stage - 1 + rested) / (plan.tends() + 1);
    }

    public Map<String, Long> amounts() {
        var result = new TreeMap<String, Long>();
        store().forEach((item, count) -> { if (count != null && count > 0) result.put(item, (long) count); });
        return Collections.unmodifiableMap(result);
    }

    public long amount(String item) {
        Integer count = store().get(item);
        return count == null ? 0 : Math.max(0, count);
    }

    public long remove(String item, long count) {
        long taken = Math.min(Math.max(0, count), amount(item));
        if (taken <= 0) return 0;
        int left = (int) (amount(item) - taken);
        if (left == 0) store().remove(item); else store().put(item, left);
        revision++;
        return taken;
    }
}
