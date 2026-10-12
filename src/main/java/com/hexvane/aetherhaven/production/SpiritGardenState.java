package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.ToLongFunction;

/**
 * Saved Spirit Planter bindings, growth, blessings, carried harvest and garden upgrades for one Conservatory.
 * No entity, chunk or world references. Integer keys identify one planter each and stay stable for its lifetime.
 * Times are whole seconds on the world's own clock, so growth only moves while that clock does. The game counts
 * from the first day of year 1, which is far below zero here, so a time is never judged by its sign.
 */
public final class SpiritGardenState {
    /** Upper bound for planters at one Conservatory, whatever its upgrades. */
    public static final int MAX_PLANTERS = 6;
    /** A seed has been chosen and Rowan has not planted it yet. */
    public static final int WAITING = -1;
    /** Sprout, young, budding, ripe: three rites carry a plant from the first to the last. */
    public static final int RIPE = 3;

    public record Planter(UUID id, Point block, String plant, int stage, long restUntil) {
        /** The cell the plant stands in, directly above its planter. */
        public Point cell() { return new Point(block.x(), block.y() + 1, block.z()); }
        public boolean ripe() { return stage >= RIPE; }
        public boolean growing() { return stage >= 0 && stage < RIPE; }
    }
    /** Part of a harvest in Rowan's basket. It is not Conservatory stock until he puts it away. */
    public record Cargo(String item, int count) {}

    private boolean adopted;
    private long revision;
    private Map<Integer, Planter> planters = new LinkedHashMap<>();
    /** Planters whose next harvest is doubled by a Life Essence blessing. */
    private Set<Integer> blessed = new HashSet<>();
    private List<Cargo> cargo = new ArrayList<>();
    private int nextSlot;
    private Map<String, Integer> upgrades = new LinkedHashMap<>();
    private boolean legacyMapped;

    public boolean adopted() { return adopted || !planters().isEmpty() || carrying(); }
    public void adopt() { adopted = true; }
    public Map<Integer, Planter> planters() { return planters == null ? Map.of() : Collections.unmodifiableMap(planters); }
    public long revision() { return revision; }
    public boolean blessed(int slot) { return blessed != null && blessed.contains(slot); }
    public List<Cargo> cargo() { return cargo == null ? List.of() : Collections.unmodifiableList(cargo); }
    public boolean carrying() { return cargo != null && !cargo.isEmpty(); }
    public int carried() { return cargo().stream().mapToInt(Cargo::count).sum(); }
    public int firstSlot() { return Math.floorMod(nextSlot, MAX_PLANTERS); }
    public void rotateAfter(int slot) { nextSlot = slot + 1; }
    public int level(String track) { return upgrades == null ? 0 : Math.max(0, upgrades.getOrDefault(track, 0)); }
    public void setLevel(String track, int level) {
        if (upgrades == null) upgrades = new LinkedHashMap<>();
        upgrades.put(track, Math.max(0, level)); adopted = true;
    }
    boolean legacyMapped() { return legacyMapped; }
    void markLegacyMapped() { legacyMapped = true; }

    /** The planter that is this block, or -1. */
    public int planterAt(Point block) {
        if (block != null) for (var bound : planters().entrySet())
            if (bound.getValue() != null && block.equals(bound.getValue().block())) return bound.getKey();
        return -1;
    }
    /** Lowest unused planter number below the allowance, or -1 when every planter is in use. */
    public int freeSlot(int allowance) {
        for (int slot = 0; slot < Math.min(allowance, MAX_PLANTERS); slot++) if (!planters().containsKey(slot)) return slot;
        return -1;
    }

    /**
     * Choosing a seed is the whole setup. Rowan plants it on his next round.
     *
     * @return null on success, otherwise the short name of what stopped it
     */
    public String bind(int slot, int allowance, long expectedRevision, Point block, String plant) {
        if (expectedRevision != revision) return "changed";
        if (slot < 0 || slot >= allowance || allowance > MAX_PLANTERS) return "full";
        if (block == null || plant == null || plant.isBlank()) return "seed";
        if (planters().containsKey(slot) || planterAt(block) >= 0) return "taken";
        if (planters == null) planters = new LinkedHashMap<>();
        planters.put(slot, new Planter(UUID.randomUUID(), block, plant, WAITING, 0));
        revision++; adopted = true;
        return null;
    }

    /** Forgets a planter and its plant. Any blessing on it is spent. */
    public boolean uproot(int slot) {
        boolean changed = planters != null && planters.remove(slot) != null;
        if (blessed != null) blessed.remove(slot);
        if (changed) revision++;
        return changed;
    }

