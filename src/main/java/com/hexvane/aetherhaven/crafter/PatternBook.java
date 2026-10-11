package com.hexvane.aetherhaven.crafter;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * A town's saved Pattern Book: the props its Crafter has studied, the commissions on his bench and the
 * finished props waiting on his shelf. Pure data and rules, with no world or inventory access.
 */
public final class PatternBook {
    public static final int BASE_QUEUE = 2;
    public static final int BASE_SALVAGE_PERCENT = 50;
    public static final int SHELF_LIMIT = 20;
    /** Collection goals. Each one is a number of known patterns and the perk it brings. */
    public enum Milestone {
        APPRENTICE_SHELF(5, "A third commission on the bench"),
        CAREFUL_HANDS(10, "Salvaging returns three quarters of a prop's materials"),
        MASTER_BENCH(20, "A fourth commission on the bench"),
        QUICK_STUDY(30, "Commissions take a quarter less time");

        private final int patterns; private final String perk;
        Milestone(int patterns, String perk) { this.patterns = patterns; this.perk = perk; }
        public int patterns() { return patterns; }
        public String perk() { return perk; }
    }
    /** One prop being built. The parts paid are kept so a cancelled commission returns exactly what it cost. */
    public record Commission(String prop, double worked, Map<String, Integer> paid) {
        public Commission { paid = paid == null ? Map.of() : Map.copyOf(paid); }
    }

    private Set<String> known = new LinkedHashSet<>();
    private List<Commission> queue = new ArrayList<>();
    private Map<String, Integer> shelf = new LinkedHashMap<>();

    public Set<String> known() { return known == null ? Set.of() : Collections.unmodifiableSet(known); }
    public List<Commission> queue() { return queue == null ? List.of() : Collections.unmodifiableList(queue); }
    public Map<String, Integer> shelf() { return shelf == null ? Map.of() : Collections.unmodifiableMap(shelf); }
    public boolean knows(String prop) { return prop != null && known != null && known.contains(prop); }
    public int shelved() { return shelf().values().stream().mapToInt(Integer::intValue).sum(); }

    public boolean reached(Milestone milestone) { return known().size() >= milestone.patterns(); }
    public Milestone next() {
        for (Milestone milestone : Milestone.values()) if (!reached(milestone)) return milestone;
        return null;
    }
    public int queueLimit() {
        return BASE_QUEUE + (reached(Milestone.APPRENTICE_SHELF) ? 1 : 0) + (reached(Milestone.MASTER_BENCH) ? 1 : 0);
    }
    public int salvagePercent() { return reached(Milestone.CAREFUL_HANDS) ? 75 : BASE_SALVAGE_PERCENT; }
    public double speed() { return reached(Milestone.QUICK_STUDY) ? 4.0 / 3.0 : 1.0; }

    /** Studying a prop is free and keeps the prop. Returns false when the pattern was already known. */
    public boolean learn(String prop) {
        if (prop == null || prop.isBlank()) return false;
        if (known == null) known = new LinkedHashSet<>();
        return known.add(prop);
    }

    /** Why a new commission cannot start, or null when it can. The caller takes payment only on null. */
    public String commissionIssue(String prop) {
        if (!knows(prop)) return "Tobin has not studied this prop yet. Show him one first.";
        if (queue().size() >= queueLimit()) return "Tobin's bench is full. Wait for a commission to finish or cancel one.";
        if (shelved() + queue().size() >= SHELF_LIMIT) return "Tobin's shelf is full. Collect your finished props first.";
        return null;
    }
    public boolean commission(String prop, Map<String, Integer> paid) {
        if (commissionIssue(prop) != null) return false;
        if (queue == null) queue = new ArrayList<>();
        queue.add(new Commission(prop, 0, paid));
        return true;
    }
    /** Removes a commission and reports what it cost, so every part can be handed back. */
    public Map<String, Integer> cancel(int index) {
        if (queue == null || index < 0 || index >= queue.size()) return null;
        return queue.remove(index).paid();
    }

    /**
     * Attended bench time. Only the first commission is worked on, so the bench order is the build order.
     * Time left over when a prop is finished carries into the next one. Returns how many props were finished.
     */
    public int work(double seconds, ToDoubleFunction<String> secondsFor) {
        if (queue == null || queue.isEmpty() || !Double.isFinite(seconds) || seconds <= 0) return 0;
        double left = Math.min(seconds, 3600) * speed(); int finished = 0;
        while (left > 0 && !queue.isEmpty()) {
            var current = queue.getFirst();
            double needed = Math.max(1, secondsFor.applyAsDouble(current.prop()));
            if (current.worked() + left < needed) {
                queue.set(0, new Commission(current.prop(), current.worked() + left, current.paid()));
                break;
            }
            left -= needed - current.worked();
            queue.removeFirst();
            if (shelf == null) shelf = new LinkedHashMap<>();
            shelf.merge(current.prop(), 1, Integer::sum);
            finished++;
        }
        return finished;
    }
    /** Share of the first commission that is done, from 0 to 1. */
    public double progress(ToDoubleFunction<String> secondsFor) {
        if (queue().isEmpty()) return 0;
        var current = queue.getFirst();
        return Math.max(0, Math.min(1, current.worked() / Math.max(1, secondsFor.applyAsDouble(current.prop()))));
    }

    /** Takes one finished prop of this kind off the shelf. The caller hands the item over first. */
    public boolean take(String prop) {
        Integer count = shelf == null ? null : shelf.get(prop);
        if (count == null || count < 1) return false;
        if (count == 1) shelf.remove(prop); else shelf.put(prop, count - 1);
        return true;
    }
}