    private Planter expected(int slot, UUID id) {
        var planter = planters().get(slot);
        return planter == null || !planter.id().equals(id) ? null : planter;
    }
    private void put(int slot, Planter old, int stage, long restUntil) {
        planters.put(slot, new Planter(old.id(), old.block(), old.plant(), stage, restUntil));
    }
    /** Only the rest can be wrong. The clock is whatever the world says it is. */
    private static boolean valid(long rest) { return rest >= 0; }

    /** Rowan sets the first sprout. The planter records it only when the block really went in. */
    public boolean plant(int slot, UUID id, long now, long rest, BooleanSupplier place) {
        var planter = expected(slot, id);
        if (planter == null || planter.stage() != WAITING || !valid(rest) || !place.getAsBoolean()) return false;
        put(slot, planter, 0, now + rest); adopted = true;
        return true;
    }

    /** True once a growing plant has rested long enough for its next rite. */
    public boolean rested(int slot, long now, long rest) {
        var planter = planters().get(slot);
        return planter != null && planter.growing() && restOver(planter.restUntil(), now, rest);
    }
    /** A rest longer than the garden's own means the clock moved or the rite was strengthened: the plant is ready. */
    private static boolean restOver(long restUntil, long now, long rest) {
        return now >= restUntil || restUntil - now > rest;
    }

    /**
     * One finished rite lifts a rested plant by one stage. Growth never comes from the clock alone.
     *
     * @param show places the plant's next shape in the world and reports whether it stands
     */
    public boolean rite(int slot, UUID id, long now, long rest, IntPredicate show) {
        var planter = expected(slot, id);
        if (planter == null || !valid(rest) || !rested(slot, now, rest) || !show.test(planter.stage() + 1)) return false;
        put(slot, planter, planter.stage() + 1, now + rest);
        return true;
    }

    /**
     * Rowan gathers a ripe plant and it starts again as a sprout, for free. Nothing reaches the stores here:
     * the harvest waits in his basket until he puts it away.
     *
     * @param regrow resets the plant in the world; a failure leaves the planter ripe and any blessing unspent
     */
    public boolean harvest(int slot, UUID id, Map<String, Integer> yields, long now, long rest, BooleanSupplier regrow) {
        var planter = expected(slot, id);
        if (planter == null || !planter.ripe() || carrying() || !valid(rest) || yields == null || yields.isEmpty()
            || yields.size() > 8 || yields.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getKey().isBlank()
                || e.getValue() == null || e.getValue() < 1 || e.getValue() > 4096)
            || !regrow.getAsBoolean()) return false;
        int share = blessed(slot) ? 2 : 1;
        cargo = new ArrayList<>();
        for (var part : yields.entrySet()) cargo.add(new Cargo(part.getKey(), part.getValue() * share));
        if (blessed != null) blessed.remove(slot);
        put(slot, planter, 0, now + rest);
        nextSlot = slot + 1; adopted = true;
        return true;
    }

    /** A plant that was broken or cleared away keeps its planter, which Rowan simply plants again. */
    public boolean lost(int slot, UUID id) {
        var planter = expected(slot, id);
        if (planter == null || planter.stage() == WAITING) return false;
        put(slot, planter, WAITING, 0);
        return true;
    }

    /** Share of the way from sprout to ripe, counting the rest before the next rite. */
    public double growth(int slot, long now, long rest) {
        var planter = planters().get(slot);
        if (planter == null || planter.stage() < 0) return 0;
        if (planter.ripe()) return 1;
        double waited = rest <= 0 || restOver(planter.restUntil(), now, rest) ? 1 : 1 - (planter.restUntil() - now) / (double) rest;
        return (planter.stage() + Math.clamp(waited, 0, 1)) / RIPE;
    }

    /** One blessing at a time. The caller takes payment only when this returns null. */
    public String blessingIssue(int slot) {
        if (!planters().containsKey(slot)) return "empty";
        if (blessed(slot)) return "blessed";
        return null;
    }
    public boolean bless(int slot) {
        if (blessingIssue(slot) != null) return false;
        if (blessed == null) blessed = new HashSet<>();
        blessed.add(slot); revision++;
        return true;
    }

    /**
     * One visit to the stores puts away everything that fits. Whatever does not fit is left behind, so one full
     * store never holds up the others. The caller has verified Rowan stands at this Conservatory's storage.
     *
     * @return what reached the stores, by item
     */
    public Map<String, Integer> deliverAll(PlotProductionState production, ToLongFunction<String> capacity) {
        var delivered = new LinkedHashMap<String, Integer>();
        for (var held : cargo()) {
            long cap = Math.min(capacity.applyAsLong(held.item()), ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
            int count = (int) Math.max(0, Math.min(held.count(), cap - production.getAmount(held.item())));
            if (count > 0) { production.addAmount(held.item(), count, cap); delivered.merge(held.item(), count, Integer::sum); }
        }
        cargo = new ArrayList<>();
        return delivered;
    }
}
